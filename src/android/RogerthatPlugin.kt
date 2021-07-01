/*
 * Copyright 2019 Green Valley Belgium NV
 * NOTICE: THIS FILE HAS BEEN MODIFIED BY GREEN VALLEY BELGIUM NV IN ACCORDANCE WITH THE APACHE LICENSE VERSION 2.0
 * Copyright 2018 GIG Technology NV
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * @@license_version:1.6@@
 */
package com.mobicage.rogerthat.cordova

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.mobicage.api.news.Rpc.getNewsStreamItems
import com.mobicage.rogerth.at.R
import com.mobicage.rogerthat.BarcodeScanningActivity
import com.mobicage.rogerthat.IdentityStore
import com.mobicage.rogerthat.ServiceBoundActivity
import com.mobicage.rogerthat.home.HomeScreenContentResult
import com.mobicage.rogerthat.home.HomeScreenViewModel
import com.mobicage.rogerthat.home.UnsupportedHomeScreenVersion
import com.mobicage.rogerthat.plugins.friends.FriendsPlugin
import com.mobicage.rogerthat.plugins.friends.Poker
import com.mobicage.rogerthat.plugins.friends.ServiceApiCallbackResult
import com.mobicage.rogerthat.plugins.friends.ServiceMenuItemInfo
import com.mobicage.rogerthat.plugins.messaging.MessagingPlugin
import com.mobicage.rogerthat.plugins.news.NewsPlugin
import com.mobicage.rogerthat.plugins.scan.ScanCommunication
import com.mobicage.rogerthat.plugins.scan.ScanTabActivity
import com.mobicage.rogerthat.plugins.system.SystemPlugin
import com.mobicage.rogerthat.util.ActionScreenUtils
import com.mobicage.rogerthat.util.JsonUtils
import com.mobicage.rogerthat.util.RequestStore
import com.mobicage.rogerthat.util.logging.L
import com.mobicage.rogerthat.util.system.SafeRunnable
import com.mobicage.rpc.IJSONable
import com.mobicage.rpc.IncompleteMessageException
import com.mobicage.rpc.IntentResponseHandler
import com.mobicage.to.news.GetNewsGroupRequestTO
import com.mobicage.to.news.GetNewsStreamItemsRequestTO
import com.mobicage.to.news.GetNewsStreamItemsResponseTO
import com.mobicage.to.system.GetUserInformationRequestTO
import com.mobicage.to.system.GetUserInformationResponseTO
import org.apache.cordova.CallbackContext
import org.apache.cordova.CordovaPlugin
import org.apache.cordova.PluginResult
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.simple.JSONValue
import java.util.*

@Suppress("UNCHECKED_CAST")
class RogerthatPlugin : CordovaPlugin() {
    private var mQRCodeScannerOpen = false
    private var mCallbackContext: CallbackContext? = null
    private var mApiResultHandlerSet = false
    private var hasInitializedBadges = false
    private lateinit var rogerthatInterface: CordovaRogerthatInterface
    private lateinit var mActionScreenUtils: ActionScreenUtils
    private var mScanCommunication: ScanCommunication? = null
    private var mPoker: Poker<ServiceBoundActivity>? = null
    private var mBroadcastReceiver: BroadcastReceiver? = null
    private val callbackMap: MutableMap<String, CallbackContext> = HashMap()
    private val mIntentCallback: ActionScreenUtils.IntentCallback =
        object : ActionScreenUtils.IntentCallback {
            override fun apiResult(result: ServiceApiCallbackResult): Boolean {
                return deliverApiResult(result)
            }

            override fun userDataUpdated(userData: String?) {
                val jsonMap =
                    if (userData == null) HashMap() else (JSONValue.parse(userData) as Map<String, Any?>)
                sendCallbackUpdate("userDataUpdated", JSONObject(jsonMap))
            }

            override fun serviceDataUpdated(serviceData: String?) {
                val jsonMap =
                    if (serviceData == null) HashMap() else (JSONValue.parse(serviceData) as Map<String, Any?>)
                sendCallbackUpdate("serviceDataUpdated", JSONObject(jsonMap))
            }

            override fun qrCodeScanned(result: Map<String, Any>) {
                sendCallbackUpdate("qrCodeScanned", JSONObject(result))
            }

            override fun badgeUpdated(params: Map<String, Any>) {
                sendCallbackUpdate("badgeUpdated", JSONObject(params))
            }
        }
    private var homeScreenKey: String? = null
    private var homeScreenLiveData: LiveData<HomeScreenContentResult>? = null
    private var homeScreenObserver: Observer<HomeScreenContentResult>? = null
    private val userProfileCallbacks = mutableListOf<CallbackContext>()
    private val userInformationCallbacks = mutableListOf<CallbackContext>()
    private var cachedUserInformation: GetUserInformationResponseTO? = null

    override fun execute(
        action: String,
        args: JSONArray,
        callbackContext: CallbackContext
    ): Boolean {
        if ("log" != action) {
            L.i("RogerthatPlugin.execute '$action'")
        }
        cordova.activity.runOnUiThread {
            try {
                processAction(action, callbackContext, args.optJSONObject(0))
            } catch (e: Exception) {
                L.bug(e)
                callbackContext.error(e.message)
            }
        }
        return true
    }

    @Throws(JSONException::class, IncompleteMessageException::class)
    private fun processAction(
        action: String,
        callbackContext: CallbackContext,
        arguments: JSONObject?
    ) {
        val args = arguments ?: JSONObject()
        when (processAction(action)) {
            "start" -> {
                if (mCallbackContext != null) {
                    callbackContext.error("RogerthatPlugin already running.")
                    return
                }
                mCallbackContext = callbackContext
                mActionScreenUtils.start(mIntentCallback)
                val pluginResult = PluginResult(PluginResult.Status.NO_RESULT)
                pluginResult.keepCallback = true
                callbackContext.sendPluginResult(pluginResult)
                setInfo()
                setBadges()
            }
            "log" -> {
                log(args)
                callbackContext.success(JSONObject())
            }
            "api_call" -> sendApiCall(callbackContext, args)
            "api_resultHandlerConfigured" -> {
                mApiResultHandlerSet = true
                mActionScreenUtils.deliverAllApiResults()
                callbackContext.success(JSONObject())
            }
            "app_exit" -> exitApp(callbackContext)
            "camera_startScanningQrCode" -> startScanningQrCode(callbackContext)
            "camera_stopScanningQrCode" -> stopScanningQrCode(callbackContext)
            "context" -> getContext(callbackContext)
            "badges_list" -> getBadges(callbackContext)
            "message_open" -> openMessage(callbackContext, args)
            "news_getNewsGroup" -> getNewsGroup(
                callbackContext, GetNewsGroupRequestTO.fromJSONMap(
                    JsonUtils.toMap(args)
                )
            )
            "news_getNewsGroups" -> getNewsGroups(callbackContext)
            "news_getNewsStreamItems" -> getNewsStreamItems(
                callbackContext, GetNewsStreamItemsRequestTO.fromJSONMap(
                    JsonUtils.toMap(args)
                )
            )
            "ui_hideKeyboard" -> hideKeyboard(callbackContext)
            "user_put" -> putUserData(callbackContext, args)
            "user_getProfile" -> getUserProfile(callbackContext)
            "user_getUserInformation" -> getUserInformation(
                callbackContext, GetUserInformationRequestTO.fromJSONMap(
                    JsonUtils.toMap(args)
                )
            )
            "util_isConnectedToInternet" -> isConnectedToInternet(callbackContext)
            "util_open" -> openActivity(callbackContext, args)
            "util_playAudio" -> playAudio(callbackContext, args)
            "homescreen_getHomeScreenContent" -> getHomeScreen(callbackContext)
            else -> {
                L.e("RogerthatPlugin.execute did not match '$action'")
                callbackContext.error("RogerthatPlugin doesn't know how to execute this action.")
            }
        }
    }

    private fun processAction(action: String): String {
        return action
    }

    private fun getNewsGroup(callbackContext: CallbackContext, request: GetNewsGroupRequestTO) {
        callbackMap[getNewsPlugin().getNewsGroup(request)] = callbackContext
    }

    private fun getNewsGroups(callbackContext: CallbackContext) {
        callbackMap[getNewsPlugin().getNewsGroups()] = callbackContext
    }

    private fun getNewsStreamItems(
        callbackContext: CallbackContext,
        request: GetNewsStreamItemsRequestTO
    ) {
        val requestId = UUID.randomUUID().toString()
        val handler = IntentResponseHandler<GetNewsStreamItemsResponseTO>(
            NewsPlugin.GET_NEWS_STREAM_ITEMS_SUCCESS,
            NewsPlugin.GET_NEWS_STREAM_ITEMS_FAILED,
            requestId
        )
        callbackMap[requestId] = callbackContext
        getNewsStreamItems(handler, request, true)
    }

    override fun shouldAllowRequest(url: String): Boolean {
        val lowerCaseUrl = url.lowercase()
        return if (lowerCaseUrl.startsWith("http://") || lowerCaseUrl.startsWith("https://")) {
            true
        } else super.shouldAllowRequest(url)
    }

    override fun onOverrideUrlLoading(url: String): Boolean {
        L.i("Branding is loading url: $url")
        val uri = Uri.parse(url)
        val lowerCaseUrl = url.lowercase()
        if (lowerCaseUrl.startsWith(POKE)) {
            val tag = url.substring(POKE.length)
            poke(tag)
            return true
        } else if (lowerCaseUrl.startsWith("http://") || lowerCaseUrl.startsWith("https://")
            || lowerCaseUrl.startsWith("tel") || lowerCaseUrl.startsWith("sms") || lowerCaseUrl.startsWith(
                "mailto"
            )
        ) {
            val customTabsBuilder = CustomTabsIntent.Builder()
            val customTabsIntent = customTabsBuilder.build()
            customTabsIntent.launchUrl(cordova.activity, uri)
            return true
        }
        return super.onOverrideUrlLoading(url)
    }

    override fun onDestroy() {
        mActionScreenUtils.stop()
        getServiceBoundActivity().unregisterReceiver(mBroadcastReceiver)
        mPoker?.stop()
    }

    private fun returnArgsMissing(callbackContext: CallbackContext) {
        callbackContext.error("User did not specify data to encode")
    }

    private fun setInfo() {
        val label = rogerthatInterface.getItemLabel()
        val hash = rogerthatInterface.getItemTagHash()
        val menuItem: ServiceMenuItemInfo? = if (hash == null && label == null) {
            null
        } else {
            ServiceMenuItemInfo(label, hash)
        }
        val info: Map<String, Any?> = getFriendsPlugin().getRogerthatUserAndServiceInfo(
            rogerthatInterface.getServiceEmail(),
            rogerthatInterface.getServiceFriend(),
            menuItem
        )
        sendCallbackUpdate("setInfo", JSONObject(info))
    }

    /**
     * To be removed
     *
     */
    @Deprecated("use getBadges instead")
    private fun setBadges() {
        if (hasInitializedBadges) {
            L.d("Not initialising badges, they were already initialized")
            return
        }
        hasInitializedBadges = true
        val activity = getServiceBoundActivity()
        activity.mainService.badgesStore.observe(activity, { badges: Map<String, Int> ->
            for ((key, value) in badges) {
                val params: MutableMap<String, Any> = HashMap()
                params["key"] = key
                params["count"] = value
                mIntentCallback.badgeUpdated(params)
            }
        })
    }

    private fun log(args: JSONObject) {
        val errorMessage = JsonUtils.optString(args, "e", null)
        val message = JsonUtils.optString(args, "m", null)
        if (errorMessage != null) {
            mActionScreenUtils.logError(
                rogerthatInterface.getServiceEmail(), rogerthatInterface.getItemLabel(),
                rogerthatInterface.getItemCoords(), errorMessage
            )
        } else {
            L.i("[BRANDING] $message")
        }
    }

    private fun sendApiCall(callbackContext: CallbackContext, args: JSONObject?) {
        if (args == null) {
            returnArgsMissing(callbackContext)
            return
        }
        val method = JsonUtils.optString(args, "method", null)
        val params = JsonUtils.optString(args, "params", null)
        val tag = JsonUtils.optString(args, "tag", null)
        if (method == null) {
            callbackContext.error("'method' is a mandatory parameter")
            return
        }
        if (tag == null) {
            callbackContext.error("'tag' is a mandatory parameter")
            return
        }
        val synchronous = args.optBoolean("synchronous", true)
        getFriendsPlugin().sendApiCall(
            rogerthatInterface.getServiceEmail()!!,
            rogerthatInterface.getItemTagHash(),
            method,
            params,
            tag,
            synchronous,
            callbackContext
        )
        // Callback will be called with the response in case of synchronous calls
        if (!synchronous) {
            callbackContext.success(JSONObject())
        }
    }

    private fun deliverApiResult(r: ServiceApiCallbackResult): Boolean {
        if (!mApiResultHandlerSet) {
            L.i("apiCallResultHandler not set, thus not delivering any api call responses.")
            return false
        }
        try {
            val obj = JSONObject()
            obj.put("method", r.method)
            obj.put("result", r.result)
            obj.put("error", r.error)
            obj.put("tag", r.tag)
            sendCallbackUpdate("apiResult", obj)
        } catch (e: JSONException) {
            L.e("JSONException... This should never happen", e)
        }
        return true
    }

    private fun exitApp(callbackContext: CallbackContext) {
        getServiceBoundActivity().finish()
        callbackContext.success(JSONObject())
    }

    private fun startScanningQrCode(callbackContext: CallbackContext) {
        val activity = getServiceBoundActivity()
        if (mQRCodeScannerOpen) {
            error(
                callbackContext,
                "camera_was_already_open",
                activity.getString(R.string.camera_was_already_open)
            )
            return
        }
        val cameraPermission =
            ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA)
        if (cameraPermission != PackageManager.PERMISSION_GRANTED) {
            val continueRunnable: SafeRunnable = object : SafeRunnable() {
                @Throws(Exception::class)
                override fun safeRun() {
                    startScanningQrCode(callbackContext)
                }
            }
            val cancelRunnable: SafeRunnable = object : SafeRunnable() {
                @Throws(Exception::class)
                override fun safeRun() {
                    error(
                        callbackContext, "camera_permission_was_not_granted",
                        activity.getString(R.string.camera_permission_was_not_granted)
                    )
                }
            }
            if (getServiceBoundActivity().askPermissionIfNeeded(
                    Manifest.permission.CAMERA, Companion.PERMISSION_REQUEST_CAMERA,
                    continueRunnable, cancelRunnable
                )
            ) return
        }
        mQRCodeScannerOpen = true
        val intent = Intent(getServiceBoundActivity(), BarcodeScanningActivity::class.java)
        cordova.startActivityForResult(this, intent, ScanTabActivity.QR_SCAN_RESULT)
        callbackContext.success(JSONObject())
    }

    private fun stopScanningQrCode(callbackContext: CallbackContext) {
        mQRCodeScannerOpen = false
        val intent = Intent(BarcodeScanningActivity.FINISH_INTENT)
        cordova.activity.sendBroadcast(intent)
        callbackContext.success(JSONObject())
    }

    @Throws(JSONException::class)
    private fun getContext(callbackContext: CallbackContext) {
        val obj = JSONObject()
        val context = rogerthatInterface.getOpenContext()
        obj.put("context", if (context == null) JSONObject() else JSONObject(context))
        callbackContext.success(obj)
    }

    private fun getBadges(callbackContext: CallbackContext) {
        val activity = getServiceBoundActivity()
        activity.mainService.badgesStore.observe(
            activity,
            { badges: Map<String, Int> ->
                // Convert map to array
                val badgesArray = JSONArray()
                for ((key, value) in badges) {
                    val obj = JSONObject()
                    try {
                        obj.put("key", key)
                        obj.put("count", value)
                        badgesArray.put(obj)
                    } catch (e: JSONException) {
                        L.bug(e)
                    }
                }
                callbackContext.success(badgesArray)
            })
    }

    @Throws(JSONException::class)
    private fun openMessage(callbackContext: CallbackContext, args: JSONObject?) {
        if (args == null) {
            returnArgsMissing(callbackContext)
            return
        }
        val messageKey = JsonUtils.optString(args, "message_key", null)
        val message = getMessagingPlugin().getStore().getMessageByKey(messageKey, true)
        if (message == null) {
            val obj = JSONObject()
            obj.put("type", "MessageNotFound")
            callbackContext.error(obj)
            return
        }
        getMessagingPlugin().showMessage(getServiceBoundActivity(), message, false, null, false)
        callbackContext.success(JSONObject())
    }

    private fun error(callbackContext: CallbackContext?, code: String, errorMessage: String) {
        try {
            val obj = JSONObject()
            obj.put("code", code)
            obj.put("message", errorMessage)
            callbackContext?.error(obj)
        } catch (je: JSONException) {
            L.e("JSONException... This should never happen", je)
            callbackContext?.error("Could not process json...")
        }
    }

    private fun hideKeyboard(callbackContext: CallbackContext) {
        mActionScreenUtils.hideKeyboard(getServiceBoundActivity().currentFocus!!.windowToken)
        callbackContext.success(JSONObject())
    }

    private fun putUserData(callbackContext: CallbackContext, args: JSONObject?) {
        if (args == null) {
            returnArgsMissing(callbackContext)
            return
        }
        val data = JsonUtils.optString(args, "u", null)
        val smart = args.optBoolean("smart", false)
        val serviceEmail = rogerthatInterface.getServiceEmail()
        if (serviceEmail == null) {
            callbackContext.error("User data cannot be saved from a service-independent context")
            return
        }
        getFriendsPlugin().putUserData(serviceEmail, data, smart)
        callbackContext.success(JSONObject())
    }

    private fun getUserProfile(callbackContext: CallbackContext) {
        userProfileCallbacks.add(callbackContext)
        sendPluginResult(callbackContext, JSONObject(getFriendsPlugin().userInfo))
    }

    private fun getUserInformation(
        callbackContext: CallbackContext,
        request: GetUserInformationRequestTO
    ) {
        if (!rogerthatInterface.isEmbeddedApp()) {
            callbackContext.error("getUserInformation can only be called from embedded apps")
            return
        }
        if (cachedUserInformation != null) {
            sendPluginResult(callbackContext, JSONObject(cachedUserInformation!!.toJSONMap()))
        } else {
            getSystemPlugin().getUserInformation(request)
        }
        userInformationCallbacks.add(callbackContext)
    }

    private fun sendPluginResult(callbackContext: CallbackContext?, result: JSONObject) {
        val pluginResult = PluginResult(PluginResult.Status.OK, result)
        pluginResult.keepCallback = true
        callbackContext!!.sendPluginResult(pluginResult)
    }

    private fun notifyProfileChanges() {
        val userInfo = JSONObject(getFriendsPlugin().userInfo)
        for (context in userProfileCallbacks) {
            sendPluginResult(context, userInfo)
        }
    }

    @Throws(JSONException::class)
    private fun isConnectedToInternet(callbackContext: CallbackContext) {
        val manager = getServiceBoundActivity().mainService.networkConnectivityManager
        val wifiConnected = manager.isWifiConnected
        val obj = JSONObject()
        obj.put("connectedToWifi", wifiConnected)
        obj.put("connected", wifiConnected || manager.isMobileDataConnected)
        callbackContext.success(obj)
    }

    @Throws(JSONException::class)
    private fun openActivity(callbackContext: CallbackContext, args: JSONObject?) {
        if (args == null) {
            returnArgsMissing(callbackContext)
            return
        }
        val actionType = JsonUtils.optString(args, "action_type", null)
        val action = JsonUtils.optString(args, "action", null)
        val title = JsonUtils.optString(args, "title", null)
        val service = JsonUtils.optString(args, "service", null)
        val activityParams = args.optJSONObject("params")
        val errorMessage = mActionScreenUtils.openActivity(
            actionType, action, title, service,
            if (activityParams == null) null else JsonUtils.toMap(activityParams)
        )
        if (errorMessage != null) {
            error(callbackContext, "unknown_error_occurred", errorMessage)
            return
        }
        callbackContext.success(JSONObject())
    }

    @Throws(JSONException::class)
    private fun playAudio(callbackContext: CallbackContext, args: JSONObject?) {
        if (args == null) {
            returnArgsMissing(callbackContext)
            return
        }
        val url = JsonUtils.optString(args, "url", null)
        val fileOnDisk = "file://" + rogerthatInterface.getFilePath() + "/" + url
        mActionScreenUtils.playAudio(fileOnDisk)
        callbackContext.success(JSONObject())
    }

    private fun sendCallbackUpdate(callback: String, args: Boolean) {
        try {
            val obj = JSONObject()
            obj.put("callback", callback)
            obj.put("args", args)
            sendPluginResult(mCallbackContext, obj)
        } catch (e: JSONException) {
            L.e("JSONException... This should never happen", e)
        }
    }

    private fun sendCallbackUpdate(callback: String, args: JSONObject) {
        try {
            val obj = JSONObject()
            obj.put("callback", callback)
            obj.put("args", args)
            sendPluginResult(mCallbackContext, obj)
        } catch (e: JSONException) {
            L.e("JSONException... This should never happen", e)
        }
    }

    private fun poke(tag: String) {
        if (mPoker == null) {
            mPoker = Poker(getServiceBoundActivity(), rogerthatInterface.getServiceEmail()!!)
        }
        mPoker!!.poke(tag, null)
    }

    private fun getHomeScreen(callbackContext: CallbackContext) {
        if (homeScreenObserver != null) {
            callbackContext.error("You can only call getHomeScreen once")
            return
        }
        homeScreenObserver = Observer { (homeScreenData, error) ->
            if (error == null) {
                sendPluginResult(callbackContext, JSONObject(homeScreenData as MutableMap<*, *>))
            } else {
                if (error is UnsupportedHomeScreenVersion) {
                    val msg =
                        getServiceBoundActivity().getString(R.string.homescreen_update_required)
                    callbackContext.error(msg)
                } else {
                    callbackContext.error(error.message)
                }
            }
        }
        reloadHomeScreen()
    }

    private fun doGetHomeScreen(
        communityId: Long,
        homeScreenId: String
    ): LiveData<HomeScreenContentResult> {
        val model = ViewModelProvider(getServiceBoundActivity()).get(
            HomeScreenViewModel::class.java
        )
        return model.getHomeScreenContent(communityId, homeScreenId)
    }

    private fun reloadHomeScreen() {
        if (homeScreenObserver == null) {
            L.d("Not loading homeScreen: no observer set")
            return
        }
        val activity = getServiceBoundActivity()
        val identity = activity.mainService.identityStore!!.getIdentity()
        val key = String.format("%s/%s", identity.communityId, identity.homeScreenId)
        if (key == homeScreenKey) {
            // Home screen community and id haven't changed, don't do anything
            return
        }
        homeScreenKey = key
        if (homeScreenLiveData != null) {
            homeScreenLiveData!!.removeObserver(homeScreenObserver!!)
        }
        val liveData = doGetHomeScreen(identity.communityId, identity.homeScreenId)
        liveData.observe(activity, homeScreenObserver!!)
        homeScreenLiveData = liveData
    }

    private fun refreshPersonalInfo() {
        if (userInformationCallbacks.isNotEmpty()) {
            getSystemPlugin().getUserInformation(GetUserInformationRequestTO())
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent) {
        L.i("RogerthatPlugin.onActivityResult requestCode -> $requestCode")
        if (requestCode == ScanTabActivity.QR_SCAN_RESULT) {
            mQRCodeScannerOpen = false
            if (resultCode == Activity.RESULT_OK) {
                val rawScanResult = intent.getStringExtra(BarcodeScanningActivity.RAW_VALUE)
                try {
                    if (rawScanResult != null) {
                        L.i("Scanned QR code: $rawScanResult")
                        val result = JSONObject()
                        result.put("content", rawScanResult)
                        if (rawScanResult.lowercase(Locale.US).startsWith("http://")
                            || rawScanResult.lowercase(Locale.US).startsWith("https://")
                        ) {
                            if (mScanCommunication == null) {
                                mScanCommunication =
                                    ScanCommunication(getServiceBoundActivity().mainService)
                            }
                            mScanCommunication!!.resolveUrl(rawScanResult)
                            result.put("status", "resolving")
                        } else {
                            result.put("status", "resolved")
                        }
                        sendCallbackUpdate("qrCodeScanned", result)
                    } else {
                        val obj = JSONObject()
                        obj.put("status", "error")
                        obj.put("content", "An unknown error has occurred")
                        sendCallbackUpdate("qrCodeScanned", obj)
                    }
                } catch (e: JSONException) {
                    L.e("JSONException... This should never happen", e)
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, intent)
    }

    override fun pluginInitialize() {
        setRogerthatInterface()
        mBroadcastReceiver = getBroadcastReceiver()
        getServiceBoundActivity().registerReceiver(mBroadcastReceiver, getIntentFilter())
    }

    private fun getServiceBoundActivity(): ServiceBoundActivity {
        val activity = cordova.activity
        if (activity is ServiceBoundActivity) {
            return activity
        }
        val msg = String.format(
            "Expected activity using RogerthatPlugin to inherit from ServiceBoundActivity: %s",
            activity.toString()
        )
        L.bug(msg)
        throw RuntimeException(msg)
    }

    private fun getIntentFilter(): IntentFilter {
        val filter = IntentFilter()
        filter.addAction(NewsPlugin.GET_NEWS_GROUP_SUCCESS)
        filter.addAction(NewsPlugin.GET_NEWS_GROUP_FAILED)
        filter.addAction(NewsPlugin.GET_NEWS_GROUPS_SUCCESS)
        filter.addAction(NewsPlugin.GET_NEWS_GROUPS_FAILED)
        filter.addAction(NewsPlugin.GET_NEWS_STREAM_ITEMS_SUCCESS)
        filter.addAction(NewsPlugin.GET_NEWS_STREAM_ITEMS_FAILED)
        filter.addAction(IdentityStore.IDENTITY_CHANGED_INTENT)
        filter.addAction(SystemPlugin.GET_USER_INFORMATION_SUCCESS)
        filter.addAction(SystemPlugin.GET_USER_INFORMATION_FAILED)
        filter.addAction(SystemPlugin.ADD_ADDRESSES_SUCCESS)
        filter.addAction(SystemPlugin.EDIT_ADDRESSES_SUCCESS)
        filter.addAction(SystemPlugin.DELETE_ADDRESSES_SUCCESS)
        filter.addAction(SystemPlugin.ADD_PHONE_NUMBERS_SUCCESS)
        filter.addAction(SystemPlugin.EDIT_PHONE_NUMBERS_SUCCESS)
        filter.addAction(SystemPlugin.DELETE_PHONE_NUMBERS_SUCCESS)
        return filter
    }

    // Some other activity may have executed this, ignore
    private fun getBroadcastReceiver(): BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action ?: return
            val requestId = intent.getStringExtra(IntentResponseHandler.REQUEST_ID)
            var callbackContext: CallbackContext? = null
            if (requestId != null) {
                callbackContext = callbackMap[requestId]
            }
            when (action) {
                NewsPlugin.GET_NEWS_GROUP_SUCCESS,
                NewsPlugin.GET_NEWS_GROUPS_SUCCESS,
                NewsPlugin.GET_NEWS_STREAM_ITEMS_SUCCESS,
                SystemPlugin.GET_USER_INFORMATION_SUCCESS -> {
                    val response = RequestStore.getResponse(requestId)
                    val successObj = JSONObject(response!!.toJSONMap())
                    cachedUserInformation = response as GetUserInformationResponseTO
                    for (callback in userInformationCallbacks) {
                        sendPluginResult(callback, successObj)
                    }
                }
                NewsPlugin.GET_NEWS_GROUP_FAILED,
                NewsPlugin.GET_NEWS_GROUPS_FAILED,
                NewsPlugin.GET_NEWS_STREAM_ITEMS_FAILED,
                SystemPlugin.GET_USER_INFORMATION_FAILED -> {
                    val err =
                        intent.getSerializableExtra(IntentResponseHandler.ERROR) as Exception
                    L.e(err)
                    error(
                        callbackContext,
                        "unknown",
                        getServiceBoundActivity().getString(R.string.unknown_error_occurred)
                    )
                }
                IdentityStore.IDENTITY_CHANGED_INTENT -> {
                    reloadHomeScreen()
                    notifyProfileChanges()
                }
                SystemPlugin.ADD_ADDRESSES_SUCCESS,
                SystemPlugin.EDIT_ADDRESSES_SUCCESS,
                SystemPlugin.DELETE_ADDRESSES_SUCCESS,
                SystemPlugin.ADD_PHONE_NUMBERS_SUCCESS,
                SystemPlugin.EDIT_PHONE_NUMBERS_SUCCESS,
                SystemPlugin.DELETE_PHONE_NUMBERS_SUCCESS -> refreshPersonalInfo()
            }
        }
    }

    private fun setRogerthatInterface() {
        val activity = cordova.activity
        if (activity is CordovaRogerthatInterface) {
            rogerthatInterface = activity
        } else if (activity is CordovaRogerthatInterfaceGetter) {
            rogerthatInterface =
                (activity as CordovaRogerthatInterfaceGetter).getRogerthatCordovaInterface()
        }
        mActionScreenUtils = ActionScreenUtils(
            getServiceBoundActivity(),
            rogerthatInterface.getServiceEmail(),
            rogerthatInterface.getItemTagHash(),
            true
        )
    }

    private fun getNewsPlugin() =
        getServiceBoundActivity().mainService.getPlugin(NewsPlugin::class.java)

    private fun getFriendsPlugin() =
        getServiceBoundActivity().mainService.getPlugin(FriendsPlugin::class.java)

    private fun getMessagingPlugin() =
        getServiceBoundActivity().mainService.getPlugin(MessagingPlugin::class.java)

    private fun getSystemPlugin() =
        getServiceBoundActivity().mainService.getPlugin(SystemPlugin::class.java)

    companion object {
        private const val POKE = "poke://"
        private const val PERMISSION_REQUEST_CAMERA = 1
    }
}

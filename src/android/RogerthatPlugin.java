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

package com.mobicage.rogerthat.cordova;


import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;

import com.google.zxing.client.android.CaptureActivity;
import com.google.zxing.client.android.Intents;
import com.mobicage.rogerth.at.R;
import com.mobicage.rogerthat.MainService;
import com.mobicage.rogerthat.plugins.friends.ActionScreenActivity;
import com.mobicage.rogerthat.plugins.friends.Poker;
import com.mobicage.rogerthat.plugins.friends.ServiceApiCallbackResult;
import com.mobicage.rogerthat.plugins.friends.ServiceMenuItemInfo;
import com.mobicage.rogerthat.plugins.messaging.Message;
import com.mobicage.rogerthat.plugins.news.NewsItem;
import com.mobicage.rogerthat.plugins.news.NewsPlugin;
import com.mobicage.rogerthat.plugins.scan.ScanCommunication;
import com.mobicage.rogerthat.plugins.scan.ScanTabActivity;
import com.mobicage.rogerthat.util.ActionScreenUtils;
import com.mobicage.rogerthat.util.JsonUtils;
import com.mobicage.rogerthat.util.RequestStore;
import com.mobicage.rogerthat.util.logging.L;
import com.mobicage.rogerthat.util.system.SafeRunnable;
import com.mobicage.rpc.IJSONable;
import com.mobicage.rpc.IncompleteMessageException;
import com.mobicage.rpc.IntentResponseHandler;
import com.mobicage.rpc.config.CloudConstants;
import com.mobicage.to.news.GetNewsGroupRequestTO;
import com.mobicage.to.news.GetNewsGroupsRequestTO;
import com.mobicage.to.news.GetNewsStreamItemsRequestTO;
import com.mobicage.to.news.GetNewsStreamItemsResponseTO;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.simple.JSONValue;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import androidx.browser.customtabs.CustomTabsIntent;
import androidx.core.content.ContextCompat;


public class RogerthatPlugin extends CordovaPlugin {

    private boolean mQRCodeScannerOpen = false;
    private final int PERMISSION_REQUEST_CAMERA = 1;

    private CallbackContext mCallbackContext = null;

    private boolean mApiResultHandlerSet = false;
    private boolean mIsListeningBacklogConnectivityChanged = false;

    private CordovaActionScreenActivity mActivity = null;
    private ScanCommunication mScanCommunication = null;

    private static final String POKE = "poke://";
    private Poker<CordovaActionScreenActivity> mPoker;

    protected final static String[] permissions = {Manifest.permission.CAMERA};

    private ServiceMenuItemInfo mMenuItem;

    private BroadcastReceiver mBroadcastReceiver;
    private Map<String, CallbackContext> callbackMap = new HashMap<>();
    private ActionScreenUtils.IntentCallback mIntentCallback = new ActionScreenUtils.IntentCallback() {
        @Override
        public boolean apiResult(ServiceApiCallbackResult result) {
            return deliverApiResult(result);
        }

        @Override
        public void userDataUpdated(String userData) {
            Map<String, Object> jsonMap = userData == null ? new HashMap<String, Object>() : (Map<String, Object>) JSONValue.parse(userData);
            sendCallbackUpdate("userDataUpdated", new JSONObject(jsonMap));
        }

        @Override
        public void serviceDataUpdated(String serviceData) {
            Map<String, Object> jsonMap = serviceData == null ? new HashMap<String, Object>() : (Map<String, Object>) JSONValue.parse(serviceData);
            sendCallbackUpdate("serviceDataUpdated", new JSONObject(jsonMap));
        }

        @Override
        public void qrCodeScanned(Map<String, Object> result) {
            sendCallbackUpdate("qrCodeScanned", new JSONObject(result));
        }

        @Override
        public void onBackendConnectivityChanged(boolean connected) {
            sendCallbackUpdate("onBackendConnectivityChanged", connected);
        }

        @Override
        public void badgeUpdated(Map<String, Object> params) {
            sendCallbackUpdate("badgeUpdated", new JSONObject(params));
        }
    };

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) {
        if (!"log".equals(action)) {
            L.i("RogerthatPlugin.execute '" + action + "'");
        }
        cordova.getActivity().runOnUiThread(() -> {
            try {
                if (action == null) {
                    callbackContext.error("Cannot execute 'null' action");
                    return;
                }
                processAction(action, callbackContext, args.optJSONObject(0));
            } catch (Exception e) {
                L.bug(e);
                callbackContext.error(e.getMessage());
            }
        });
        return true;
    }

    private void processAction(String action, CallbackContext callbackContext, JSONObject args) throws JSONException, IncompleteMessageException {
        if (args == null) {
            args = new JSONObject();
        }
        switch (processAction(action)) {
            case "start":
                if (mCallbackContext != null) {
                    callbackContext.error("RogerthatPlugin already running.");
                    return;
                }
                mCallbackContext = callbackContext;

                mActivity.getActionScreenUtils().start(mIntentCallback);

                PluginResult pluginResult = new PluginResult(PluginResult.Status.NO_RESULT);
                pluginResult.setKeepCallback(true);
                mCallbackContext.sendPluginResult(pluginResult);

                setInfo();
                setBadges();

                break;
            case "log":
                log(args);
                callbackContext.success(new JSONObject());
                break;
            case "api_call":
                sendApiCall(callbackContext, args);

                break;
            case "api_resultHandlerConfigured":
                mApiResultHandlerSet = true;
                mActivity.getActionScreenUtils().deliverAllApiResults();
                callbackContext.success(new JSONObject());

                break;
            case "app_exit":
                exitApp(callbackContext);

                break;
            case "app_exitWithResult":
                exitAppWithResult(callbackContext, args);
                break;
            case "camera_startScanningQrCode":
                startScanningQrCode(callbackContext);
                break;
            case "camera_stopScanningQrCode":
                stopScanningQrCode(callbackContext);

                break;
            case "context":
                getContext(callbackContext);
                break;
            case "message_open":
                openMessage(callbackContext, args);
                break;
            case "news_getNewsGroup":
                getNewsGroup(callbackContext, new GetNewsGroupRequestTO(JsonUtils.toMap(args)));
                break;
            case "news_getNewsGroups":
                getNewsGroups(callbackContext, new GetNewsGroupsRequestTO(JsonUtils.toMap(args)));
                break;
            case "news_getNewsStreamItems":
                getNewsStreamItems(callbackContext, new GetNewsStreamItemsRequestTO(JsonUtils.toMap(args)));
                break;
            case "security_createKeyPair":
                createKeyPair(callbackContext, args);
                break;
            case "security_hasKeyPair":
                hasKeyPair(callbackContext, args);
                break;
            case "security_getPublicKey":
                getPublicKey(callbackContext, args);
                break;
            case "security_getSeed":
                getSeed(callbackContext, args);
                break;
            case "security_listAddresses":
                listAddresses(callbackContext, args);
                break;
            case "security_getAddress":
                getAddress(callbackContext, args);
                break;
            case "security_sign":
                signPayload(callbackContext, args);
                break;
            case "security_verify":
                verifySignedPayload(callbackContext, args);
                break;
            case "security_listKeyPairs":
                listKeyPairs(callbackContext, args);
                break;
            case "system_onBackendConnectivityChanged":
                onBackendConnectivityChanged(callbackContext);
                break;
            case "ui_hideKeyboard":
                hideKeyboard(callbackContext);
                break;
            case "user_put":
                putUserData(callbackContext, args);
                break;
            case "util_embeddedAppTranslations":
                embeddedAppTranslations(callbackContext);
                break;
            case "util_isConnectedToInternet":
                isConnectedToInternet(callbackContext);
                break;
            case "util_open":
                openActivity(callbackContext, args);
                break;
            case "util_playAudio":
                playAudio(callbackContext, args);
                break;
            default:
                L.e("RogerthatPlugin.execute did not match '" + action + "'");
                callbackContext.error("RogerthatPlugin doesn't know how to execute this action.");
                break;
        }
    }

    private String processAction(String action) {
        return action;
    }

    private void getNewsGroup(CallbackContext callbackContext, GetNewsGroupRequestTO request) {
        callbackMap.put(mActivity.getNewsPlugin().getNewsGroup(request), callbackContext);
    }

    private void getNewsGroups(CallbackContext callbackContext, GetNewsGroupsRequestTO request) {
        callbackMap.put(mActivity.getNewsPlugin().getNewsGroups(), callbackContext);
    }

    private void getNewsStreamItems(CallbackContext callbackContext, GetNewsStreamItemsRequestTO request) {
        String requestId = UUID.randomUUID().toString();
        IntentResponseHandler<GetNewsStreamItemsResponseTO> handler = new IntentResponseHandler<>(NewsPlugin.GET_NEWS_STREAM_ITEMS_SUCCESS, NewsPlugin.GET_NEWS_STREAM_ITEMS_FAILED, requestId);
        callbackMap.put(requestId, callbackContext);
        com.mobicage.api.news.Rpc.getNewsStreamItems(handler, request, true);
    }

    public Boolean shouldAllowRequest(String url) {
        final String lowerCaseUrl = url.toLowerCase();
        if (lowerCaseUrl.startsWith("http://") || lowerCaseUrl.startsWith("https://")) {
            return true;
        }

        return super.shouldAllowRequest(url);
    }

    public boolean onOverrideUrlLoading(String url) {
        L.i("Branding is loading url: " + url);
        CordovaActionScreenActivity activity = getActivity();
        Uri uri = Uri.parse(url);
        String lowerCaseUrl = url.toLowerCase();
        if (lowerCaseUrl.startsWith(POKE)) {
            String tag = url.substring(POKE.length());
            poke(tag);
            return true;
        } else if (lowerCaseUrl.startsWith("http://") || lowerCaseUrl.startsWith("https://")
            || lowerCaseUrl.startsWith("tel") || lowerCaseUrl.startsWith("sms") || lowerCaseUrl.startsWith("mailto")) {
            CustomTabsIntent.Builder customTabsBuilder = new CustomTabsIntent.Builder();
            CustomTabsIntent customTabsIntent = customTabsBuilder.build();
            customTabsIntent.launchUrl(activity, uri);
            return true;
        }
        return super.onOverrideUrlLoading(url);
    }


    public void onDestroy() {
        if (mIsListeningBacklogConnectivityChanged) {
            mActivity.getActionScreenUtils().stopBacklogListener();
        }
        mActivity.getActionScreenUtils().stop();
        mActivity.unregisterReceiver(mBroadcastReceiver);
    }

    private void returnArgsMissing(final CallbackContext callbackContext) {
        callbackContext.error("User did not specify data to encode");
    }

    private void setInfo() throws JSONException {
        Map<String, Object> info = mActivity.getFriendsPlugin().getRogerthatUserAndServiceInfo(
            mActivity.getServiceEmail(), mActivity.getServiceFriend(), mMenuItem);
        sendCallbackUpdate("setInfo", new JSONObject(info));
    }

    private void setBadges() {
        for (Map.Entry<String, Long> entry : mActivity.getMainService().getBadges().entrySet()) {
            Map<String, Object> params = new HashMap<>();
            params.put("key", entry.getKey());
            params.put("count", entry.getValue());
            mIntentCallback.badgeUpdated(params);
        }
    }

    private void log(final JSONObject args) throws JSONException {
        final String errorMessage = JsonUtils.optString(args, "e", null);
        final String message = JsonUtils.optString(args, "m", null);
        if (errorMessage != null) {
            mActivity.getActionScreenUtils().logError(mActivity.getServiceEmail(), mActivity.getItemLabel(),
                mActivity.getItemCoords(), errorMessage);
        } else {
            L.i("[BRANDING] " + message);
        }
    }

    private void sendApiCall(final CallbackContext callbackContext, final JSONObject args) {
        if (args == null) {
            returnArgsMissing(callbackContext);
            return;
        }
        final String method = JsonUtils.optString(args, "method", null);
        final String params = JsonUtils.optString(args, "params", null);
        final String tag = JsonUtils.optString(args, "tag", null);
        final boolean synchronous = args.optBoolean("synchronous", true);

        mActivity.getFriendsPlugin().sendApiCall(mActivity.getServiceEmail(), mActivity.getItemTagHash(), method, params, tag, synchronous, callbackContext);
        // Callback will be called with the response in case of synchronous calls
        if (!synchronous) {
            callbackContext.success(new JSONObject());
        }
    }

    private boolean deliverApiResult(ServiceApiCallbackResult r) {
        if (!mApiResultHandlerSet) {
            L.i("apiCallResultHandler not set, thus not delivering any api call responses.");
            return false;
        }
        try {
            JSONObject obj = new JSONObject();
            obj.put("method", r.method);
            obj.put("result", r.result);
            obj.put("error", r.error);
            obj.put("tag", r.tag);
            sendCallbackUpdate("apiResult", obj);
        } catch (JSONException e) {
            L.e("JSONException... This should never happen", e);
        }
        return true;
    }

    private void exitApp(final CallbackContext callbackContext) throws JSONException {
        mActivity.finish();
        callbackContext.success(new JSONObject());
    }

    private void exitAppWithResult(final CallbackContext callbackContext, final JSONObject args) throws JSONException {
        final String result = JsonUtils.optString(args, "result", null);
        if (result != null) {
            Intent resultIntent = new Intent(ActionScreenActivity.EXIT_APP);
            resultIntent.putExtra(ActionScreenActivity.EXIT_APP_RESULT, result);
            mActivity.setResult(Activity.RESULT_OK, resultIntent);
        }
        mActivity.finish();
        callbackContext.success(new JSONObject());
    }

    private void startScanningQrCode(final CallbackContext callbackContext) throws JSONException {
        if (mQRCodeScannerOpen) {
            error(callbackContext, "camera_was_already_open", mActivity.getString(R.string.camera_was_already_open));
            return;
        }

        int cameraPermission = ContextCompat.checkSelfPermission(mActivity, Manifest.permission.CAMERA);
        if (cameraPermission != PackageManager.PERMISSION_GRANTED) {
            final SafeRunnable continueRunnable = new SafeRunnable() {
                @Override
                protected void safeRun() throws Exception {
                    startScanningQrCode(callbackContext);
                }
            };
            final SafeRunnable cancelRunnable = new SafeRunnable() {
                @Override
                protected void safeRun() throws Exception {
                    error(callbackContext, "camera_permission_was_not_granted",
                        mActivity.getString(R.string.camera_permission_was_not_granted));
                }
            };
            if (mActivity.askPermissionIfNeeded(Manifest.permission.CAMERA, PERMISSION_REQUEST_CAMERA,
                continueRunnable, cancelRunnable))
                return;
        }

        mQRCodeScannerOpen = true;

        final Intent intent = new Intent(mActivity, CaptureActivity.class);
        intent.setAction(Intents.Scan.ACTION);
        intent.putExtra(Intents.Scan.MODE, Intents.Scan.QR_CODE_MODE);
        this.cordova.startActivityForResult(this, intent, ScanTabActivity.ZXING_SCAN_RESULT);

        callbackContext.success(new JSONObject());
    }

    private void stopScanningQrCode(final CallbackContext callbackContext) throws JSONException {
        mQRCodeScannerOpen = false;
        Intent intent = new Intent(CaptureActivity.FINISH_INTENT);
        cordova.getActivity().sendBroadcast(intent);
        callbackContext.success(new JSONObject());
    }

    private void getContext(final CallbackContext callbackContext) throws JSONException {
        JSONObject obj = new JSONObject();
        String context = mActivity.getContext();
        obj.put("context", context == null ? new JSONObject() : new JSONObject(context));
        callbackContext.success(obj);
    }

    private void openMessage(final CallbackContext callbackContext, final JSONObject args) throws JSONException {
        if (args == null) {
            returnArgsMissing(callbackContext);
            return;
        }
        final String messageKey = JsonUtils.optString(args, "message_key", null);
        final Message message = mActivity.getMessagingPlugin().getStore().getMessageByKey(messageKey, true);
        if (message == null) {
            JSONObject obj = new JSONObject();
            obj.put("type", "MessageNotFound");
            callbackContext.error(obj);
            return;
        }
        mActivity.getMessagingPlugin().showMessage(mActivity, message, false, null, false);
        callbackContext.success(new JSONObject());
    }

    private abstract class SecurityCallback<T> implements MainService.SecurityCallback<T> {
        private CallbackContext callbackContext;

        public SecurityCallback(CallbackContext callbackContext) {
            this.callbackContext = callbackContext;
        }

        public abstract void populateResult(final T result, final JSONObject obj) throws JSONException;

        @Override
        public void onSuccess(T result) {
            try {
                JSONObject obj = new JSONObject();
                populateResult(result, obj);
                callbackContext.success(obj);
            } catch (JSONException je) {
                L.e("JSONException... This should never happen", je);
                callbackContext.error("Could not process json...");
            }
        }

        @Override
        public void onError(String code, String errorMessage) {
            error(callbackContext, code, errorMessage);
        }

    }

    private void error(final CallbackContext callbackContext, String code, String errorMessage) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("code", code);
            obj.put("message", errorMessage);
            callbackContext.error(obj);
        } catch (JSONException je) {
            L.e("JSONException... This should never happen", je);
            callbackContext.error("Could not process json...");
        }
    }

    private void createKeyPair(final CallbackContext callbackContext, final JSONObject args) throws JSONException {
        SecurityCallback<MainService.CreateKeyPairResult> sc = new SecurityCallback<MainService.CreateKeyPairResult>(callbackContext) {
            @Override
            public void populateResult(MainService.CreateKeyPairResult r, JSONObject obj) throws JSONException {
                obj.put("public_key", r.publicKey);
                obj.put("seed", r.seed);
            }
        };

        mActivity.getActionScreenUtils().createKeyPair(args, sc);
    }

    private void hasKeyPair(final CallbackContext callbackContext, final JSONObject args) throws JSONException {
        SecurityCallback<Boolean> sc = new SecurityCallback<Boolean>(callbackContext) {
            @Override
            public void populateResult(Boolean exists, JSONObject obj) throws JSONException {
                obj.put("exists", exists);
            }
        };

        mActivity.getActionScreenUtils().hasKeyPair(args, sc);
    }

    private void getPublicKey(final CallbackContext callbackContext, final JSONObject args) throws JSONException {
        SecurityCallback<String> sc = new SecurityCallback<String>(callbackContext) {
            @Override
            public void populateResult(String publicKey, JSONObject obj) throws JSONException {
                obj.put("public_key", publicKey);
            }

            @Override
            public void onSuccess(String publicKey) {
                if (publicKey == null) {
                    onError("key_not_found", mActivity.getString(R.string.key_not_found));
                } else {
                    super.onSuccess(publicKey);
                }
            }
        };

        mActivity.getActionScreenUtils().getPublicKey(args, sc);
    }

    private void getSeed(final CallbackContext callbackContext, final JSONObject args) throws JSONException {
        SecurityCallback<String> sc = new SecurityCallback<String>(callbackContext) {
            @Override
            public void populateResult(String seed, JSONObject obj) throws JSONException {
                obj.put("seed", seed);
            }
        };

        mActivity.getActionScreenUtils().getSeed(args, sc);
    }

    private void listAddresses(final CallbackContext callbackContext, final JSONObject args) throws JSONException {
        SecurityCallback<List<Map<String, String>>> sc = new SecurityCallback<List<Map<String, String>>>(callbackContext) {
            @Override
            public void populateResult(List<Map<String, String>> addresses, JSONObject obj) throws JSONException {
                obj.put("addresses", addresses);
            }
        };

        mActivity.getActionScreenUtils().listAddresses(args, sc);
    }

    private void getAddress(final CallbackContext callbackContext, final JSONObject args) throws JSONException {
        SecurityCallback<String> sc = new SecurityCallback<String>(callbackContext) {
            @Override
            public void populateResult(String address, JSONObject obj) throws JSONException {
                obj.put("address", address);
            }
        };

        mActivity.getActionScreenUtils().getAddress(args, sc);
    }

    private void signPayload(final CallbackContext callbackContext, final JSONObject args) throws JSONException {
        final String payload = JsonUtils.optString(args, "payload", null);

        SecurityCallback<String> sc = new SecurityCallback<String>(callbackContext) {
            @Override
            public void populateResult(String result, JSONObject obj) throws JSONException {
            }

            @Override
            public void onSuccess(String payloadSignature) {
                try {
                    JSONObject obj = new JSONObject();
                    obj.put("payload", payload);
                    obj.put("payload_signature", payloadSignature);
                    callbackContext.success(obj);
                } catch (Exception e) {
                    L.bug("signPayload onSuccess exception", e);
                    onError("unknown_error_occurred", mActivity.getString(R.string.unknown_error_occurred));
                }
            }
        };

        mActivity.getActionScreenUtils().signPayload(args, payload, sc);
    }

    private void verifySignedPayload(final CallbackContext callbackContext, final JSONObject args) throws JSONException {
        SecurityCallback<Boolean> sc = new SecurityCallback<Boolean>(callbackContext) {
            @Override
            public void populateResult(Boolean valid, JSONObject obj) throws JSONException {
                obj.put("valid", valid);
            }
        };

        mActivity.getActionScreenUtils().verifySignedPayload(args, sc);
    }

    private void listKeyPairs(final CallbackContext callbackContext, final JSONObject args) {
        SecurityCallback<List<Map<String, String>>> sc = new SecurityCallback<List<Map<String, String>>>(callbackContext) {
            @Override
            public void populateResult(List<Map<String, String>> keyPairs, JSONObject obj) throws JSONException {
                JSONArray array = new JSONArray();
                for (Map<String, String> pair : keyPairs) {
                    JSONObject jsonPair = new JSONObject();
                    for (String key : pair.keySet()) {
                        jsonPair.put(key, pair.get(key));
                    }
                    array.put(jsonPair);
                }
                obj.put("keyPairs", array);
            }
        };

        mActivity.getActionScreenUtils().listKeyPairs(args, sc);
    }

    private void onBackendConnectivityChanged(final CallbackContext callbackContext) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("connected", mActivity.getMainService().isBacklogConnected());
        callbackContext.success(obj);

        if (!mIsListeningBacklogConnectivityChanged && CloudConstants.USE_XMPP_KICK_CHANNEL) {
            mActivity.getActionScreenUtils().startBacklogListener();
            mIsListeningBacklogConnectivityChanged = true;
        }
    }

    private void hideKeyboard(final CallbackContext callbackContext) {
        mActivity.getActionScreenUtils().hideKeyboard(mActivity.getCurrentFocus().getWindowToken());
        callbackContext.success(new JSONObject());
    }

    private void putUserData(final CallbackContext callbackContext, final JSONObject args) throws JSONException {
        if (args == null) {
            returnArgsMissing(callbackContext);
            return;
        }
        final String data = JsonUtils.optString(args, "u", null);
        boolean smart = args.optBoolean("smart", false);
        mActivity.getFriendsPlugin().putUserData(mActivity.getServiceEmail(), data, smart);
        callbackContext.success(new JSONObject());
    }

    private void embeddedAppTranslations(final CallbackContext callbackContext) throws JSONException {
        String embeddedApp = mActivity.getEmbeddedApp();
        if (embeddedApp == null) {
            error(callbackContext, "unknown_error_occurred", mActivity.getString(R.string.unknown_error_occurred));
            return;
        }

        String translations = mActivity.getSystemPlugin().getStore().getEmbeddedAppTranslations(embeddedApp);
        JSONObject obj = new JSONObject();
        obj.put("translations", translations == null ? new JSONObject() : new JSONObject(translations));
        callbackContext.success(obj);
    }

    private void isConnectedToInternet(final CallbackContext callbackContext) throws JSONException {
        boolean wifiConnected = mActivity.getMainService().getNetworkConnectivityManager().isWifiConnected();
        JSONObject obj = new JSONObject();
        obj.put("connectedToWifi", wifiConnected);
        obj.put("connected", wifiConnected || mActivity.getMainService().getNetworkConnectivityManager().isMobileDataConnected());
        callbackContext.success(obj);
    }

    private void openActivity(final CallbackContext callbackContext, final JSONObject args) throws JSONException {
        if (args == null) {
            returnArgsMissing(callbackContext);
            return;
        }
        final String actionType = JsonUtils.optString(args, "action_type", null);
        final String action = JsonUtils.optString(args, "action", null);
        final String title = JsonUtils.optString(args, "title", null);
        final String service = JsonUtils.optString(args, "service", null);
        final JSONObject activityParams = args.optJSONObject("params");

        String errorMessage = mActivity.getActionScreenUtils().openActivity(actionType, action, title, service,
            activityParams == null ? null : JsonUtils.toMap(activityParams));
        if (errorMessage != null) {
            error(callbackContext, "unknown_error_occurred", errorMessage);
            return;
        }
        callbackContext.success(new JSONObject());
    }

    private void playAudio(final CallbackContext callbackContext, final JSONObject args) throws JSONException {
        if (args == null) {
            returnArgsMissing(callbackContext);
            return;
        }
        final String url = JsonUtils.optString(args, "url", null);
        String fileOnDisk = "file://" + mActivity.getBrandingResult().dir.getAbsolutePath() + "/" + url;
        mActivity.getActionScreenUtils().playAudio(fileOnDisk);
        callbackContext.success(new JSONObject());
    }

    private void sendCallbackUpdate(String callback, boolean args) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("callback", callback);
            obj.put("args", args);
            PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, obj);
            pluginResult.setKeepCallback(true);
            mCallbackContext.sendPluginResult(pluginResult);
        } catch (JSONException e) {
            L.e("JSONException... This should never happen", e);
        }
    }

    private void sendCallbackUpdate(String callback, JSONObject args) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("callback", callback);
            obj.put("args", args);
            PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, obj);
            pluginResult.setKeepCallback(true);
            mCallbackContext.sendPluginResult(pluginResult);
        } catch (JSONException e) {
            L.e("JSONException... This should never happen", e);
        }
    }

    private void poke(String tag) {
        CordovaActionScreenActivity activity = getActivity();
        if (mPoker == null) {
            mPoker = new Poker<>(activity, activity.getServiceEmail());
        }

        mPoker.poke(tag, null);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        L.i("RogerthatPlugin.onActivityResult requestCode -> " + requestCode);
        if (requestCode == ScanTabActivity.ZXING_SCAN_RESULT) {
            mQRCodeScannerOpen = false;
            if (resultCode == Activity.RESULT_OK) {
                L.i("onActivityResult ZXING_SCAN_RESULT");
                mQRCodeScannerOpen = false;
                final String rawScanResult = intent.getStringExtra("SCAN_RESULT");
                try {
                    if (rawScanResult != null) {
                        L.i("Scanned QR code: " + rawScanResult);
                        JSONObject result = new JSONObject();
                        result.put("content", rawScanResult);
                        if (rawScanResult.toLowerCase(Locale.US).startsWith("http://")
                            || rawScanResult.toLowerCase(Locale.US).startsWith("https://")) {
                            if (mScanCommunication == null) {
                                mScanCommunication = new ScanCommunication(mActivity.getMainService());
                            }
                            mScanCommunication.resolveUrl(rawScanResult);
                            result.put("status", "resolving");

                        } else {
                            result.put("status", "resolved");
                        }
                        sendCallbackUpdate("qrCodeScanned", result);

                    } else {
                        L.i("onActivityResult ZXING_SCAN_RESULT error");
                        JSONObject obj = new JSONObject();
                        obj.put("status", "error");
                        obj.put("content", "An unknown error has occurred");

                        sendCallbackUpdate("qrCodeScanned", obj);
                    }
                } catch (JSONException e) {
                    L.e("JSONException... This should never happen", e);
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, intent);
    }

    protected void pluginInitialize() {
        mMenuItem = getActivity().getServiceMenuItem();
        mBroadcastReceiver = getBroadcastReceiver();
        mActivity.registerReceiver(mBroadcastReceiver, getIntentFilter());
    }

    private IntentFilter getIntentFilter() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(NewsPlugin.GET_NEWS_GROUP_SUCCESS);
        filter.addAction(NewsPlugin.GET_NEWS_GROUP_FAILED);
        filter.addAction(NewsPlugin.GET_NEWS_GROUPS_SUCCESS);
        filter.addAction(NewsPlugin.GET_NEWS_GROUPS_FAILED);
        filter.addAction(NewsPlugin.GET_NEWS_STREAM_ITEMS_SUCCESS);
        filter.addAction(NewsPlugin.GET_NEWS_STREAM_ITEMS_FAILED);
        return filter;
    }

    private BroadcastReceiver getBroadcastReceiver() {
        return new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                String requestId = intent.getStringExtra(IntentResponseHandler.REQUEST_ID);
                if (action == null || requestId == null) {
                    return;
                }
                CallbackContext callbackContext = callbackMap.get(requestId);
                if (callbackContext == null) {
                    // Some other activity may have executed this, ignore
                    return;
                }
                IJSONable response = RequestStore.getResponse(requestId);
                if (response == null) {
                    return;
                }
                switch (action) {
                    case NewsPlugin.GET_NEWS_GROUP_SUCCESS:
                    case NewsPlugin.GET_NEWS_GROUPS_SUCCESS:
                    case NewsPlugin.GET_NEWS_STREAM_ITEMS_SUCCESS:
                        callbackContext.success(new JSONObject(response.toJSONMap()));
                        break;
                    case NewsPlugin.GET_NEWS_GROUP_FAILED:
                    case NewsPlugin.GET_NEWS_GROUPS_FAILED:
                    case NewsPlugin.GET_NEWS_STREAM_ITEMS_FAILED:
                        Exception err = (Exception) intent.getSerializableExtra(IntentResponseHandler.ERROR);
                        L.e(err);
                        error(callbackContext, "unknown", mActivity.getString(R.string.unknown_error_occurred));
                        break;
                }
            }
        };
    }

    private CordovaActionScreenActivity getActivity() {
        if (mActivity == null) {
            mActivity = (CordovaActionScreenActivity) cordova.getActivity();
        }
        return mActivity;
    }

}

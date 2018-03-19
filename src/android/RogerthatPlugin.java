/*
 * Copyright 2017 GIG Technology NV
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
 * @@license_version:1.3@@
 */

package com.mobicage.rogerthat.cordova;


import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.support.customtabs.CustomTabsIntent;
import android.support.v4.content.ContextCompat;

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
import com.mobicage.rogerthat.plugins.scan.ScanCommunication;
import com.mobicage.rogerthat.plugins.scan.ScanTabActivity;
import com.mobicage.rogerthat.util.ActionScreenUtils;
import com.mobicage.rogerthat.util.TextUtils;
import com.mobicage.rogerthat.util.logging.L;
import com.mobicage.rogerthat.util.system.SafeRunnable;
import com.mobicage.rpc.config.CloudConstants;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.jivesoftware.smack.util.Base64;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.simple.JSONValue;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
        public void onBeaconInReach(Map<String, Object> beacon) {
            sendCallbackUpdate("onBeaconInReach", new JSONObject(beacon));
        }

        @Override
        public void onBeaconOutOfReach(Map<String, Object> beacon) {

            sendCallbackUpdate("onBeaconOutOfReach", new JSONObject(beacon));
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
        public void newsReceived(long[] ids) {
            try {
                JSONObject obj = new JSONObject();
                obj.put("ids", ids);
                sendCallbackUpdate("newsReceived", obj);
            } catch (JSONException e) {
                L.e("JSONException... This should never happen", e);
            }
        }

        @Override
        public void badgeUpdated(Map<String, Object> params) {
            sendCallbackUpdate("badgeUpdated", new JSONObject(params));
        }
    };

    @Override
    public boolean execute(final String action, final JSONArray args, final CallbackContext callbackContext) {
        if (!"log".equals(action)) {
            L.i("RogerthatPlugin.execute '" + action + "'");
        }
        getActivity();
        mActivity.getMainService().postOnUIHandler(new SafeRunnable() {
            @Override
            protected void safeRun() throws Exception {
                try {
                    if (action == null) {
                        callbackContext.error("Cannot excute 'null' action");
                        return;
                    }
                    if (action.equals("start")) {
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

                    } else if (action.equals("log")) {
                        log(args.optJSONObject(0));
                        callbackContext.success(new JSONObject());
                    } else if (action.equals("api_call")) {
                        sendApiCall(callbackContext, args.optJSONObject(0));

                    } else if (action.equals("api_resultHandlerConfigured")) {
                        mApiResultHandlerSet = true;
                        mActivity.getActionScreenUtils().deliverAllApiResults();
                        callbackContext.success(new JSONObject());

                    } else if (action.equals("app_exit")) {
                        exitApp(callbackContext);

                    } else if (action.equals("app_exitWithResult")) {
                        exitAppWithResult(callbackContext, args.optJSONObject(0));

                    } else if (action.equals("camera_startScanningQrCode")) {
                        startScanningQrCode(callbackContext);

                    } else if (action.equals("camera_stopScanningQrCode")) {
                        stopScanningQrCode(callbackContext);

                    } else if (action.equals("context")) {
                        getContext(callbackContext);

                    } else if (action.equals("message_open")) {
                        openMessage(callbackContext, args.optJSONObject(0));

                    } else if (action.equals("news_count")) {
                        countNews(callbackContext, args.optJSONObject(0));

                    } else if (action.equals("news_get")) {
                        getNews(callbackContext, args.optJSONObject(0));

                    } else if (action.equals("news_list")) {
                        listNews(callbackContext, args.optJSONObject(0));

                    } else if (action.equals("security_createKeyPair")) {
                        createKeyPair(callbackContext, args.optJSONObject(0));

                    } else if (action.equals("security_hasKeyPair")) {
                        hasKeyPair(callbackContext, args.optJSONObject(0));

                    } else if (action.equals("security_getPublicKey")) {
                        getPublicKey(callbackContext, args.optJSONObject(0));

                    } else if (action.equals("security_getSeed")) {
                        getSeed(callbackContext, args.optJSONObject(0));

                    } else if (action.equals("security_getAddress")) {
                        getAddress(callbackContext, args.optJSONObject(0));

                    } else if (action.equals("security_sign")) {
                        signPayload(callbackContext, args.optJSONObject(0));

                    } else if (action.equals("security_verify")) {
                        verifySignedPayload(callbackContext, args.optJSONObject(0));

                    } else if (action.equals("service_getBeaconsInReach")) {
                        getBeaconsInReach(callbackContext);

                    } else if (action.equals("system_onBackendConnectivityChanged")) {
                        onBackendConnectivityChanged(callbackContext);

                    } else if (action.equals("ui_hideKeyboard")) {
                        hideKeyboard(callbackContext);

                    } else if (action.equals("user_put")) {
                        putUserData(callbackContext, args.optJSONObject(0));

                    } else if (action.equals("util_embeddedAppTranslations")) {
                        embeddedAppTranslations(callbackContext);

                    } else if (action.equals("util_isConnectedToInternet")) {
                        isConnectedToInternet(callbackContext);

                    } else if (action.equals("util_open")) {
                        openActivity(callbackContext, args.optJSONObject(0));

                    } else if (action.equals("util_playAudio")) {
                        playAudio(callbackContext, args.optJSONObject(0));

                    } else {
                        L.e("RogerthatPlugin.execute did not match '" + action + "'");
                        callbackContext.error("RogerthatPlugin doesn't know how to excecute this action.");
                    }
                } catch (JSONException e) {
                    L.e("JSONException... This should never happen", e);
                    callbackContext.error("Could not process json...");
                }
            }
        });
        return true;
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
    }

    private void returnArgsMissing(final CallbackContext callbackContext) {
        callbackContext.error("User did not specify data to encode");
    }

    private void setInfo() throws JSONException {
        Map<String, Object> info = mActivity.getFriendsPlugin().getRogerthatUserAndServiceInfo(
                mActivity.getServiceEmail(), mActivity.getServiceFriend(), mMenuItem);
        sendCallbackUpdate("setInfo", new JSONObject(info));
    }

    private void log(final JSONObject args) throws JSONException {
        final String errorMessage = TextUtils.optString(args, "e", null);
        final String message = TextUtils.optString(args, "m", null);
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
        final String method = TextUtils.optString(args, "method", null);
        final String params = TextUtils.optString(args, "params", null);
        final String tag = TextUtils.optString(args, "tag", null);

        mActivity.getFriendsPlugin().sendApiCall(mActivity.getServiceEmail(), mActivity.getItemTagHash(), method, params, tag);
        callbackContext.success(new JSONObject());
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
        final String result = TextUtils.optString(args, "result", null);
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
        final String messageKey = TextUtils.optString(args, "message_key", null);
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

    private void countNews(final CallbackContext callbackContext, final JSONObject args) throws JSONException {
        if (args == null) {
            returnArgsMissing(callbackContext);
            return;
        }
        long count = mActivity.getActionScreenUtils().countNews(args);
        JSONObject obj = new JSONObject();
        obj.put("count", count);
        callbackContext.success(obj);
    }

    private void getNews(final CallbackContext callbackContext, final JSONObject args) throws JSONException {
        if (args == null) {
            returnArgsMissing(callbackContext);
            return;
        }
        NewsItem item = mActivity.getActionScreenUtils().getNews(args);
        JSONObject obj = new JSONObject();
        obj.put("item", item == null ? null : item.toJSONMap());
        callbackContext.success(obj);
    }

    private void listNews(final CallbackContext callbackContext, final JSONObject args) throws JSONException {
        if (args == null) {
            returnArgsMissing(callbackContext);
            return;
        }
        Map<String, Object> result = mActivity.getActionScreenUtils().listNews(args);

        JSONArray newsItems = new JSONArray();
        for (NewsItem ni : (List<NewsItem>) result.get("items")) {
            newsItems.put(ni.toJSONMap());
        }

        JSONObject obj = new JSONObject();
        obj.put("cursor", result.get("cursor"));
        obj.put("items", newsItems);
        callbackContext.success(obj);
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
        final String payload = TextUtils.optString(args, "payload", null);

        SecurityCallback<byte[]> sc = new SecurityCallback<byte[]>(callbackContext) {
            @Override
            public void populateResult(byte[] result, JSONObject obj) throws JSONException {
            }

            @Override
            public void onSuccess(byte[] payloadSignature) {
                try {
                    JSONObject obj = new JSONObject();
                    obj.put("payload", payload);
                    obj.put("payload_signature", Base64.encodeBytes(payloadSignature, Base64.DONT_BREAK_LINES));
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

    private void getBeaconsInReach(final CallbackContext callbackContext) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("beacons", new JSONArray(mActivity.getActionScreenUtils().getBeaconsInReach()));
        callbackContext.success(obj);
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
        final String data = TextUtils.optString(args, "u", null);
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
        final String actionType = TextUtils.optString(args, "action_type", null);
        final String action = TextUtils.optString(args, "action", null);
        final String title = TextUtils.optString(args, "title", null);

        String errorMessage = mActivity.getActionScreenUtils().openActivity(actionType, action, title);
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
        final String url = TextUtils.optString(args, "url", null);
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
    }

    private CordovaActionScreenActivity getActivity() {
        if (mActivity == null) {
            mActivity = (CordovaActionScreenActivity) cordova.getActivity();
        }
        return mActivity;
    }

}

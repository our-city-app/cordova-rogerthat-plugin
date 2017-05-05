package com.mobicage.rogerthat.cordova;


import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.support.v4.content.ContextCompat;
import com.google.zxing.client.android.CaptureActivity;
import com.google.zxing.client.android.Intents;
import com.mobicage.rogerthat.MainService;
import com.mobicage.rogerthat.plugins.friends.Poker;
import com.mobicage.rogerthat.plugins.friends.ServiceApiCallbackResult;
import com.mobicage.rogerthat.plugins.messaging.Message;
import com.mobicage.rogerthat.plugins.scan.ScanCommunication;
import com.mobicage.rogerthat.plugins.scan.ScanTabActivity;
import com.mobicage.rogerthat.util.ActionScreenUtils;
import com.mobicage.rogerthat.util.Security;
import com.mobicage.rogerthat.util.logging.L;
import com.mobicage.rogerthat.util.system.SafeRunnable;
import com.mobicage.rpc.config.AppConstants;
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
import java.util.Locale;
import java.util.Map;

import static android.app.Activity.RESULT_OK;

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

    protected final static String[] permissions = { Manifest.permission.CAMERA };

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
    };

    @Override
    public boolean execute(final String action, final JSONArray args, final CallbackContext callbackContext) {
        if (action != "log") {
            L.i("RogerthatPlugin.execute '" + action + "'");
        }
        if (mActivity == null) {
            mActivity = (CordovaActionScreenActivity) cordova.getActivity();
        }
        mActivity.getMainService().postOnUIHandler(new SafeRunnable() {
            @Override
            protected void safeRun() throws Exception {
                try {
                    if(action == null){
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

                    } else if (action.equals("camera_startScanningQrCode")) {
                        startScanningQrCode(callbackContext);

                    } else if (action.equals("camera_stopScanningQrCode")) {
                        stopScanningQrCode(callbackContext);

                    } else if (action.equals("message_open")) {
                        openMessage(callbackContext, args.optJSONObject(0));

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

    public boolean onOverrideUrlLoading(String url) {
        L.i("Branding is loading url: " + url);
        Uri uri = Uri.parse(url);
        String lowerCaseUrl = url.toLowerCase();
        if (lowerCaseUrl.startsWith(POKE)) {
            String tag = url.substring(POKE.length());
            poke(tag);
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

    private void setInfo() throws JSONException  {
        Map<String, Object> info =  mActivity.getFriendsPlugin().getRogerthatUserAndServiceInfo(mActivity.getServiceEmail(), mActivity.getServiceFriend());
        sendCallbackUpdate("setInfo", new JSONObject(info));
    }

    private void log(final JSONObject args) throws JSONException {
        final String errorMessage = args.optString("e", null);
        final String message = args.optString("m", null);
        if (errorMessage != null) {
            mActivity.getActionScreenUtils().logError(mActivity.getServiceEmail(), mActivity.getItemLabel(),
                  mActivity.getItemCoords(), message);
        } else {
            L.i("[BRANDING] " + message);
        }
    }

    private void sendApiCall(final CallbackContext callbackContext, final JSONObject args) {
        if (args == null) {
            returnArgsMissing(callbackContext);
            return;
        }
        final String method = args.optString("method", null);
        final String params = args.optString("params", null);
        final String tag = args.optString("tag", null);

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

    private void startScanningQrCode(final CallbackContext callbackContext) throws JSONException {
        if (mQRCodeScannerOpen) {
            JSONObject obj = new JSONObject();
            obj.put("exception", "Camera was already open");
            callbackContext.error(obj);
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
                    JSONObject obj = new JSONObject();
                    obj.put("exception", "Camera permission was not granted");
                    callbackContext.error(obj);
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

    private void openMessage(final CallbackContext callbackContext, final JSONObject args) throws JSONException {
        if (args == null) {
            returnArgsMissing(callbackContext);
            return;
        }
        final String messageKey= args.optString("message_key", null);
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

    private void signPayload(final CallbackContext callbackContext, final JSONObject args) throws JSONException {
        if (!AppConstants.SECURE_APP) {
            JSONObject obj = new JSONObject();
            obj.put("exception", "Security is not enabled in your app.");
            callbackContext.error(obj);
            return;
        }
        final String message= args.optString("message", null);
        final String payload= args.optString("payload", null);
        final boolean forcePin = args.optBoolean("force_pin", false);

        mActivity.getMainService().postAtFrontOfUIHandler(new SafeRunnable() {
            @Override
            protected void safeRun() throws Exception {
                try {
                    final byte[] payloadData = Security.sha256Digest(payload);
                    if (payloadData == null) {
                        throw new Exception("payloadData was null");
                    }

                    MainService.SecurityCallback sc = new MainService.SecurityCallback() {
                        @Override
                        public void onSuccess(Object result) {
                            try {
                                byte[] payloadSignature = (byte[]) result;
                                JSONObject obj = new JSONObject();
                                obj.put("payload", payload);
                                obj.put("payload_signature", Base64.encodeBytes(payloadSignature, Base64.DONT_BREAK_LINES));
                                callbackContext.success(obj);
                            } catch (Exception e) {
                                L.d("'security/sign' onSuccess exception", e);
                                try {
                                    JSONObject obj = new JSONObject();
                                    obj.put("exception", "Payload signature not of correct format.");
                                    callbackContext.error(obj);
                                } catch(JSONException je) {
                                    L.e("JSONException... This should never happen", e);
                                }
                            }
                        }

                        @Override
                        public void onError(Exception error) {
                            L.d("'security/sign' onError", error);
                            try {
                                JSONObject obj = new JSONObject();
                                obj.put("exception", error.getMessage());
                                callbackContext.error(obj);
                            } catch(JSONException je) {
                                L.e("JSONException... This should never happen", je);
                            }
                        }
                    };

                    mActivity.getMainService().sign(message, payloadData, forcePin, sc);

                }  catch (Exception e) {
                    JSONObject obj = new JSONObject();
                    obj.put("exception", "Payload not of correct format.");
                    callbackContext.error(obj);
                }
            }
        });
    }

    private void verifySignedPayload(final CallbackContext callbackContext, final JSONObject args) throws JSONException {
        if (!AppConstants.SECURE_APP) {
            JSONObject obj = new JSONObject();
            obj.put("exception", "Security is not enabled in your app.");
            callbackContext.error(obj);
            return;
        }
        final String payload= args.optString("payload", null);
        final String payloadSignature = args.optString("payload_signature", null);

        mActivity.getMainService().postAtFrontOfUIHandler(new SafeRunnable() {
            @Override
            protected void safeRun() throws Exception {

                try {
                    final byte[] payloadData = Security.sha256Digest(payload);
                    if (payloadData == null) {
                        throw new Exception("payloadData was null");
                    }

                    final byte[] payloadDataSignature = Base64.decode(payloadSignature);
                    boolean valid = mActivity.getMainService().validate(payloadData, payloadDataSignature);
                    JSONObject obj = new JSONObject();
                    obj.put("valid", valid);
                    callbackContext.success(obj);
                } catch (Exception e) {
                    L.d("'security/verify' onSuccess exception", e);
                    JSONObject obj = new JSONObject();
                    obj.put("exception", "Payload not of correct format.");
                    callbackContext.error(obj);
                }
            }
        });
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

    private void hideKeyboard(final CallbackContext callbackContext)  {
        mActivity.getActionScreenUtils().hideKeyboard(mActivity.getCurrentFocus().getWindowToken());
        callbackContext.success(new JSONObject());
    }

    private void putUserData(final CallbackContext callbackContext, final JSONObject args) throws JSONException {
        if (args == null) {
            returnArgsMissing(callbackContext);
            return;
        }
        final String userData = args.optString("u", null);
        mActivity.getFriendsPlugin().putUserData(mActivity.getServiceEmail(), userData);
        callbackContext.success(new JSONObject());
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
        final String actionType = args.optString("action_type", null);
        final String action = args.optString("action", null);
        final String title = args.optString("title", null);

        String errorMessage = mActivity.getActionScreenUtils().openActivity(actionType, action, title);
        if (errorMessage != null) {
            JSONObject obj = new JSONObject();
            obj.put("exception", errorMessage);
            callbackContext.error(obj);
            return;
        }
        callbackContext.success(new JSONObject());
    }

    private void playAudio(final CallbackContext callbackContext, final JSONObject args) throws JSONException {
        if (args == null) {
            returnArgsMissing(callbackContext);
            return;
        }
        final String url = args.optString("url", null);
        String fileOnDisk = "file://" + mActivity.getBrandingResult().dir.getAbsolutePath() + "/" + url;
        mActivity.getActionScreenUtils().playAudio(fileOnDisk);
        callbackContext.success(new JSONObject());
    }

    private void sendCallbackUpdate(String callback, String args) {
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
        if (mActivity == null) {
            mActivity = (CordovaActionScreenActivity) cordova.getActivity();
        }
        if (mPoker == null) {
            mPoker = new Poker<>(mActivity, mActivity.getServiceEmail());
        }

        mPoker.poke(tag, null);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        L.i("RogerthatPlugin.onActivityResult requestCode -> " + requestCode);
        if (resultCode == RESULT_OK) {
            if (requestCode == ScanTabActivity.ZXING_SCAN_RESULT) {
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
}

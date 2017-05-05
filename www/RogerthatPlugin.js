console.log("Loading RogerthatPlugin v0.1.0");
var exec = cordova.require("cordova/exec");

var MAJOR_VERSION = 0;
var MINOR_VERSION = 1;
var PATCH_VERSION = 0;
var FEATURE_CHECKING = 0;
var FEATURE_SUPPORTED = 1;
var FEATURE_NOT_SUPPORTED = 2;

var PROXIMITY_UNKNOWN = 0;
var PROXIMITY_IMMEDIATE = 1;
var PROXIMITY_NEAR = 2;
var PROXIMITY_FAR = 3;

var ready = false;
var apiResultReceivedCallbackSet = false;

var _dummy = function() {
};

var apiUserCallbacks = {
    resultReceived : _dummy
};

var userCallbacks = {
    onBackendConnectivityChanged : _dummy,
    onBeaconInReach : _dummy,
    onBeaconOutOfReach : _dummy,
    qrCodeScanned : _dummy,
    ready : _dummy,
    serviceDataUpdated : _dummy,
    userDataUpdated : _dummy
};

var utils = {
    backgroundSize : (function() {
        var thisBody = document.documentElement || document.body, thisStyle = thisBody.style, support = thisStyle.backgroundSize !== undefined;

        return support;
    })(),
    checkCapabilities : function() {
        // Test base64 url support
        var callbackBase64URI = function() {
            rogerthatPlugin.features.base64URI = this.width == 1 && this.height == 1 ? FEATURE_SUPPORTED : FEATURE_NOT_SUPPORTED;
            if (rogerthatPlugin.features.callback !== undefined)
                rogerthatPlugin.features.callback('base64URI');
        };

        var img = new Image();
        img.onload = img.onerror = img.onabort = callbackBase64URI;
        img.src = "data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///ywAAAAAAQABAAACAUwAOw==";

        // Test css3 support
        rogerthatPlugin.features.backgroundSize = utils.backgroundSize ? FEATURE_SUPPORTED : FEATURE_NOT_SUPPORTED;
        if (rogerthatPlugin.features.callback !== undefined)
            rogerthatPlugin.features.callback('backgroundSize');
    },
    exec : function (success, fail, action, args) {
        utils.logFunctionName("utils.exec -> " + action);
        exec(utils.getSuccessCallback(success, action), utils.getErrorCallback(fail, action), "RogerthatPlugin", action, args);
    },
    generateCallbacksRegister : function(callbacks) {
        var callbacksRegister = {};
        Object.keys(callbacks).forEach(function(key) {
            callbacksRegister[key] = function(callback) {
                callbacks[key] = callback;
            };
        });
        return callbacksRegister;
    },
    logFunctionName : function (functionName) {
        console.log("RogerthatPlugin." + functionName);
    },
    getErrorCallback : function (ecb, functionName) {
        if (typeof ecb === 'function') {
            return ecb;
        } else {
            return function (result) {
                console.log("The injected error callback of '" + functionName + "' received: " + JSON.stringify(result));
            }
        }
    },
    getSuccessCallback : function (scb, functionName) {
        if (typeof scb === 'function') {
            return scb;
        } else {
            return function (result) {
                console.log("The injected success callback of '" + functionName + "' received: " + JSON.stringify(result));
            }
        }
    },
    processCallbackResult : function (result) {
        utils.logFunctionName("processCallbackResult <- " + result.callback);
        console.log(result);
        if (result.callback === "setInfo") {
            ready = true;
            utils.setRogerthatData(result.args);
            rogerthatPlugin.util._translateHTML();
            userCallbacks.ready();

        } else if (result.callback === "apiResult") {
            apiUserCallbacks.resultReceived(result.args.method, result.args.result, result.args.error, result.args.tag);

        } else if (result.callback === "onBackendConnectivityChanged") {
            userCallbacks.onBackendConnectivityChanged(result.args);

        } else if (result.callback === "onBeaconInReach") {
            userCallbacks.onBeaconInReach(result.args);

        } else if (result.callback === "onBeaconOutOfReach") {
            userCallbacks.onBeaconOutOfReach(result.args);

        } else if (result.callback === "qrCodeScanned") {
            userCallbacks.qrCodeScanned(result.args);

        } else if (result.callback === "userDataUpdated") {
            rogerthatPlugin.user.data = result.args;
            userCallbacks.userDataUpdated();

        } else if (result.callback === "serviceDataUpdated") {
            rogerthatPlugin.service.data = result.args;
            userCallbacks.serviceDataUpdated();

        } else {
            utils.logFunctionName("processCallbackResult was unhandled");
        }
    },
    setRogerthatData : function(info) {
        Object.keys(info).forEach(function(key) {
            Object.assign(rogerthatPlugin[key], info[key]);
        });

        if (info.system === undefined) {
            rogerthatPlugin.system.os = "unknown";
            rogerthatPlugin.system.version = "unknown";
            rogerthatPlugin.system.appVersion = "unknown";
            rogerthatPlugin.features.beacons = FEATURE_NOT_SUPPORTED;
        } else {
            if (rogerthatPlugin.system.os == "ios" && rogerthatPlugin.system.version !== undefined
                && rogerthatPlugin.system.appVersion !== undefined) {
                var version = rogerthatPlugin.system.version.split(".");
                if (parseInt(version[0]) >= 7) {
                    rogerthatPlugin.features.beacons = FEATURE_SUPPORTED;
                } else {
                    rogerthatPlugin.features.beacons = FEATURE_NOT_SUPPORTED;
                }
            } else if (rogerthatPlugin.system.os == "android" && rogerthatPlugin.system.version !== undefined) {
                if (parseInt(rogerthatPlugin.system.version) >= 18) {
                    rogerthatPlugin.features.beacons = FEATURE_SUPPORTED;
                } else {
                    rogerthatPlugin.features.beacons = FEATURE_NOT_SUPPORTED;
                }
            } else {
                rogerthatPlugin.features.beacons = FEATURE_NOT_SUPPORTED;
            }
        }
        if (rogerthatPlugin.features.callback !== undefined)
            rogerthatPlugin.features.callback('beacons');
    }
};

function RogerthatPlugin() {
    utils.logFunctionName("constructor");
}

var apiCallbacksRegister = utils.generateCallbacksRegister(apiUserCallbacks);
apiCallbacksRegister.resultReceived = function(callback) {
    // Can only be set once
    if (apiResultReceivedCallbackSet) {
        console.log("Can only set resultReceived callback once.");
        return;
    }

    apiUserCallbacks.resultReceived = callback;
    apiResultReceivedCallbackSet = true;

    // Notify the app that a resultHandler is set
    utils.exec(null, null, "api_resultHandlerConfigured", []);
};

RogerthatPlugin.prototype.api = {
    callbacks: apiCallbacksRegister,
    call : function(method, params, tag) {
        utils.exec(null, null, "api_call", [{"method": method,
                                             "params": params,
                                             "tag": tag}]);
    }
};

var callbacksRegister = utils.generateCallbacksRegister(userCallbacks);
callbacksRegister.ready = function(callback) {
    userCallbacks.ready = function() {
        callback();
    };
    if (ready) {
        userCallbacks.ready();
    }
};

RogerthatPlugin.prototype.MAJOR_VERSION = MAJOR_VERSION;
RogerthatPlugin.prototype.MINOR_VERSION = MINOR_VERSION;
RogerthatPlugin.prototype.PATCH_VERSION = PATCH_VERSION;
RogerthatPlugin.prototype.PROXIMITY_UNKNOWN = PROXIMITY_UNKNOWN;
RogerthatPlugin.prototype.PROXIMITY_IMMEDIATE = PROXIMITY_IMMEDIATE;
RogerthatPlugin.prototype.PROXIMITY_NEAR = PROXIMITY_NEAR;
RogerthatPlugin.prototype.PROXIMITY_FAR = PROXIMITY_FAR;
RogerthatPlugin.prototype.VERSION = MAJOR_VERSION + "." + MINOR_VERSION + "." + PATCH_VERSION;

RogerthatPlugin.prototype.callbacks = callbacksRegister;

RogerthatPlugin.prototype.camera = {
    FRONT : "front",
    BACK : "back",
    startScanningQrCode : function (cameraType, successCallback, errorCallback) {
        utils.exec(successCallback, errorCallback, "camera_startScanningQrCode", []);
    },
    stopScanningQrCode : function (cameraType, successCallback, errorCallback) {
        utils.exec(successCallback, errorCallback, "camera_stopScanningQrCode", []);
    }
};

RogerthatPlugin.prototype.features = {
    base64URI : FEATURE_CHECKING,
    backgroundSize : FEATURE_CHECKING,
    beacons : FEATURE_CHECKING,
    callback : undefined
};

RogerthatPlugin.prototype.message = {
    open : function(messageKey, successCallback, errorCallback) {
        utils.exec(successCallback, errorCallback, "message_open", [{"message_key": messageKey}]);
    }
};

RogerthatPlugin.prototype.security = {
    sign : function (message, payload, forcePin, successCallback, errorCallback) {
        utils.exec(successCallback, errorCallback, "security_sign", [{"message": message,
                                                                      "payload": payload,
                                                                      "force_pin": forcePin}]);
    },
    verify : function (payload, payloadSignature, successCallback, errorCallback) {
        utils.exec(successCallback, errorCallback, "security_verify", [{"payload": payload,
                                                                        "payload_signature": payloadSignature}]);
    }
};

RogerthatPlugin.prototype.service = {
    getBeaconsInReach : function (successCallback, errorCallback) {
        utils.exec(successCallback, errorCallback, "service_getBeaconsInReach", []);
    }
};

RogerthatPlugin.prototype.system = {
    onBackendConnectivityChanged : function (successCallback, errorCallback) {
        utils.exec(successCallback, errorCallback, "system_onBackendConnectivityChanged", []);
    }
};

RogerthatPlugin.prototype.ui = {
    hideKeyboard : function () {
        utils.exec(null, null, "ui_hideKeyboard", []);
    }
};

RogerthatPlugin.prototype.user = {
    put : function () {
        utils.exec(null, null, "user_put", [{"u": rogerthatPlugin.user.data}]);
    }
};

RogerthatPlugin.prototype.util = {
    isConnectedToInternet : function(successCallback, errorCallback) {
        utils.exec(successCallback, errorCallback, "util_isConnectedToInternet", []);
    },
    open : function(params, successCallback, errorCallback) {
        if (!params) {
            return;
        }
        if (params.action_type) {
            params.action = SHA256_hash(params.action);
        }
        utils.exec(successCallback, errorCallback, "util_open", [params]);
    },
    playAudio : function (url, successCallback, errorCallback) {
        utils.exec(successCallback, errorCallback, "util_playAudio", [{"url": url}]);
    },
    translate : function(key, parameters) {
        var language = rogerthatPlugin.user.language || rogerthatPlugin.util._translations.defaultLanguage;
        var translation = undefined;
        if (language != rogerthatPlugin.util._translations.defaultLanguage) {
            var translationSet = rogerthatPlugin.util._translations.values[key];
            if (translationSet) {
                translation = translationSet[language];
                if (translation === undefined) {
                    if (language.indexOf('_') != -1) {
                        language = language.split('_')[0];
                        translation = translationSet[language];
                    }
                }
            }
        }

        if (translation == undefined) {
            // language is defaultLanguage / key is missing / key is not translated
            translation = key;
        }

        if (parameters) {
            $.each(parameters, function(param, value) {
                translation = translation.replace('%(' + param + ')s', value);
            });
        }
        return translation;
    },
    uuid : function() {
        var S4 = function() {
            return (((1 + Math.random()) * 0x10000) | 0).toString(16).substring(1);
        };
        return (S4() + S4() + "-" + S4() + "-" + S4() + "-" + S4() + "-" + S4() + S4() + S4());
    },
    _translateHTML : function() {
        var tags = document.getElementsByTagName('x-rogerthat-t')
        for (var i = tags.length - 1; i >= 0; i--){
            var obj = tags[i];
            var translatedString = rogerthatPlugin.util.translate(obj.innerHTML);
            if(obj.outerHTML) {
                obj.outerHTML = translatedString;
            } else {
                var tmpObj = document.createElement("div");
                tmpObj.innerHTML='<!--THIS DATA SHOULD BE REPLACED-->';
                var objParent = obj.parentNode;
                objParent.replaceChild(tmpObj, obj);
                objParent.innerHTML = objParent.innerHTML.replace('<div><!--THIS DATA SHOULD BE REPLACED--></div>', translatedString);
            }
        }
    },
    _translations : {
        defaultLanguage : "en",
        values : {
        // eg; "Name": { "fr": "Nom", "nl": "Naam" }
        }
    },
};

var exceptionToObject = function(exception) {
    var result = {};
    if (!exception) {
        return result;
    }
    if (typeof exception.name !== 'undefined') {
        result.name = exception.name;
    }
    if (typeof exception.message !== 'undefined') {
        result.message = exception.message;
    }
    if (typeof exception.stack !== 'undefined') {
        result.stack = exception.stack;
    }
    if (typeof exception.fileName !== 'undefined') {
        result.fileName = exception.fileName;
    }
    if (typeof exception.lineNumber !== 'undefined') {
        result.lineNumber = exception.lineNumber;
    }
    if (typeof exception.columnNumber !== 'undefined') {
        result.columnNumber = exception.columnNumber;
    }
    if (typeof exception.description !== 'undefined') {
        result.description = exception.description;
    }
    return result;
};

var bind = function(context, fn) {
    return function () {
        return fn.apply(context, arguments);
    };
};

var windowErrorHandler = function(msg, url, line, column, errorObj) {
    var result = {
        msg: msg,
        url: url,
        line: line,
        column: column,
        errorObj: exceptionToObject(errorObj)
    }
    utils.exec(null, null, "log", [{"e": JSON.stringify(result)}]);
};

if (typeof window !== 'undefined') {
    window.onerror = bind(this, windowErrorHandler);
}

if (typeof rogerthat_translations !== "undefined") {
    RogerthatPlugin.prototype.util._translations = rogerthat_translations;
}

var rogerthatPlugin = new RogerthatPlugin();
module.exports = rogerthatPlugin;

utils.checkCapabilities();
utils.exec(utils.processCallbackResult, null, "start", []);

/*
 *  jssha256 version 0.1  -  Copyright 2006 B. Poettering
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU General Public License as
 *  published by the Free Software Foundation; either version 2 of the
 *  License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA
 *  02111-1307 USA
 */

function string_to_array(a){var b=a.length;var c=new Array(b);for(var d=0;d<b;d++)c[d]=a.charCodeAt(d);return c;}function array_to_hex_string(a){var b="";for(var c=0;c<a.length;c++)b+=SHA256_hexchars[a[c]>>4]+SHA256_hexchars[a[c]&0x0f];return b;}function SHA256_init(){SHA256_H=new Array(0x6a09e667,0xbb67ae85,0x3c6ef372,0xa54ff53a,0x510e527f,0x9b05688c,0x1f83d9ab,0x5be0cd19);SHA256_buf=new Array();SHA256_len=0;}function SHA256_write(a){if(typeof a=="string")SHA256_buf=SHA256_buf.concat(string_to_array(a));else SHA256_buf=SHA256_buf.concat(a);for(var b=0;b+64<=SHA256_buf.length;b+=64)SHA256_Hash_Byte_Block(SHA256_H,SHA256_buf.slice(b,b+64));SHA256_buf=SHA256_buf.slice(b);SHA256_len+=a.length;}function SHA256_finalize(){SHA256_buf[SHA256_buf.length]=0x80;if(SHA256_buf.length>64-8){for(var a=SHA256_buf.length;a<64;a++)SHA256_buf[a]=0;SHA256_Hash_Byte_Block(SHA256_H,SHA256_buf);SHA256_buf.length=0;}for(var a=SHA256_buf.length;a<64-5;a++)SHA256_buf[a]=0;SHA256_buf[59]=(SHA256_len>>>29)&0xff;SHA256_buf[60]=(SHA256_len>>>21)&0xff;SHA256_buf[61]=(SHA256_len>>>13)&0xff;SHA256_buf[62]=(SHA256_len>>>5)&0xff;SHA256_buf[63]=(SHA256_len<<3)&0xff;SHA256_Hash_Byte_Block(SHA256_H,SHA256_buf);var b=new Array(32);for(var a=0;a<8;a++){b[4*a+0]=SHA256_H[a]>>>24;b[4*a+1]=(SHA256_H[a]>>16)&0xff;b[4*a+2]=(SHA256_H[a]>>8)&0xff;b[4*a+3]=SHA256_H[a]&0xff;}delete SHA256_H;delete SHA256_buf;delete SHA256_len;return b;}function SHA256_hash(a){var b;SHA256_init();SHA256_write(a);b=SHA256_finalize();return array_to_hex_string(b);}function HMAC_SHA256_init(a){if(typeof a=="string")HMAC_SHA256_key=string_to_array(a);else HMAC_SHA256_key=new Array().concat(a);if(HMAC_SHA256_key.length>64){SHA256_init();SHA256_write(HMAC_SHA256_key);HMAC_SHA256_key=SHA256_finalize();}for(var b=HMAC_SHA256_key.length;b<64;b++)HMAC_SHA256_key[b]=0;for(var b=0;b<64;b++)HMAC_SHA256_key[b]^=0x36;SHA256_init();SHA256_write(HMAC_SHA256_key);}function HMAC_SHA256_write(a){SHA256_write(a);}function HMAC_SHA256_finalize(){var a=SHA256_finalize();for(var b=0;b<64;b++)HMAC_SHA256_key[b]^=0x36^0x5c;SHA256_init();SHA256_write(HMAC_SHA256_key);SHA256_write(a);for(var b=0;b<64;b++)HMAC_SHA256_key[b]=0;delete HMAC_SHA256_key;return SHA256_finalize();}function HMAC_SHA256_MAC(a,b){var c;HMAC_SHA256_init(a);HMAC_SHA256_write(b);c=HMAC_SHA256_finalize();return array_to_hex_string(c);}SHA256_hexchars=new Array('0','1','2','3','4','5','6','7','8','9','a','b','c','d','e','f');SHA256_K=new Array(0x428a2f98,0x71374491,0xb5c0fbcf,0xe9b5dba5,0x3956c25b,0x59f111f1,0x923f82a4,0xab1c5ed5,0xd807aa98,0x12835b01,0x243185be,0x550c7dc3,0x72be5d74,0x80deb1fe,0x9bdc06a7,0xc19bf174,0xe49b69c1,0xefbe4786,0x0fc19dc6,0x240ca1cc,0x2de92c6f,0x4a7484aa,0x5cb0a9dc,0x76f988da,0x983e5152,0xa831c66d,0xb00327c8,0xbf597fc7,0xc6e00bf3,0xd5a79147,0x06ca6351,0x14292967,0x27b70a85,0x2e1b2138,0x4d2c6dfc,0x53380d13,0x650a7354,0x766a0abb,0x81c2c92e,0x92722c85,0xa2bfe8a1,0xa81a664b,0xc24b8b70,0xc76c51a3,0xd192e819,0xd6990624,0xf40e3585,0x106aa070,0x19a4c116,0x1e376c08,0x2748774c,0x34b0bcb5,0x391c0cb3,0x4ed8aa4a,0x5b9cca4f,0x682e6ff3,0x748f82ee,0x78a5636f,0x84c87814,0x8cc70208,0x90befffa,0xa4506ceb,0xbef9a3f7,0xc67178f2);function SHA256_sigma0(a){return((a>>>7)|(a<<25))^((a>>>18)|(a<<14))^(a>>>3);}function SHA256_sigma1(a){return((a>>>17)|(a<<15))^((a>>>19)|(a<<13))^(a>>>10);}function SHA256_Sigma0(a){return((a>>>2)|(a<<30))^((a>>>13)|(a<<19))^((a>>>22)|(a<<10));}function SHA256_Sigma1(a){return((a>>>6)|(a<<26))^((a>>>11)|(a<<21))^((a>>>25)|(a<<7));}function SHA256_Ch(a,b,c){return c^(a&(b^c));}function SHA256_Maj(a,b,c){return(a&b)^(c&(a^b));}function SHA256_Hash_Word_Block(a,b){for(var c=16;c<64;c++)b[c]=(SHA256_sigma1(b[c-2])+b[c-7]+SHA256_sigma0(b[c-15])+b[c-16])&0xffffffff;var d=new Array().concat(a);for(var c=0;c<64;c++){var e=d[7]+SHA256_Sigma1(d[4])+SHA256_Ch(d[4],d[5],d[6])+SHA256_K[c]+b[c];var f=SHA256_Sigma0(d[0])+SHA256_Maj(d[0],d[1],d[2]);d.pop();d.unshift((e+f)&0xffffffff);d[4]=(d[4]+e)&0xffffffff;}for(var c=0;c<8;c++)a[c]=(a[c]+d[c])&0xffffffff;}function SHA256_Hash_Byte_Block(a,b){var c=new Array(16);for(var d=0;d<16;d++)c[d]=b[4*d+0]<<24|b[4*d+1]<<16|b[4*d+2]<<8|b[4*d+3];SHA256_Hash_Word_Block(a,c);}

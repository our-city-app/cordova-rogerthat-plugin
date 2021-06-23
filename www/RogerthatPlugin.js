var exec = cordova.require("cordova/exec");
var sha256 = require("./Sha256");

var ready = false;
var apiResultReceivedCallbackSet = false;

var _dummy = function () {
};

var apiUserCallbacks = {
    resultReceived: _dummy
};

var userCallbacks = {
    qrCodeScanned: _dummy,
    ready: _dummy,
    serviceDataUpdated: _dummy,
    userDataUpdated: _dummy,
    badgeUpdated: _dummy
};

var utils = {
    exec: function (success, fail, action, args) {
        if (action !== 'log') {
            utils.logFunctionName("utils.exec -> " + action);
        }
        exec(utils.getSuccessCallback(success, action), utils.getErrorCallback(fail, action), "RogerthatPlugin", action, args);
    },
    generateCallbacksRegister: function (callbacks) {
        var callbacksRegister = {};
        Object.keys(callbacks).forEach(function (key) {
            callbacksRegister[key] = function (callback) {
                callbacks[key] = callback;
            };
        });
        return callbacksRegister;
    },
    logFunctionName: function (functionName) {
        console.log("RogerthatPlugin." + functionName);
    },
    getErrorCallback: function (ecb, functionName) {
        if (typeof ecb === 'function') {
            return ecb;
        } else {
            return function (result) {
                console.log("The injected error callback of '" + functionName + "' received: " + JSON.stringify(result));
            };
        }
    },
    getSuccessCallback: function (scb, functionName) {
        if (typeof scb === 'function') {
            return scb;
        } else {
            return function (result) {
                console.log("The injected success callback of '" + functionName + "' received: " + JSON.stringify(result));
            };
        }
    },
    processCallbackResult: function (result) {
        utils.logFunctionName("processCallbackResult <- " + result.callback);
        if (result.callback === "setInfo") {
            ready = true;
            utils.setRogerthatData(result.args);
            userCallbacks.ready();

        } else if (result.callback === "apiResult") {
            apiUserCallbacks.resultReceived(result.args.method, result.args.result, result.args.error, result.args.tag);

        } else if (result.callback === "badgeUpdated") {
            userCallbacks.badgeUpdated(result.args);

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
    setRogerthatData: function (info) {
        for (const key of Object.keys(info)) {
            const data = info[key];
            if (data) {
                Object.assign(rogerthatPlugin[key], data);
            }
        }
    }
};

function RogerthatPlugin() {
    patchConsole();
    this.api = {
        callbacks: apiCallbacksRegister,
        call: function (method, params, tag, synchronous) {
            return new Promise(function (resolve, reject) {
                utils.exec(resolve, reject, "api_call", [{method: method, params: params, tag: tag, synchronous: synchronous}]);
            });
        }
    };
    this.app = {
        exit: function () {
            utils.exec(null, null, "app_exit", []);
        },
        exitWithResult: function (result) {
            utils.exec(null, null, "app_exitWithResult", [{"result": result}]);
        }
    };
    this.camera = {
        FRONT: "front",
        BACK: "back",
        startScanningQrCode: function (cameraType, successCallback, errorCallback) {
            return new Promise(function (resolve, reject) {
                utils.exec(execCallback(resolve, successCallback), execCallback(reject, errorCallback), "camera_startScanningQrCode", []);
            });
        },
        stopScanningQrCode: function (cameraType, successCallback, errorCallback) {
            return new Promise(function (resolve, reject) {
                utils.exec(execCallback(resolve, successCallback), execCallback(reject, errorCallback), "camera_stopScanningQrCode", []);
            });
        }
    };
    this.context = function (successCallback, errorCallback) {
        return new Promise(function (resolve, reject) {
            utils.exec(execCallback(resolve, successCallback), execCallback(reject, errorCallback), "context", []);
        });
    };
    this.getBadges = function (onResult, onError) {
        utils.exec(onResult, onError, "badges_list", []);
    };
    this.homeScreen = {
        getHomeScreenContent: function (resolve, reject) {
            // Not a promise since resolve may be called multiple times when the home screen is updated
            utils.exec(resolve, reject, "homescreen_getHomeScreenContent", []);
        }
    };
    this.message = {
        open: function (messageKey, successCallback, errorCallback) {
            return new Promise(function (resolve, reject) {
                utils.exec(execCallback(resolve, successCallback), execCallback(reject, errorCallback), "message_open", [{"message_key": messageKey}]);
            });
        }
    };
    // Set via info call
    this.menuItem = {};
    this.news = {
        getNewsGroup: function (request) {
            return new Promise(function (resolve, reject) {
                utils.exec(resolve, reject, "news_getNewsGroup", [request]);
            });
        },
        getNewsGroups: function (request) {
            return new Promise(function (resolve, reject) {
                utils.exec(resolve, reject, "news_getNewsGroups", [request]);
            });
        },
        getNewsStreamItems: function (request) {
            return new Promise(function (resolve, reject) {
                utils.exec(resolve, reject, "news_getNewsStreamItems", [request]);
            });
        }
    };
    // Set via info call
    this.service = {};
    this.system = {
        os: 'unknown',
        version: 'unknown',
        appVersion: 'unknown'
    };
    this.ui = {
        hideKeyboard: function () {
            return new Promise(function (resolve, reject) {
                utils.exec(resolve, reject, "ui_hideKeyboard", []);
            });
        }
    };
    this.user = {
        // Set via setInfo
        data: {},
        getProfile: function (resolve, reject) {
            utils.exec(resolve, reject, "user_getProfile", []);
        },
        put: function (data) {
            var crp = {};
            if (data === undefined) {
                crp.u = rogerthatPlugin.user.data;
            } else {
                Object.assign(this.user.data, data);
                crp.smart = true;
                crp.u = data;
            }
            return new Promise(function (resolve, reject) {
                utils.exec(resolve, reject, "user_put", [crp]);
            });
        }
    };
    this.util = {
        isConnectedToInternet: function (successCallback, errorCallback) {
            return new Promise(function (resolve, reject) {
                utils.exec(execCallback(resolve, successCallback), execCallback(reject, errorCallback), "util_isConnectedToInternet", []);
            });
        },
        open: function (params, successCallback, errorCallback) {
            var paramsCopy = Object.assign({}, params);
            if (paramsCopy.action_type) {
                if (paramsCopy.action_type === "click" || paramsCopy.action_type === "action") {
                    // This should be handled higher up, not in this library.
                    if (paramsCopy.action.length !== 64) {
                        paramsCopy.action = sha256(paramsCopy.action);
                        console.warn('`action` should be an sha256 hash');
                    }
                }
            }
            return new Promise(function (resolve, reject) {
                utils.exec(execCallback(resolve, successCallback), execCallback(reject, errorCallback), "util_open", [paramsCopy]);
            });
        },
        playAudio: function (url, successCallback, errorCallback) {
            return new Promise(function (resolve, reject) {
                utils.exec(execCallback(resolve, successCallback), execCallback(reject, errorCallback), "util_playAudio", [{"url": url}]);
            });
        },
        uuid: function () {
            var S4 = function () {
                return (((1 + Math.random()) * 0x10000) | 0).toString(16).substring(1);
            };
            return (S4() + S4() + "-" + S4() + "-" + S4() + "-" + S4() + "-" + S4() + S4() + S4());
        },
    };
}

var apiCallbacksRegister = utils.generateCallbacksRegister(apiUserCallbacks);
apiCallbacksRegister.resultReceived = function (callback) {
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

var callbacksRegister = utils.generateCallbacksRegister(userCallbacks);
callbacksRegister.ready = function (callback) {
    userCallbacks.ready = function () {
        callback();
    };
    if (ready) {
        userCallbacks.ready();
    }
};

RogerthatPlugin.prototype.callbacks = callbacksRegister;


function execCallback(promiseCallback, regularCallback) {
    return function (result) {
        if (regularCallback) {
            console.warn('Callback is deprecated, use promise instead');
            regularCallback(result);
        }
        promiseCallback(result);
    };
}

function exceptionToObject(exception) {
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
}

var bind = function (context, fn) {
    return function () {
        return fn.apply(context, arguments);
    };
};

function windowErrorHandler(msg, url, line, column, errorObj) {
    var result = {
        msg: msg,
        url: url,
        line: line,
        column: column,
        errorObj: exceptionToObject(errorObj)
    };
    utils.exec(_dummy, _dummy, "log", [{"e": JSON.stringify(result)}]);
}

if (typeof window !== 'undefined') {
    window.onerror = bind(this, windowErrorHandler);
}

function getStackTrace(e) {
    if (RogerthatPlugin.prototype.system !== undefined && RogerthatPlugin.prototype.system.os === 'android') {
        var stack = (e.stack + '\n').replace(/^\S[^\(]+?[\n$]/gm, '')
            .replace(/^\s+(at eval )?at\s+/gm, '')
            .replace(/^([^\(]+?)([\n$])/gm, '{anonymous}()@$1$2')
            .replace(/^Object.<anonymous>\s*\(([^\)]+)\)/gm, '{anonymous}()@$1').split('\n');
        stack.pop();
        return stack.join('\n');
    } else {
        return e.stack.replace(/\[native code]\n/m, '')
            .replace(/^(?=\w+Error:).*$\n/m, '')
            .replace(/^@/gm, '{anonymous}()@');
    }
}

/**
 * Extends the default console functions so it dispatches the logs to the app
 * This can can in turn dispatch it to the server if log forwarding is enabled.
 * Only patches console.error for android because the rest is handled by CordovaWebChromeClient#onConsoleMessage
 */
function patchConsole() {
    var methods = ['debug', 'info', 'log', 'warn', 'error'];
    var newConsole = window.console;

    function logToApp(args) {
        var log;
        try {
            log = args.length === 1 ? JSON.stringify(args[0]) : JSON.stringify(args);
        } catch (ignored) {
            log = args.length === 1 ? args[0] : args;
        }
        utils.exec(_dummy, _dummy, 'log', [{m: log}]);
    }

    function logErrorToApp(args) {
        var message = '';
        for (var i = 0; i < args.length; i++) {
            var arg = args[i];
            if (arg instanceof Error) {
                message += arg.name + ': ' + arg.message + '\n' + getStackTrace(arg);
            } else {
                message += arg + '\n';
            }
        }
        utils.exec(_dummy, _dummy, 'log', [{e: JSON.stringify(message)}]);
    }


    function intercept(method) {
        var original = newConsole[method];
        newConsole[method] = function () {
            if (method === 'error') {
                logErrorToApp(arguments);
            } else {
                logToApp(arguments);
            }
            original.apply(newConsole, arguments);
        };
    }

    for (var i = 0; i < methods.length; i++) {
        if (methods[i] === 'error' || RogerthatPlugin.prototype.system && RogerthatPlugin.prototype.system.os === 'ios') {
            intercept(methods[i]);
        }
    }
}

var rogerthatPlugin = new RogerthatPlugin();
module.exports = rogerthatPlugin;
utils.exec(utils.processCallbackResult, null, "start", []);

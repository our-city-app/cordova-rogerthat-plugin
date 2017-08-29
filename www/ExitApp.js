var exec = require('cordova/exec');
exports.exitApp = function() {
    exec(
        function callback(data) {
            console.log("exitApp success", data);
        },
        function errorHandler(err) {
            console.log("exitApp error", err);
        },
        'RogerthatPlugin',
        'exitApp',
        []
    );
};

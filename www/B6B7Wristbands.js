var exec = require('cordova/exec');
               
               exports.setDevice = function (success, error, model, uuid, major, minor, command, url, timer) {
               exec(success, error, 'WristbandsPlugin', 'setDevice', [model, uuid, major, minor, command, url, timer]); }
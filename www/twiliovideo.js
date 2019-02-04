var exec = require('cordova/exec');

var TwilioVideo = function() {
    return TwilioVideo;
};

TwilioVideo.openRoom = function(token, room, eventCallback, config, launchActivity) {
    config = !!config ? config : null;
    launchActivity = !!launchActivity;
    exec(function(e) {
        console.log("Twilio open room event fired: " + e);
        if (eventCallback) {
            eventCallback(e);
        }
    }, null, 'TwilioVideoPlugin', 'openRoom', [token, room, config, launchActivity]);
};

TwilioVideo.leaveRoom = function(eventCallback) {
    exec(function(e) {
        console.log("Twilio close room event fired: " + e);
        if (eventCallback) {
            eventCallback(e);
        }
    }, null, 'TwilioVideoPlugin', 'leaveRoom', []);
};

TwilioVideo.widgetVisibility = function(setVisible, eventCallback) {
    setVisible = !!setVisible;
    exec(function(e) {
        console.log("Twilio widget visibility toggle event fired: " + e);
        if (eventCallback) {
            eventCallback(e);
        }
    }, null, 'TwilioVideoPlugin', 'widgetVisibility', [setVisible]);
};

TwilioVideo.toggleMic = function(setEnabled, eventCallback) {
    setEnabled = !!setEnabled;
    exec(function(e) {
        console.log("Twilio toggle mic event fired: " + e);
        if (eventCallback) {
            eventCallback(e);
        }
    }, null, 'TwilioVideoPlugin', 'toggleMic', [setEnabled]);
};

TwilioVideo.setActiveSpeaker = function(particpantId, eventCallback) {
    particpantId = !!particpantId ? particpantId : null;
    exec(function(e) {
        console.log("Twilio set active speaker #"+ particpantId +" event fired: " + e);
        if (eventCallback) {
            eventCallback(e);
        }
    }, null, 'TwilioVideoPlugin', 'setActiveSpeaker', [particpantId]);
};

module.exports = TwilioVideo;
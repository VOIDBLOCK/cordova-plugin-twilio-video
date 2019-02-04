package org.apache.cordova.twiliovideo;

import android.util.Log;

/**
 * Created by rpanadero on 13/9/18.
 * Further Developed by VOIDBLOCK TEAM
 */
public class CallEventsProducer {
    public static final String OPEN_LISTENER_KEYWORD       = "open";
    public static final String CLOSE_LISTENER_KEYWORD      = "close";
    public static final String VISIBILITY_LISTENER_KEYWORD = "visibility";
    public static final String MIC_TOGGLE_LISTENER_KEYWORD = "mic";

    private static CallEventsProducer instance;
    private CallObserver openListener;
    private CallObserver closeListener;
    private CallObserver visibilityListener;
    private CallObserver micListener;

    public static CallEventsProducer getInstance() {
        if (instance == null) {
            instance = new CallEventsProducer();
        }
        
        return instance;
    }

    public boolean setObserver(String type, CallObserver listener) {
        return setListener(type, listener);
    }

    public void publishEvent(String type, CallEvent event) {
        if (hasListener(type)) {
            getListener(type).onEvent(event.name());
        } else {
            Log.i("TwilioEvents", "Event " + type + ":" + event + " has no assigned listeners!");
        }
    }

    private boolean hasListener(String type) {
        return getListener(type) != null;
    }

    private CallObserver getListener(String type) {
        CallObserver listener = null;

        if (type == OPEN_LISTENER_KEYWORD) {
            listener = openListener;
        } else if (type == CLOSE_LISTENER_KEYWORD) {
            listener = closeListener;
        } else if (type == VISIBILITY_LISTENER_KEYWORD) {
            listener = visibilityListener;
        } else if (type == MIC_TOGGLE_LISTENER_KEYWORD) {
            listener = micListener;
        }

        return listener;
    }

    private boolean setListener(String type, CallObserver listener) {
        boolean ret = true;

        if (type == OPEN_LISTENER_KEYWORD) {
            openListener = listener;
        } else if (type == CLOSE_LISTENER_KEYWORD) {
            closeListener = listener;
        } else if (type == VISIBILITY_LISTENER_KEYWORD) {
            visibilityListener = listener;
        } else if (type == MIC_TOGGLE_LISTENER_KEYWORD) {
            micListener = listener;
        } else {
            ret = false;
        }

        Log.i("TwilioEventsProducer", "Listener set for type: " + type);
        return ret;
    }
}

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

    public void setObserver(String type, CallObserver listener) {
        setListener(type, listener);
    }

    public void publishEvent(String type, CallEvent event) {
        if (hasListener(type)) {
            getListener(type).onEvent(event.name());
        } else {
            Log.i("TwilioEvents: " + type, "Event " + event + " has no assigned listeners!");
        }
    }

    private boolean hasListener(String type) {
        return getListener(type) != null;
    }

    private CallObserver getListener(String type) {
        CallObserver listener = null;

        if (type == OPEN_LISTENER_KEYWORD) {
            this.listener = openListener;
        } else if (type == CLOSE_LISTENER_KEYWORD) {
            this.listener = closeListener;
        } else if (type == VISIBILITY_LISTENER_KEYWORD) {
            this.listener = visibilityListener;
        } else if (type == MIC_TOGGLE_LISTENER_KEYWORD) {
            this.listener = micListener;
        }

        return this.listener;
    }

    private void setListener(String type, CallObserver listener) {
        boolean ret = true;

        if (type == OPEN_LISTENER_KEYWORD) {
            this.openListener = listener;
        } else if (type == CLOSE_LISTENER_KEYWORD) {
            this.closeListener = listener;
        } else if (type == VISIBILITY_LISTENER_KEYWORD) {
            this.visibilityListener = listener;
        } else if (type == MIC_TOGGLE_LISTENER_KEYWORD) {
            this.micListener = listener;
        } else {
            ret = false;
        }

        return ret;
    }
}

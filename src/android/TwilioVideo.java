package org.apache.cordova.twiliovideo;
// IMPORT R class HERE

import com.twilio.video.CameraCapturer;
import com.twilio.video.ConnectOptions;
import com.twilio.video.LocalAudioTrack;
import com.twilio.video.LocalParticipant;
import com.twilio.video.LocalVideoTrack;
import com.twilio.video.RemoteAudioTrack;
import com.twilio.video.RemoteAudioTrackPublication;
import com.twilio.video.RemoteDataTrack;
import com.twilio.video.RemoteDataTrackPublication;
import com.twilio.video.RemoteParticipant;
import com.twilio.video.RemoteVideoTrack;
import com.twilio.video.RemoteVideoTrackPublication;
import com.twilio.video.Room;
import com.twilio.video.RoomState;
import com.twilio.video.TwilioException;
import com.twilio.video.Video;
import com.twilio.video.VideoRenderer;
import com.twilio.video.VideoTrack;
import com.twilio.video.VideoView;
import org.apache.cordova.BuildHelper;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaResourceApi;
import org.apache.cordova.LOG;
import org.apache.cordova.PermissionHelper;
import org.apache.cordova.PluginResult;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaWebView;
import org.json.JSONArray;
import org.json.JSONException;
import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;
import java.util.Collections;


public class TwilioVideo extends CordovaPlugin {

    public TwilioVideo that;

    public CallbackContext callbackContext;
    private CordovaInterface cordova;
    private String roomId;
    private String token;
    private CallConfig config = new CallConfig();

    private LinearLayout linearLayout;
    private static float MINIMIZED_WIDGET_HEIGHT        = 104;
    private static float MINIMIZED_PRIMARY_VIDEO_HEIGHT = 56;
    private boolean isViewResizeAllowed = true;
    private boolean isViewExpanded      = false;
    private boolean isRoomOpen          = false;
    private boolean isUsingWidget       = true;

    private static final int CAMERA_MIC_PERMISSION_REQUEST_CODE = 1;
    private static final String TAG = "TwilioVideo";

    /*
     * Audio and video tracks can be created with names. This feature is useful for categorizing
     * tracks of participants. For example, if one participant publishes a video track with
     * ScreenCapturer and CameraCapturer with the names "screen" and "camera" respectively then
     * other participants can use RemoteVideoTrack#getName to determine which video track is
     * produced from the other participant's screen or camera.
     */
    private static final String LOCAL_AUDIO_TRACK_NAME = "mic";
    private static final String LOCAL_VIDEO_TRACK_NAME = "camera";

    /*
     * Access token used to connect. This field will be set either from the console generated token
     * or the request to the token server.
     */
    private String accessToken;

    /*
     * A Room represents communication between a local participant and one or more participants.
     */
    private Room room;
    private LocalParticipant localParticipant;

    /*
     * A VideoView receives frames from a local or remote video track and renders them
     * to an associated view.
     */
    private VideoView primaryVideoView;
    private VideoView thumbnailVideoView;

    /*
     * Android application UI elements
     */
    private CameraCapturerCompat cameraCapturer;
    private LocalAudioTrack localAudioTrack;
    private LocalVideoTrack localVideoTrack;
    private RelativeLayout rootPluginWidget;
    private FloatingActionButton resizeActionFab;
    private FloatingActionButton switchCameraActionFab;
    private FloatingActionButton localVideoActionFab;
    private FloatingActionButton muteActionFab;
    private FloatingActionButton switchAudioActionFab;
    private LinearLayout progressBar;
    private AudioManager audioManager;
    private String participantIdentity;

    private int previousAudioMode;
    private int widgetHeightDiff;
    private boolean previousMicrophoneMute;
    private VideoRenderer localVideoView;
    private boolean disconnectedFromOnDestroy;

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        this.cordova = cordova;
        that = this;
        // your init code here
    }

	public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
		this.callbackContext = callbackContext;
		if (action.equals("openRoom")) {
		    this.registerCallListener(CallEventsProducer.OPEN_LISTENER_KEYWORD, callbackContext);
		   	this.openRoom(args);
		}
		else if (action.equals("leaveRoom")) {
		    this.registerCallListener(CallEventsProducer.CLOSE_LISTENER_KEYWORD, callbackContext);
		   	this.leaveRoom();
		}
		else if (action.equals("widgetVisibility")) {
            Boolean makeVisible = false;

            if (args.length() > 0) {
                makeVisible = args.getBoolean(0);
            }
            
		    this.registerCallListener(CallEventsProducer.VISIBILITY_LISTENER_KEYWORD, callbackContext);
            this.widgetVisibility(makeVisible);
		}
		else if (action.equals("toggleMic")) {
            Boolean setEnabled = false;

            if (args.length() > 0) {
                setEnabled = args.getBoolean(0);
            }
            
		    this.registerCallListener(CallEventsProducer.MIC_TOGGLE_LISTENER_KEYWORD, callbackContext);
            this.toggleMic(setEnabled);
		}
		else if (action.equals("setActiveSpeaker")) {
            String particpantId = args.getString(0);
            this.setActiveSpeaker(particpantId);
		    // this.registerCallListener("set_active_speaker", callbackContext);
		}
        return true;
    }
    

    public void openRoom(final JSONArray args) {
        try {
            this.token = args.getString(0);
            this.roomId = args.getString(1);
            that = this;
            final String token = this.token;
            final String roomId = this.roomId;

            this.accessToken       = token;
            Boolean launchActivity = false;
            
            if (args.length() > 2) {
                this.config.parse(args.getJSONObject(2));
            }

            if (args.length() > 3) {
                launchActivity = args.getBoolean(3);
            }

            LOG.d("TOKEN", token);
            LOG.d("ROOMID", roomId);

            if (launchActivity) {
                cordova.getThreadPool().execute(new Runnable() {
                    public void run() {
                        that.isUsingWidget = false;
                        Intent intentTwilioVideo = new Intent(that.cordova.getActivity().getBaseContext(), TwilioVideoActivity.class);
                        intentTwilioVideo.putExtra("token", token);
                        intentTwilioVideo.putExtra("roomId", roomId);
                        intentTwilioVideo.putExtra("config", config);
                        // avoid calling other phonegap apps
                        intentTwilioVideo.setPackage(that.cordova.getActivity().getApplicationContext().getPackageName());
                        //that.cordova.startActivityForResult(that, intentTwilioVideo);
                        //that.cordova.getActivity().startActivity(intentTwilioVideo);
                        that.cordova.startActivityForResult(that, intentTwilioVideo, 0);
                    }
                });
            } else {
                cordova.getActivity().runOnUiThread(new Runnable() {
                    public void run() {
                        that.isUsingWidget = true;
                        that.initWidgetViews();
                    }
                });
            }
        } catch (JSONException e) {
            //Log.e(TAG, "Invalid JSON string: " + json, e);
            //return null;
        }
    }

    public void leaveRoom() {
        cordova.getActivity().runOnUiThread(new Runnable() {
            public void run() {
                if (!that.isRoomOpen || !that.isUsingWidget) {
                    that.publishCloseEvent(CallEvent.DISCONNECTED);
                    return;
                }

                that.disconnectRoom();
                that.finish();
            }
        });
    }

    public void widgetVisibility(final Boolean setVisibile) {
        cordova.getActivity().runOnUiThread(new Runnable() {
            public void run() {
                if (!that.isRoomOpen || !that.isUsingWidget) {
                    that.publishVisibilityEvent(CallEvent.DISCONNECTED);
                    return;
                }

                that.rootPluginWidget.setVisibility(setVisibile ? View.VISIBLE : View.GONE);
                that.publishVisibilityEvent(setVisibile ? CallEvent.WIDGET_SHOW : CallEvent.WIDGET_HIDE);
            }
        });
    }

    public void toggleMic(final Boolean setEnabled) {
        cordova.getActivity().runOnUiThread(new Runnable() {
            public void run() {
                if (!that.isRoomOpen || !that.isUsingWidget) {
                    that.publishMicEvent(CallEvent.DISCONNECTED);
                    return;
                }

                if (setEnabled) {
                    that.muteActionFab.show();
                } else {
                    that.muteActionFab.hide();
                }

                that.configureAudio(setEnabled);
                that.publishMicEvent(setEnabled ? CallEvent.MIC_ENABLED : CallEvent.MIC_DISABLED);
            }
        });
    }

    public void setActiveSpeaker(final String particpantId) {
        cordova.getActivity().runOnUiThread(new Runnable() {
            public void run() {
                if (!that.isRoomOpen || !that.isUsingWidget) {
                    // that.publishCloseEvent(CallEvent.DISCONNECTED);
                    return;
                }

                // that.
                // TODO: Set Current Active Speaker
            }
        });
    }


    private void initWidgetViews() {
        publishOpenEvent(CallEvent.OPENED);

        isRoomOpen          = true;
        isViewResizeAllowed = config.getEnableWidgetResize();
        isViewExpanded      = false;

        Activity activity   = cordova.getActivity();
        int screenW         = getDeviceDimen(true);
        int screenH         = getDeviceDimen(false);
        int widgetH         = Math.round(convertDpToPixel(MINIMIZED_WIDGET_HEIGHT));
        int heightDiff      = Math.round(screenH - widgetH);
        LayoutParams tlp    = new LayoutParams(screenW, screenH);
        LayoutParams wlp    = new LayoutParams(screenW, widgetH);
        View nativeCtrls    = activity.getLayoutInflater().inflate(R.layout.activity_video, null);

        nativeCtrls.setLayoutParams(wlp);

        if (linearLayout != null) {
            linearLayout.removeAllViews();
        }

        linearLayout = new LinearLayout(activity);
        linearLayout.setLayoutParams(tlp);

        linearLayout.setPadding(0, heightDiff, 0, 0);
        linearLayout.addView(nativeCtrls);

        activity.getWindow().addContentView(linearLayout, tlp);
        nativeCtrls.bringToFront();

        widgetHeightDiff = heightDiff;
        primaryVideoView = linearLayout.findViewById(R.id.primary_video_view);
        thumbnailVideoView = linearLayout.findViewById(R.id.thumbnail_video_view);

        rootPluginWidget = linearLayout.findViewById(R.id.plugin_root_view);
        resizeActionFab = linearLayout.findViewById(R.id.resize_action_fab);
        switchCameraActionFab = linearLayout.findViewById(R.id.switch_camera_action_fab);
        localVideoActionFab = linearLayout.findViewById(R.id.local_video_action_fab);
        muteActionFab = linearLayout.findViewById(R.id.mute_action_fab);
        switchAudioActionFab = linearLayout.findViewById(R.id.switch_audio_action_fab);
        progressBar = linearLayout.findViewById(R.id.progress_indicator);

        Log.d(TAG, "INIT UI EVENTS & STYLING");
        initializeUI();

        activity.setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);

        audioManager = (AudioManager) activity.getSystemService(Context.AUDIO_SERVICE);
        audioManager.setSpeakerphoneOn(true);

        Log.d(TAG, "PERMISSIONS CHECK");

        if (!checkPermissionForCameraAndMicrophone()) {
            Log.d(TAG, "REQUEST PERMISSIONS");
            requestPermissionForCameraAndMicrophone();
        } else {
            Log.d(TAG, "PERMISSIONS OK. CREATE LOCAL MEDIA");
            createAudioAndVideoTracks();
            connectToRoom();
        }

        // hideLoading();
    }

    private void initializeUI() {
        if (! isViewResizeAllowed) {
            rootPluginWidget.getLayoutParams().height = getDeviceDimen(false);
            rootPluginWidget.requestLayout();
            resizeActionFab.hide();
        }

//        if (config.getPrimaryColorHex() != null) {
//            int primaryColor = Color.parseColor(config.getPrimaryColorHex());
//            ColorStateList color = ColorStateList.valueOf(primaryColor);
//
//            resizeActionFab.setBackgroundTintList(color);
//        }

//        if (config.getSecondaryColorHex() != null) {
//            int secondaryColor = Color.parseColor(config.getSecondaryColorHex());
//            ColorStateList color = ColorStateList.valueOf(secondaryColor);
//            switchCameraActionFab.setBackgroundTintList(color);
//            localVideoActionFab.setBackgroundTintList(color);
//            muteActionFab.setBackgroundTintList(color);
//            switchAudioActionFab.setBackgroundTintList(color);
//        }

        switchCameraActionFab.show();
        switchCameraActionFab.setOnClickListener(switchCameraClickListener());
        localVideoActionFab.show();
        localVideoActionFab.setOnClickListener(localVideoClickListener());
        muteActionFab.show();
        muteActionFab.setOnClickListener(muteClickListener());
        switchAudioActionFab.show();
        switchAudioActionFab.setOnClickListener(switchAudioClickListener());
    }

    private void registerCallListener(String type, final CallbackContext callbackContext) {
        if (callbackContext == null) {
            return;
        }
        CallEventsProducer.getInstance().setObserver(type, new CallObserver() {
            @Override
            public void onEvent(String event) {
                Log.i("TwilioEvents", "Event received: " + event);
                PluginResult result = new PluginResult(PluginResult.Status.OK, event);
                result.setKeepCallback(true);
                callbackContext.sendPluginResult(result);
            }
        });
    }

    public Bundle onSaveInstanceState() {
        Bundle state = new Bundle();
        state.putString("token", this.token);
        state.putString("roomId", this.roomId);
        state.putSerializable("config", this.config);
        return state;
    }

    public void onRestoreStateForActivityResult(Bundle state, CallbackContext callbackContext) {
        this.token = state.getString("token");
        this.roomId = state.getString("roomId");
        this.config = (CallConfig) state.getSerializable("config");
        this.callbackContext = callbackContext;
    }

    private int getDeviceDimen(Boolean returnWidth) {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        WindowManager windowmanager = (WindowManager) cordova.getContext().getSystemService(Context.WINDOW_SERVICE);
        windowmanager.getDefaultDisplay().getMetrics(displayMetrics);

        int deviceWidth = displayMetrics.widthPixels;
        int deviceHeight = displayMetrics.heightPixels;

        return (!!returnWidth ? deviceWidth : deviceHeight);
    }

    public static float convertPixelsToDp(float px) {
        DisplayMetrics metrics = Resources.getSystem().getDisplayMetrics();
        float dp = px / (metrics.densityDpi / 160f);
        return Math.round(dp);
    }

    public static float convertDpToPixel(float dp) {
        DisplayMetrics metrics = Resources.getSystem().getDisplayMetrics();
        float px = dp * (metrics.densityDpi / 160f);
        return Math.round(px);
    }

    private void publishOpenEvent(CallEvent event) {
        publishEvent(CallEventsProducer.OPEN_LISTENER_KEYWORD, event);
    }

    private void publishCloseEvent(CallEvent event) {
        publishEvent(CallEventsProducer.CLOSE_LISTENER_KEYWORD, event);
    }

    private void publishVisibilityEvent(CallEvent event) {
        publishEvent(CallEventsProducer.VISIBILITY_LISTENER_KEYWORD, event);
    }

    private void publishMicEvent(CallEvent event) {
        publishEvent(CallEventsProducer.MIC_TOGGLE_LISTENER_KEYWORD, event);
    }

    private void publishEvent(String type, CallEvent event) {
        CallEventsProducer.getInstance().publishEvent(type, event);
    }

    private boolean checkPermissionForCameraAndMicrophone() {
        int resultCamera = ContextCompat.checkSelfPermission(cordova.getContext(), Manifest.permission.CAMERA);
        int resultMic = ContextCompat.checkSelfPermission(cordova.getContext(), Manifest.permission.RECORD_AUDIO);
        return resultCamera == PackageManager.PERMISSION_GRANTED &&
                resultMic == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissionForCameraAndMicrophone() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(cordova.getActivity(), Manifest.permission.CAMERA) ||
                ActivityCompat.shouldShowRequestPermissionRationale(cordova.getActivity(), Manifest.permission.RECORD_AUDIO)) {
            Toast.makeText(cordova.getContext(),
                    R.string.permissions_needed,
                    Toast.LENGTH_LONG).show();
        } else {
            ActivityCompat.requestPermissions(
                    cordova.getActivity(),
                    new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO},
                    CAMERA_MIC_PERMISSION_REQUEST_CODE);
        }
    }

    private void createAudioAndVideoTracks() {
        // Share your microphone
        localAudioTrack = LocalAudioTrack.create(cordova.getContext(), true, LOCAL_AUDIO_TRACK_NAME);

        // Share your camera
        cameraCapturer = new CameraCapturerCompat(cordova.getContext(), getAvailableCameraSource());
        localVideoTrack = LocalVideoTrack.create(cordova.getContext(),
                true,
                cameraCapturer.getVideoCapturer(),
                LOCAL_VIDEO_TRACK_NAME);

        moveLocalVideoToThumbnailView();
    }

    private CameraCapturer.CameraSource getAvailableCameraSource() {
        return (CameraCapturer.isSourceAvailable(CameraCapturer.CameraSource.FRONT_CAMERA)) ?
                (CameraCapturer.CameraSource.FRONT_CAMERA) :
                (CameraCapturer.CameraSource.BACK_CAMERA);
    }

    private void moveLocalVideoToThumbnailView() {
        if (thumbnailVideoView.getVisibility() == View.GONE) {
            thumbnailVideoView.setVisibility(View.VISIBLE);

            // if (isViewExpanded) {
            // }

            if(localVideoTrack!=null) {
                localVideoTrack.removeRenderer(primaryVideoView);
                localVideoTrack.addRenderer(thumbnailVideoView);
            }

            if(localVideoView != null && thumbnailVideoView != null) {
                localVideoView = thumbnailVideoView;
            }

            thumbnailVideoView.setMirror(cameraCapturer.getCameraSource() ==
                    CameraCapturer.CameraSource.FRONT_CAMERA);
        }
    }

    /*
     * Called when participant leaves the room
     */
    private void removeRemoteParticipant(RemoteParticipant participant) {
        if (!participant.getIdentity().equals(participantIdentity)) {
            return;
        }

        /*
         * Remove participant renderer
         */
        if (participant.getRemoteVideoTracks().size() > 0) {
            RemoteVideoTrackPublication remoteVideoTrackPublication =
                    participant.getRemoteVideoTracks().get(0);

            /*
             * Remove video only if subscribed to participant track
             */
            if (remoteVideoTrackPublication.isTrackSubscribed()) {
                removeParticipantVideo(remoteVideoTrackPublication.getRemoteVideoTrack());
            }
        }
    }

    private void removeParticipantVideo(VideoTrack videoTrack) {
        primaryVideoView.setVisibility(View.GONE);
        videoTrack.removeRenderer(primaryVideoView);
    }

    private void requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioAttributes playbackAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build();
            AudioFocusRequest focusRequest =
                    new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                            .setAudioAttributes(playbackAttributes)
                            .setAcceptsDelayedFocusGain(true)
                            .setOnAudioFocusChangeListener(
                                    new AudioManager.OnAudioFocusChangeListener() {
                                        @Override
                                        public void onAudioFocusChange(int i) { }
                                    })
                            .build();
            audioManager.requestAudioFocus(focusRequest);
        } else {
            audioManager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        }
    }

    private void presentConnectionErrorAlert(String message) {
        if (config.getHandleErrorInApp()) {
            Log.i(TAG, "Error handling disabled for the plugin. This error should be handled in the hybrid app");
            finish();
            return;
        }
        Log.i(TAG, "Connection error handled by the plugin");
        AlertDialog.Builder builder = new AlertDialog.Builder(cordova.getContext());
        builder.setMessage(message)
                .setCancelable(false)
                .setPositiveButton(config.getI18nAccept(), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        finish();
                    }
                });
        AlertDialog alert = builder.create();
        alert.show();
    }

    /*
     * Called when participant joins the room
     */
    private void addRemoteParticipant(RemoteParticipant participant) {
        participantIdentity = participant.getIdentity();


        /*
         * Add participant renderer
         */
        if (participant.getRemoteVideoTracks().size() > 0) {
            RemoteVideoTrackPublication remoteVideoTrackPublication =
                    participant.getRemoteVideoTracks().get(0);

            /*
             * Only render video tracks that are subscribed to
             */
            if (remoteVideoTrackPublication.isTrackSubscribed()) {
                addRemoteParticipantVideo(remoteVideoTrackPublication.getRemoteVideoTrack());
            }
        }

        /*
         * Start listening for participant media events
         */
        participant.setListener(remoteParticipantListener());
    }

    /*
     * Set primary view as renderer for participant video track
     */
    private void addRemoteParticipantVideo(VideoTrack videoTrack) {
        primaryVideoView.setVisibility(View.VISIBLE);
        primaryVideoView.setMirror(false);
        videoTrack.addRenderer(primaryVideoView);
    }

    private void configureAudio(boolean enable) {
        if (enable) {
            previousAudioMode = audioManager.getMode();
            // Request audio focus before making any device switch
            requestAudioFocus();
            /*
             * Use MODE_IN_COMMUNICATION as the default audio mode. It is required
             * to be in this mode when playout and/or recording starts for the best
             * possible VoIP performance. Some devices have difficulties with
             * speaker mode if this is not set.
             */
            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            /*
             * Always disable microphone mute during a WebRTC call.
             */
            previousMicrophoneMute = audioManager.isMicrophoneMute();
            audioManager.setMicrophoneMute(false);
        } else {
            audioManager.setMode(previousAudioMode);
            audioManager.abandonAudioFocus(null);
            audioManager.setMicrophoneMute(previousMicrophoneMute);
        }
    }

    private void connectToRoom() {
        configureAudio(true);
        ConnectOptions.Builder connectOptionsBuilder = new ConnectOptions.Builder(accessToken)
                .roomName(this.roomId);

        /*
         * Add local audio track to connect options to share with participants.
         */
        if (localAudioTrack != null) {
            connectOptionsBuilder
                    .audioTracks(Collections.singletonList(localAudioTrack));
        }

        /*
         * Add local video track to connect options to share with participants.
         */
        if (localVideoTrack != null) {
            connectOptionsBuilder.videoTracks(Collections.singletonList(localVideoTrack));
        }

        room = Video.connect(cordova.getContext(), connectOptionsBuilder.build(), roomListener());

        resizeActionFab.show();
        resizeActionFab.setOnClickListener(resizeClickListener());
    }

    protected void disconnectRoom() {
        /*
         * Always disconnect from the room before leaving the Activity to
         * ensure any memory allocated to the Room resource is freed.
         */
        if (room != null && room.getState() != RoomState.DISCONNECTED) {
            room.disconnect();
            disconnectedFromOnDestroy = true;
        }

        /*
         * Release the local audio and video tracks ensuring any memory allocated to audio
         * or video is freed.
         */
        if (localAudioTrack != null) {
            localAudioTrack.release();
            localAudioTrack = null;
        }

        if (localVideoTrack != null) {
            localVideoTrack.release();
            localVideoTrack = null;
        }
    }

    protected void finish() {
        isRoomOpen = false;
        configureAudio(false);
        cordova.getActivity().overridePendingTransition(0, 0);
        linearLayout.removeAllViews();
        publishOpenEvent(CallEvent.CLOSED);
        publishCloseEvent(CallEvent.CLOSED);
    }


    /*
     * Room events listener
     */
    private Room.Listener roomListener() {
        return new Room.Listener() {
            @Override
            public void onConnected(Room room) {
                localParticipant = room.getLocalParticipant();
                publishOpenEvent(CallEvent.CONNECTED);

                for (RemoteParticipant remoteParticipant : room.getRemoteParticipants()) {
                    addRemoteParticipant(remoteParticipant);
                    break;
                }

                hideLoading();
            }

            @Override
            public void onConnectFailure(Room room, TwilioException e) {
                publishOpenEvent(CallEvent.CONNECT_FAILURE);
                TwilioVideo.this.presentConnectionErrorAlert(config.getI18nConnectionError());
            }

            @Override
            public void onDisconnected(Room room, TwilioException e) {
                localParticipant = null;
                TwilioVideo.this.room = null;
                // Only reinitialize the UI if disconnect was not called from onDestroy()
                if (!disconnectedFromOnDestroy && e != null) {
                    publishOpenEvent(CallEvent.DISCONNECTED_WITH_ERROR);
                    TwilioVideo.this.presentConnectionErrorAlert(config.getI18nDisconnectedWithError());
                } else {
                    publishOpenEvent(CallEvent.DISCONNECTED);
                }
            }

            @Override
            public void onParticipantConnected(Room room, RemoteParticipant participant) {
                publishOpenEvent(CallEvent.PARTICIPANT_CONNECTED);
                addRemoteParticipant(participant);
            }

            @Override
            public void onParticipantDisconnected(Room room, RemoteParticipant participant) {
                publishOpenEvent(CallEvent.PARTICIPANT_DISCONNECTED);
                removeRemoteParticipant(participant);
            }

            @Override
            public void onRecordingStarted(Room room) {
                /*
                 * Indicates when media shared to a Room is being recorded. Note that
                 * recording is only available in our Group Rooms developer preview.
                 */
                Log.d(TAG, "onRecordingStarted");
            }

            @Override
            public void onRecordingStopped(Room room) {
                /*
                 * Indicates when media shared to a Room is no longer being recorded. Note that
                 * recording is only available in our Group Rooms developer preview.
                 */
                Log.d(TAG, "onRecordingStopped");
            }
        };
    }

    private RemoteParticipant.Listener remoteParticipantListener() {
        return new RemoteParticipant.Listener() {

            @Override
            public void onAudioTrackPublished(RemoteParticipant remoteParticipant, RemoteAudioTrackPublication remoteAudioTrackPublication) {
                Log.i(TAG, String.format("onAudioTrackPublished: " +
                                "[RemoteParticipant: identity=%s], " +
                                "[RemoteAudioTrackPublication: sid=%s, enabled=%b, " +
                                "subscribed=%b, name=%s]",
                        remoteParticipant.getIdentity(),
                        remoteAudioTrackPublication.getTrackSid(),
                        remoteAudioTrackPublication.isTrackEnabled(),
                        remoteAudioTrackPublication.isTrackSubscribed(),
                        remoteAudioTrackPublication.getTrackName()));
            }

            @Override
            public void onAudioTrackUnpublished(RemoteParticipant remoteParticipant, RemoteAudioTrackPublication remoteAudioTrackPublication) {
                Log.i(TAG, String.format("onAudioTrackUnpublished: " +
                                "[RemoteParticipant: identity=%s], " +
                                "[RemoteAudioTrackPublication: sid=%s, enabled=%b, " +
                                "subscribed=%b, name=%s]",
                        remoteParticipant.getIdentity(),
                        remoteAudioTrackPublication.getTrackSid(),
                        remoteAudioTrackPublication.isTrackEnabled(),
                        remoteAudioTrackPublication.isTrackSubscribed(),
                        remoteAudioTrackPublication.getTrackName()));
            }

            @Override
            public void onAudioTrackSubscribed(RemoteParticipant remoteParticipant, RemoteAudioTrackPublication remoteAudioTrackPublication, RemoteAudioTrack remoteAudioTrack) {
                Log.i(TAG, String.format("onAudioTrackSubscribed: " +
                                "[RemoteParticipant: identity=%s], " +
                                "[RemoteAudioTrack: enabled=%b, playbackEnabled=%b, name=%s]",
                        remoteParticipant.getIdentity(),
                        remoteAudioTrack.isEnabled(),
                        remoteAudioTrack.isPlaybackEnabled(),
                        remoteAudioTrack.getName()));
                publishOpenEvent(CallEvent.AUDIO_TRACK_ADDED);
            }

            @Override
            public void onAudioTrackSubscriptionFailed(RemoteParticipant remoteParticipant, RemoteAudioTrackPublication remoteAudioTrackPublication, TwilioException twilioException) {
                Log.i(TAG, String.format("onAudioTrackSubscriptionFailed: " +
                                "[RemoteParticipant: identity=%s], " +
                                "[RemoteAudioTrackPublication: sid=%b, name=%s]" +
                                "[TwilioException: code=%d, message=%s]",
                        remoteParticipant.getIdentity(),
                        remoteAudioTrackPublication.getTrackSid(),
                        remoteAudioTrackPublication.getTrackName(),
                        twilioException.getCode(),
                        twilioException.getMessage()));
            }

            @Override
            public void onAudioTrackUnsubscribed(RemoteParticipant remoteParticipant, RemoteAudioTrackPublication remoteAudioTrackPublication, RemoteAudioTrack remoteAudioTrack) {
                Log.i(TAG, String.format("onAudioTrackUnsubscribed: " +
                                "[RemoteParticipant: identity=%s], " +
                                "[RemoteAudioTrack: enabled=%b, playbackEnabled=%b, name=%s]",
                        remoteParticipant.getIdentity(),
                        remoteAudioTrack.isEnabled(),
                        remoteAudioTrack.isPlaybackEnabled(),
                        remoteAudioTrack.getName()));
                publishOpenEvent(CallEvent.AUDIO_TRACK_REMOVED);
            }

            @Override
            public void onVideoTrackPublished(RemoteParticipant remoteParticipant, RemoteVideoTrackPublication remoteVideoTrackPublication) {
                Log.i(TAG, String.format("onVideoTrackPublished: " +
                                "[RemoteParticipant: identity=%s], " +
                                "[RemoteVideoTrackPublication: sid=%s, enabled=%b, " +
                                "subscribed=%b, name=%s]",
                        remoteParticipant.getIdentity(),
                        remoteVideoTrackPublication.getTrackSid(),
                        remoteVideoTrackPublication.isTrackEnabled(),
                        remoteVideoTrackPublication.isTrackSubscribed(),
                        remoteVideoTrackPublication.getTrackName()));
            }

            @Override
            public void onVideoTrackUnpublished(RemoteParticipant remoteParticipant, RemoteVideoTrackPublication remoteVideoTrackPublication) {
                Log.i(TAG, String.format("onVideoTrackUnpublished: " +
                                "[RemoteParticipant: identity=%s], " +
                                "[RemoteVideoTrackPublication: sid=%s, enabled=%b, " +
                                "subscribed=%b, name=%s]",
                        remoteParticipant.getIdentity(),
                        remoteVideoTrackPublication.getTrackSid(),
                        remoteVideoTrackPublication.isTrackEnabled(),
                        remoteVideoTrackPublication.isTrackSubscribed(),
                        remoteVideoTrackPublication.getTrackName()));
            }

            @Override
            public void onVideoTrackSubscribed(RemoteParticipant remoteParticipant, RemoteVideoTrackPublication remoteVideoTrackPublication, RemoteVideoTrack remoteVideoTrack) {
                Log.i(TAG, String.format("onVideoTrackSubscribed: " +
                                "[RemoteParticipant: identity=%s], " +
                                "[RemoteVideoTrack: enabled=%b, name=%s]",
                        remoteParticipant.getIdentity(),
                        remoteVideoTrack.isEnabled(),
                        remoteVideoTrack.getName()));
                publishOpenEvent(CallEvent.VIDEO_TRACK_ADDED);
                addRemoteParticipantVideo(remoteVideoTrack);
            }

            @Override
            public void onVideoTrackSubscriptionFailed(RemoteParticipant remoteParticipant, RemoteVideoTrackPublication remoteVideoTrackPublication, TwilioException twilioException) {
                Log.i(TAG, String.format("onVideoTrackSubscriptionFailed: " +
                                "[RemoteParticipant: identity=%s], " +
                                "[RemoteVideoTrackPublication: sid=%b, name=%s]" +
                                "[TwilioException: code=%d, message=%s]",
                        remoteParticipant.getIdentity(),
                        remoteVideoTrackPublication.getTrackSid(),
                        remoteVideoTrackPublication.getTrackName(),
                        twilioException.getCode(),
                        twilioException.getMessage()));
            }

            @Override
            public void onVideoTrackUnsubscribed(RemoteParticipant remoteParticipant, RemoteVideoTrackPublication remoteVideoTrackPublication, RemoteVideoTrack remoteVideoTrack) {
                Log.i(TAG, String.format("onVideoTrackUnsubscribed: " +
                                "[RemoteParticipant: identity=%s], " +
                                "[RemoteVideoTrack: enabled=%b, name=%s]",
                        remoteParticipant.getIdentity(),
                        remoteVideoTrack.isEnabled(),
                        remoteVideoTrack.getName()));
                publishOpenEvent(CallEvent.VIDEO_TRACK_REMOVED);
                removeParticipantVideo(remoteVideoTrack);
            }

            @Override
            public void onDataTrackPublished(RemoteParticipant remoteParticipant, RemoteDataTrackPublication remoteDataTrackPublication) {
                Log.i(TAG, String.format("onDataTrackPublished: " +
                                "[RemoteParticipant: identity=%s], " +
                                "[RemoteDataTrackPublication: sid=%s, enabled=%b, " +
                                "subscribed=%b, name=%s]",
                        remoteParticipant.getIdentity(),
                        remoteDataTrackPublication.getTrackSid(),
                        remoteDataTrackPublication.isTrackEnabled(),
                        remoteDataTrackPublication.isTrackSubscribed(),
                        remoteDataTrackPublication.getTrackName()));
            }

            @Override
            public void onDataTrackUnpublished(RemoteParticipant remoteParticipant, RemoteDataTrackPublication remoteDataTrackPublication) {
                Log.i(TAG, String.format("onDataTrackUnpublished: " +
                                "[RemoteParticipant: identity=%s], " +
                                "[RemoteDataTrackPublication: sid=%s, enabled=%b, " +
                                "subscribed=%b, name=%s]",
                        remoteParticipant.getIdentity(),
                        remoteDataTrackPublication.getTrackSid(),
                        remoteDataTrackPublication.isTrackEnabled(),
                        remoteDataTrackPublication.isTrackSubscribed(),
                        remoteDataTrackPublication.getTrackName()));
            }

            @Override
            public void onDataTrackSubscribed(RemoteParticipant remoteParticipant, RemoteDataTrackPublication remoteDataTrackPublication, RemoteDataTrack remoteDataTrack) {
                Log.i(TAG, String.format("onDataTrackSubscribed: " +
                                "[RemoteParticipant: identity=%s], " +
                                "[RemoteDataTrack: enabled=%b, name=%s]",
                        remoteParticipant.getIdentity(),
                        remoteDataTrack.isEnabled(),
                        remoteDataTrack.getName()));
            }

            @Override
            public void onDataTrackSubscriptionFailed(RemoteParticipant remoteParticipant, RemoteDataTrackPublication remoteDataTrackPublication, TwilioException twilioException) {
                Log.i(TAG, String.format("onDataTrackSubscriptionFailed: " +
                                "[RemoteParticipant: identity=%s], " +
                                "[RemoteDataTrackPublication: sid=%b, name=%s]" +
                                "[TwilioException: code=%d, message=%s]",
                        remoteParticipant.getIdentity(),
                        remoteDataTrackPublication.getTrackSid(),
                        remoteDataTrackPublication.getTrackName(),
                        twilioException.getCode(),
                        twilioException.getMessage()));
            }

            @Override
            public void onDataTrackUnsubscribed(RemoteParticipant remoteParticipant, RemoteDataTrackPublication remoteDataTrackPublication, RemoteDataTrack remoteDataTrack) {
                Log.i(TAG, String.format("onDataTrackUnsubscribed: " +
                                "[RemoteParticipant: identity=%s], " +
                                "[RemoteDataTrack: enabled=%b, name=%s]",
                        remoteParticipant.getIdentity(),
                        remoteDataTrack.isEnabled(),
                        remoteDataTrack.getName()));
            }

            @Override
            public void onAudioTrackEnabled(RemoteParticipant remoteParticipant, RemoteAudioTrackPublication remoteAudioTrackPublication) {

            }

            @Override
            public void onAudioTrackDisabled(RemoteParticipant remoteParticipant, RemoteAudioTrackPublication remoteAudioTrackPublication) {

            }

            @Override
            public void onVideoTrackEnabled(RemoteParticipant remoteParticipant, RemoteVideoTrackPublication remoteVideoTrackPublication) {

            }

            @Override
            public void onVideoTrackDisabled(RemoteParticipant remoteParticipant, RemoteVideoTrackPublication remoteVideoTrackPublication) {

            }
        };
    }

    private View.OnClickListener resizeClickListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "Resize button clicked!");
                showLoading();
                onWidgetResize();
                hideLoading();
            }
        };
    }

    private View.OnClickListener disconnectClickListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                /*
                 * Disconnect from room
                 */
                if (room != null) {
                    room.disconnect();
                }

                finish();
            }
        };
    }

    private View.OnClickListener switchCameraClickListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (cameraCapturer != null) {
                    showLoading();
                    CameraCapturer.CameraSource cameraSource = cameraCapturer.getCameraSource();
                    cameraCapturer.switchCamera();

                    if (thumbnailVideoView.getVisibility() == View.VISIBLE) {
                        thumbnailVideoView.setMirror(cameraSource == CameraCapturer.CameraSource.BACK_CAMERA);
                    } else {
                        primaryVideoView.setMirror(cameraSource == CameraCapturer.CameraSource.BACK_CAMERA);
                    }
                    
                    hideLoading();
                }
            }
        };
    }

    private View.OnClickListener switchAudioClickListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showLoading();

                if(audioManager.isSpeakerphoneOn()) {
                    audioManager.setSpeakerphoneOn(false);
                }else{
                    audioManager.setSpeakerphoneOn(true);

                }

                int icon = audioManager.isSpeakerphoneOn() ?
                        R.drawable.ic_phonelink_ring_white_24dp : R.drawable.ic_volume_headhphones_white_24dp;

                switchAudioActionFab.setImageDrawable(cordova.getContext().getResources().getDrawable(icon));
                hideLoading();
            }
        };
    }

    private View.OnClickListener localVideoClickListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                /*
                 * Enable/disable the local video track
                 */
                if (localVideoTrack != null) {
                    showLoading();
                    boolean enable = !localVideoTrack.isEnabled();
                    localVideoTrack.enable(enable);
                    int icon;
                    if (enable) {
                        icon = R.drawable.ic_videocam_green_24px;
                        switchCameraActionFab.show();
                    } else {
                        icon = R.drawable.ic_videocam_off_red_24px;
                        switchCameraActionFab.hide();
                    }

                    localVideoActionFab.setImageDrawable(cordova.getContext().getResources().getDrawable(icon));
                    hideLoading();
                }
            }
        };
    }

    private View.OnClickListener muteClickListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                /*
                 * Enable/disable the local audio track. The results of this operation are
                 * signaled to other Participants in the same Room. When an audio track is
                 * disabled, the audio is muted.
                 */
                if (localAudioTrack != null) {
                    boolean enable = !localAudioTrack.isEnabled();
                    localAudioTrack.enable(enable);
                    int icon = enable ? R.drawable.ic_mic_green_24px : R.drawable.ic_mic_off_red_24px;
                    muteActionFab.setImageDrawable(cordova.getContext().getResources().getDrawable(icon));
                }
            }
        };
    }

    private void onWidgetResize() {
        if (! isViewResizeAllowed) {
            return;
        }

        int icon;
        int height;
        int pvHeight;
        int pvWidth;
        int pvRad = Math.round(convertDpToPixel(MINIMIZED_PRIMARY_VIDEO_HEIGHT));
        int db12  = Math.round(convertDpToPixel(12));
        int db10  = Math.round(convertDpToPixel(10));

        FrameLayout.LayoutParams pvlp = (FrameLayout.LayoutParams) primaryVideoView.getLayoutParams();

        // thumbnailVideoView.setVisibility(isViewExpanded ? View.GONE: View.VISIBLE);

        if (isViewExpanded) {
            pvHeight       = pvRad;
            pvWidth        = pvRad;
            height         = Math.round(convertDpToPixel(MINIMIZED_WIDGET_HEIGHT));
            icon           = R.drawable.ic_expand_white_24px;

            pvlp.setMargins(0, db12, 0, 0);
            pvlp.setMarginEnd(db10);
        } else {
            pvHeight       = getDeviceDimen(false);
            pvWidth        = getDeviceDimen(true);
            height         = getDeviceDimen(false);
            icon           = R.drawable.ic_contract_white_24px;

            pvlp.setMargins(0, 0, 0, 0);
            pvlp.setMarginEnd(0);
        }

        linearLayout.setPadding(0, (isViewExpanded ? widgetHeightDiff : 0), 0, 0);
        resizeActionFab.setImageDrawable(cordova.getContext().getResources().getDrawable(icon));
        rootPluginWidget.getLayoutParams().height = height;
        rootPluginWidget.requestLayout();

        primaryVideoView.setLayoutParams(pvlp);
        primaryVideoView.getLayoutParams().height = pvHeight;
        primaryVideoView.getLayoutParams().width  = pvWidth;
        primaryVideoView.requestLayout();

        cordova.getActivity().overridePendingTransition(0, 0);

        isViewExpanded = !isViewExpanded;
    }

    private void showLoading() {
        progressBar.setVisibility(View.VISIBLE);
    }

    private void hideLoading() {
        progressBar.setVisibility(View.GONE);
    }
}
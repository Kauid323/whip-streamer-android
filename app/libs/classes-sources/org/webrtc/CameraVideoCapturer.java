package org.webrtc;

import android.media.MediaRecorder;

/* loaded from: classes.jar:org/webrtc/CameraVideoCapturer.class */
public interface CameraVideoCapturer extends VideoCapturer {

    /* loaded from: classes.jar:org/webrtc/CameraVideoCapturer$CameraEventsHandler.class */
    public interface CameraEventsHandler {
        void onCameraError(String str);

        void onCameraDisconnected();

        void onCameraFreezed(String str);

        void onCameraOpening(String str);

        void onFirstFrameAvailable();

        void onCameraClosed();
    }

    /* loaded from: classes.jar:org/webrtc/CameraVideoCapturer$CameraSwitchHandler.class */
    public interface CameraSwitchHandler {
        void onCameraSwitchDone(boolean z);

        void onCameraSwitchError(String str);
    }

    @Deprecated
    /* loaded from: classes.jar:org/webrtc/CameraVideoCapturer$MediaRecorderHandler.class */
    public interface MediaRecorderHandler {
        void onMediaRecorderSuccess();

        void onMediaRecorderError(String str);
    }

    void switchCamera(CameraSwitchHandler cameraSwitchHandler);

    void switchCamera(CameraSwitchHandler cameraSwitchHandler, String str);

    @Deprecated
    default void addMediaRecorderToCamera(MediaRecorder mediaRecorder, MediaRecorderHandler resultHandler) {
        throw new UnsupportedOperationException("Deprecated and not implemented.");
    }

    @Deprecated
    default void removeMediaRecorderFromCamera(MediaRecorderHandler resultHandler) {
        throw new UnsupportedOperationException("Deprecated and not implemented.");
    }

    /* loaded from: classes.jar:org/webrtc/CameraVideoCapturer$CameraStatistics.class */
    public static class CameraStatistics {
        private static final String TAG = "CameraStatistics";
        private static final int CAMERA_OBSERVER_PERIOD_MS = 2000;
        private static final int CAMERA_FREEZE_REPORT_TIMOUT_MS = 4000;
        private final SurfaceTextureHelper surfaceTextureHelper;
        private final CameraEventsHandler eventsHandler;
        private int frameCount;
        private int freezePeriodCount;
        private final Runnable cameraObserver = new Runnable() { // from class: org.webrtc.CameraVideoCapturer.CameraStatistics.1
            @Override // java.lang.Runnable
            public void run() {
                int cameraFps = Math.round((CameraStatistics.this.frameCount * 1000.0f) / 2000.0f);
                Logging.m1d(CameraStatistics.TAG, "Camera fps: " + cameraFps + ".");
                if (CameraStatistics.this.frameCount != 0) {
                    CameraStatistics.this.freezePeriodCount = 0;
                } else {
                    CameraStatistics.access$104(CameraStatistics.this);
                    if (CameraStatistics.CAMERA_OBSERVER_PERIOD_MS * CameraStatistics.this.freezePeriodCount >= CameraStatistics.CAMERA_FREEZE_REPORT_TIMOUT_MS && CameraStatistics.this.eventsHandler != null) {
                        Logging.m2e(CameraStatistics.TAG, "Camera freezed.");
                        if (CameraStatistics.this.surfaceTextureHelper.isTextureInUse()) {
                            CameraStatistics.this.eventsHandler.onCameraFreezed("Camera failure. Client must return video buffers.");
                            return;
                        } else {
                            CameraStatistics.this.eventsHandler.onCameraFreezed("Camera failure.");
                            return;
                        }
                    }
                }
                CameraStatistics.this.frameCount = 0;
                CameraStatistics.this.surfaceTextureHelper.getHandler().postDelayed(this, 2000L);
            }
        };

        static /* synthetic */ int access$104(CameraStatistics x0) {
            int i = x0.freezePeriodCount + 1;
            x0.freezePeriodCount = i;
            return i;
        }

        public CameraStatistics(SurfaceTextureHelper surfaceTextureHelper, CameraEventsHandler eventsHandler) {
            if (surfaceTextureHelper == null) {
                throw new IllegalArgumentException("SurfaceTextureHelper is null");
            }
            this.surfaceTextureHelper = surfaceTextureHelper;
            this.eventsHandler = eventsHandler;
            this.frameCount = 0;
            this.freezePeriodCount = 0;
            surfaceTextureHelper.getHandler().postDelayed(this.cameraObserver, 2000L);
        }

        private void checkThread() {
            if (Thread.currentThread() != this.surfaceTextureHelper.getHandler().getLooper().getThread()) {
                throw new IllegalStateException("Wrong thread");
            }
        }

        public void addFrame() {
            checkThread();
            this.frameCount++;
        }

        public void release() {
            this.surfaceTextureHelper.getHandler().removeCallbacks(this.cameraObserver);
        }
    }
}

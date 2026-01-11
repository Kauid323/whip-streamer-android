package org.webrtc;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import org.webrtc.CameraSession;
import org.webrtc.CameraVideoCapturer;

/* loaded from: classes.jar:org/webrtc/CameraCapturer.class */
abstract class CameraCapturer implements CameraVideoCapturer {
    private static final String TAG = "CameraCapturer";
    private static final int MAX_OPEN_CAMERA_ATTEMPTS = 3;
    private static final int OPEN_CAMERA_DELAY_MS = 500;
    private static final int OPEN_CAMERA_TIMEOUT = 10000;
    private final CameraEnumerator cameraEnumerator;
    private final CameraVideoCapturer.CameraEventsHandler eventsHandler;
    private final Handler uiThreadHandler;
    private Handler cameraThreadHandler;
    private Context applicationContext;
    private CapturerObserver capturerObserver;
    private SurfaceTextureHelper surfaceHelper;
    private boolean sessionOpening;

    @Nullable
    private CameraSession currentSession;
    private String cameraName;
    private String pendingCameraName;
    private int width;
    private int height;
    private int framerate;
    private int openAttemptsRemaining;

    @Nullable
    private CameraVideoCapturer.CameraSwitchHandler switchEventsHandler;

    @Nullable
    private CameraVideoCapturer.CameraStatistics cameraStatistics;
    private boolean firstFrameObserved;

    @Nullable
    private final CameraSession.CreateSessionCallback createSessionCallback = new CameraSession.CreateSessionCallback() { // from class: org.webrtc.CameraCapturer.1
        @Override // org.webrtc.CameraSession.CreateSessionCallback
        public void onDone(CameraSession session) {
            CameraCapturer.this.checkIsOnCameraThread();
            Logging.m1d(CameraCapturer.TAG, "Create session done. Switch state: " + CameraCapturer.this.switchState);
            CameraCapturer.this.uiThreadHandler.removeCallbacks(CameraCapturer.this.openCameraTimeoutRunnable);
            synchronized (CameraCapturer.this.stateLock) {
                CameraCapturer.this.capturerObserver.onCapturerStarted(true);
                CameraCapturer.this.sessionOpening = false;
                CameraCapturer.this.currentSession = session;
                CameraCapturer.this.cameraStatistics = new CameraVideoCapturer.CameraStatistics(CameraCapturer.this.surfaceHelper, CameraCapturer.this.eventsHandler);
                CameraCapturer.this.firstFrameObserved = false;
                CameraCapturer.this.stateLock.notifyAll();
                if (CameraCapturer.this.switchState != SwitchState.IN_PROGRESS) {
                    if (CameraCapturer.this.switchState == SwitchState.PENDING) {
                        String selectedCameraName = CameraCapturer.this.pendingCameraName;
                        CameraCapturer.this.pendingCameraName = null;
                        CameraCapturer.this.switchState = SwitchState.IDLE;
                        CameraCapturer.this.switchCameraInternal(CameraCapturer.this.switchEventsHandler, selectedCameraName);
                    }
                } else {
                    CameraCapturer.this.switchState = SwitchState.IDLE;
                    if (CameraCapturer.this.switchEventsHandler != null) {
                        CameraCapturer.this.switchEventsHandler.onCameraSwitchDone(CameraCapturer.this.cameraEnumerator.isFrontFacing(CameraCapturer.this.cameraName));
                        CameraCapturer.this.switchEventsHandler = null;
                    }
                }
            }
        }

        @Override // org.webrtc.CameraSession.CreateSessionCallback
        public void onFailure(CameraSession.FailureType failureType, String error) {
            CameraCapturer.this.checkIsOnCameraThread();
            CameraCapturer.this.uiThreadHandler.removeCallbacks(CameraCapturer.this.openCameraTimeoutRunnable);
            synchronized (CameraCapturer.this.stateLock) {
                CameraCapturer.this.capturerObserver.onCapturerStarted(false);
                CameraCapturer.access$1710(CameraCapturer.this);
                if (CameraCapturer.this.openAttemptsRemaining <= 0) {
                    Logging.m3w(CameraCapturer.TAG, "Opening camera failed, passing: " + error);
                    CameraCapturer.this.sessionOpening = false;
                    CameraCapturer.this.stateLock.notifyAll();
                    if (CameraCapturer.this.switchState != SwitchState.IDLE) {
                        if (CameraCapturer.this.switchEventsHandler != null) {
                            CameraCapturer.this.switchEventsHandler.onCameraSwitchError(error);
                            CameraCapturer.this.switchEventsHandler = null;
                        }
                        CameraCapturer.this.switchState = SwitchState.IDLE;
                    }
                    if (failureType == CameraSession.FailureType.DISCONNECTED) {
                        CameraCapturer.this.eventsHandler.onCameraDisconnected();
                    } else {
                        CameraCapturer.this.eventsHandler.onCameraError(error);
                    }
                } else {
                    Logging.m3w(CameraCapturer.TAG, "Opening camera failed, retry: " + error);
                    CameraCapturer.this.createSessionInternal(CameraCapturer.OPEN_CAMERA_DELAY_MS);
                }
            }
        }
    };

    @Nullable
    private final CameraSession.Events cameraSessionEventsHandler = new CameraSession.Events() { // from class: org.webrtc.CameraCapturer.2
        @Override // org.webrtc.CameraSession.Events
        public void onCameraOpening() {
            CameraCapturer.this.checkIsOnCameraThread();
            synchronized (CameraCapturer.this.stateLock) {
                if (CameraCapturer.this.currentSession == null) {
                    CameraCapturer.this.eventsHandler.onCameraOpening(CameraCapturer.this.cameraName);
                } else {
                    Logging.m3w(CameraCapturer.TAG, "onCameraOpening while session was open.");
                }
            }
        }

        @Override // org.webrtc.CameraSession.Events
        public void onCameraError(CameraSession session, String error) {
            CameraCapturer.this.checkIsOnCameraThread();
            synchronized (CameraCapturer.this.stateLock) {
                if (session == CameraCapturer.this.currentSession) {
                    CameraCapturer.this.eventsHandler.onCameraError(error);
                    CameraCapturer.this.stopCapture();
                } else {
                    Logging.m3w(CameraCapturer.TAG, "onCameraError from another session: " + error);
                }
            }
        }

        @Override // org.webrtc.CameraSession.Events
        public void onCameraDisconnected(CameraSession session) {
            CameraCapturer.this.checkIsOnCameraThread();
            synchronized (CameraCapturer.this.stateLock) {
                if (session == CameraCapturer.this.currentSession) {
                    CameraCapturer.this.eventsHandler.onCameraDisconnected();
                    CameraCapturer.this.stopCapture();
                } else {
                    Logging.m3w(CameraCapturer.TAG, "onCameraDisconnected from another session.");
                }
            }
        }

        @Override // org.webrtc.CameraSession.Events
        public void onCameraClosed(CameraSession session) {
            CameraCapturer.this.checkIsOnCameraThread();
            synchronized (CameraCapturer.this.stateLock) {
                if (session == CameraCapturer.this.currentSession || CameraCapturer.this.currentSession == null) {
                    CameraCapturer.this.eventsHandler.onCameraClosed();
                } else {
                    Logging.m1d(CameraCapturer.TAG, "onCameraClosed from another session.");
                }
            }
        }

        @Override // org.webrtc.CameraSession.Events
        public void onFrameCaptured(CameraSession session, VideoFrame frame) {
            CameraCapturer.this.checkIsOnCameraThread();
            synchronized (CameraCapturer.this.stateLock) {
                if (session == CameraCapturer.this.currentSession) {
                    if (!CameraCapturer.this.firstFrameObserved) {
                        CameraCapturer.this.eventsHandler.onFirstFrameAvailable();
                        CameraCapturer.this.firstFrameObserved = true;
                    }
                    CameraCapturer.this.cameraStatistics.addFrame();
                    CameraCapturer.this.capturerObserver.onFrameCaptured(frame);
                    return;
                }
                Logging.m3w(CameraCapturer.TAG, "onFrameCaptured from another session.");
            }
        }
    };
    private final Runnable openCameraTimeoutRunnable = new Runnable() { // from class: org.webrtc.CameraCapturer.3
        @Override // java.lang.Runnable
        public void run() {
            CameraCapturer.this.eventsHandler.onCameraError("Camera failed to start within timeout.");
        }
    };
    private final Object stateLock = new Object();
    private SwitchState switchState = SwitchState.IDLE;

    /* loaded from: classes.jar:org/webrtc/CameraCapturer$SwitchState.class */
    enum SwitchState {
        IDLE,
        PENDING,
        IN_PROGRESS
    }

    protected abstract void createCameraSession(CameraSession.CreateSessionCallback createSessionCallback, CameraSession.Events events, Context context, SurfaceTextureHelper surfaceTextureHelper, String str, int i, int i2, int i3);

    static /* synthetic */ int access$1710(CameraCapturer x0) {
        int i = x0.openAttemptsRemaining;
        x0.openAttemptsRemaining = i - 1;
        return i;
    }

    public CameraCapturer(String cameraName, @Nullable CameraVideoCapturer.CameraEventsHandler eventsHandler, CameraEnumerator cameraEnumerator) {
        this.eventsHandler = eventsHandler == null ? new CameraVideoCapturer.CameraEventsHandler() { // from class: org.webrtc.CameraCapturer.4
            @Override // org.webrtc.CameraVideoCapturer.CameraEventsHandler
            public void onCameraError(String errorDescription) {
            }

            @Override // org.webrtc.CameraVideoCapturer.CameraEventsHandler
            public void onCameraDisconnected() {
            }

            @Override // org.webrtc.CameraVideoCapturer.CameraEventsHandler
            public void onCameraFreezed(String errorDescription) {
            }

            @Override // org.webrtc.CameraVideoCapturer.CameraEventsHandler
            public void onCameraOpening(String cameraName2) {
            }

            @Override // org.webrtc.CameraVideoCapturer.CameraEventsHandler
            public void onFirstFrameAvailable() {
            }

            @Override // org.webrtc.CameraVideoCapturer.CameraEventsHandler
            public void onCameraClosed() {
            }
        } : eventsHandler;
        this.cameraEnumerator = cameraEnumerator;
        this.cameraName = cameraName;
        List<String> deviceNames = Arrays.asList(cameraEnumerator.getDeviceNames());
        this.uiThreadHandler = new Handler(Looper.getMainLooper());
        if (deviceNames.isEmpty()) {
            throw new RuntimeException("No cameras attached.");
        }
        if (!deviceNames.contains(this.cameraName)) {
            throw new IllegalArgumentException("Camera name " + this.cameraName + " does not match any known camera device.");
        }
    }

    @Override // org.webrtc.VideoCapturer
    public void initialize(SurfaceTextureHelper surfaceTextureHelper, Context applicationContext, CapturerObserver capturerObserver) {
        this.applicationContext = applicationContext;
        this.capturerObserver = capturerObserver;
        this.surfaceHelper = surfaceTextureHelper;
        this.cameraThreadHandler = surfaceTextureHelper.getHandler();
    }

    @Override // org.webrtc.VideoCapturer
    public void startCapture(int width, int height, int framerate) {
        Logging.m1d(TAG, "startCapture: " + width + "x" + height + "@" + framerate);
        if (this.applicationContext == null) {
            throw new RuntimeException("CameraCapturer must be initialized before calling startCapture.");
        }
        synchronized (this.stateLock) {
            if (this.sessionOpening || this.currentSession != null) {
                Logging.m3w(TAG, "Session already open");
                return;
            }
            this.width = width;
            this.height = height;
            this.framerate = framerate;
            this.sessionOpening = true;
            this.openAttemptsRemaining = 3;
            createSessionInternal(0);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void createSessionInternal(int delayMs) {
        this.uiThreadHandler.postDelayed(this.openCameraTimeoutRunnable, delayMs + OPEN_CAMERA_TIMEOUT);
        this.cameraThreadHandler.postDelayed(new Runnable() { // from class: org.webrtc.CameraCapturer.5
            @Override // java.lang.Runnable
            public void run() {
                CameraCapturer.this.createCameraSession(CameraCapturer.this.createSessionCallback, CameraCapturer.this.cameraSessionEventsHandler, CameraCapturer.this.applicationContext, CameraCapturer.this.surfaceHelper, CameraCapturer.this.cameraName, CameraCapturer.this.width, CameraCapturer.this.height, CameraCapturer.this.framerate);
            }
        }, delayMs);
    }

    @Override // org.webrtc.VideoCapturer
    public void stopCapture() {
        Logging.m1d(TAG, "Stop capture");
        synchronized (this.stateLock) {
            while (this.sessionOpening) {
                Logging.m1d(TAG, "Stop capture: Waiting for session to open");
                try {
                    this.stateLock.wait();
                } catch (InterruptedException e) {
                    Logging.m3w(TAG, "Stop capture interrupted while waiting for the session to open.");
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            if (this.currentSession != null) {
                Logging.m1d(TAG, "Stop capture: Nulling session");
                this.cameraStatistics.release();
                this.cameraStatistics = null;
                final CameraSession oldSession = this.currentSession;
                this.cameraThreadHandler.post(new Runnable() { // from class: org.webrtc.CameraCapturer.6
                    @Override // java.lang.Runnable
                    public void run() {
                        oldSession.stop();
                    }
                });
                this.currentSession = null;
                this.capturerObserver.onCapturerStopped();
            } else {
                Logging.m1d(TAG, "Stop capture: No session open");
            }
        }
        Logging.m1d(TAG, "Stop capture done");
    }

    @Override // org.webrtc.VideoCapturer
    public void changeCaptureFormat(int width, int height, int framerate) {
        Logging.m1d(TAG, "changeCaptureFormat: " + width + "x" + height + "@" + framerate);
        synchronized (this.stateLock) {
            stopCapture();
            startCapture(width, height, framerate);
        }
    }

    @Override // org.webrtc.VideoCapturer
    public void dispose() {
        Logging.m1d(TAG, "dispose");
        stopCapture();
    }

    @Override // org.webrtc.CameraVideoCapturer
    public void switchCamera(final CameraVideoCapturer.CameraSwitchHandler switchEventsHandler) {
        Logging.m1d(TAG, "switchCamera");
        this.cameraThreadHandler.post(new Runnable() { // from class: org.webrtc.CameraCapturer.7
            @Override // java.lang.Runnable
            public void run() {
                List<String> deviceNames = Arrays.asList(CameraCapturer.this.cameraEnumerator.getDeviceNames());
                if (deviceNames.size() < 2) {
                    CameraCapturer.this.reportCameraSwitchError("No camera to switch to.", switchEventsHandler);
                    return;
                }
                int cameraNameIndex = deviceNames.indexOf(CameraCapturer.this.cameraName);
                String cameraName = deviceNames.get((cameraNameIndex + 1) % deviceNames.size());
                CameraCapturer.this.switchCameraInternal(switchEventsHandler, cameraName);
            }
        });
    }

    @Override // org.webrtc.CameraVideoCapturer
    public void switchCamera(final CameraVideoCapturer.CameraSwitchHandler switchEventsHandler, final String cameraName) {
        Logging.m1d(TAG, "switchCamera");
        this.cameraThreadHandler.post(new Runnable() { // from class: org.webrtc.CameraCapturer.8
            @Override // java.lang.Runnable
            public void run() {
                CameraCapturer.this.switchCameraInternal(switchEventsHandler, cameraName);
            }
        });
    }

    @Override // org.webrtc.VideoCapturer
    public boolean isScreencast() {
        return false;
    }

    public void printStackTrace() {
        Thread cameraThread = null;
        if (this.cameraThreadHandler != null) {
            cameraThread = this.cameraThreadHandler.getLooper().getThread();
        }
        if (cameraThread != null) {
            StackTraceElement[] cameraStackTrace = cameraThread.getStackTrace();
            if (cameraStackTrace.length > 0) {
                Logging.m1d(TAG, "CameraCapturer stack trace:");
                for (StackTraceElement traceElem : cameraStackTrace) {
                    Logging.m1d(TAG, traceElem.toString());
                }
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void reportCameraSwitchError(String error, @Nullable CameraVideoCapturer.CameraSwitchHandler switchEventsHandler) {
        Logging.m2e(TAG, error);
        if (switchEventsHandler != null) {
            switchEventsHandler.onCameraSwitchError(error);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void switchCameraInternal(@Nullable CameraVideoCapturer.CameraSwitchHandler switchEventsHandler, String selectedCameraName) {
        Logging.m1d(TAG, "switchCamera internal");
        List<String> deviceNames = Arrays.asList(this.cameraEnumerator.getDeviceNames());
        if (!deviceNames.contains(selectedCameraName)) {
            reportCameraSwitchError("Attempted to switch to unknown camera device " + selectedCameraName, switchEventsHandler);
            return;
        }
        synchronized (this.stateLock) {
            if (this.switchState != SwitchState.IDLE) {
                reportCameraSwitchError("Camera switch already in progress.", switchEventsHandler);
                return;
            }
            if (!this.sessionOpening && this.currentSession == null) {
                reportCameraSwitchError("switchCamera: camera is not running.", switchEventsHandler);
                return;
            }
            this.switchEventsHandler = switchEventsHandler;
            if (this.sessionOpening) {
                this.switchState = SwitchState.PENDING;
                this.pendingCameraName = selectedCameraName;
                return;
            }
            this.switchState = SwitchState.IN_PROGRESS;
            Logging.m1d(TAG, "switchCamera: Stopping session");
            this.cameraStatistics.release();
            this.cameraStatistics = null;
            final CameraSession oldSession = this.currentSession;
            this.cameraThreadHandler.post(new Runnable() { // from class: org.webrtc.CameraCapturer.9
                @Override // java.lang.Runnable
                public void run() {
                    oldSession.stop();
                }
            });
            this.currentSession = null;
            this.cameraName = selectedCameraName;
            this.sessionOpening = true;
            this.openAttemptsRemaining = 1;
            createSessionInternal(0);
            Logging.m1d(TAG, "switchCamera done");
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void checkIsOnCameraThread() {
        if (Thread.currentThread() != this.cameraThreadHandler.getLooper().getThread()) {
            Logging.m2e(TAG, "Check is on camera thread failed.");
            throw new RuntimeException("Not on camera thread.");
        }
    }

    protected String getCameraName() {
        String str;
        synchronized (this.stateLock) {
            str = this.cameraName;
        }
        return str;
    }
}

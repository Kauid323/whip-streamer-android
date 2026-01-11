package org.webrtc;

import android.content.Context;
import android.os.Process;
import android.support.annotation.Nullable;
import java.util.List;
import org.webrtc.Logging;
import org.webrtc.NativeLibrary;
import org.webrtc.PeerConnection;
import org.webrtc.audio.AudioDeviceModule;
import org.webrtc.audio.JavaAudioDeviceModule;

/* loaded from: classes.jar:org/webrtc/PeerConnectionFactory.class */
public class PeerConnectionFactory {
    public static final String TRIAL_ENABLED = "Enabled";

    @Deprecated
    public static final String VIDEO_FRAME_EMIT_TRIAL = "VideoFrameEmit";
    private static final String TAG = "PeerConnectionFactory";
    private static final String VIDEO_CAPTURER_THREAD_NAME = "VideoCapturerThread";
    private static volatile boolean internalTracerInitialized;

    @Nullable
    private static ThreadInfo staticNetworkThread;

    @Nullable
    private static ThreadInfo staticWorkerThread;

    @Nullable
    private static ThreadInfo staticSignalingThread;
    private long nativeFactory;

    @Nullable
    private volatile ThreadInfo networkThread;

    @Nullable
    private volatile ThreadInfo workerThread;

    @Nullable
    private volatile ThreadInfo signalingThread;

    private static native void nativeInitializeAndroidGlobals();

    private static native void nativeInitializeFieldTrials(String str);

    private static native String nativeFindFieldTrialsFullName(String str);

    private static native void nativeInitializeInternalTracer();

    private static native void nativeShutdownInternalTracer();

    private static native boolean nativeStartInternalTracingCapture(String str);

    private static native void nativeStopInternalTracingCapture();

    /* JADX INFO: Access modifiers changed from: private */
    public static native PeerConnectionFactory nativeCreatePeerConnectionFactory(Context context, Options options, long j, long j2, long j3, VideoEncoderFactory videoEncoderFactory, VideoDecoderFactory videoDecoderFactory, long j4, long j5, long j6, long j7, long j8);

    private static native long nativeCreatePeerConnection(long j, PeerConnection.RTCConfiguration rTCConfiguration, MediaConstraints mediaConstraints, long j2, SSLCertificateVerifier sSLCertificateVerifier);

    private static native long nativeCreateLocalMediaStream(long j, String str);

    private static native long nativeCreateVideoSource(long j, boolean z, boolean z2);

    private static native long nativeCreateVideoTrack(long j, String str, long j2);

    private static native long nativeCreateAudioSource(long j, MediaConstraints mediaConstraints);

    private static native long nativeCreateAudioTrack(long j, String str, long j2);

    private static native boolean nativeStartAecDump(long j, int i, int i2);

    private static native void nativeStopAecDump(long j);

    private static native void nativeFreeFactory(long j);

    private static native long nativeGetNativePeerConnectionFactory(long j);

    private static native void nativeInjectLoggable(JNILogging jNILogging, int i);

    private static native void nativeDeleteLoggable();

    private static native void nativePrintStackTrace(int i);

    private static native void nativePrintStackTracesOfRegisteredThreads();

    /* loaded from: classes.jar:org/webrtc/PeerConnectionFactory$ThreadInfo.class */
    private static class ThreadInfo {
        final Thread thread;
        final int tid;

        public static ThreadInfo getCurrent() {
            return new ThreadInfo(Thread.currentThread(), Process.myTid());
        }

        private ThreadInfo(Thread thread, int tid) {
            this.thread = thread;
            this.tid = tid;
        }
    }

    /* loaded from: classes.jar:org/webrtc/PeerConnectionFactory$InitializationOptions.class */
    public static class InitializationOptions {
        final Context applicationContext;
        final String fieldTrials;
        final boolean enableInternalTracer;
        final NativeLibraryLoader nativeLibraryLoader;
        final String nativeLibraryName;

        @Nullable
        Loggable loggable;

        @Nullable
        Logging.Severity loggableSeverity;

        private InitializationOptions(Context applicationContext, String fieldTrials, boolean enableInternalTracer, NativeLibraryLoader nativeLibraryLoader, String nativeLibraryName, @Nullable Loggable loggable, @Nullable Logging.Severity loggableSeverity) {
            this.applicationContext = applicationContext;
            this.fieldTrials = fieldTrials;
            this.enableInternalTracer = enableInternalTracer;
            this.nativeLibraryLoader = nativeLibraryLoader;
            this.nativeLibraryName = nativeLibraryName;
            this.loggable = loggable;
            this.loggableSeverity = loggableSeverity;
        }

        public static Builder builder(Context applicationContext) {
            return new Builder(applicationContext);
        }

        /* loaded from: classes.jar:org/webrtc/PeerConnectionFactory$InitializationOptions$Builder.class */
        public static class Builder {
            private final Context applicationContext;
            private boolean enableInternalTracer;

            @Nullable
            private Loggable loggable;

            @Nullable
            private Logging.Severity loggableSeverity;
            private String fieldTrials = "";
            private NativeLibraryLoader nativeLibraryLoader = new NativeLibrary.DefaultLoader();
            private String nativeLibraryName = "jingle_peerconnection_so";

            Builder(Context applicationContext) {
                this.applicationContext = applicationContext;
            }

            public Builder setFieldTrials(String fieldTrials) {
                this.fieldTrials = fieldTrials;
                return this;
            }

            public Builder setEnableInternalTracer(boolean enableInternalTracer) {
                this.enableInternalTracer = enableInternalTracer;
                return this;
            }

            public Builder setNativeLibraryLoader(NativeLibraryLoader nativeLibraryLoader) {
                this.nativeLibraryLoader = nativeLibraryLoader;
                return this;
            }

            public Builder setNativeLibraryName(String nativeLibraryName) {
                this.nativeLibraryName = nativeLibraryName;
                return this;
            }

            public Builder setInjectableLogger(Loggable loggable, Logging.Severity severity) {
                this.loggable = loggable;
                this.loggableSeverity = severity;
                return this;
            }

            public InitializationOptions createInitializationOptions() {
                return new InitializationOptions(this.applicationContext, this.fieldTrials, this.enableInternalTracer, this.nativeLibraryLoader, this.nativeLibraryName, this.loggable, this.loggableSeverity);
            }
        }
    }

    /* loaded from: classes.jar:org/webrtc/PeerConnectionFactory$Options.class */
    public static class Options {
        static final int ADAPTER_TYPE_UNKNOWN = 0;
        static final int ADAPTER_TYPE_ETHERNET = 1;
        static final int ADAPTER_TYPE_WIFI = 2;
        static final int ADAPTER_TYPE_CELLULAR = 4;
        static final int ADAPTER_TYPE_VPN = 8;
        static final int ADAPTER_TYPE_LOOPBACK = 16;
        static final int ADAPTER_TYPE_ANY = 32;
        public int networkIgnoreMask;
        public boolean disableEncryption;
        public boolean disableNetworkMonitor;

        @CalledByNative("Options")
        int getNetworkIgnoreMask() {
            return this.networkIgnoreMask;
        }

        @CalledByNative("Options")
        boolean getDisableEncryption() {
            return this.disableEncryption;
        }

        @CalledByNative("Options")
        boolean getDisableNetworkMonitor() {
            return this.disableNetworkMonitor;
        }
    }

    /* loaded from: classes.jar:org/webrtc/PeerConnectionFactory$Builder.class */
    public static class Builder {

        @Nullable
        private Options options;

        @Nullable
        private AudioDeviceModule audioDeviceModule;
        private AudioEncoderFactoryFactory audioEncoderFactoryFactory;
        private AudioDecoderFactoryFactory audioDecoderFactoryFactory;

        @Nullable
        private VideoEncoderFactory videoEncoderFactory;

        @Nullable
        private VideoDecoderFactory videoDecoderFactory;

        @Nullable
        private AudioProcessingFactory audioProcessingFactory;

        @Nullable
        private FecControllerFactoryFactoryInterface fecControllerFactoryFactory;

        @Nullable
        private NetworkControllerFactoryFactory networkControllerFactoryFactory;

        @Nullable
        private NetworkStatePredictorFactoryFactory networkStatePredictorFactoryFactory;

        @Nullable
        private NetEqFactoryFactory neteqFactoryFactory;

        private Builder() {
            this.audioEncoderFactoryFactory = new BuiltinAudioEncoderFactoryFactory();
            this.audioDecoderFactoryFactory = new BuiltinAudioDecoderFactoryFactory();
        }

        public Builder setOptions(Options options) {
            this.options = options;
            return this;
        }

        public Builder setAudioDeviceModule(AudioDeviceModule audioDeviceModule) {
            this.audioDeviceModule = audioDeviceModule;
            return this;
        }

        public Builder setAudioEncoderFactoryFactory(AudioEncoderFactoryFactory audioEncoderFactoryFactory) {
            if (audioEncoderFactoryFactory == null) {
                throw new IllegalArgumentException("PeerConnectionFactory.Builder does not accept a null AudioEncoderFactoryFactory.");
            }
            this.audioEncoderFactoryFactory = audioEncoderFactoryFactory;
            return this;
        }

        public Builder setAudioDecoderFactoryFactory(AudioDecoderFactoryFactory audioDecoderFactoryFactory) {
            if (audioDecoderFactoryFactory == null) {
                throw new IllegalArgumentException("PeerConnectionFactory.Builder does not accept a null AudioDecoderFactoryFactory.");
            }
            this.audioDecoderFactoryFactory = audioDecoderFactoryFactory;
            return this;
        }

        public Builder setVideoEncoderFactory(VideoEncoderFactory videoEncoderFactory) {
            this.videoEncoderFactory = videoEncoderFactory;
            return this;
        }

        public Builder setVideoDecoderFactory(VideoDecoderFactory videoDecoderFactory) {
            this.videoDecoderFactory = videoDecoderFactory;
            return this;
        }

        public Builder setAudioProcessingFactory(AudioProcessingFactory audioProcessingFactory) {
            if (audioProcessingFactory == null) {
                throw new NullPointerException("PeerConnectionFactory builder does not accept a null AudioProcessingFactory.");
            }
            this.audioProcessingFactory = audioProcessingFactory;
            return this;
        }

        public Builder setFecControllerFactoryFactoryInterface(FecControllerFactoryFactoryInterface fecControllerFactoryFactory) {
            this.fecControllerFactoryFactory = fecControllerFactoryFactory;
            return this;
        }

        public Builder setNetworkControllerFactoryFactory(NetworkControllerFactoryFactory networkControllerFactoryFactory) {
            this.networkControllerFactoryFactory = networkControllerFactoryFactory;
            return this;
        }

        public Builder setNetworkStatePredictorFactoryFactory(NetworkStatePredictorFactoryFactory networkStatePredictorFactoryFactory) {
            this.networkStatePredictorFactoryFactory = networkStatePredictorFactoryFactory;
            return this;
        }

        public Builder setNetEqFactoryFactory(NetEqFactoryFactory neteqFactoryFactory) {
            this.neteqFactoryFactory = neteqFactoryFactory;
            return this;
        }

        public PeerConnectionFactory createPeerConnectionFactory() {
            long jCreateNativeNetworkControllerFactory;
            long jCreateNativeNetworkStatePredictorFactory;
            PeerConnectionFactory.checkInitializeHasBeenCalled();
            if (this.audioDeviceModule == null) {
                this.audioDeviceModule = JavaAudioDeviceModule.builder(ContextUtils.getApplicationContext()).createAudioDeviceModule();
            }
            Context applicationContext = ContextUtils.getApplicationContext();
            Options options = this.options;
            long nativeAudioDeviceModulePointer = this.audioDeviceModule.getNativeAudioDeviceModulePointer();
            long jCreateNativeAudioEncoderFactory = this.audioEncoderFactoryFactory.createNativeAudioEncoderFactory();
            long jCreateNativeAudioDecoderFactory = this.audioDecoderFactoryFactory.createNativeAudioDecoderFactory();
            VideoEncoderFactory videoEncoderFactory = this.videoEncoderFactory;
            VideoDecoderFactory videoDecoderFactory = this.videoDecoderFactory;
            long jCreateNative = this.audioProcessingFactory == null ? 0L : this.audioProcessingFactory.createNative();
            long jCreateNative2 = this.fecControllerFactoryFactory == null ? 0L : this.fecControllerFactoryFactory.createNative();
            if (this.networkControllerFactoryFactory == null) {
                jCreateNativeNetworkControllerFactory = 0;
            } else {
                jCreateNativeNetworkControllerFactory = this.networkControllerFactoryFactory.createNativeNetworkControllerFactory();
            }
            if (this.networkStatePredictorFactoryFactory == null) {
                jCreateNativeNetworkStatePredictorFactory = 0;
            } else {
                jCreateNativeNetworkStatePredictorFactory = this.networkStatePredictorFactoryFactory.createNativeNetworkStatePredictorFactory();
            }
            return PeerConnectionFactory.nativeCreatePeerConnectionFactory(applicationContext, options, nativeAudioDeviceModulePointer, jCreateNativeAudioEncoderFactory, jCreateNativeAudioDecoderFactory, videoEncoderFactory, videoDecoderFactory, jCreateNative, jCreateNative2, jCreateNativeNetworkControllerFactory, jCreateNativeNetworkStatePredictorFactory, this.neteqFactoryFactory == null ? 0L : this.neteqFactoryFactory.createNativeNetEqFactory());
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static void initialize(InitializationOptions options) {
        ContextUtils.initialize(options.applicationContext);
        NativeLibrary.initialize(options.nativeLibraryLoader, options.nativeLibraryName);
        nativeInitializeAndroidGlobals();
        nativeInitializeFieldTrials(options.fieldTrials);
        if (options.enableInternalTracer && !internalTracerInitialized) {
            initializeInternalTracer();
        }
        if (options.loggable != null) {
            Logging.injectLoggable(options.loggable, options.loggableSeverity);
            nativeInjectLoggable(new JNILogging(options.loggable), options.loggableSeverity.ordinal());
        } else {
            Logging.m1d(TAG, "PeerConnectionFactory was initialized without an injected Loggable. Any existing Loggable will be deleted.");
            Logging.deleteInjectedLoggable();
            nativeDeleteLoggable();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void checkInitializeHasBeenCalled() {
        if (!NativeLibrary.isLoaded() || ContextUtils.getApplicationContext() == null) {
            throw new IllegalStateException("PeerConnectionFactory.initialize was not called before creating a PeerConnectionFactory.");
        }
    }

    private static void initializeInternalTracer() {
        internalTracerInitialized = true;
        nativeInitializeInternalTracer();
    }

    public static void shutdownInternalTracer() {
        internalTracerInitialized = false;
        nativeShutdownInternalTracer();
    }

    @Deprecated
    public static void initializeFieldTrials(String fieldTrialsInitString) {
        nativeInitializeFieldTrials(fieldTrialsInitString);
    }

    public static String fieldTrialsFindFullName(String name) {
        return NativeLibrary.isLoaded() ? nativeFindFieldTrialsFullName(name) : "";
    }

    public static boolean startInternalTracingCapture(String tracingFilename) {
        return nativeStartInternalTracingCapture(tracingFilename);
    }

    public static void stopInternalTracingCapture() {
        nativeStopInternalTracingCapture();
    }

    @CalledByNative
    PeerConnectionFactory(long nativeFactory) {
        checkInitializeHasBeenCalled();
        if (nativeFactory == 0) {
            throw new RuntimeException("Failed to initialize PeerConnectionFactory!");
        }
        this.nativeFactory = nativeFactory;
    }

    @Nullable
    PeerConnection createPeerConnectionInternal(PeerConnection.RTCConfiguration rtcConfig, MediaConstraints constraints, PeerConnection.Observer observer, SSLCertificateVerifier sslCertificateVerifier) {
        checkPeerConnectionFactoryExists();
        long nativeObserver = PeerConnection.createNativePeerConnectionObserver(observer);
        if (nativeObserver == 0) {
            return null;
        }
        long nativePeerConnection = nativeCreatePeerConnection(this.nativeFactory, rtcConfig, constraints, nativeObserver, sslCertificateVerifier);
        if (nativePeerConnection == 0) {
            return null;
        }
        return new PeerConnection(nativePeerConnection);
    }

    @Deprecated
    @Nullable
    public PeerConnection createPeerConnection(PeerConnection.RTCConfiguration rtcConfig, MediaConstraints constraints, PeerConnection.Observer observer) {
        return createPeerConnectionInternal(rtcConfig, constraints, observer, null);
    }

    @Deprecated
    @Nullable
    public PeerConnection createPeerConnection(List<PeerConnection.IceServer> iceServers, MediaConstraints constraints, PeerConnection.Observer observer) {
        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);
        return createPeerConnection(rtcConfig, constraints, observer);
    }

    @Nullable
    public PeerConnection createPeerConnection(List<PeerConnection.IceServer> iceServers, PeerConnection.Observer observer) {
        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);
        return createPeerConnection(rtcConfig, observer);
    }

    @Nullable
    public PeerConnection createPeerConnection(PeerConnection.RTCConfiguration rtcConfig, PeerConnection.Observer observer) {
        return createPeerConnection(rtcConfig, (MediaConstraints) null, observer);
    }

    @Nullable
    public PeerConnection createPeerConnection(PeerConnection.RTCConfiguration rtcConfig, PeerConnectionDependencies dependencies) {
        return createPeerConnectionInternal(rtcConfig, null, dependencies.getObserver(), dependencies.getSSLCertificateVerifier());
    }

    public MediaStream createLocalMediaStream(String label) {
        checkPeerConnectionFactoryExists();
        return new MediaStream(nativeCreateLocalMediaStream(this.nativeFactory, label));
    }

    public VideoSource createVideoSource(boolean isScreencast, boolean alignTimestamps) {
        checkPeerConnectionFactoryExists();
        return new VideoSource(nativeCreateVideoSource(this.nativeFactory, isScreencast, alignTimestamps));
    }

    public VideoSource createVideoSource(boolean isScreencast) {
        return createVideoSource(isScreencast, true);
    }

    public VideoTrack createVideoTrack(String id, VideoSource source) {
        checkPeerConnectionFactoryExists();
        return new VideoTrack(nativeCreateVideoTrack(this.nativeFactory, id, source.getNativeVideoTrackSource()));
    }

    public AudioSource createAudioSource(MediaConstraints constraints) {
        checkPeerConnectionFactoryExists();
        return new AudioSource(nativeCreateAudioSource(this.nativeFactory, constraints));
    }

    public AudioTrack createAudioTrack(String id, AudioSource source) {
        checkPeerConnectionFactoryExists();
        return new AudioTrack(nativeCreateAudioTrack(this.nativeFactory, id, source.getNativeAudioSource()));
    }

    public boolean startAecDump(int file_descriptor, int filesize_limit_bytes) {
        checkPeerConnectionFactoryExists();
        return nativeStartAecDump(this.nativeFactory, file_descriptor, filesize_limit_bytes);
    }

    public void stopAecDump() {
        checkPeerConnectionFactoryExists();
        nativeStopAecDump(this.nativeFactory);
    }

    public void dispose() {
        checkPeerConnectionFactoryExists();
        nativeFreeFactory(this.nativeFactory);
        this.networkThread = null;
        this.workerThread = null;
        this.signalingThread = null;
        this.nativeFactory = 0L;
    }

    public long getNativePeerConnectionFactory() {
        checkPeerConnectionFactoryExists();
        return nativeGetNativePeerConnectionFactory(this.nativeFactory);
    }

    public long getNativeOwnedFactoryAndThreads() {
        checkPeerConnectionFactoryExists();
        return this.nativeFactory;
    }

    private void checkPeerConnectionFactoryExists() {
        if (this.nativeFactory == 0) {
            throw new IllegalStateException("PeerConnectionFactory has been disposed.");
        }
    }

    private static void printStackTrace(@Nullable ThreadInfo threadInfo, boolean printNativeStackTrace) {
        if (threadInfo == null) {
            return;
        }
        String threadName = threadInfo.thread.getName();
        StackTraceElement[] stackTraces = threadInfo.thread.getStackTrace();
        if (stackTraces.length > 0) {
            Logging.m3w(TAG, threadName + " stacktrace:");
            for (StackTraceElement stackTrace : stackTraces) {
                Logging.m3w(TAG, stackTrace.toString());
            }
        }
        if (printNativeStackTrace) {
            Logging.m3w(TAG, "*** *** *** *** *** *** *** *** *** *** *** *** *** *** *** ***");
            Logging.m3w(TAG, "pid: " + Process.myPid() + ", tid: " + threadInfo.tid + ", name: " + threadName + "  >>> WebRTC <<<");
            nativePrintStackTrace(threadInfo.tid);
        }
    }

    @Deprecated
    public static void printStackTraces() {
        printStackTrace(staticNetworkThread, false);
        printStackTrace(staticWorkerThread, false);
        printStackTrace(staticSignalingThread, false);
    }

    public void printInternalStackTraces(boolean printNativeStackTraces) {
        printStackTrace(this.signalingThread, printNativeStackTraces);
        printStackTrace(this.workerThread, printNativeStackTraces);
        printStackTrace(this.networkThread, printNativeStackTraces);
        if (printNativeStackTraces) {
            nativePrintStackTracesOfRegisteredThreads();
        }
    }

    @CalledByNative
    private void onNetworkThreadReady() {
        this.networkThread = ThreadInfo.getCurrent();
        staticNetworkThread = this.networkThread;
        Logging.m1d(TAG, "onNetworkThreadReady");
    }

    @CalledByNative
    private void onWorkerThreadReady() {
        this.workerThread = ThreadInfo.getCurrent();
        staticWorkerThread = this.workerThread;
        Logging.m1d(TAG, "onWorkerThreadReady");
    }

    @CalledByNative
    private void onSignalingThreadReady() {
        this.signalingThread = ThreadInfo.getCurrent();
        staticSignalingThread = this.signalingThread;
        Logging.m1d(TAG, "onSignalingThreadReady");
    }
}

package org.webrtc.audio;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioRecordingConfiguration;
import android.os.Build;
import android.os.Process;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.webrtc.CalledByNative;
import org.webrtc.Logging;
import org.webrtc.ThreadUtils;
import org.webrtc.VideoCodecType;
import org.webrtc.audio.JavaAudioDeviceModule;

/* loaded from: classes.jar:org/webrtc/audio/WebRtcAudioRecord.class */
class WebRtcAudioRecord {
    private static final String TAG = "WebRtcAudioRecordExternal";
    private static final int CALLBACK_BUFFER_SIZE_MS = 10;
    private static final int BUFFERS_PER_SECOND = 100;
    private static final int BUFFER_SIZE_FACTOR = 2;
    private static final long AUDIO_RECORD_THREAD_JOIN_TIMEOUT_MS = 2000;
    public static final int DEFAULT_AUDIO_SOURCE = 7;
    public static final int DEFAULT_AUDIO_FORMAT = 2;
    private static final int AUDIO_RECORD_START = 0;
    private static final int AUDIO_RECORD_STOP = 1;
    private static final int CHECK_REC_STATUS_DELAY_MS = 100;
    private final Context context;
    private final AudioManager audioManager;
    private final int audioSource;
    private final int audioFormat;
    private final JavaAudioDeviceModule.AudioRecordFactory audioRecordFactory;
    private long nativeAudioRecord;
    private final WebRtcAudioEffects effects;

    private ByteBuffer byteBuffer;

    private AudioRecord audioRecord;

    private AudioRecordThread audioThread;

    private AudioDeviceInfo preferredDevice;

    private ScheduledExecutorService executor;

    private ScheduledFuture<String> future;
    private volatile boolean microphoneMute;
    private boolean audioSourceMatchesRecordingSession;
    private boolean isAudioConfigVerified;
    private byte[] emptyBytes;

    private final JavaAudioDeviceModule.AudioRecordErrorCallback errorCallback;

    private final JavaAudioDeviceModule.AudioRecordStateCallback stateCallback;

    private final JavaAudioDeviceModule.SamplesReadyCallback audioSamplesReadyCallback;
    private final boolean isAcousticEchoCancelerSupported;
    private final boolean isNoiseSuppressorSupported;

    private native void nativeCacheDirectBufferAddress(long j, ByteBuffer byteBuffer);

    /* JADX INFO: Access modifiers changed from: private */
    public native void nativeDataIsRecorded(long j, int i);

    /* loaded from: classes.jar:org/webrtc/audio/WebRtcAudioRecord$AudioRecordThread.class */
    private class AudioRecordThread extends Thread {
        private volatile boolean keepAlive;

        public AudioRecordThread(String name) {
            super(name);
            this.keepAlive = true;
        }

        @Override // java.lang.Thread, java.lang.Runnable
        public void run() throws IllegalStateException, SecurityException, IllegalArgumentException {
            Process.setThreadPriority(-19);
            Logging.d(WebRtcAudioRecord.TAG, "AudioRecordThread" + WebRtcAudioUtils.getThreadInfo());
            WebRtcAudioRecord.assertTrue(WebRtcAudioRecord.this.audioRecord.getRecordingState() == 3);
            WebRtcAudioRecord.this.doAudioRecordStateCallback(0);
            System.nanoTime();
            while (this.keepAlive) {
                int bytesRead = WebRtcAudioRecord.this.audioRecord.read(WebRtcAudioRecord.this.byteBuffer, WebRtcAudioRecord.this.byteBuffer.capacity());
                if (bytesRead == WebRtcAudioRecord.this.byteBuffer.capacity()) {
                    if (WebRtcAudioRecord.this.microphoneMute) {
                        WebRtcAudioRecord.this.byteBuffer.clear();
                        WebRtcAudioRecord.this.byteBuffer.put(WebRtcAudioRecord.this.emptyBytes);
                    }
                    if (this.keepAlive) {
                        WebRtcAudioRecord.this.nativeDataIsRecorded(WebRtcAudioRecord.this.nativeAudioRecord, bytesRead);
                    }
                    if (WebRtcAudioRecord.this.audioSamplesReadyCallback != null) {
                        byte[] data = Arrays.copyOfRange(WebRtcAudioRecord.this.byteBuffer.array(), WebRtcAudioRecord.this.byteBuffer.arrayOffset(), WebRtcAudioRecord.this.byteBuffer.capacity() + WebRtcAudioRecord.this.byteBuffer.arrayOffset());
                        WebRtcAudioRecord.this.audioSamplesReadyCallback.onWebRtcAudioRecordSamplesReady(new JavaAudioDeviceModule.AudioSamples(WebRtcAudioRecord.this.audioRecord.getAudioFormat(), WebRtcAudioRecord.this.audioRecord.getChannelCount(), WebRtcAudioRecord.this.audioRecord.getSampleRate(), data));
                    }
                } else {
                    String errorMessage = "AudioRecord.read failed: " + bytesRead;
                    Logging.e(WebRtcAudioRecord.TAG, errorMessage);
                    if (bytesRead == -3) {
                        this.keepAlive = false;
                        WebRtcAudioRecord.this.reportWebRtcAudioRecordError(errorMessage);
                    }
                }
            }
            try {
                if (WebRtcAudioRecord.this.audioRecord != null) {
                    WebRtcAudioRecord.this.audioRecord.stop();
                    WebRtcAudioRecord.this.doAudioRecordStateCallback(1);
                }
            } catch (IllegalStateException e) {
                Logging.e(WebRtcAudioRecord.TAG, "AudioRecord.stop failed: " + e.getMessage());
            }
        }

        public void stopThread() {
            Logging.d(WebRtcAudioRecord.TAG, "stopThread");
            this.keepAlive = false;
        }
    }

    @CalledByNative
    WebRtcAudioRecord(Context context, AudioManager audioManager) {
        this(context, audioManager, 7, 2, null, null, null, null, WebRtcAudioEffects.isAcousticEchoCancelerSupported(), WebRtcAudioEffects.isNoiseSuppressorSupported());
    }

    public WebRtcAudioRecord(Context context, AudioManager audioManager, int audioSource, int audioFormat, JavaAudioDeviceModule.AudioRecordFactory audioRecordFactory, JavaAudioDeviceModule.AudioRecordErrorCallback errorCallback, JavaAudioDeviceModule.AudioRecordStateCallback stateCallback, JavaAudioDeviceModule.SamplesReadyCallback audioSamplesReadyCallback, boolean isAcousticEchoCancelerSupported, boolean isNoiseSuppressorSupported) {
        this.effects = new WebRtcAudioEffects();
        if (isAcousticEchoCancelerSupported && !WebRtcAudioEffects.isAcousticEchoCancelerSupported()) {
            throw new IllegalArgumentException("HW AEC not supported");
        }
        if (isNoiseSuppressorSupported && !WebRtcAudioEffects.isNoiseSuppressorSupported()) {
            throw new IllegalArgumentException("HW NS not supported");
        }
        this.context = context;
        this.audioManager = audioManager;
        this.audioSource = audioSource;
        this.audioFormat = audioFormat;
        this.audioRecordFactory = audioRecordFactory;
        this.errorCallback = errorCallback;
        this.stateCallback = stateCallback;
        this.audioSamplesReadyCallback = audioSamplesReadyCallback;
        this.isAcousticEchoCancelerSupported = isAcousticEchoCancelerSupported;
        this.isNoiseSuppressorSupported = isNoiseSuppressorSupported;
        Logging.d(TAG, "ctor" + WebRtcAudioUtils.getThreadInfo());
    }

    @CalledByNative
    public void setNativeAudioRecord(long nativeAudioRecord) {
        this.nativeAudioRecord = nativeAudioRecord;
    }

    @CalledByNative
    boolean isAcousticEchoCancelerSupported() {
        return this.isAcousticEchoCancelerSupported;
    }

    @CalledByNative
    boolean isNoiseSuppressorSupported() {
        return this.isNoiseSuppressorSupported;
    }

    @CalledByNative
    boolean isAudioConfigVerified() {
        return this.isAudioConfigVerified;
    }

    @CalledByNative
    boolean isAudioSourceMatchingRecordingSession() {
        if (!this.isAudioConfigVerified) {
            Logging.w(TAG, "Audio configuration has not yet been verified");
            return false;
        }
        return this.audioSourceMatchesRecordingSession;
    }

    @CalledByNative
    private boolean enableBuiltInAEC(boolean enable) {
        Logging.d(TAG, "enableBuiltInAEC(" + enable + ")");
        return this.effects.setAEC(enable);
    }

    @CalledByNative
    private boolean enableBuiltInNS(boolean enable) {
        Logging.d(TAG, "enableBuiltInNS(" + enable + ")");
        return this.effects.setNS(enable);
    }

    @CalledByNative
    private int initRecording(int sampleRate, int channels) {
        Logging.d(TAG, "initRecording(sampleRate=" + sampleRate + ", channels=" + channels + ")");
        if (this.audioRecord != null) {
            reportWebRtcAudioRecordInitError("InitRecording called twice without StopRecording.");
            return -1;
        }
        int bytesPerFrame = channels * getBytesPerSample(this.audioFormat);
        int framesPerBuffer = sampleRate / 100;
        this.byteBuffer = ByteBuffer.allocateDirect(bytesPerFrame * framesPerBuffer);
        if (!this.byteBuffer.hasArray()) {
            reportWebRtcAudioRecordInitError("ByteBuffer does not have backing array.");
            return -1;
        }
        Logging.d(TAG, "byteBuffer.capacity: " + this.byteBuffer.capacity());
        this.emptyBytes = new byte[this.byteBuffer.capacity()];
        nativeCacheDirectBufferAddress(this.nativeAudioRecord, this.byteBuffer);
        int channelConfig = channelCountToConfiguration(channels);
        int minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, this.audioFormat);
        if (minBufferSize == -1 || minBufferSize == -2) {
            reportWebRtcAudioRecordInitError("AudioRecord.getMinBufferSize failed: " + minBufferSize);
            return -1;
        }
        Logging.d(TAG, "AudioRecord.getMinBufferSize: " + minBufferSize);
        int bufferSizeInBytes = Math.max(2 * minBufferSize, this.byteBuffer.capacity());
        Logging.d(TAG, "bufferSizeInBytes: " + bufferSizeInBytes);
        try {
            if (Build.VERSION.SDK_INT >= 23) {
                if (this.audioRecordFactory != null) {
                    Logging.d(TAG, "Creating AudioRecord via injected AudioRecordFactory");
                    this.audioRecord = this.audioRecordFactory.createAudioRecord(sampleRate, channelConfig, this.audioFormat, bufferSizeInBytes);
                } else {
                    this.audioRecord = createAudioRecordOnMOrHigher(this.audioSource, sampleRate, channelConfig, this.audioFormat, bufferSizeInBytes);
                }
                if (this.preferredDevice != null) {
                    setPreferredDevice(this.preferredDevice);
                }
            } else {
                this.audioRecord = createAudioRecordOnLowerThanM(this.audioSource, sampleRate, channelConfig, this.audioFormat, bufferSizeInBytes);
            }
            if (this.audioRecord == null || this.audioRecord.getState() != 1) {
                reportWebRtcAudioRecordInitError("Creation or initialization of audio recorder failed.");
                releaseAudioResources();
                return -1;
            }
            this.effects.enable(this.audioRecord.getAudioSessionId());
            logMainParameters();
            logMainParametersExtended();
            int numActiveRecordingSessions = logRecordingConfigurations(false);
            if (numActiveRecordingSessions != 0) {
                Logging.w(TAG, "Potential microphone conflict. Active sessions: " + numActiveRecordingSessions);
            }
            return framesPerBuffer;
        } catch (IllegalArgumentException | UnsupportedOperationException e) {
            reportWebRtcAudioRecordInitError(e.getMessage());
            releaseAudioResources();
            return -1;
        }
    }

    @TargetApi(23)
    void setPreferredDevice(AudioDeviceInfo preferredDevice) {
        Logging.d(TAG, "setPreferredDevice " + (preferredDevice != null ? Integer.valueOf(preferredDevice.getId()) : null));
        this.preferredDevice = preferredDevice;
        if (this.audioRecord != null && !this.audioRecord.setPreferredDevice(preferredDevice)) {
            Logging.e(TAG, "setPreferredDevice failed");
        }
    }

    @CalledByNative
    private boolean startRecording() throws IllegalStateException {
        Logging.d(TAG, "startRecording");
        assertTrue(this.audioRecord != null);
        assertTrue(this.audioThread == null);
        try {
            this.audioRecord.startRecording();
            if (this.audioRecord.getRecordingState() != 3) {
                reportWebRtcAudioRecordStartError(JavaAudioDeviceModule.AudioRecordStartErrorCode.AUDIO_RECORD_START_STATE_MISMATCH, "AudioRecord.startRecording failed - incorrect state: " + this.audioRecord.getRecordingState());
                return false;
            }
            this.audioThread = new AudioRecordThread("AudioRecordJavaThread");
            this.audioThread.start();
            scheduleLogRecordingConfigurationsTask();
            return true;
        } catch (IllegalStateException e) {
            reportWebRtcAudioRecordStartError(JavaAudioDeviceModule.AudioRecordStartErrorCode.AUDIO_RECORD_START_EXCEPTION, "AudioRecord.startRecording failed: " + e.getMessage());
            return false;
        }
    }

    @CalledByNative
    private boolean stopRecording() {
        Logging.d(TAG, "stopRecording");
        assertTrue(this.audioThread != null);
        if (this.future != null) {
            if (!this.future.isDone()) {
                this.future.cancel(true);
            }
            this.future = null;
        }
        if (this.executor != null) {
            this.executor.shutdownNow();
            this.executor = null;
        }
        this.audioThread.stopThread();
        if (!ThreadUtils.joinUninterruptibly(this.audioThread, AUDIO_RECORD_THREAD_JOIN_TIMEOUT_MS)) {
            Logging.e(TAG, "Join of AudioRecordJavaThread timed out");
            WebRtcAudioUtils.logAudioState(TAG, this.context, this.audioManager);
        }
        this.audioThread = null;
        this.effects.release();
        releaseAudioResources();
        return true;
    }

    @TargetApi(23)
    private static AudioRecord createAudioRecordOnMOrHigher(int audioSource, int sampleRate, int channelConfig, int audioFormat, int bufferSizeInBytes) {
        Logging.d(TAG, "createAudioRecordOnMOrHigher");
        return new AudioRecord.Builder().setAudioSource(audioSource).setAudioFormat(new AudioFormat.Builder().setEncoding(audioFormat).setSampleRate(sampleRate).setChannelMask(channelConfig).build()).setBufferSizeInBytes(bufferSizeInBytes).build();
    }

    private static AudioRecord createAudioRecordOnLowerThanM(int audioSource, int sampleRate, int channelConfig, int audioFormat, int bufferSizeInBytes) {
        Logging.d(TAG, "createAudioRecordOnLowerThanM");
        return new AudioRecord(audioSource, sampleRate, channelConfig, audioFormat, bufferSizeInBytes);
    }

    private void logMainParameters() {
        Logging.d(TAG, "AudioRecord: session ID: " + this.audioRecord.getAudioSessionId() + ", channels: " + this.audioRecord.getChannelCount() + ", sample rate: " + this.audioRecord.getSampleRate());
    }

    @TargetApi(23)
    private void logMainParametersExtended() {
        if (Build.VERSION.SDK_INT >= 23) {
            Logging.d(TAG, "AudioRecord: buffer size in frames: " + this.audioRecord.getBufferSizeInFrames());
        }
    }

    @TargetApi(24)
    private int logRecordingConfigurations(boolean verifyAudioConfig) {
        if (Build.VERSION.SDK_INT < 24) {
            Logging.w(TAG, "AudioManager#getActiveRecordingConfigurations() requires N or higher");
            return 0;
        }
        if (this.audioRecord == null) {
            return 0;
        }
        List<AudioRecordingConfiguration> configs = this.audioManager.getActiveRecordingConfigurations();
        int numActiveRecordingSessions = configs.size();
        Logging.d(TAG, "Number of active recording sessions: " + numActiveRecordingSessions);
        if (numActiveRecordingSessions > 0) {
            logActiveRecordingConfigs(this.audioRecord.getAudioSessionId(), configs);
            if (verifyAudioConfig) {
                this.audioSourceMatchesRecordingSession = verifyAudioConfig(this.audioRecord.getAudioSource(), this.audioRecord.getAudioSessionId(), this.audioRecord.getFormat(), this.audioRecord.getRoutedDevice(), configs);
                this.isAudioConfigVerified = true;
            }
        }
        return numActiveRecordingSessions;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void assertTrue(boolean condition) {
        if (!condition) {
            throw new AssertionError("Expected condition to be true");
        }
    }

    private int channelCountToConfiguration(int channels) {
        return channels == 1 ? 16 : 12;
    }

    public void setMicrophoneMute(boolean mute) {
        Logging.w(TAG, "setMicrophoneMute(" + mute + ")");
        this.microphoneMute = mute;
    }

    private void releaseAudioResources() {
        Logging.d(TAG, "releaseAudioResources");
        if (this.audioRecord != null) {
            this.audioRecord.release();
            this.audioRecord = null;
        }
    }

    private void reportWebRtcAudioRecordInitError(String errorMessage) {
        Logging.e(TAG, "Init recording error: " + errorMessage);
        WebRtcAudioUtils.logAudioState(TAG, this.context, this.audioManager);
        logRecordingConfigurations(false);
        if (this.errorCallback != null) {
            this.errorCallback.onWebRtcAudioRecordInitError(errorMessage);
        }
    }

    private void reportWebRtcAudioRecordStartError(JavaAudioDeviceModule.AudioRecordStartErrorCode errorCode, String errorMessage) {
        Logging.e(TAG, "Start recording error: " + errorCode + ". " + errorMessage);
        WebRtcAudioUtils.logAudioState(TAG, this.context, this.audioManager);
        logRecordingConfigurations(false);
        if (this.errorCallback != null) {
            this.errorCallback.onWebRtcAudioRecordStartError(errorCode, errorMessage);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void reportWebRtcAudioRecordError(String errorMessage) {
        Logging.e(TAG, "Run-time recording error: " + errorMessage);
        WebRtcAudioUtils.logAudioState(TAG, this.context, this.audioManager);
        if (this.errorCallback != null) {
            this.errorCallback.onWebRtcAudioRecordError(errorMessage);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void doAudioRecordStateCallback(int audioState) {
        Logging.d(TAG, "doAudioRecordStateCallback: " + audioStateToString(audioState));
        if (this.stateCallback != null) {
            if (audioState == 0) {
                this.stateCallback.onWebRtcAudioRecordStart();
            } else if (audioState == 1) {
                this.stateCallback.onWebRtcAudioRecordStop();
            } else {
                Logging.e(TAG, "Invalid audio state");
            }
        }
    }

    private static int getBytesPerSample(int audioFormat) {
        switch (audioFormat) {
            case 0:
            case VideoCodecType.VIDEO_CODEC_MULTIPLEX /* 5 */:
            case 6:
            case DEFAULT_AUDIO_SOURCE /* 7 */:
            case 8:
            case 9:
            case CALLBACK_BUFFER_SIZE_MS /* 10 */:
            case 11:
            case 12:
            default:
                throw new IllegalArgumentException("Bad audio format " + audioFormat);
            case 1:
            case 2:
            case 13:
                return 2;
            case 3:
                return 1;
            case 4:
                return 4;
        }
    }

    private void scheduleLogRecordingConfigurationsTask() {
        Logging.d(TAG, "scheduleLogRecordingConfigurationsTask");
        if (Build.VERSION.SDK_INT < 24) {
            return;
        }
        if (this.executor != null) {
            this.executor.shutdownNow();
        }
        this.executor = Executors.newSingleThreadScheduledExecutor();
        Callable<String> callable = () -> {
            logRecordingConfigurations(true);
            return "Scheduled task is done";
        };
        if (this.future != null && !this.future.isDone()) {
            this.future.cancel(true);
        }
        this.future = this.executor.schedule(callable, 100L, TimeUnit.MILLISECONDS);
    }

    @TargetApi(24)
    private static boolean logActiveRecordingConfigs(int session, List<AudioRecordingConfiguration> configs) {
        assertTrue(!configs.isEmpty());
        Logging.d(TAG, "AudioRecordingConfigurations: ");
        for (AudioRecordingConfiguration config : configs) {
            StringBuilder conf = new StringBuilder();
            int audioSource = config.getClientAudioSource();
            conf.append("  client audio source=").append(WebRtcAudioUtils.audioSourceToString(audioSource)).append(", client session id=").append(config.getClientAudioSessionId()).append(" (").append(session).append(")").append("\n");
            AudioFormat format = config.getFormat();
            conf.append("  Device AudioFormat: ").append("channel count=").append(format.getChannelCount()).append(", channel index mask=").append(format.getChannelIndexMask()).append(", channel mask=").append(WebRtcAudioUtils.channelMaskToString(format.getChannelMask())).append(", encoding=").append(WebRtcAudioUtils.audioEncodingToString(format.getEncoding())).append(", sample rate=").append(format.getSampleRate()).append("\n");
            AudioFormat format2 = config.getClientFormat();
            conf.append("  Client AudioFormat: ").append("channel count=").append(format2.getChannelCount()).append(", channel index mask=").append(format2.getChannelIndexMask()).append(", channel mask=").append(WebRtcAudioUtils.channelMaskToString(format2.getChannelMask())).append(", encoding=").append(WebRtcAudioUtils.audioEncodingToString(format2.getEncoding())).append(", sample rate=").append(format2.getSampleRate()).append("\n");
            AudioDeviceInfo device = config.getAudioDevice();
            if (device != null) {
                assertTrue(device.isSource());
                conf.append("  AudioDevice: ").append("type=").append(WebRtcAudioUtils.deviceTypeToString(device.getType())).append(", id=").append(device.getId());
            }
            Logging.d(TAG, conf.toString());
        }
        return true;
    }

    @TargetApi(24)
    private static boolean verifyAudioConfig(int source, int session, AudioFormat format, AudioDeviceInfo device, List<AudioRecordingConfiguration> configs) {
        assertTrue(!configs.isEmpty());
        for (AudioRecordingConfiguration config : configs) {
            AudioDeviceInfo configDevice = config.getAudioDevice();
            if (configDevice != null && config.getClientAudioSource() == source && config.getClientAudioSessionId() == session && config.getClientFormat().getEncoding() == format.getEncoding() && config.getClientFormat().getSampleRate() == format.getSampleRate() && config.getClientFormat().getChannelMask() == format.getChannelMask() && config.getClientFormat().getChannelIndexMask() == format.getChannelIndexMask() && config.getFormat().getEncoding() != 0 && config.getFormat().getSampleRate() > 0 && (config.getFormat().getChannelMask() != 0 || config.getFormat().getChannelIndexMask() != 0)) {
                if (checkDeviceMatch(configDevice, device)) {
                    Logging.d(TAG, "verifyAudioConfig: PASS");
                    return true;
                }
            }
        }
        Logging.e(TAG, "verifyAudioConfig: FAILED");
        return false;
    }

    @TargetApi(24)
    private static boolean checkDeviceMatch(AudioDeviceInfo devA, AudioDeviceInfo devB) {
        return devA.getId() == devB.getId() && devA.getType() == devB.getType();
    }

    private static String audioStateToString(int state) {
        switch (state) {
            case 0:
                return "START";
            case 1:
                return "STOP";
            default:
                return "INVALID";
        }
    }
}

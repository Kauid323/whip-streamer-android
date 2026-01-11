package org.webrtc.voiceengine;

import android.media.AudioRecord;
import android.os.Build;
import android.os.Process;
import android.support.annotation.Nullable;
import java.nio.ByteBuffer;
import java.util.Arrays;
import org.webrtc.Logging;
import org.webrtc.ThreadUtils;

/* loaded from: classes.jar:org/webrtc/voiceengine/WebRtcAudioRecord.class */
public class WebRtcAudioRecord {
    private static final boolean DEBUG = false;
    private static final String TAG = "WebRtcAudioRecord";
    private static final int BITS_PER_SAMPLE = 16;
    private static final int CALLBACK_BUFFER_SIZE_MS = 10;
    private static final int BUFFERS_PER_SECOND = 100;
    private static final int BUFFER_SIZE_FACTOR = 2;
    private static final long AUDIO_RECORD_THREAD_JOIN_TIMEOUT_MS = 2000;
    private static final int DEFAULT_AUDIO_SOURCE = getDefaultAudioSource();
    private static int audioSource = DEFAULT_AUDIO_SOURCE;
    private final long nativeAudioRecord;

    @Nullable
    private WebRtcAudioEffects effects;
    private ByteBuffer byteBuffer;

    @Nullable
    private AudioRecord audioRecord;

    @Nullable
    private AudioRecordThread audioThread;
    private static volatile boolean microphoneMute;
    private byte[] emptyBytes;

    @Nullable
    private static WebRtcAudioRecordErrorCallback errorCallback;

    @Nullable
    private static WebRtcAudioRecordSamplesReadyCallback audioSamplesReadyCallback;

    /* loaded from: classes.jar:org/webrtc/voiceengine/WebRtcAudioRecord$AudioRecordStartErrorCode.class */
    public enum AudioRecordStartErrorCode {
        AUDIO_RECORD_START_EXCEPTION,
        AUDIO_RECORD_START_STATE_MISMATCH
    }

    /* loaded from: classes.jar:org/webrtc/voiceengine/WebRtcAudioRecord$WebRtcAudioRecordErrorCallback.class */
    public interface WebRtcAudioRecordErrorCallback {
        void onWebRtcAudioRecordInitError(String str);

        void onWebRtcAudioRecordStartError(AudioRecordStartErrorCode audioRecordStartErrorCode, String str);

        void onWebRtcAudioRecordError(String str);
    }

    /* loaded from: classes.jar:org/webrtc/voiceengine/WebRtcAudioRecord$WebRtcAudioRecordSamplesReadyCallback.class */
    public interface WebRtcAudioRecordSamplesReadyCallback {
        void onWebRtcAudioRecordSamplesReady(AudioSamples audioSamples);
    }

    private native void nativeCacheDirectBufferAddress(ByteBuffer byteBuffer, long j);

    /* JADX INFO: Access modifiers changed from: private */
    public native void nativeDataIsRecorded(int i, long j);

    public static void setErrorCallback(WebRtcAudioRecordErrorCallback errorCallback2) {
        Logging.m1d(TAG, "Set error callback");
        errorCallback = errorCallback2;
    }

    /* loaded from: classes.jar:org/webrtc/voiceengine/WebRtcAudioRecord$AudioSamples.class */
    public static class AudioSamples {
        private final int audioFormat;
        private final int channelCount;
        private final int sampleRate;
        private final byte[] data;

        private AudioSamples(AudioRecord audioRecord, byte[] data) {
            this.audioFormat = audioRecord.getAudioFormat();
            this.channelCount = audioRecord.getChannelCount();
            this.sampleRate = audioRecord.getSampleRate();
            this.data = data;
        }

        public int getAudioFormat() {
            return this.audioFormat;
        }

        public int getChannelCount() {
            return this.channelCount;
        }

        public int getSampleRate() {
            return this.sampleRate;
        }

        public byte[] getData() {
            return this.data;
        }
    }

    public static void setOnAudioSamplesReady(WebRtcAudioRecordSamplesReadyCallback callback) {
        audioSamplesReadyCallback = callback;
    }

    /* loaded from: classes.jar:org/webrtc/voiceengine/WebRtcAudioRecord$AudioRecordThread.class */
    private class AudioRecordThread extends Thread {
        private volatile boolean keepAlive;

        public AudioRecordThread(String name) {
            super(name);
            this.keepAlive = true;
        }

        @Override // java.lang.Thread, java.lang.Runnable
        public void run() throws IllegalStateException, SecurityException, IllegalArgumentException {
            Process.setThreadPriority(-19);
            Logging.m1d(WebRtcAudioRecord.TAG, "AudioRecordThread" + WebRtcAudioUtils.getThreadInfo());
            WebRtcAudioRecord.assertTrue(WebRtcAudioRecord.this.audioRecord.getRecordingState() == 3);
            System.nanoTime();
            while (this.keepAlive) {
                int bytesRead = WebRtcAudioRecord.this.audioRecord.read(WebRtcAudioRecord.this.byteBuffer, WebRtcAudioRecord.this.byteBuffer.capacity());
                if (bytesRead == WebRtcAudioRecord.this.byteBuffer.capacity()) {
                    if (WebRtcAudioRecord.microphoneMute) {
                        WebRtcAudioRecord.this.byteBuffer.clear();
                        WebRtcAudioRecord.this.byteBuffer.put(WebRtcAudioRecord.this.emptyBytes);
                    }
                    if (this.keepAlive) {
                        WebRtcAudioRecord.this.nativeDataIsRecorded(bytesRead, WebRtcAudioRecord.this.nativeAudioRecord);
                    }
                    if (WebRtcAudioRecord.audioSamplesReadyCallback != null) {
                        byte[] data = Arrays.copyOf(WebRtcAudioRecord.this.byteBuffer.array(), WebRtcAudioRecord.this.byteBuffer.capacity());
                        WebRtcAudioRecord.audioSamplesReadyCallback.onWebRtcAudioRecordSamplesReady(new AudioSamples(WebRtcAudioRecord.this.audioRecord, data));
                    }
                } else {
                    String errorMessage = "AudioRecord.read failed: " + bytesRead;
                    Logging.m2e(WebRtcAudioRecord.TAG, errorMessage);
                    if (bytesRead == -3) {
                        this.keepAlive = false;
                        WebRtcAudioRecord.this.reportWebRtcAudioRecordError(errorMessage);
                    }
                }
            }
            try {
                if (WebRtcAudioRecord.this.audioRecord != null) {
                    WebRtcAudioRecord.this.audioRecord.stop();
                }
            } catch (IllegalStateException e) {
                Logging.m2e(WebRtcAudioRecord.TAG, "AudioRecord.stop failed: " + e.getMessage());
            }
        }

        public void stopThread() {
            Logging.m1d(WebRtcAudioRecord.TAG, "stopThread");
            this.keepAlive = false;
        }
    }

    WebRtcAudioRecord(long nativeAudioRecord) {
        Logging.m1d(TAG, "ctor" + WebRtcAudioUtils.getThreadInfo());
        this.nativeAudioRecord = nativeAudioRecord;
        this.effects = WebRtcAudioEffects.create();
    }

    private boolean enableBuiltInAEC(boolean enable) {
        Logging.m1d(TAG, "enableBuiltInAEC(" + enable + ')');
        if (this.effects == null) {
            Logging.m2e(TAG, "Built-in AEC is not supported on this platform");
            return false;
        }
        return this.effects.setAEC(enable);
    }

    private boolean enableBuiltInNS(boolean enable) {
        Logging.m1d(TAG, "enableBuiltInNS(" + enable + ')');
        if (this.effects == null) {
            Logging.m2e(TAG, "Built-in NS is not supported on this platform");
            return false;
        }
        return this.effects.setNS(enable);
    }

    private int initRecording(int sampleRate, int channels) {
        Logging.m1d(TAG, "initRecording(sampleRate=" + sampleRate + ", channels=" + channels + ")");
        if (this.audioRecord != null) {
            reportWebRtcAudioRecordInitError("InitRecording called twice without StopRecording.");
            return -1;
        }
        int bytesPerFrame = channels * 2;
        int framesPerBuffer = sampleRate / BUFFERS_PER_SECOND;
        this.byteBuffer = ByteBuffer.allocateDirect(bytesPerFrame * framesPerBuffer);
        Logging.m1d(TAG, "byteBuffer.capacity: " + this.byteBuffer.capacity());
        this.emptyBytes = new byte[this.byteBuffer.capacity()];
        nativeCacheDirectBufferAddress(this.byteBuffer, this.nativeAudioRecord);
        int channelConfig = channelCountToConfiguration(channels);
        int minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, 2);
        if (minBufferSize == -1 || minBufferSize == -2) {
            reportWebRtcAudioRecordInitError("AudioRecord.getMinBufferSize failed: " + minBufferSize);
            return -1;
        }
        Logging.m1d(TAG, "AudioRecord.getMinBufferSize: " + minBufferSize);
        int bufferSizeInBytes = Math.max(2 * minBufferSize, this.byteBuffer.capacity());
        Logging.m1d(TAG, "bufferSizeInBytes: " + bufferSizeInBytes);
        try {
            this.audioRecord = new AudioRecord(audioSource, sampleRate, channelConfig, 2, bufferSizeInBytes);
            if (this.audioRecord == null || this.audioRecord.getState() != 1) {
                reportWebRtcAudioRecordInitError("Failed to create a new AudioRecord instance");
                releaseAudioResources();
                return -1;
            }
            if (this.effects != null) {
                this.effects.enable(this.audioRecord.getAudioSessionId());
            }
            logMainParameters();
            logMainParametersExtended();
            return framesPerBuffer;
        } catch (IllegalArgumentException e) {
            reportWebRtcAudioRecordInitError("AudioRecord ctor error: " + e.getMessage());
            releaseAudioResources();
            return -1;
        }
    }

    private boolean startRecording() throws IllegalStateException {
        Logging.m1d(TAG, "startRecording");
        assertTrue(this.audioRecord != null);
        assertTrue(this.audioThread == null);
        try {
            this.audioRecord.startRecording();
            if (this.audioRecord.getRecordingState() != 3) {
                reportWebRtcAudioRecordStartError(AudioRecordStartErrorCode.AUDIO_RECORD_START_STATE_MISMATCH, "AudioRecord.startRecording failed - incorrect state :" + this.audioRecord.getRecordingState());
                return false;
            }
            this.audioThread = new AudioRecordThread("AudioRecordJavaThread");
            this.audioThread.start();
            return true;
        } catch (IllegalStateException e) {
            reportWebRtcAudioRecordStartError(AudioRecordStartErrorCode.AUDIO_RECORD_START_EXCEPTION, "AudioRecord.startRecording failed: " + e.getMessage());
            return false;
        }
    }

    private boolean stopRecording() {
        Logging.m1d(TAG, "stopRecording");
        assertTrue(this.audioThread != null);
        this.audioThread.stopThread();
        if (!ThreadUtils.joinUninterruptibly(this.audioThread, AUDIO_RECORD_THREAD_JOIN_TIMEOUT_MS)) {
            Logging.m2e(TAG, "Join of AudioRecordJavaThread timed out");
            WebRtcAudioUtils.logAudioState(TAG);
        }
        this.audioThread = null;
        if (this.effects != null) {
            this.effects.release();
        }
        releaseAudioResources();
        return true;
    }

    private void logMainParameters() {
        Logging.m1d(TAG, "AudioRecord: session ID: " + this.audioRecord.getAudioSessionId() + ", channels: " + this.audioRecord.getChannelCount() + ", sample rate: " + this.audioRecord.getSampleRate());
    }

    private void logMainParametersExtended() {
        if (Build.VERSION.SDK_INT >= 23) {
            Logging.m1d(TAG, "AudioRecord: buffer size in frames: " + this.audioRecord.getBufferSizeInFrames());
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void assertTrue(boolean condition) {
        if (!condition) {
            throw new AssertionError("Expected condition to be true");
        }
    }

    private int channelCountToConfiguration(int channels) {
        if (channels == 1) {
            return BITS_PER_SAMPLE;
        }
        return 12;
    }

    public static synchronized void setAudioSource(int source) {
        Logging.m3w(TAG, "Audio source is changed from: " + audioSource + " to " + source);
        audioSource = source;
    }

    private static int getDefaultAudioSource() {
        return 7;
    }

    public static void setMicrophoneMute(boolean mute) {
        Logging.m3w(TAG, "setMicrophoneMute(" + mute + ")");
        microphoneMute = mute;
    }

    private void releaseAudioResources() {
        Logging.m1d(TAG, "releaseAudioResources");
        if (this.audioRecord != null) {
            this.audioRecord.release();
            this.audioRecord = null;
        }
    }

    private void reportWebRtcAudioRecordInitError(String errorMessage) {
        Logging.m2e(TAG, "Init recording error: " + errorMessage);
        WebRtcAudioUtils.logAudioState(TAG);
        if (errorCallback != null) {
            errorCallback.onWebRtcAudioRecordInitError(errorMessage);
        }
    }

    private void reportWebRtcAudioRecordStartError(AudioRecordStartErrorCode errorCode, String errorMessage) {
        Logging.m2e(TAG, "Start recording error: " + errorCode + ". " + errorMessage);
        WebRtcAudioUtils.logAudioState(TAG);
        if (errorCallback != null) {
            errorCallback.onWebRtcAudioRecordStartError(errorCode, errorMessage);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void reportWebRtcAudioRecordError(String errorMessage) {
        Logging.m2e(TAG, "Run-time recording error: " + errorMessage);
        WebRtcAudioUtils.logAudioState(TAG);
        if (errorCallback != null) {
            errorCallback.onWebRtcAudioRecordError(errorMessage);
        }
    }
}

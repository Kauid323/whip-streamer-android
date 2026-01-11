package org.webrtc.audio;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Process;
import android.support.annotation.Nullable;
import java.nio.ByteBuffer;
import org.webrtc.CalledByNative;
import org.webrtc.Logging;
import org.webrtc.ThreadUtils;
import org.webrtc.audio.JavaAudioDeviceModule;

/* loaded from: classes.jar:org/webrtc/audio/WebRtcAudioTrack.class */
class WebRtcAudioTrack {
    private static final String TAG = "WebRtcAudioTrackExternal";
    private static final int BITS_PER_SAMPLE = 16;
    private static final int CALLBACK_BUFFER_SIZE_MS = 10;
    private static final int BUFFERS_PER_SECOND = 100;
    private static final long AUDIO_TRACK_THREAD_JOIN_TIMEOUT_MS = 2000;
    private static final int DEFAULT_USAGE = getDefaultUsageAttribute();
    private static final int AUDIO_TRACK_START = 0;
    private static final int AUDIO_TRACK_STOP = 1;
    private long nativeAudioTrack;
    private final Context context;
    private final AudioManager audioManager;
    private final ThreadUtils.ThreadChecker threadChecker;
    private ByteBuffer byteBuffer;

    @Nullable
    private AudioTrack audioTrack;

    @Nullable
    private AudioTrackThread audioThread;
    private final VolumeLogger volumeLogger;
    private volatile boolean speakerMute;
    private byte[] emptyBytes;

    @Nullable
    private final JavaAudioDeviceModule.AudioTrackErrorCallback errorCallback;

    @Nullable
    private final JavaAudioDeviceModule.AudioTrackStateCallback stateCallback;

    private static native void nativeCacheDirectBufferAddress(long j, ByteBuffer byteBuffer);

    /* JADX INFO: Access modifiers changed from: private */
    public static native void nativeGetPlayoutData(long j, int i);

    private static int getDefaultUsageAttribute() {
        if (Build.VERSION.SDK_INT >= 21) {
            return 2;
        }
        return 0;
    }

    /* loaded from: classes.jar:org/webrtc/audio/WebRtcAudioTrack$AudioTrackThread.class */
    private class AudioTrackThread extends Thread {
        private volatile boolean keepAlive;

        public AudioTrackThread(String name) {
            super(name);
            this.keepAlive = true;
        }

        @Override // java.lang.Thread, java.lang.Runnable
        public void run() throws SecurityException, IllegalArgumentException {
            Process.setThreadPriority(-19);
            Logging.m1d(WebRtcAudioTrack.TAG, "AudioTrackThread" + WebRtcAudioUtils.getThreadInfo());
            WebRtcAudioTrack.assertTrue(WebRtcAudioTrack.this.audioTrack.getPlayState() == 3);
            WebRtcAudioTrack.this.doAudioTrackStateCallback(0);
            int sizeInBytes = WebRtcAudioTrack.this.byteBuffer.capacity();
            while (this.keepAlive) {
                WebRtcAudioTrack.nativeGetPlayoutData(WebRtcAudioTrack.this.nativeAudioTrack, sizeInBytes);
                WebRtcAudioTrack.assertTrue(sizeInBytes <= WebRtcAudioTrack.this.byteBuffer.remaining());
                if (WebRtcAudioTrack.this.speakerMute) {
                    WebRtcAudioTrack.this.byteBuffer.clear();
                    WebRtcAudioTrack.this.byteBuffer.put(WebRtcAudioTrack.this.emptyBytes);
                    WebRtcAudioTrack.this.byteBuffer.position(0);
                }
                int bytesWritten = writeBytes(WebRtcAudioTrack.this.audioTrack, WebRtcAudioTrack.this.byteBuffer, sizeInBytes);
                if (bytesWritten != sizeInBytes) {
                    Logging.m2e(WebRtcAudioTrack.TAG, "AudioTrack.write played invalid number of bytes: " + bytesWritten);
                    if (bytesWritten < 0) {
                        this.keepAlive = false;
                        WebRtcAudioTrack.this.reportWebRtcAudioTrackError("AudioTrack.write failed: " + bytesWritten);
                    }
                }
                WebRtcAudioTrack.this.byteBuffer.rewind();
            }
        }

        private int writeBytes(AudioTrack audioTrack, ByteBuffer byteBuffer, int sizeInBytes) {
            if (Build.VERSION.SDK_INT >= 21) {
                return audioTrack.write(byteBuffer, sizeInBytes, 0);
            }
            return audioTrack.write(byteBuffer.array(), byteBuffer.arrayOffset(), sizeInBytes);
        }

        public void stopThread() {
            Logging.m1d(WebRtcAudioTrack.TAG, "stopThread");
            this.keepAlive = false;
        }
    }

    @CalledByNative
    WebRtcAudioTrack(Context context, AudioManager audioManager) {
        this(context, audioManager, null, null);
    }

    WebRtcAudioTrack(Context context, AudioManager audioManager, @Nullable JavaAudioDeviceModule.AudioTrackErrorCallback errorCallback, @Nullable JavaAudioDeviceModule.AudioTrackStateCallback stateCallback) {
        this.threadChecker = new ThreadUtils.ThreadChecker();
        this.threadChecker.detachThread();
        this.context = context;
        this.audioManager = audioManager;
        this.errorCallback = errorCallback;
        this.stateCallback = stateCallback;
        this.volumeLogger = new VolumeLogger(audioManager);
        Logging.m1d(TAG, "ctor" + WebRtcAudioUtils.getThreadInfo());
    }

    @CalledByNative
    public void setNativeAudioTrack(long nativeAudioTrack) {
        this.nativeAudioTrack = nativeAudioTrack;
    }

    @CalledByNative
    private int initPlayout(int sampleRate, int channels, double bufferSizeFactor) {
        this.threadChecker.checkIsOnValidThread();
        Logging.m1d(TAG, "initPlayout(sampleRate=" + sampleRate + ", channels=" + channels + ", bufferSizeFactor=" + bufferSizeFactor + ")");
        int bytesPerFrame = channels * 2;
        this.byteBuffer = ByteBuffer.allocateDirect(bytesPerFrame * (sampleRate / BUFFERS_PER_SECOND));
        Logging.m1d(TAG, "byteBuffer.capacity: " + this.byteBuffer.capacity());
        this.emptyBytes = new byte[this.byteBuffer.capacity()];
        nativeCacheDirectBufferAddress(this.nativeAudioTrack, this.byteBuffer);
        int channelConfig = channelCountToConfiguration(channels);
        int minBufferSizeInBytes = (int) (AudioTrack.getMinBufferSize(sampleRate, channelConfig, 2) * bufferSizeFactor);
        Logging.m1d(TAG, "minBufferSizeInBytes: " + minBufferSizeInBytes);
        if (minBufferSizeInBytes < this.byteBuffer.capacity()) {
            reportWebRtcAudioTrackInitError("AudioTrack.getMinBufferSize returns an invalid value.");
            return -1;
        }
        if (this.audioTrack != null) {
            reportWebRtcAudioTrackInitError("Conflict with existing AudioTrack.");
            return -1;
        }
        try {
            if (Build.VERSION.SDK_INT >= 21) {
                this.audioTrack = createAudioTrackOnLollipopOrHigher(sampleRate, channelConfig, minBufferSizeInBytes);
            } else {
                this.audioTrack = createAudioTrackOnLowerThanLollipop(sampleRate, channelConfig, minBufferSizeInBytes);
            }
            if (this.audioTrack == null || this.audioTrack.getState() != 1) {
                reportWebRtcAudioTrackInitError("Initialization of audio track failed.");
                releaseAudioResources();
                return -1;
            }
            logMainParameters();
            logMainParametersExtended();
            return minBufferSizeInBytes;
        } catch (IllegalArgumentException e) {
            reportWebRtcAudioTrackInitError(e.getMessage());
            releaseAudioResources();
            return -1;
        }
    }

    @CalledByNative
    private boolean startPlayout() throws IllegalStateException {
        this.threadChecker.checkIsOnValidThread();
        this.volumeLogger.start();
        Logging.m1d(TAG, "startPlayout");
        assertTrue(this.audioTrack != null);
        assertTrue(this.audioThread == null);
        try {
            this.audioTrack.play();
            if (this.audioTrack.getPlayState() != 3) {
                reportWebRtcAudioTrackStartError(JavaAudioDeviceModule.AudioTrackStartErrorCode.AUDIO_TRACK_START_STATE_MISMATCH, "AudioTrack.play failed - incorrect state :" + this.audioTrack.getPlayState());
                releaseAudioResources();
                return false;
            }
            this.audioThread = new AudioTrackThread("AudioTrackJavaThread");
            this.audioThread.start();
            return true;
        } catch (IllegalStateException e) {
            reportWebRtcAudioTrackStartError(JavaAudioDeviceModule.AudioTrackStartErrorCode.AUDIO_TRACK_START_EXCEPTION, "AudioTrack.play failed: " + e.getMessage());
            releaseAudioResources();
            return false;
        }
    }

    @CalledByNative
    private boolean stopPlayout() throws IllegalStateException {
        this.threadChecker.checkIsOnValidThread();
        this.volumeLogger.stop();
        Logging.m1d(TAG, "stopPlayout");
        assertTrue(this.audioThread != null);
        logUnderrunCount();
        this.audioThread.stopThread();
        Logging.m1d(TAG, "Stopping the AudioTrackThread...");
        this.audioThread.interrupt();
        if (!ThreadUtils.joinUninterruptibly(this.audioThread, AUDIO_TRACK_THREAD_JOIN_TIMEOUT_MS)) {
            Logging.m2e(TAG, "Join of AudioTrackThread timed out.");
            WebRtcAudioUtils.logAudioState(TAG, this.context, this.audioManager);
        }
        Logging.m1d(TAG, "AudioTrackThread has now been stopped.");
        this.audioThread = null;
        if (this.audioTrack != null) {
            Logging.m1d(TAG, "Calling AudioTrack.stop...");
            try {
                this.audioTrack.stop();
                Logging.m1d(TAG, "AudioTrack.stop is done.");
                doAudioTrackStateCallback(1);
            } catch (IllegalStateException e) {
                Logging.m2e(TAG, "AudioTrack.stop failed: " + e.getMessage());
            }
        }
        releaseAudioResources();
        return true;
    }

    @CalledByNative
    private int getStreamMaxVolume() {
        this.threadChecker.checkIsOnValidThread();
        Logging.m1d(TAG, "getStreamMaxVolume");
        return this.audioManager.getStreamMaxVolume(0);
    }

    @CalledByNative
    private boolean setStreamVolume(int volume) {
        this.threadChecker.checkIsOnValidThread();
        Logging.m1d(TAG, "setStreamVolume(" + volume + ")");
        if (isVolumeFixed()) {
            Logging.m2e(TAG, "The device implements a fixed volume policy.");
            return false;
        }
        this.audioManager.setStreamVolume(0, volume, 0);
        return true;
    }

    private boolean isVolumeFixed() {
        if (Build.VERSION.SDK_INT < 21) {
            return false;
        }
        return this.audioManager.isVolumeFixed();
    }

    @CalledByNative
    private int getStreamVolume() {
        this.threadChecker.checkIsOnValidThread();
        Logging.m1d(TAG, "getStreamVolume");
        return this.audioManager.getStreamVolume(0);
    }

    @CalledByNative
    private int GetPlayoutUnderrunCount() {
        if (Build.VERSION.SDK_INT >= 24) {
            if (this.audioTrack != null) {
                return this.audioTrack.getUnderrunCount();
            }
            return -1;
        }
        return -2;
    }

    private void logMainParameters() {
        Logging.m1d(TAG, "AudioTrack: session ID: " + this.audioTrack.getAudioSessionId() + ", channels: " + this.audioTrack.getChannelCount() + ", sample rate: " + this.audioTrack.getSampleRate() + ", max gain: " + AudioTrack.getMaxVolume());
    }

    @TargetApi(21)
    private static AudioTrack createAudioTrackOnLollipopOrHigher(int sampleRateInHz, int channelConfig, int bufferSizeInBytes) {
        Logging.m1d(TAG, "createAudioTrackOnLollipopOrHigher");
        int nativeOutputSampleRate = AudioTrack.getNativeOutputSampleRate(0);
        Logging.m1d(TAG, "nativeOutputSampleRate: " + nativeOutputSampleRate);
        if (sampleRateInHz != nativeOutputSampleRate) {
            Logging.m3w(TAG, "Unable to use fast mode since requested sample rate is not native");
        }
        return new AudioTrack(new AudioAttributes.Builder().setUsage(DEFAULT_USAGE).setContentType(1).build(), new AudioFormat.Builder().setEncoding(2).setSampleRate(sampleRateInHz).setChannelMask(channelConfig).build(), bufferSizeInBytes, 1, 0);
    }

    private static AudioTrack createAudioTrackOnLowerThanLollipop(int sampleRateInHz, int channelConfig, int bufferSizeInBytes) {
        return new AudioTrack(0, sampleRateInHz, channelConfig, 2, bufferSizeInBytes, 1);
    }

    private void logBufferSizeInFrames() {
        if (Build.VERSION.SDK_INT >= 23) {
            Logging.m1d(TAG, "AudioTrack: buffer size in frames: " + this.audioTrack.getBufferSizeInFrames());
        }
    }

    @CalledByNative
    private int getBufferSizeInFrames() {
        if (Build.VERSION.SDK_INT >= 23) {
            return this.audioTrack.getBufferSizeInFrames();
        }
        return -1;
    }

    private void logBufferCapacityInFrames() {
        if (Build.VERSION.SDK_INT >= 24) {
            Logging.m1d(TAG, "AudioTrack: buffer capacity in frames: " + this.audioTrack.getBufferCapacityInFrames());
        }
    }

    private void logMainParametersExtended() {
        logBufferSizeInFrames();
        logBufferCapacityInFrames();
    }

    private void logUnderrunCount() {
        if (Build.VERSION.SDK_INT >= 24) {
            Logging.m1d(TAG, "underrun count: " + this.audioTrack.getUnderrunCount());
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void assertTrue(boolean condition) {
        if (!condition) {
            throw new AssertionError("Expected condition to be true");
        }
    }

    private int channelCountToConfiguration(int channels) {
        return channels == 1 ? 4 : 12;
    }

    public void setSpeakerMute(boolean mute) {
        Logging.m3w(TAG, "setSpeakerMute(" + mute + ")");
        this.speakerMute = mute;
    }

    private void releaseAudioResources() {
        Logging.m1d(TAG, "releaseAudioResources");
        if (this.audioTrack != null) {
            this.audioTrack.release();
            this.audioTrack = null;
        }
    }

    private void reportWebRtcAudioTrackInitError(String errorMessage) {
        Logging.m2e(TAG, "Init playout error: " + errorMessage);
        WebRtcAudioUtils.logAudioState(TAG, this.context, this.audioManager);
        if (this.errorCallback != null) {
            this.errorCallback.onWebRtcAudioTrackInitError(errorMessage);
        }
    }

    private void reportWebRtcAudioTrackStartError(JavaAudioDeviceModule.AudioTrackStartErrorCode errorCode, String errorMessage) {
        Logging.m2e(TAG, "Start playout error: " + errorCode + ". " + errorMessage);
        WebRtcAudioUtils.logAudioState(TAG, this.context, this.audioManager);
        if (this.errorCallback != null) {
            this.errorCallback.onWebRtcAudioTrackStartError(errorCode, errorMessage);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void reportWebRtcAudioTrackError(String errorMessage) {
        Logging.m2e(TAG, "Run-time playback error: " + errorMessage);
        WebRtcAudioUtils.logAudioState(TAG, this.context, this.audioManager);
        if (this.errorCallback != null) {
            this.errorCallback.onWebRtcAudioTrackError(errorMessage);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void doAudioTrackStateCallback(int audioState) {
        Logging.m1d(TAG, "doAudioTrackStateCallback: " + audioState);
        if (this.stateCallback != null) {
            if (audioState == 0) {
                this.stateCallback.onWebRtcAudioTrackStart();
            } else if (audioState == 1) {
                this.stateCallback.onWebRtcAudioTrackStop();
            } else {
                Logging.m2e(TAG, "Invalid audio state");
            }
        }
    }
}

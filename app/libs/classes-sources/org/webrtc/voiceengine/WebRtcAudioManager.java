package org.webrtc.voiceengine;

import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.os.Build;
import android.support.annotation.Nullable;
import java.util.Timer;
import java.util.TimerTask;
import org.webrtc.ContextUtils;
import org.webrtc.Logging;
import org.webrtc.MediaStreamTrack;

/* loaded from: classes.jar:org/webrtc/voiceengine/WebRtcAudioManager.class */
public class WebRtcAudioManager {
    private static final boolean DEBUG = false;
    private static final String TAG = "WebRtcAudioManager";
    private static final boolean blacklistDeviceForAAudioUsage = true;
    private static boolean useStereoOutput;
    private static boolean useStereoInput;
    private static boolean blacklistDeviceForOpenSLESUsage;
    private static boolean blacklistDeviceForOpenSLESUsageIsOverridden;
    private static final int BITS_PER_SAMPLE = 16;
    private static final int DEFAULT_FRAME_PER_BUFFER = 256;
    private final long nativeAudioManager;
    private final AudioManager audioManager;
    private boolean initialized;
    private int nativeSampleRate;
    private int nativeChannels;
    private boolean hardwareAEC;
    private boolean hardwareAGC;
    private boolean hardwareNS;
    private boolean lowLatencyOutput;
    private boolean lowLatencyInput;
    private boolean proAudio;
    private boolean aAudio;
    private int sampleRate;
    private int outputChannels;
    private int inputChannels;
    private int outputBufferSize;
    private int inputBufferSize;
    private final VolumeLogger volumeLogger;

    private native void nativeCacheAudioParameters(int i, int i2, int i3, boolean z, boolean z2, boolean z3, boolean z4, boolean z5, boolean z6, boolean z7, int i4, int i5, long j);

    public static synchronized void setBlacklistDeviceForOpenSLESUsage(boolean enable) {
        blacklistDeviceForOpenSLESUsageIsOverridden = true;
        blacklistDeviceForOpenSLESUsage = enable;
    }

    public static synchronized void setStereoOutput(boolean enable) {
        Logging.m3w(TAG, "Overriding default output behavior: setStereoOutput(" + enable + ')');
        useStereoOutput = enable;
    }

    public static synchronized void setStereoInput(boolean enable) {
        Logging.m3w(TAG, "Overriding default input behavior: setStereoInput(" + enable + ')');
        useStereoInput = enable;
    }

    public static synchronized boolean getStereoOutput() {
        return useStereoOutput;
    }

    public static synchronized boolean getStereoInput() {
        return useStereoInput;
    }

    /* loaded from: classes.jar:org/webrtc/voiceengine/WebRtcAudioManager$VolumeLogger.class */
    private static class VolumeLogger {
        private static final String THREAD_NAME = "WebRtcVolumeLevelLoggerThread";
        private static final int TIMER_PERIOD_IN_SECONDS = 30;
        private final AudioManager audioManager;

        @Nullable
        private Timer timer;

        public VolumeLogger(AudioManager audioManager) {
            this.audioManager = audioManager;
        }

        public void start() {
            this.timer = new Timer(THREAD_NAME);
            this.timer.schedule(new LogVolumeTask(this.audioManager.getStreamMaxVolume(2), this.audioManager.getStreamMaxVolume(0)), 0L, 30000L);
        }

        /* loaded from: classes.jar:org/webrtc/voiceengine/WebRtcAudioManager$VolumeLogger$LogVolumeTask.class */
        private class LogVolumeTask extends TimerTask {
            private final int maxRingVolume;
            private final int maxVoiceCallVolume;

            LogVolumeTask(int maxRingVolume, int maxVoiceCallVolume) {
                this.maxRingVolume = maxRingVolume;
                this.maxVoiceCallVolume = maxVoiceCallVolume;
            }

            @Override // java.util.TimerTask, java.lang.Runnable
            public void run() {
                int mode = VolumeLogger.this.audioManager.getMode();
                if (mode == 1) {
                    Logging.m1d(WebRtcAudioManager.TAG, "STREAM_RING stream volume: " + VolumeLogger.this.audioManager.getStreamVolume(2) + " (max=" + this.maxRingVolume + ")");
                } else if (mode == 3) {
                    Logging.m1d(WebRtcAudioManager.TAG, "VOICE_CALL stream volume: " + VolumeLogger.this.audioManager.getStreamVolume(0) + " (max=" + this.maxVoiceCallVolume + ")");
                }
            }
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void stop() {
            if (this.timer != null) {
                this.timer.cancel();
                this.timer = null;
            }
        }
    }

    WebRtcAudioManager(long nativeAudioManager) {
        Logging.m1d(TAG, "ctor" + WebRtcAudioUtils.getThreadInfo());
        this.nativeAudioManager = nativeAudioManager;
        this.audioManager = (AudioManager) ContextUtils.getApplicationContext().getSystemService(MediaStreamTrack.AUDIO_TRACK_KIND);
        this.volumeLogger = new VolumeLogger(this.audioManager);
        storeAudioParameters();
        nativeCacheAudioParameters(this.sampleRate, this.outputChannels, this.inputChannels, this.hardwareAEC, this.hardwareAGC, this.hardwareNS, this.lowLatencyOutput, this.lowLatencyInput, this.proAudio, this.aAudio, this.outputBufferSize, this.inputBufferSize, nativeAudioManager);
        WebRtcAudioUtils.logAudioState(TAG);
    }

    private boolean init() {
        Logging.m1d(TAG, "init" + WebRtcAudioUtils.getThreadInfo());
        if (this.initialized) {
            return true;
        }
        Logging.m1d(TAG, "audio mode is: " + WebRtcAudioUtils.modeToString(this.audioManager.getMode()));
        this.initialized = true;
        this.volumeLogger.start();
        return true;
    }

    private void dispose() {
        Logging.m1d(TAG, "dispose" + WebRtcAudioUtils.getThreadInfo());
        if (this.initialized) {
            this.volumeLogger.stop();
        }
    }

    private boolean isCommunicationModeEnabled() {
        return this.audioManager.getMode() == 3;
    }

    private boolean isDeviceBlacklistedForOpenSLESUsage() {
        boolean zDeviceIsBlacklistedForOpenSLESUsage;
        if (blacklistDeviceForOpenSLESUsageIsOverridden) {
            zDeviceIsBlacklistedForOpenSLESUsage = blacklistDeviceForOpenSLESUsage;
        } else {
            zDeviceIsBlacklistedForOpenSLESUsage = WebRtcAudioUtils.deviceIsBlacklistedForOpenSLESUsage();
        }
        boolean blacklisted = zDeviceIsBlacklistedForOpenSLESUsage;
        if (blacklisted) {
            Logging.m1d(TAG, Build.MODEL + " is blacklisted for OpenSL ES usage!");
        }
        return blacklisted;
    }

    private void storeAudioParameters() {
        this.outputChannels = getStereoOutput() ? 2 : 1;
        this.inputChannels = getStereoInput() ? 2 : 1;
        this.sampleRate = getNativeOutputSampleRate();
        this.hardwareAEC = isAcousticEchoCancelerSupported();
        this.hardwareAGC = false;
        this.hardwareNS = isNoiseSuppressorSupported();
        this.lowLatencyOutput = isLowLatencyOutputSupported();
        this.lowLatencyInput = isLowLatencyInputSupported();
        this.proAudio = isProAudioSupported();
        this.aAudio = isAAudioSupported();
        this.outputBufferSize = this.lowLatencyOutput ? getLowLatencyOutputFramesPerBuffer() : getMinOutputFrameSize(this.sampleRate, this.outputChannels);
        this.inputBufferSize = this.lowLatencyInput ? getLowLatencyInputFramesPerBuffer() : getMinInputFrameSize(this.sampleRate, this.inputChannels);
    }

    private boolean hasEarpiece() {
        return ContextUtils.getApplicationContext().getPackageManager().hasSystemFeature("android.hardware.telephony");
    }

    private boolean isLowLatencyOutputSupported() {
        return ContextUtils.getApplicationContext().getPackageManager().hasSystemFeature("android.hardware.audio.low_latency");
    }

    public boolean isLowLatencyInputSupported() {
        return Build.VERSION.SDK_INT >= 21 && isLowLatencyOutputSupported();
    }

    private boolean isProAudioSupported() {
        return Build.VERSION.SDK_INT >= 23 && ContextUtils.getApplicationContext().getPackageManager().hasSystemFeature("android.hardware.audio.pro");
    }

    private boolean isAAudioSupported() {
        Logging.m3w(TAG, "AAudio support is currently disabled on all devices!");
        return false;
    }

    private int getNativeOutputSampleRate() {
        if (WebRtcAudioUtils.runningOnEmulator()) {
            Logging.m1d(TAG, "Running emulator, overriding sample rate to 8 kHz.");
            return 8000;
        }
        if (WebRtcAudioUtils.isDefaultSampleRateOverridden()) {
            Logging.m1d(TAG, "Default sample rate is overriden to " + WebRtcAudioUtils.getDefaultSampleRateHz() + " Hz");
            return WebRtcAudioUtils.getDefaultSampleRateHz();
        }
        int sampleRateHz = getSampleRateForApiLevel();
        Logging.m1d(TAG, "Sample rate is set to " + sampleRateHz + " Hz");
        return sampleRateHz;
    }

    private int getSampleRateForApiLevel() {
        if (Build.VERSION.SDK_INT < 17) {
            return WebRtcAudioUtils.getDefaultSampleRateHz();
        }
        String sampleRateString = this.audioManager.getProperty("android.media.property.OUTPUT_SAMPLE_RATE");
        return sampleRateString == null ? WebRtcAudioUtils.getDefaultSampleRateHz() : Integer.parseInt(sampleRateString);
    }

    private int getLowLatencyOutputFramesPerBuffer() {
        String framesPerBuffer;
        assertTrue(isLowLatencyOutputSupported());
        return (Build.VERSION.SDK_INT >= 17 && (framesPerBuffer = this.audioManager.getProperty("android.media.property.OUTPUT_FRAMES_PER_BUFFER")) != null) ? Integer.parseInt(framesPerBuffer) : DEFAULT_FRAME_PER_BUFFER;
    }

    private static boolean isAcousticEchoCancelerSupported() {
        return WebRtcAudioEffects.canUseAcousticEchoCanceler();
    }

    private static boolean isNoiseSuppressorSupported() {
        return WebRtcAudioEffects.canUseNoiseSuppressor();
    }

    private static int getMinOutputFrameSize(int sampleRateInHz, int numChannels) {
        int bytesPerFrame = numChannels * 2;
        int channelConfig = numChannels == 1 ? 4 : 12;
        return AudioTrack.getMinBufferSize(sampleRateInHz, channelConfig, 2) / bytesPerFrame;
    }

    private int getLowLatencyInputFramesPerBuffer() {
        assertTrue(isLowLatencyInputSupported());
        return getLowLatencyOutputFramesPerBuffer();
    }

    private static int getMinInputFrameSize(int sampleRateInHz, int numChannels) {
        int bytesPerFrame = numChannels * 2;
        int channelConfig = numChannels == 1 ? BITS_PER_SAMPLE : 12;
        return AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, 2) / bytesPerFrame;
    }

    private static void assertTrue(boolean condition) {
        if (!condition) {
            throw new AssertionError("Expected condition to be true");
        }
    }
}

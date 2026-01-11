package org.webrtc.audio;

import android.content.Context;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.os.Build;
import org.webrtc.CalledByNative;
import org.webrtc.Logging;
import org.webrtc.MediaStreamTrack;

/* loaded from: classes.jar:org/webrtc/audio/WebRtcAudioManager.class */
class WebRtcAudioManager {
    private static final String TAG = "WebRtcAudioManagerExternal";
    private static final int DEFAULT_SAMPLE_RATE_HZ = 16000;
    private static final int BITS_PER_SAMPLE = 16;
    private static final int DEFAULT_FRAME_PER_BUFFER = 256;

    WebRtcAudioManager() {
    }

    @CalledByNative
    static AudioManager getAudioManager(Context context) {
        return (AudioManager) context.getSystemService(MediaStreamTrack.AUDIO_TRACK_KIND);
    }

    @CalledByNative
    static int getOutputBufferSize(Context context, AudioManager audioManager, int sampleRate, int numberOfOutputChannels) {
        if (isLowLatencyOutputSupported(context)) {
            return getLowLatencyFramesPerBuffer(audioManager);
        }
        return getMinOutputFrameSize(sampleRate, numberOfOutputChannels);
    }

    @CalledByNative
    static int getInputBufferSize(Context context, AudioManager audioManager, int sampleRate, int numberOfInputChannels) {
        if (isLowLatencyInputSupported(context)) {
            return getLowLatencyFramesPerBuffer(audioManager);
        }
        return getMinInputFrameSize(sampleRate, numberOfInputChannels);
    }

    private static boolean isLowLatencyOutputSupported(Context context) {
        return context.getPackageManager().hasSystemFeature("android.hardware.audio.low_latency");
    }

    private static boolean isLowLatencyInputSupported(Context context) {
        return Build.VERSION.SDK_INT >= 21 && isLowLatencyOutputSupported(context);
    }

    @CalledByNative
    static int getSampleRate(AudioManager audioManager) {
        if (WebRtcAudioUtils.runningOnEmulator()) {
            Logging.m1d(TAG, "Running emulator, overriding sample rate to 8 kHz.");
            return 8000;
        }
        int sampleRateHz = getSampleRateForApiLevel(audioManager);
        Logging.m1d(TAG, "Sample rate is set to " + sampleRateHz + " Hz");
        return sampleRateHz;
    }

    private static int getSampleRateForApiLevel(AudioManager audioManager) {
        String sampleRateString;
        return (Build.VERSION.SDK_INT >= 17 && (sampleRateString = audioManager.getProperty("android.media.property.OUTPUT_SAMPLE_RATE")) != null) ? Integer.parseInt(sampleRateString) : DEFAULT_SAMPLE_RATE_HZ;
    }

    private static int getLowLatencyFramesPerBuffer(AudioManager audioManager) {
        String framesPerBuffer;
        return (Build.VERSION.SDK_INT >= 17 && (framesPerBuffer = audioManager.getProperty("android.media.property.OUTPUT_FRAMES_PER_BUFFER")) != null) ? Integer.parseInt(framesPerBuffer) : DEFAULT_FRAME_PER_BUFFER;
    }

    private static int getMinOutputFrameSize(int sampleRateInHz, int numChannels) {
        int bytesPerFrame = numChannels * 2;
        int channelConfig = numChannels == 1 ? 4 : 12;
        return AudioTrack.getMinBufferSize(sampleRateInHz, channelConfig, 2) / bytesPerFrame;
    }

    private static int getMinInputFrameSize(int sampleRateInHz, int numChannels) {
        int bytesPerFrame = numChannels * 2;
        int channelConfig = numChannels == 1 ? BITS_PER_SAMPLE : 12;
        return AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, 2) / bytesPerFrame;
    }
}

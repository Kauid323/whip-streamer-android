package org.webrtc.audio;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.AudioRecord;
import org.webrtc.JniCommon;
import org.webrtc.Logging;
import org.webrtc.MediaStreamTrack;

/* loaded from: classes.jar:org/webrtc/audio/JavaAudioDeviceModule.class */
public class JavaAudioDeviceModule implements AudioDeviceModule {
    private static final String TAG = "JavaAudioDeviceModule";
    private final Context context;
    private final AudioManager audioManager;
    private final WebRtcAudioRecord audioInput;
    private final WebRtcAudioTrack audioOutput;
    private final int inputSampleRate;
    private final int outputSampleRate;
    private final boolean useStereoInput;
    private final boolean useStereoOutput;
    private final Object nativeLock;
    private long nativeAudioDeviceModule;

    /* loaded from: classes.jar:org/webrtc/audio/JavaAudioDeviceModule$AudioRecordErrorCallback.class */
    public interface AudioRecordErrorCallback {
        void onWebRtcAudioRecordInitError(String str);

        void onWebRtcAudioRecordStartError(AudioRecordStartErrorCode audioRecordStartErrorCode, String str);

        void onWebRtcAudioRecordError(String str);
    }

    /* loaded from: classes.jar:org/webrtc/audio/JavaAudioDeviceModule$AudioRecordStartErrorCode.class */
    public enum AudioRecordStartErrorCode {
        AUDIO_RECORD_START_EXCEPTION,
        AUDIO_RECORD_START_STATE_MISMATCH
    }

    /* loaded from: classes.jar:org/webrtc/audio/JavaAudioDeviceModule$AudioRecordStateCallback.class */
    public interface AudioRecordStateCallback {
        void onWebRtcAudioRecordStart();

        void onWebRtcAudioRecordStop();
    }

    /* loaded from: classes.jar:org/webrtc/audio/JavaAudioDeviceModule$AudioTrackErrorCallback.class */
    public interface AudioTrackErrorCallback {
        void onWebRtcAudioTrackInitError(String str);

        void onWebRtcAudioTrackStartError(AudioTrackStartErrorCode audioTrackStartErrorCode, String str);

        void onWebRtcAudioTrackError(String str);
    }

    /* loaded from: classes.jar:org/webrtc/audio/JavaAudioDeviceModule$AudioTrackStartErrorCode.class */
    public enum AudioTrackStartErrorCode {
        AUDIO_TRACK_START_EXCEPTION,
        AUDIO_TRACK_START_STATE_MISMATCH
    }

    /* loaded from: classes.jar:org/webrtc/audio/JavaAudioDeviceModule$AudioTrackStateCallback.class */
    public interface AudioTrackStateCallback {
        void onWebRtcAudioTrackStart();

        void onWebRtcAudioTrackStop();
    }

    /* loaded from: classes.jar:org/webrtc/audio/JavaAudioDeviceModule$SamplesReadyCallback.class */
    public interface SamplesReadyCallback {
        void onWebRtcAudioRecordSamplesReady(AudioSamples audioSamples);
    }

    /* loaded from: classes.jar:org/webrtc/audio/JavaAudioDeviceModule$AudioRecordFactory.class */
    public interface AudioRecordFactory {
        AudioRecord createAudioRecord(int sampleRate, int channelConfig, int audioFormat, int bufferSizeInBytes);
    }

    private static native long nativeCreateAudioDeviceModule(Context context, AudioManager audioManager, WebRtcAudioRecord webRtcAudioRecord, WebRtcAudioTrack webRtcAudioTrack, int i, int i2, boolean z, boolean z2);

    public static Builder builder(Context context) {
        return new Builder(context);
    }

    /* loaded from: classes.jar:org/webrtc/audio/JavaAudioDeviceModule$Builder.class */
    public static class Builder {
        private final Context context;
        private final AudioManager audioManager;
        private int inputSampleRate;
        private int outputSampleRate;
        private int audioSource;
        private int audioFormat;
        private AudioRecordFactory audioRecordFactory;
        private AudioTrackErrorCallback audioTrackErrorCallback;
        private AudioRecordErrorCallback audioRecordErrorCallback;
        private SamplesReadyCallback samplesReadyCallback;
        private AudioTrackStateCallback audioTrackStateCallback;
        private AudioRecordStateCallback audioRecordStateCallback;
        private boolean useHardwareAcousticEchoCanceler;
        private boolean useHardwareNoiseSuppressor;
        private boolean useStereoInput;
        private boolean useStereoOutput;

        private Builder(Context context) {
            this.audioSource = 7;
            this.audioFormat = 2;
            this.useHardwareAcousticEchoCanceler = JavaAudioDeviceModule.isBuiltInAcousticEchoCancelerSupported();
            this.useHardwareNoiseSuppressor = JavaAudioDeviceModule.isBuiltInNoiseSuppressorSupported();
            this.context = context;
            this.audioManager = (AudioManager) context.getSystemService(MediaStreamTrack.AUDIO_TRACK_KIND);
            this.inputSampleRate = WebRtcAudioManager.getSampleRate(this.audioManager);
            this.outputSampleRate = WebRtcAudioManager.getSampleRate(this.audioManager);
        }

        public Builder setSampleRate(int sampleRate) {
            Logging.d(JavaAudioDeviceModule.TAG, "Input/Output sample rate overridden to: " + sampleRate);
            this.inputSampleRate = sampleRate;
            this.outputSampleRate = sampleRate;
            return this;
        }

        public Builder setInputSampleRate(int inputSampleRate) {
            Logging.d(JavaAudioDeviceModule.TAG, "Input sample rate overridden to: " + inputSampleRate);
            this.inputSampleRate = inputSampleRate;
            return this;
        }

        public Builder setOutputSampleRate(int outputSampleRate) {
            Logging.d(JavaAudioDeviceModule.TAG, "Output sample rate overridden to: " + outputSampleRate);
            this.outputSampleRate = outputSampleRate;
            return this;
        }

        public Builder setAudioSource(int audioSource) {
            this.audioSource = audioSource;
            return this;
        }

        public Builder setAudioFormat(int audioFormat) {
            this.audioFormat = audioFormat;
            return this;
        }

        public Builder setAudioRecordFactory(AudioRecordFactory audioRecordFactory) {
            this.audioRecordFactory = audioRecordFactory;
            return this;
        }

        public Builder setAudioTrackErrorCallback(AudioTrackErrorCallback audioTrackErrorCallback) {
            this.audioTrackErrorCallback = audioTrackErrorCallback;
            return this;
        }

        public Builder setAudioRecordErrorCallback(AudioRecordErrorCallback audioRecordErrorCallback) {
            this.audioRecordErrorCallback = audioRecordErrorCallback;
            return this;
        }

        public Builder setSamplesReadyCallback(SamplesReadyCallback samplesReadyCallback) {
            this.samplesReadyCallback = samplesReadyCallback;
            return this;
        }

        public Builder setAudioTrackStateCallback(AudioTrackStateCallback audioTrackStateCallback) {
            this.audioTrackStateCallback = audioTrackStateCallback;
            return this;
        }

        public Builder setAudioRecordStateCallback(AudioRecordStateCallback audioRecordStateCallback) {
            this.audioRecordStateCallback = audioRecordStateCallback;
            return this;
        }

        public Builder setUseHardwareNoiseSuppressor(boolean useHardwareNoiseSuppressor) {
            if (useHardwareNoiseSuppressor && !JavaAudioDeviceModule.isBuiltInNoiseSuppressorSupported()) {
                Logging.e(JavaAudioDeviceModule.TAG, "HW NS not supported");
                useHardwareNoiseSuppressor = false;
            }
            this.useHardwareNoiseSuppressor = useHardwareNoiseSuppressor;
            return this;
        }

        public Builder setUseHardwareAcousticEchoCanceler(boolean useHardwareAcousticEchoCanceler) {
            if (useHardwareAcousticEchoCanceler && !JavaAudioDeviceModule.isBuiltInAcousticEchoCancelerSupported()) {
                Logging.e(JavaAudioDeviceModule.TAG, "HW AEC not supported");
                useHardwareAcousticEchoCanceler = false;
            }
            this.useHardwareAcousticEchoCanceler = useHardwareAcousticEchoCanceler;
            return this;
        }

        public Builder setUseStereoInput(boolean useStereoInput) {
            this.useStereoInput = useStereoInput;
            return this;
        }

        public Builder setUseStereoOutput(boolean useStereoOutput) {
            this.useStereoOutput = useStereoOutput;
            return this;
        }

        public JavaAudioDeviceModule createAudioDeviceModule() {
            Logging.d(JavaAudioDeviceModule.TAG, "createAudioDeviceModule");
            if (this.useHardwareNoiseSuppressor) {
                Logging.d(JavaAudioDeviceModule.TAG, "HW NS will be used.");
            } else {
                if (JavaAudioDeviceModule.isBuiltInNoiseSuppressorSupported()) {
                    Logging.d(JavaAudioDeviceModule.TAG, "Overriding default behavior; now using WebRTC NS!");
                }
                Logging.d(JavaAudioDeviceModule.TAG, "HW NS will not be used.");
            }
            if (this.useHardwareAcousticEchoCanceler) {
                Logging.d(JavaAudioDeviceModule.TAG, "HW AEC will be used.");
            } else {
                if (JavaAudioDeviceModule.isBuiltInAcousticEchoCancelerSupported()) {
                    Logging.d(JavaAudioDeviceModule.TAG, "Overriding default behavior; now using WebRTC AEC!");
                }
                Logging.d(JavaAudioDeviceModule.TAG, "HW AEC will not be used.");
            }
            WebRtcAudioRecord audioInput = new WebRtcAudioRecord(this.context, this.audioManager, this.audioSource, this.audioFormat, this.audioRecordFactory, this.audioRecordErrorCallback, this.audioRecordStateCallback, this.samplesReadyCallback, this.useHardwareAcousticEchoCanceler, this.useHardwareNoiseSuppressor);
            WebRtcAudioTrack audioOutput = new WebRtcAudioTrack(this.context, this.audioManager, this.audioTrackErrorCallback, this.audioTrackStateCallback);
            return new JavaAudioDeviceModule(this.context, this.audioManager, audioInput, audioOutput, this.inputSampleRate, this.outputSampleRate, this.useStereoInput, this.useStereoOutput);
        }
    }

    /* loaded from: classes.jar:org/webrtc/audio/JavaAudioDeviceModule$AudioSamples.class */
    public static class AudioSamples {
        private final int audioFormat;
        private final int channelCount;
        private final int sampleRate;
        private final byte[] data;

        public AudioSamples(int audioFormat, int channelCount, int sampleRate, byte[] data) {
            this.audioFormat = audioFormat;
            this.channelCount = channelCount;
            this.sampleRate = sampleRate;
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

    public static boolean isBuiltInAcousticEchoCancelerSupported() {
        return WebRtcAudioEffects.isAcousticEchoCancelerSupported();
    }

    public static boolean isBuiltInNoiseSuppressorSupported() {
        return WebRtcAudioEffects.isNoiseSuppressorSupported();
    }

    private JavaAudioDeviceModule(Context context, AudioManager audioManager, WebRtcAudioRecord audioInput, WebRtcAudioTrack audioOutput, int inputSampleRate, int outputSampleRate, boolean useStereoInput, boolean useStereoOutput) {
        this.nativeLock = new Object();
        this.context = context;
        this.audioManager = audioManager;
        this.audioInput = audioInput;
        this.audioOutput = audioOutput;
        this.inputSampleRate = inputSampleRate;
        this.outputSampleRate = outputSampleRate;
        this.useStereoInput = useStereoInput;
        this.useStereoOutput = useStereoOutput;
    }

    @Override // org.webrtc.audio.AudioDeviceModule
    public long getNativeAudioDeviceModulePointer() {
        long j;
        synchronized (this.nativeLock) {
            if (this.nativeAudioDeviceModule == 0) {
                this.nativeAudioDeviceModule = nativeCreateAudioDeviceModule(this.context, this.audioManager, this.audioInput, this.audioOutput, this.inputSampleRate, this.outputSampleRate, this.useStereoInput, this.useStereoOutput);
            }
            j = this.nativeAudioDeviceModule;
        }
        return j;
    }

    @Override // org.webrtc.audio.AudioDeviceModule
    public void release() {
        synchronized (this.nativeLock) {
            if (this.nativeAudioDeviceModule != 0) {
                JniCommon.nativeReleaseRef(this.nativeAudioDeviceModule);
                this.nativeAudioDeviceModule = 0L;
            }
        }
    }

    @Override // org.webrtc.audio.AudioDeviceModule
    public void setSpeakerMute(boolean mute) {
        Logging.d(TAG, "setSpeakerMute: " + mute);
        this.audioOutput.setSpeakerMute(mute);
    }

    @Override // org.webrtc.audio.AudioDeviceModule
    public void setMicrophoneMute(boolean mute) {
        Logging.d(TAG, "setMicrophoneMute: " + mute);
        this.audioInput.setMicrophoneMute(mute);
    }

    public void setPreferredInputDevice(AudioDeviceInfo preferredInputDevice) {
        Logging.d(TAG, "setPreferredInputDevice: " + preferredInputDevice);
        this.audioInput.setPreferredDevice(preferredInputDevice);
    }
}

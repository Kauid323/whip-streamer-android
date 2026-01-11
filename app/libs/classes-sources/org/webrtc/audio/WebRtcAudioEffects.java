package org.webrtc.audio;

import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.AudioEffect;
import android.media.audiofx.NoiseSuppressor;
import android.os.Build;
import android.support.annotation.Nullable;
import java.util.UUID;
import org.webrtc.Logging;

/* loaded from: classes.jar:org/webrtc/audio/WebRtcAudioEffects.class */
class WebRtcAudioEffects {
    private static final boolean DEBUG = false;
    private static final String TAG = "WebRtcAudioEffectsExternal";
    private static final UUID AOSP_ACOUSTIC_ECHO_CANCELER = UUID.fromString("bb392ec0-8d4d-11e0-a896-0002a5d5c51b");
    private static final UUID AOSP_NOISE_SUPPRESSOR = UUID.fromString("c06c8400-8e06-11e0-9cb6-0002a5d5c51b");

    @Nullable
    private static AudioEffect.Descriptor[] cachedEffects;

    @Nullable
    private AcousticEchoCanceler aec;

    /* renamed from: ns */
    @Nullable
    private NoiseSuppressor f10ns;
    private boolean shouldEnableAec;
    private boolean shouldEnableNs;

    public static boolean isAcousticEchoCancelerSupported() {
        if (Build.VERSION.SDK_INT < 18) {
            return false;
        }
        return isEffectTypeAvailable(AudioEffect.EFFECT_TYPE_AEC, AOSP_ACOUSTIC_ECHO_CANCELER);
    }

    public static boolean isNoiseSuppressorSupported() {
        if (Build.VERSION.SDK_INT < 18) {
            return false;
        }
        return isEffectTypeAvailable(AudioEffect.EFFECT_TYPE_NS, AOSP_NOISE_SUPPRESSOR);
    }

    public WebRtcAudioEffects() {
        Logging.m1d(TAG, "ctor" + WebRtcAudioUtils.getThreadInfo());
    }

    public boolean setAEC(boolean enable) {
        Logging.m1d(TAG, "setAEC(" + enable + ")");
        if (!isAcousticEchoCancelerSupported()) {
            Logging.m3w(TAG, "Platform AEC is not supported");
            this.shouldEnableAec = false;
            return false;
        }
        if (this.aec != null && enable != this.shouldEnableAec) {
            Logging.m2e(TAG, "Platform AEC state can't be modified while recording");
            return false;
        }
        this.shouldEnableAec = enable;
        return true;
    }

    public boolean setNS(boolean enable) {
        Logging.m1d(TAG, "setNS(" + enable + ")");
        if (!isNoiseSuppressorSupported()) {
            Logging.m3w(TAG, "Platform NS is not supported");
            this.shouldEnableNs = false;
            return false;
        }
        if (this.f10ns != null && enable != this.shouldEnableNs) {
            Logging.m2e(TAG, "Platform NS state can't be modified while recording");
            return false;
        }
        this.shouldEnableNs = enable;
        return true;
    }

    public void enable(int audioSession) {
        Logging.m1d(TAG, "enable(audioSession=" + audioSession + ")");
        assertTrue(this.aec == null);
        assertTrue(this.f10ns == null);
        if (isAcousticEchoCancelerSupported()) {
            this.aec = AcousticEchoCanceler.create(audioSession);
            if (this.aec != null) {
                boolean enabled = this.aec.getEnabled();
                boolean enable = this.shouldEnableAec && isAcousticEchoCancelerSupported();
                if (this.aec.setEnabled(enable) != 0) {
                    Logging.m2e(TAG, "Failed to set the AcousticEchoCanceler state");
                }
                Logging.m1d(TAG, "AcousticEchoCanceler: was " + (enabled ? "enabled" : "disabled") + ", enable: " + enable + ", is now: " + (this.aec.getEnabled() ? "enabled" : "disabled"));
            } else {
                Logging.m2e(TAG, "Failed to create the AcousticEchoCanceler instance");
            }
        }
        if (isNoiseSuppressorSupported()) {
            this.f10ns = NoiseSuppressor.create(audioSession);
            if (this.f10ns != null) {
                boolean enabled2 = this.f10ns.getEnabled();
                boolean enable2 = this.shouldEnableNs && isNoiseSuppressorSupported();
                if (this.f10ns.setEnabled(enable2) != 0) {
                    Logging.m2e(TAG, "Failed to set the NoiseSuppressor state");
                }
                Logging.m1d(TAG, "NoiseSuppressor: was " + (enabled2 ? "enabled" : "disabled") + ", enable: " + enable2 + ", is now: " + (this.f10ns.getEnabled() ? "enabled" : "disabled"));
                return;
            }
            Logging.m2e(TAG, "Failed to create the NoiseSuppressor instance");
        }
    }

    public void release() {
        Logging.m1d(TAG, "release");
        if (this.aec != null) {
            this.aec.release();
            this.aec = null;
        }
        if (this.f10ns != null) {
            this.f10ns.release();
            this.f10ns = null;
        }
    }

    private boolean effectTypeIsVoIP(UUID type) {
        if (Build.VERSION.SDK_INT < 18) {
            return false;
        }
        return (AudioEffect.EFFECT_TYPE_AEC.equals(type) && isAcousticEchoCancelerSupported()) || (AudioEffect.EFFECT_TYPE_NS.equals(type) && isNoiseSuppressorSupported());
    }

    private static void assertTrue(boolean condition) {
        if (!condition) {
            throw new AssertionError("Expected condition to be true");
        }
    }

    @Nullable
    private static AudioEffect.Descriptor[] getAvailableEffects() {
        if (cachedEffects != null) {
            return cachedEffects;
        }
        cachedEffects = AudioEffect.queryEffects();
        return cachedEffects;
    }

    private static boolean isEffectTypeAvailable(UUID effectType, UUID blockListedUuid) {
        AudioEffect.Descriptor[] effects = getAvailableEffects();
        if (effects == null) {
            return false;
        }
        for (AudioEffect.Descriptor d : effects) {
            if (d.type.equals(effectType)) {
                return !d.uuid.equals(blockListedUuid);
            }
        }
        return false;
    }
}

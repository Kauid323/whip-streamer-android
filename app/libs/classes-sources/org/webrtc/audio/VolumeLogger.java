package org.webrtc.audio;

import android.media.AudioManager;
import android.support.annotation.Nullable;
import java.util.Timer;
import java.util.TimerTask;
import org.webrtc.Logging;

/* loaded from: classes.jar:org/webrtc/audio/VolumeLogger.class */
class VolumeLogger {
    private static final String TAG = "VolumeLogger";
    private static final String THREAD_NAME = "WebRtcVolumeLevelLoggerThread";
    private static final int TIMER_PERIOD_IN_SECONDS = 30;
    private final AudioManager audioManager;

    @Nullable
    private Timer timer;

    public VolumeLogger(AudioManager audioManager) {
        this.audioManager = audioManager;
    }

    public void start() {
        Logging.m1d(TAG, "start" + WebRtcAudioUtils.getThreadInfo());
        if (this.timer != null) {
            return;
        }
        Logging.m1d(TAG, "audio mode is: " + WebRtcAudioUtils.modeToString(this.audioManager.getMode()));
        this.timer = new Timer(THREAD_NAME);
        this.timer.schedule(new LogVolumeTask(this.audioManager.getStreamMaxVolume(2), this.audioManager.getStreamMaxVolume(0)), 0L, 30000L);
    }

    /* loaded from: classes.jar:org/webrtc/audio/VolumeLogger$LogVolumeTask.class */
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
                Logging.m1d(VolumeLogger.TAG, "STREAM_RING stream volume: " + VolumeLogger.this.audioManager.getStreamVolume(2) + " (max=" + this.maxRingVolume + ")");
            } else if (mode == 3) {
                Logging.m1d(VolumeLogger.TAG, "VOICE_CALL stream volume: " + VolumeLogger.this.audioManager.getStreamVolume(0) + " (max=" + this.maxVoiceCallVolume + ")");
            }
        }
    }

    public void stop() {
        Logging.m1d(TAG, "stop" + WebRtcAudioUtils.getThreadInfo());
        if (this.timer != null) {
            this.timer.cancel();
            this.timer = null;
        }
    }
}

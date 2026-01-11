package org.webrtc;

/* loaded from: classes.jar:org/webrtc/AudioTrack.class */
public class AudioTrack extends MediaStreamTrack {
    private static native void nativeSetVolume(long j, double d);

    public AudioTrack(long nativeTrack) {
        super(nativeTrack);
    }

    public void setVolume(double volume) {
        nativeSetVolume(getNativeAudioTrack(), volume);
    }

    long getNativeAudioTrack() {
        return getNativeMediaStreamTrack();
    }
}

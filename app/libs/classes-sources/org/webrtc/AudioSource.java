package org.webrtc;

/* loaded from: classes.jar:org/webrtc/AudioSource.class */
public class AudioSource extends MediaSource {
    public AudioSource(long nativeSource) {
        super(nativeSource);
    }

    long getNativeAudioSource() {
        return getNativeMediaSource();
    }
}

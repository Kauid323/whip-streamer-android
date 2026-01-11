package org.webrtc.audio;

/* loaded from: classes.jar:org/webrtc/audio/AudioDeviceModule.class */
public interface AudioDeviceModule {
    long getNativeAudioDeviceModulePointer();

    void release();

    void setSpeakerMute(boolean z);

    void setMicrophoneMute(boolean z);
}

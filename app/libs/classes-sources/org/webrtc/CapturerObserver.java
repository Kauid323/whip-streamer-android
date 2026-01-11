package org.webrtc;

/* loaded from: classes.jar:org/webrtc/CapturerObserver.class */
public interface CapturerObserver {
    void onCapturerStarted(boolean z);

    void onCapturerStopped();

    void onFrameCaptured(VideoFrame videoFrame);
}

package org.webrtc;

/* loaded from: classes.jar:org/webrtc/VideoSink.class */
public interface VideoSink {
    @CalledByNative
    void onFrame(VideoFrame videoFrame);
}

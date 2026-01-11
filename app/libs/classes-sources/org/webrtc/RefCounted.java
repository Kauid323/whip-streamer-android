package org.webrtc;

/* loaded from: classes.jar:org/webrtc/RefCounted.class */
public interface RefCounted {
    @CalledByNative
    void retain();

    @CalledByNative
    void release();
}

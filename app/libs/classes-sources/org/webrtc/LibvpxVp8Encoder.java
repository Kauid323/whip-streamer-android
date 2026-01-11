package org.webrtc;

/* loaded from: classes.jar:org/webrtc/LibvpxVp8Encoder.class */
public class LibvpxVp8Encoder extends WrappedNativeVideoEncoder {
    static native long nativeCreateEncoder();

    @Override // org.webrtc.WrappedNativeVideoEncoder, org.webrtc.VideoEncoder
    public long createNativeVideoEncoder() {
        return nativeCreateEncoder();
    }

    @Override // org.webrtc.WrappedNativeVideoEncoder, org.webrtc.VideoEncoder
    public boolean isHardwareEncoder() {
        return false;
    }
}

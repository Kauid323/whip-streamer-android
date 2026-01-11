package org.webrtc;

/* loaded from: classes.jar:org/webrtc/LibvpxVp8Decoder.class */
public class LibvpxVp8Decoder extends WrappedNativeVideoDecoder {
    static native long nativeCreateDecoder();

    @Override // org.webrtc.WrappedNativeVideoDecoder, org.webrtc.VideoDecoder
    public long createNativeVideoDecoder() {
        return nativeCreateDecoder();
    }
}

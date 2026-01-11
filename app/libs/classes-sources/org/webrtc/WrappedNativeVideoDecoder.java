package org.webrtc;

import org.webrtc.VideoDecoder;

/* loaded from: classes.jar:org/webrtc/WrappedNativeVideoDecoder.class */
public abstract class WrappedNativeVideoDecoder implements VideoDecoder {
    @Override // org.webrtc.VideoDecoder
    public abstract long createNativeVideoDecoder();

    @Override // org.webrtc.VideoDecoder
    public final VideoCodecStatus initDecode(VideoDecoder.Settings settings, VideoDecoder.Callback decodeCallback) {
        throw new UnsupportedOperationException("Not implemented.");
    }

    @Override // org.webrtc.VideoDecoder
    public final VideoCodecStatus release() {
        throw new UnsupportedOperationException("Not implemented.");
    }

    @Override // org.webrtc.VideoDecoder
    public final VideoCodecStatus decode(EncodedImage frame, VideoDecoder.DecodeInfo info) {
        throw new UnsupportedOperationException("Not implemented.");
    }

    @Override // org.webrtc.VideoDecoder
    public final boolean getPrefersLateDecoding() {
        throw new UnsupportedOperationException("Not implemented.");
    }

    @Override // org.webrtc.VideoDecoder
    public final String getImplementationName() {
        throw new UnsupportedOperationException("Not implemented.");
    }
}

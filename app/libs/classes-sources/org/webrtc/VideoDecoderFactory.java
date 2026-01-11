package org.webrtc;

import android.support.annotation.Nullable;

/* loaded from: classes.jar:org/webrtc/VideoDecoderFactory.class */
public interface VideoDecoderFactory {
    @Deprecated
    @Nullable
    default VideoDecoder createDecoder(String codecType) {
        throw new UnsupportedOperationException("Deprecated and not implemented.");
    }

    @CalledByNative
    @Nullable
    default VideoDecoder createDecoder(VideoCodecInfo info) {
        return createDecoder(info.getName());
    }

    @CalledByNative
    default VideoCodecInfo[] getSupportedCodecs() {
        return new VideoCodecInfo[0];
    }
}

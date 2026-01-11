package org.webrtc;

import android.support.annotation.Nullable;

/* loaded from: classes.jar:org/webrtc/VideoEncoderFactory.class */
public interface VideoEncoderFactory {

    /* loaded from: classes.jar:org/webrtc/VideoEncoderFactory$VideoEncoderSelector.class */
    public interface VideoEncoderSelector {
        @CalledByNative("VideoEncoderSelector")
        void onCurrentEncoder(VideoCodecInfo videoCodecInfo);

        @CalledByNative("VideoEncoderSelector")
        @Nullable
        VideoCodecInfo onAvailableBitrate(int i);

        @CalledByNative("VideoEncoderSelector")
        @Nullable
        VideoCodecInfo onEncoderBroken();
    }

    @CalledByNative
    @Nullable
    VideoEncoder createEncoder(VideoCodecInfo videoCodecInfo);

    @CalledByNative
    VideoCodecInfo[] getSupportedCodecs();

    @CalledByNative
    default VideoCodecInfo[] getImplementations() {
        return getSupportedCodecs();
    }

    @CalledByNative
    default VideoEncoderSelector getEncoderSelector() {
        return null;
    }
}

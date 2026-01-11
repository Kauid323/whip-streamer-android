package org.webrtc;

import android.support.annotation.Nullable;
import org.webrtc.VideoEncoder;

/* loaded from: classes.jar:org/webrtc/VideoEncoderWrapper.class */
class VideoEncoderWrapper {
    /* JADX INFO: Access modifiers changed from: private */
    public static native void nativeOnEncodedFrame(long j, EncodedImage encodedImage);

    VideoEncoderWrapper() {
    }

    @CalledByNative
    static boolean getScalingSettingsOn(VideoEncoder.ScalingSettings scalingSettings) {
        return scalingSettings.f9on;
    }

    @CalledByNative
    @Nullable
    static Integer getScalingSettingsLow(VideoEncoder.ScalingSettings scalingSettings) {
        return scalingSettings.low;
    }

    @CalledByNative
    @Nullable
    static Integer getScalingSettingsHigh(VideoEncoder.ScalingSettings scalingSettings) {
        return scalingSettings.high;
    }

    @CalledByNative
    static VideoEncoder.Callback createEncoderCallback(long nativeEncoder) {
        return (frame, info) -> {
            nativeOnEncodedFrame(nativeEncoder, frame);
        };
    }
}

package org.webrtc;

import android.support.annotation.Nullable;
import org.webrtc.VideoFrame;
import org.webrtc.VideoProcessor;
import org.webrtc.VideoSource;

/* loaded from: classes.jar:org/webrtc/NativeAndroidVideoTrackSource.class */
class NativeAndroidVideoTrackSource {
    private final long nativeAndroidVideoTrackSource;

    private static native void nativeSetIsScreencast(long j, boolean z);

    private static native void nativeSetState(long j, boolean z);

    private static native void nativeAdaptOutputFormat(long j, int i, int i2, @Nullable Integer num, int i3, int i4, @Nullable Integer num2, @Nullable Integer num3);

    @Nullable
    private static native VideoProcessor.FrameAdaptationParameters nativeAdaptFrame(long j, int i, int i2, int i3, long j2);

    private static native void nativeOnFrameCaptured(long j, int i, long j2, VideoFrame.Buffer buffer);

    public NativeAndroidVideoTrackSource(long nativeAndroidVideoTrackSource) {
        this.nativeAndroidVideoTrackSource = nativeAndroidVideoTrackSource;
    }

    public void setState(boolean isLive) {
        nativeSetState(this.nativeAndroidVideoTrackSource, isLive);
    }

    @Nullable
    public VideoProcessor.FrameAdaptationParameters adaptFrame(VideoFrame frame) {
        return nativeAdaptFrame(this.nativeAndroidVideoTrackSource, frame.getBuffer().getWidth(), frame.getBuffer().getHeight(), frame.getRotation(), frame.getTimestampNs());
    }

    public void onFrameCaptured(VideoFrame frame) {
        nativeOnFrameCaptured(this.nativeAndroidVideoTrackSource, frame.getRotation(), frame.getTimestampNs(), frame.getBuffer());
    }

    public void adaptOutputFormat(VideoSource.AspectRatio targetLandscapeAspectRatio, @Nullable Integer maxLandscapePixelCount, VideoSource.AspectRatio targetPortraitAspectRatio, @Nullable Integer maxPortraitPixelCount, @Nullable Integer maxFps) {
        nativeAdaptOutputFormat(this.nativeAndroidVideoTrackSource, targetLandscapeAspectRatio.width, targetLandscapeAspectRatio.height, maxLandscapePixelCount, targetPortraitAspectRatio.width, targetPortraitAspectRatio.height, maxPortraitPixelCount, maxFps);
    }

    public void setIsScreencast(boolean isScreencast) {
        nativeSetIsScreencast(this.nativeAndroidVideoTrackSource, isScreencast);
    }

    @CalledByNative
    static VideoProcessor.FrameAdaptationParameters createFrameAdaptationParameters(int cropX, int cropY, int cropWidth, int cropHeight, int scaleWidth, int scaleHeight, long timestampNs, boolean drop) {
        return new VideoProcessor.FrameAdaptationParameters(cropX, cropY, cropWidth, cropHeight, scaleWidth, scaleHeight, timestampNs, drop);
    }
}

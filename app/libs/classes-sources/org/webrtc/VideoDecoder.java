package org.webrtc;

/* loaded from: classes.jar:org/webrtc/VideoDecoder.class */
public interface VideoDecoder {

    /* loaded from: classes.jar:org/webrtc/VideoDecoder$Callback.class */
    public interface Callback {
        void onDecodedFrame(VideoFrame videoFrame, Integer num, Integer num2);
    }

    @CalledByNative
    VideoCodecStatus initDecode(Settings settings, Callback callback);

    @CalledByNative
    VideoCodecStatus release();

    @CalledByNative
    VideoCodecStatus decode(EncodedImage encodedImage, DecodeInfo decodeInfo);

    @CalledByNative
    boolean getPrefersLateDecoding();

    @CalledByNative
    String getImplementationName();

    /* loaded from: classes.jar:org/webrtc/VideoDecoder$Settings.class */
    public static class Settings {
        public final int numberOfCores;
        public final int width;
        public final int height;

        @CalledByNative("Settings")
        public Settings(int numberOfCores, int width, int height) {
            this.numberOfCores = numberOfCores;
            this.width = width;
            this.height = height;
        }
    }

    /* loaded from: classes.jar:org/webrtc/VideoDecoder$DecodeInfo.class */
    public static class DecodeInfo {
        public final boolean isMissingFrames;
        public final long renderTimeMs;

        public DecodeInfo(boolean isMissingFrames, long renderTimeMs) {
            this.isMissingFrames = isMissingFrames;
            this.renderTimeMs = renderTimeMs;
        }
    }

    @CalledByNative
    default long createNativeVideoDecoder() {
        return 0L;
    }
}

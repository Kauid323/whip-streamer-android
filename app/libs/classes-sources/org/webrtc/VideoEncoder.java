package org.webrtc;

import android.support.annotation.Nullable;
import org.webrtc.EncodedImage;

/* loaded from: classes.jar:org/webrtc/VideoEncoder.class */
public interface VideoEncoder {

    /* loaded from: classes.jar:org/webrtc/VideoEncoder$Callback.class */
    public interface Callback {
        void onEncodedFrame(EncodedImage encodedImage, CodecSpecificInfo codecSpecificInfo);
    }

    /* loaded from: classes.jar:org/webrtc/VideoEncoder$CodecSpecificInfo.class */
    public static class CodecSpecificInfo {
    }

    /* loaded from: classes.jar:org/webrtc/VideoEncoder$CodecSpecificInfoH264.class */
    public static class CodecSpecificInfoH264 extends CodecSpecificInfo {
    }

    /* loaded from: classes.jar:org/webrtc/VideoEncoder$CodecSpecificInfoVP8.class */
    public static class CodecSpecificInfoVP8 extends CodecSpecificInfo {
    }

    /* loaded from: classes.jar:org/webrtc/VideoEncoder$CodecSpecificInfoVP9.class */
    public static class CodecSpecificInfoVP9 extends CodecSpecificInfo {
    }

    @CalledByNative
    VideoCodecStatus initEncode(Settings settings, Callback callback);

    @CalledByNative
    VideoCodecStatus release();

    @CalledByNative
    VideoCodecStatus encode(VideoFrame videoFrame, EncodeInfo encodeInfo);

    @CalledByNative
    VideoCodecStatus setRateAllocation(BitrateAllocation bitrateAllocation, int i);

    @CalledByNative
    ScalingSettings getScalingSettings();

    @CalledByNative
    String getImplementationName();

    /* loaded from: classes.jar:org/webrtc/VideoEncoder$Settings.class */
    public static class Settings {
        public final int numberOfCores;
        public final int width;
        public final int height;
        public final int startBitrate;
        public final int maxFramerate;
        public final int numberOfSimulcastStreams;
        public final boolean automaticResizeOn;
        public final Capabilities capabilities;

        @Deprecated
        public Settings(int numberOfCores, int width, int height, int startBitrate, int maxFramerate, int numberOfSimulcastStreams, boolean automaticResizeOn) {
            this(numberOfCores, width, height, startBitrate, maxFramerate, numberOfSimulcastStreams, automaticResizeOn, new Capabilities(false));
        }

        @CalledByNative("Settings")
        public Settings(int numberOfCores, int width, int height, int startBitrate, int maxFramerate, int numberOfSimulcastStreams, boolean automaticResizeOn, Capabilities capabilities) {
            this.numberOfCores = numberOfCores;
            this.width = width;
            this.height = height;
            this.startBitrate = startBitrate;
            this.maxFramerate = maxFramerate;
            this.numberOfSimulcastStreams = numberOfSimulcastStreams;
            this.automaticResizeOn = automaticResizeOn;
            this.capabilities = capabilities;
        }
    }

    /* loaded from: classes.jar:org/webrtc/VideoEncoder$Capabilities.class */
    public static class Capabilities {
        public final boolean lossNotification;

        @CalledByNative("Capabilities")
        public Capabilities(boolean lossNotification) {
            this.lossNotification = lossNotification;
        }
    }

    /* loaded from: classes.jar:org/webrtc/VideoEncoder$EncodeInfo.class */
    public static class EncodeInfo {
        public final EncodedImage.FrameType[] frameTypes;

        @CalledByNative("EncodeInfo")
        public EncodeInfo(EncodedImage.FrameType[] frameTypes) {
            this.frameTypes = frameTypes;
        }
    }

    /* loaded from: classes.jar:org/webrtc/VideoEncoder$BitrateAllocation.class */
    public static class BitrateAllocation {
        public final int[][] bitratesBbs;

        @CalledByNative("BitrateAllocation")
        public BitrateAllocation(int[][] bitratesBbs) {
            this.bitratesBbs = bitratesBbs;
        }

        public int getSum() {
            int sum = 0;
            for (int[] spatialLayer : this.bitratesBbs) {
                for (int bitrate : spatialLayer) {
                    sum += bitrate;
                }
            }
            return sum;
        }
    }

    /* loaded from: classes.jar:org/webrtc/VideoEncoder$ScalingSettings.class */
    public static class ScalingSettings {

        /* renamed from: on */
        public final boolean f9on;

        @Nullable
        public final Integer low;

        @Nullable
        public final Integer high;
        public static final ScalingSettings OFF = new ScalingSettings();

        public ScalingSettings(int low, int high) {
            this.f9on = true;
            this.low = Integer.valueOf(low);
            this.high = Integer.valueOf(high);
        }

        private ScalingSettings() {
            this.f9on = false;
            this.low = null;
            this.high = null;
        }

        @Deprecated
        public ScalingSettings(boolean on) {
            this.f9on = on;
            this.low = null;
            this.high = null;
        }

        @Deprecated
        public ScalingSettings(boolean on, int low, int high) {
            this.f9on = on;
            this.low = Integer.valueOf(low);
            this.high = Integer.valueOf(high);
        }

        public String toString() {
            return this.f9on ? "[ " + this.low + ", " + this.high + " ]" : "OFF";
        }
    }

    /* loaded from: classes.jar:org/webrtc/VideoEncoder$ResolutionBitrateLimits.class */
    public static class ResolutionBitrateLimits {
        public final int frameSizePixels;
        public final int minStartBitrateBps;
        public final int minBitrateBps;
        public final int maxBitrateBps;

        public ResolutionBitrateLimits(int frameSizePixels, int minStartBitrateBps, int minBitrateBps, int maxBitrateBps) {
            this.frameSizePixels = frameSizePixels;
            this.minStartBitrateBps = minStartBitrateBps;
            this.minBitrateBps = minBitrateBps;
            this.maxBitrateBps = maxBitrateBps;
        }

        @CalledByNative("ResolutionBitrateLimits")
        public int getFrameSizePixels() {
            return this.frameSizePixels;
        }

        @CalledByNative("ResolutionBitrateLimits")
        public int getMinStartBitrateBps() {
            return this.minStartBitrateBps;
        }

        @CalledByNative("ResolutionBitrateLimits")
        public int getMinBitrateBps() {
            return this.minBitrateBps;
        }

        @CalledByNative("ResolutionBitrateLimits")
        public int getMaxBitrateBps() {
            return this.maxBitrateBps;
        }
    }

    @CalledByNative
    default long createNativeVideoEncoder() {
        return 0L;
    }

    @CalledByNative
    default boolean isHardwareEncoder() {
        return true;
    }

    @CalledByNative
    default ResolutionBitrateLimits[] getResolutionBitrateLimits() {
        ResolutionBitrateLimits[] bitrate_limits = new ResolutionBitrateLimits[0];
        return bitrate_limits;
    }
}

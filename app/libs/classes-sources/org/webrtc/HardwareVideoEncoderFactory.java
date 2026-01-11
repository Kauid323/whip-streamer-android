package org.webrtc;

import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Build;
import android.support.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.webrtc.EglBase;
import org.webrtc.EglBase14;

/* loaded from: classes.jar:org/webrtc/HardwareVideoEncoderFactory.class */
public class HardwareVideoEncoderFactory implements VideoEncoderFactory {
    private static final String TAG = "HardwareVideoEncoderFactory";
    private static final int QCOM_VP8_KEY_FRAME_INTERVAL_ANDROID_L_MS = 15000;
    private static final int QCOM_VP8_KEY_FRAME_INTERVAL_ANDROID_M_MS = 20000;
    private static final int QCOM_VP8_KEY_FRAME_INTERVAL_ANDROID_N_MS = 15000;
    private static final List<String> H264_HW_EXCEPTION_MODELS = Arrays.asList("SAMSUNG-SGH-I337", "Nexus 7", "Nexus 4");

    @Nullable
    private final EglBase14.Context sharedContext;
    private final boolean enableIntelVp8Encoder;
    private final boolean enableH264HighProfile;

    @Nullable
    private final Predicate<MediaCodecInfo> codecAllowedPredicate;

    public HardwareVideoEncoderFactory(EglBase.Context sharedContext, boolean enableIntelVp8Encoder, boolean enableH264HighProfile) {
        this(sharedContext, enableIntelVp8Encoder, enableH264HighProfile, null);
    }

    public HardwareVideoEncoderFactory(EglBase.Context sharedContext, boolean enableIntelVp8Encoder, boolean enableH264HighProfile, @Nullable Predicate<MediaCodecInfo> codecAllowedPredicate) {
        if (sharedContext instanceof EglBase14.Context) {
            this.sharedContext = (EglBase14.Context) sharedContext;
        } else {
            Logging.m3w(TAG, "No shared EglBase.Context.  Encoders will not use texture mode.");
            this.sharedContext = null;
        }
        this.enableIntelVp8Encoder = enableIntelVp8Encoder;
        this.enableH264HighProfile = enableH264HighProfile;
        this.codecAllowedPredicate = codecAllowedPredicate;
    }

    @Deprecated
    public HardwareVideoEncoderFactory(boolean enableIntelVp8Encoder, boolean enableH264HighProfile) {
        this(null, enableIntelVp8Encoder, enableH264HighProfile);
    }

    @Override // org.webrtc.VideoEncoderFactory
    @Nullable
    public VideoEncoder createEncoder(VideoCodecInfo input) {
        VideoCodecMimeType type;
        MediaCodecInfo info;
        if (Build.VERSION.SDK_INT < 19 || (info = findCodecForType((type = VideoCodecMimeType.valueOf(input.name)))) == null) {
            return null;
        }
        String codecName = info.getName();
        String mime = type.mimeType();
        Integer surfaceColorFormat = MediaCodecUtils.selectColorFormat(MediaCodecUtils.TEXTURE_COLOR_FORMATS, info.getCapabilitiesForType(mime));
        Integer yuvColorFormat = MediaCodecUtils.selectColorFormat(MediaCodecUtils.ENCODER_COLOR_FORMATS, info.getCapabilitiesForType(mime));
        if (type == VideoCodecMimeType.H264) {
            boolean isHighProfile = H264Utils.isSameH264Profile(input.params, MediaCodecUtils.getCodecProperties(type, true));
            boolean isBaselineProfile = H264Utils.isSameH264Profile(input.params, MediaCodecUtils.getCodecProperties(type, false));
            if (!isHighProfile && !isBaselineProfile) {
                return null;
            }
            if (isHighProfile && !isH264HighProfileSupported(info)) {
                return null;
            }
        }
        return new HardwareVideoEncoder(new MediaCodecWrapperFactoryImpl(), codecName, type, surfaceColorFormat, yuvColorFormat, input.params, getKeyFrameIntervalSec(type), getForcedKeyFrameIntervalMs(type, codecName), createBitrateAdjuster(type, codecName), this.sharedContext);
    }

    @Override // org.webrtc.VideoEncoderFactory
    public VideoCodecInfo[] getSupportedCodecs() {
        if (Build.VERSION.SDK_INT < 19) {
            return new VideoCodecInfo[0];
        }
        List<VideoCodecInfo> supportedCodecInfos = new ArrayList<>();
        for (VideoCodecMimeType type : new VideoCodecMimeType[]{VideoCodecMimeType.VP8, VideoCodecMimeType.VP9, VideoCodecMimeType.H264}) {
            MediaCodecInfo codec = findCodecForType(type);
            if (codec != null) {
                String name = type.name();
                if (type == VideoCodecMimeType.H264 && isH264HighProfileSupported(codec)) {
                    supportedCodecInfos.add(new VideoCodecInfo(name, MediaCodecUtils.getCodecProperties(type, true)));
                }
                supportedCodecInfos.add(new VideoCodecInfo(name, MediaCodecUtils.getCodecProperties(type, false)));
            }
        }
        return (VideoCodecInfo[]) supportedCodecInfos.toArray(new VideoCodecInfo[supportedCodecInfos.size()]);
    }

    @Nullable
    private MediaCodecInfo findCodecForType(VideoCodecMimeType type) {
        for (int i = 0; i < MediaCodecList.getCodecCount(); i++) {
            MediaCodecInfo info = null;
            try {
                info = MediaCodecList.getCodecInfoAt(i);
            } catch (IllegalArgumentException e) {
                Logging.m4e(TAG, "Cannot retrieve encoder codec info", e);
            }
            if (info != null && info.isEncoder() && isSupportedCodec(info, type)) {
                return info;
            }
        }
        return null;
    }

    private boolean isSupportedCodec(MediaCodecInfo info, VideoCodecMimeType type) {
        return MediaCodecUtils.codecSupportsType(info, type) && MediaCodecUtils.selectColorFormat(MediaCodecUtils.ENCODER_COLOR_FORMATS, info.getCapabilitiesForType(type.mimeType())) != null && isHardwareSupportedInCurrentSdk(info, type) && isMediaCodecAllowed(info);
    }

    private boolean isHardwareSupportedInCurrentSdk(MediaCodecInfo info, VideoCodecMimeType type) {
        switch (type) {
            case VP8:
                return isHardwareSupportedInCurrentSdkVp8(info);
            case VP9:
                return isHardwareSupportedInCurrentSdkVp9(info);
            case H264:
                return isHardwareSupportedInCurrentSdkH264(info);
            default:
                return false;
        }
    }

    private boolean isHardwareSupportedInCurrentSdkVp8(MediaCodecInfo info) {
        String name = info.getName();
        return (name.startsWith("OMX.qcom.") && Build.VERSION.SDK_INT >= 19) || (name.startsWith("OMX.Exynos.") && Build.VERSION.SDK_INT >= 23) || (name.startsWith("OMX.Intel.") && Build.VERSION.SDK_INT >= 21 && this.enableIntelVp8Encoder);
    }

    private boolean isHardwareSupportedInCurrentSdkVp9(MediaCodecInfo info) {
        String name = info.getName();
        return (name.startsWith("OMX.qcom.") || name.startsWith("OMX.Exynos.")) && Build.VERSION.SDK_INT >= 24;
    }

    private boolean isHardwareSupportedInCurrentSdkH264(MediaCodecInfo info) {
        if (H264_HW_EXCEPTION_MODELS.contains(Build.MODEL)) {
            return false;
        }
        String name = info.getName();
        return (name.startsWith("OMX.qcom.") && Build.VERSION.SDK_INT >= 19) || (name.startsWith("OMX.Exynos.") && Build.VERSION.SDK_INT >= 21);
    }

    private boolean isMediaCodecAllowed(MediaCodecInfo info) {
        if (this.codecAllowedPredicate == null) {
            return true;
        }
        return this.codecAllowedPredicate.test(info);
    }

    private int getKeyFrameIntervalSec(VideoCodecMimeType type) {
        switch (type) {
            case VP8:
            case VP9:
                return 100;
            case H264:
                return 20;
            default:
                throw new IllegalArgumentException("Unsupported VideoCodecMimeType " + type);
        }
    }

    private int getForcedKeyFrameIntervalMs(VideoCodecMimeType type, String codecName) {
        if (type == VideoCodecMimeType.VP8 && codecName.startsWith("OMX.qcom.")) {
            if (Build.VERSION.SDK_INT == 21 || Build.VERSION.SDK_INT == 22) {
                return 15000;
            }
            if (Build.VERSION.SDK_INT == 23) {
                return QCOM_VP8_KEY_FRAME_INTERVAL_ANDROID_M_MS;
            }
            if (Build.VERSION.SDK_INT > 23) {
                return 15000;
            }
            return 0;
        }
        return 0;
    }

    private BitrateAdjuster createBitrateAdjuster(VideoCodecMimeType type, String codecName) {
        if (codecName.startsWith("OMX.Exynos.")) {
            if (type == VideoCodecMimeType.VP8) {
                return new DynamicBitrateAdjuster();
            }
            return new FramerateBitrateAdjuster();
        }
        return new BaseBitrateAdjuster();
    }

    private boolean isH264HighProfileSupported(MediaCodecInfo info) {
        return this.enableH264HighProfile && Build.VERSION.SDK_INT > 23 && info.getName().startsWith("OMX.Exynos.");
    }
}

package org.webrtc;

import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Build;
import android.support.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import org.webrtc.EglBase;

/* loaded from: classes.jar:org/webrtc/MediaCodecVideoDecoderFactory.class */
class MediaCodecVideoDecoderFactory implements VideoDecoderFactory {
    private static final String TAG = "MediaCodecVideoDecoderFactory";

    @Nullable
    private final EglBase.Context sharedContext;

    @Nullable
    private final Predicate<MediaCodecInfo> codecAllowedPredicate;

    public MediaCodecVideoDecoderFactory(@Nullable EglBase.Context sharedContext, @Nullable Predicate<MediaCodecInfo> codecAllowedPredicate) {
        this.sharedContext = sharedContext;
        this.codecAllowedPredicate = codecAllowedPredicate;
    }

    @Override // org.webrtc.VideoDecoderFactory
    @Nullable
    public VideoDecoder createDecoder(VideoCodecInfo codecType) {
        VideoCodecMimeType type = VideoCodecMimeType.valueOf(codecType.getName());
        MediaCodecInfo info = findCodecForType(type);
        if (info == null) {
            return null;
        }
        MediaCodecInfo.CodecCapabilities capabilities = info.getCapabilitiesForType(type.mimeType());
        return new AndroidVideoDecoder(new MediaCodecWrapperFactoryImpl(), info.getName(), type, MediaCodecUtils.selectColorFormat(MediaCodecUtils.DECODER_COLOR_FORMATS, capabilities).intValue(), this.sharedContext);
    }

    @Override // org.webrtc.VideoDecoderFactory
    public VideoCodecInfo[] getSupportedCodecs() {
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
        if (Build.VERSION.SDK_INT < 19) {
            return null;
        }
        for (int i = 0; i < MediaCodecList.getCodecCount(); i++) {
            MediaCodecInfo info = null;
            try {
                info = MediaCodecList.getCodecInfoAt(i);
            } catch (IllegalArgumentException e) {
                Logging.m4e(TAG, "Cannot retrieve decoder codec info", e);
            }
            if (info != null && !info.isEncoder() && isSupportedCodec(info, type)) {
                return info;
            }
        }
        return null;
    }

    private boolean isSupportedCodec(MediaCodecInfo info, VideoCodecMimeType type) {
        info.getName();
        if (!MediaCodecUtils.codecSupportsType(info, type) || MediaCodecUtils.selectColorFormat(MediaCodecUtils.DECODER_COLOR_FORMATS, info.getCapabilitiesForType(type.mimeType())) == null) {
            return false;
        }
        return isCodecAllowed(info);
    }

    private boolean isCodecAllowed(MediaCodecInfo info) {
        if (this.codecAllowedPredicate == null) {
            return true;
        }
        return this.codecAllowedPredicate.test(info);
    }

    private boolean isH264HighProfileSupported(MediaCodecInfo info) {
        String name = info.getName();
        if (Build.VERSION.SDK_INT >= 21 && name.startsWith("OMX.qcom.")) {
            return true;
        }
        if (Build.VERSION.SDK_INT >= 23 && name.startsWith("OMX.Exynos.")) {
            return true;
        }
        return false;
    }
}

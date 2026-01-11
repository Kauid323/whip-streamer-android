package org.webrtc;

import android.media.MediaCodecInfo;
import android.support.annotation.Nullable;
import org.webrtc.EglBase;

/* loaded from: classes.jar:org/webrtc/PlatformSoftwareVideoDecoderFactory.class */
public class PlatformSoftwareVideoDecoderFactory extends MediaCodecVideoDecoderFactory {
    private static final Predicate<MediaCodecInfo> defaultAllowedPredicate = new Predicate<MediaCodecInfo>() { // from class: org.webrtc.PlatformSoftwareVideoDecoderFactory.1
        @Override // org.webrtc.Predicate
        public boolean test(MediaCodecInfo arg) {
            return MediaCodecUtils.isSoftwareOnly(arg);
        }
    };

    @Override // org.webrtc.MediaCodecVideoDecoderFactory, org.webrtc.VideoDecoderFactory
    public /* bridge */ /* synthetic */ VideoCodecInfo[] getSupportedCodecs() {
        return super.getSupportedCodecs();
    }

    @Override // org.webrtc.MediaCodecVideoDecoderFactory, org.webrtc.VideoDecoderFactory
    @Nullable
    public /* bridge */ /* synthetic */ VideoDecoder createDecoder(VideoCodecInfo videoCodecInfo) {
        return super.createDecoder(videoCodecInfo);
    }

    public PlatformSoftwareVideoDecoderFactory(@Nullable EglBase.Context sharedContext) {
        super(sharedContext, defaultAllowedPredicate);
    }
}

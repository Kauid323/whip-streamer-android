package org.webrtc;

import android.support.annotation.Nullable;
import java.util.Arrays;
import java.util.LinkedHashSet;
import org.webrtc.EglBase;

/* loaded from: classes.jar:org/webrtc/DefaultVideoDecoderFactory.class */
public class DefaultVideoDecoderFactory implements VideoDecoderFactory {
    private final VideoDecoderFactory hardwareVideoDecoderFactory;
    private final VideoDecoderFactory softwareVideoDecoderFactory;

    @Nullable
    private final VideoDecoderFactory platformSoftwareVideoDecoderFactory;

    public DefaultVideoDecoderFactory(@Nullable EglBase.Context eglContext) {
        this.softwareVideoDecoderFactory = new SoftwareVideoDecoderFactory();
        this.hardwareVideoDecoderFactory = new HardwareVideoDecoderFactory(eglContext);
        this.platformSoftwareVideoDecoderFactory = new PlatformSoftwareVideoDecoderFactory(eglContext);
    }

    DefaultVideoDecoderFactory(VideoDecoderFactory hardwareVideoDecoderFactory) {
        this.softwareVideoDecoderFactory = new SoftwareVideoDecoderFactory();
        this.hardwareVideoDecoderFactory = hardwareVideoDecoderFactory;
        this.platformSoftwareVideoDecoderFactory = null;
    }

    @Override // org.webrtc.VideoDecoderFactory
    @Nullable
    public VideoDecoder createDecoder(VideoCodecInfo codecType) {
        VideoDecoder softwareDecoder = this.softwareVideoDecoderFactory.createDecoder(codecType);
        VideoDecoder hardwareDecoder = this.hardwareVideoDecoderFactory.createDecoder(codecType);
        if (softwareDecoder == null && this.platformSoftwareVideoDecoderFactory != null) {
            softwareDecoder = this.platformSoftwareVideoDecoderFactory.createDecoder(codecType);
        }
        if (hardwareDecoder == null || softwareDecoder == null) {
            return hardwareDecoder != null ? hardwareDecoder : softwareDecoder;
        }
        return new VideoDecoderFallback(softwareDecoder, hardwareDecoder);
    }

    @Override // org.webrtc.VideoDecoderFactory
    public VideoCodecInfo[] getSupportedCodecs() {
        LinkedHashSet<VideoCodecInfo> supportedCodecInfos = new LinkedHashSet<>();
        supportedCodecInfos.addAll(Arrays.asList(this.softwareVideoDecoderFactory.getSupportedCodecs()));
        supportedCodecInfos.addAll(Arrays.asList(this.hardwareVideoDecoderFactory.getSupportedCodecs()));
        if (this.platformSoftwareVideoDecoderFactory != null) {
            supportedCodecInfos.addAll(Arrays.asList(this.platformSoftwareVideoDecoderFactory.getSupportedCodecs()));
        }
        return (VideoCodecInfo[]) supportedCodecInfos.toArray(new VideoCodecInfo[supportedCodecInfos.size()]);
    }
}

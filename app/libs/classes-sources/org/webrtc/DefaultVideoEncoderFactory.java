package org.webrtc;

import android.support.annotation.Nullable;
import java.util.Arrays;
import java.util.LinkedHashSet;
import org.webrtc.EglBase;

/* loaded from: classes.jar:org/webrtc/DefaultVideoEncoderFactory.class */
public class DefaultVideoEncoderFactory implements VideoEncoderFactory {
    private final VideoEncoderFactory hardwareVideoEncoderFactory;
    private final VideoEncoderFactory softwareVideoEncoderFactory = new SoftwareVideoEncoderFactory();

    public DefaultVideoEncoderFactory(EglBase.Context eglContext, boolean enableIntelVp8Encoder, boolean enableH264HighProfile) {
        this.hardwareVideoEncoderFactory = new HardwareVideoEncoderFactory(eglContext, enableIntelVp8Encoder, enableH264HighProfile);
    }

    DefaultVideoEncoderFactory(VideoEncoderFactory hardwareVideoEncoderFactory) {
        this.hardwareVideoEncoderFactory = hardwareVideoEncoderFactory;
    }

    @Override // org.webrtc.VideoEncoderFactory
    @Nullable
    public VideoEncoder createEncoder(VideoCodecInfo info) {
        VideoEncoder softwareEncoder = this.softwareVideoEncoderFactory.createEncoder(info);
        VideoEncoder hardwareEncoder = this.hardwareVideoEncoderFactory.createEncoder(info);
        if (hardwareEncoder == null || softwareEncoder == null) {
            return hardwareEncoder != null ? hardwareEncoder : softwareEncoder;
        }
        return new VideoEncoderFallback(softwareEncoder, hardwareEncoder);
    }

    @Override // org.webrtc.VideoEncoderFactory
    public VideoCodecInfo[] getSupportedCodecs() {
        LinkedHashSet<VideoCodecInfo> supportedCodecInfos = new LinkedHashSet<>();
        supportedCodecInfos.addAll(Arrays.asList(this.softwareVideoEncoderFactory.getSupportedCodecs()));
        supportedCodecInfos.addAll(Arrays.asList(this.hardwareVideoEncoderFactory.getSupportedCodecs()));
        return (VideoCodecInfo[]) supportedCodecInfos.toArray(new VideoCodecInfo[supportedCodecInfos.size()]);
    }
}

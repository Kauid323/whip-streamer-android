package org.webrtc;

import android.support.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/* loaded from: classes.jar:org/webrtc/SoftwareVideoEncoderFactory.class */
public class SoftwareVideoEncoderFactory implements VideoEncoderFactory {
    @Override // org.webrtc.VideoEncoderFactory
    @Nullable
    public VideoEncoder createEncoder(VideoCodecInfo info) {
        if (info.name.equalsIgnoreCase("VP8")) {
            return new LibvpxVp8Encoder();
        }
        if (info.name.equalsIgnoreCase("VP9") && LibvpxVp9Encoder.nativeIsSupported()) {
            return new LibvpxVp9Encoder();
        }
        return null;
    }

    @Override // org.webrtc.VideoEncoderFactory
    public VideoCodecInfo[] getSupportedCodecs() {
        return supportedCodecs();
    }

    static VideoCodecInfo[] supportedCodecs() {
        List<VideoCodecInfo> codecs = new ArrayList<>();
        codecs.add(new VideoCodecInfo("VP8", new HashMap()));
        if (LibvpxVp9Encoder.nativeIsSupported()) {
            codecs.add(new VideoCodecInfo("VP9", new HashMap()));
        }
        return (VideoCodecInfo[]) codecs.toArray(new VideoCodecInfo[codecs.size()]);
    }
}

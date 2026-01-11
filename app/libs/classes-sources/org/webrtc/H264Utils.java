package org.webrtc;

import java.util.HashMap;
import java.util.Map;

/* loaded from: classes.jar:org/webrtc/H264Utils.class */
class H264Utils {
    public static final String H264_FMTP_PROFILE_LEVEL_ID = "profile-level-id";
    public static final String H264_FMTP_LEVEL_ASYMMETRY_ALLOWED = "level-asymmetry-allowed";
    public static final String H264_FMTP_PACKETIZATION_MODE = "packetization-mode";
    public static final String H264_PROFILE_CONSTRAINED_BASELINE = "42e0";
    public static final String H264_PROFILE_CONSTRAINED_HIGH = "640c";
    public static final String H264_LEVEL_3_1 = "1f";
    public static final String H264_CONSTRAINED_HIGH_3_1 = "640c1f";
    public static final String H264_CONSTRAINED_BASELINE_3_1 = "42e01f";
    public static VideoCodecInfo DEFAULT_H264_BASELINE_PROFILE_CODEC = new VideoCodecInfo("H264", getDefaultH264Params(false));
    public static VideoCodecInfo DEFAULT_H264_HIGH_PROFILE_CODEC = new VideoCodecInfo("H264", getDefaultH264Params(true));

    private static native boolean nativeIsSameH264Profile(Map<String, String> map, Map<String, String> map2);

    H264Utils() {
    }

    public static Map<String, String> getDefaultH264Params(boolean isHighProfile) {
        Map<String, String> params = new HashMap<>();
        params.put("level-asymmetry-allowed", "1");
        params.put("packetization-mode", "1");
        params.put("profile-level-id", isHighProfile ? "640c1f" : "42e01f");
        return params;
    }

    public static boolean isSameH264Profile(Map<String, String> params1, Map<String, String> params2) {
        return nativeIsSameH264Profile(params1, params2);
    }
}

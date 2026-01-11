package org.webrtc;

import java.io.IOException;

/* loaded from: classes.jar:org/webrtc/MediaCodecWrapperFactory.class */
interface MediaCodecWrapperFactory {
    MediaCodecWrapper createByCodecName(String str) throws IOException;
}

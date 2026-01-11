package org.webrtc;

/* loaded from: classes.jar:org/webrtc/BitrateAdjuster.class */
interface BitrateAdjuster {
    void setTargets(int i, int i2);

    void reportEncodedFrame(int i);

    int getAdjustedBitrateBps();

    int getCodecConfigFramerate();
}

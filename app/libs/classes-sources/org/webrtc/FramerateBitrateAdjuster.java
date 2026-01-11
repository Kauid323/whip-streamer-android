package org.webrtc;

/* loaded from: classes.jar:org/webrtc/FramerateBitrateAdjuster.class */
class FramerateBitrateAdjuster extends BaseBitrateAdjuster {
    private static final int INITIAL_FPS = 30;

    FramerateBitrateAdjuster() {
    }

    @Override // org.webrtc.BaseBitrateAdjuster, org.webrtc.BitrateAdjuster
    public void setTargets(int targetBitrateBps, int targetFps) {
        if (this.targetFps == 0) {
            targetFps = INITIAL_FPS;
        }
        super.setTargets(targetBitrateBps, targetFps);
        this.targetBitrateBps = (this.targetBitrateBps * INITIAL_FPS) / this.targetFps;
    }

    @Override // org.webrtc.BaseBitrateAdjuster, org.webrtc.BitrateAdjuster
    public int getCodecConfigFramerate() {
        return INITIAL_FPS;
    }
}

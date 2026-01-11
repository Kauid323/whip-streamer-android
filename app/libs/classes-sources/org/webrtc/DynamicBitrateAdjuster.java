package org.webrtc;

/* loaded from: classes.jar:org/webrtc/DynamicBitrateAdjuster.class */
class DynamicBitrateAdjuster extends BaseBitrateAdjuster {
    private static final double BITRATE_ADJUSTMENT_SEC = 3.0d;
    private static final double BITRATE_ADJUSTMENT_MAX_SCALE = 4.0d;
    private static final int BITRATE_ADJUSTMENT_STEPS = 20;
    private static final double BITS_PER_BYTE = 8.0d;
    private double deviationBytes;
    private double timeSinceLastAdjustmentMs;
    private int bitrateAdjustmentScaleExp;

    DynamicBitrateAdjuster() {
    }

    @Override // org.webrtc.BaseBitrateAdjuster, org.webrtc.BitrateAdjuster
    public void setTargets(int targetBitrateBps, int targetFps) {
        if (this.targetBitrateBps > 0 && targetBitrateBps < this.targetBitrateBps) {
            this.deviationBytes = (this.deviationBytes * targetBitrateBps) / this.targetBitrateBps;
        }
        super.setTargets(targetBitrateBps, targetFps);
    }

    @Override // org.webrtc.BaseBitrateAdjuster, org.webrtc.BitrateAdjuster
    public void reportEncodedFrame(int size) {
        if (this.targetFps == 0) {
            return;
        }
        double expectedBytesPerFrame = (this.targetBitrateBps / BITS_PER_BYTE) / this.targetFps;
        this.deviationBytes += size - expectedBytesPerFrame;
        this.timeSinceLastAdjustmentMs += 1000.0d / this.targetFps;
        double deviationThresholdBytes = this.targetBitrateBps / BITS_PER_BYTE;
        double deviationCap = BITRATE_ADJUSTMENT_SEC * deviationThresholdBytes;
        this.deviationBytes = Math.min(this.deviationBytes, deviationCap);
        this.deviationBytes = Math.max(this.deviationBytes, -deviationCap);
        if (this.timeSinceLastAdjustmentMs <= 3000.0d) {
            return;
        }
        if (this.deviationBytes > deviationThresholdBytes) {
            int bitrateAdjustmentInc = (int) ((this.deviationBytes / deviationThresholdBytes) + 0.5d);
            this.bitrateAdjustmentScaleExp -= bitrateAdjustmentInc;
            this.bitrateAdjustmentScaleExp = Math.max(this.bitrateAdjustmentScaleExp, -20);
            this.deviationBytes = deviationThresholdBytes;
        } else if (this.deviationBytes < (-deviationThresholdBytes)) {
            int bitrateAdjustmentInc2 = (int) (((-this.deviationBytes) / deviationThresholdBytes) + 0.5d);
            this.bitrateAdjustmentScaleExp += bitrateAdjustmentInc2;
            this.bitrateAdjustmentScaleExp = Math.min(this.bitrateAdjustmentScaleExp, BITRATE_ADJUSTMENT_STEPS);
            this.deviationBytes = -deviationThresholdBytes;
        }
        this.timeSinceLastAdjustmentMs = 0.0d;
    }

    private double getBitrateAdjustmentScale() {
        return Math.pow(BITRATE_ADJUSTMENT_MAX_SCALE, this.bitrateAdjustmentScaleExp / 20.0d);
    }

    @Override // org.webrtc.BaseBitrateAdjuster, org.webrtc.BitrateAdjuster
    public int getAdjustedBitrateBps() {
        return (int) (this.targetBitrateBps * getBitrateAdjustmentScale());
    }
}

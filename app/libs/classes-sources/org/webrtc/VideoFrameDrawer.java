package org.webrtc;

import android.graphics.Matrix;
import android.graphics.Point;
import android.opengl.GLES20;
import android.support.annotation.Nullable;
import java.nio.ByteBuffer;
import org.webrtc.RendererCommon;
import org.webrtc.VideoFrame;

/* loaded from: classes.jar:org/webrtc/VideoFrameDrawer.class */
public class VideoFrameDrawer {
    public static final String TAG = "VideoFrameDrawer";
    static final float[] srcPoints = {0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f};
    private int renderWidth;
    private int renderHeight;

    @Nullable
    private VideoFrame lastI420Frame;
    private final float[] dstPoints = new float[6];
    private final Point renderSize = new Point();
    private final YuvUploader yuvUploader = new YuvUploader();
    private final Matrix renderMatrix = new Matrix();

    public static void drawTexture(RendererCommon.GlDrawer drawer, VideoFrame.TextureBuffer buffer, Matrix renderMatrix, int frameWidth, int frameHeight, int viewportX, int viewportY, int viewportWidth, int viewportHeight) {
        Matrix finalMatrix = new Matrix(buffer.getTransformMatrix());
        finalMatrix.preConcat(renderMatrix);
        float[] finalGlMatrix = RendererCommon.convertMatrixFromAndroidGraphicsMatrix(finalMatrix);
        switch (buffer.getType()) {
            case OES:
                drawer.drawOes(buffer.getTextureId(), finalGlMatrix, frameWidth, frameHeight, viewportX, viewportY, viewportWidth, viewportHeight);
                return;
            case RGB:
                drawer.drawRgb(buffer.getTextureId(), finalGlMatrix, frameWidth, frameHeight, viewportX, viewportY, viewportWidth, viewportHeight);
                return;
            default:
                throw new RuntimeException("Unknown texture type.");
        }
    }

    /* loaded from: classes.jar:org/webrtc/VideoFrameDrawer$YuvUploader.class */
    private static class YuvUploader {

        @Nullable
        private ByteBuffer copyBuffer;

        @Nullable
        private int[] yuvTextures;

        private YuvUploader() {
        }

        @Nullable
        public int[] uploadYuvData(int width, int height, int[] strides, ByteBuffer[] planes) {
            ByteBuffer byteBuffer;
            int[] planeWidths = {width, width / 2, width / 2};
            int[] planeHeights = {height, height / 2, height / 2};
            int copyCapacityNeeded = 0;
            for (int i = 0; i < 3; i++) {
                if (strides[i] > planeWidths[i]) {
                    copyCapacityNeeded = Math.max(copyCapacityNeeded, planeWidths[i] * planeHeights[i]);
                }
            }
            if (copyCapacityNeeded > 0 && (this.copyBuffer == null || this.copyBuffer.capacity() < copyCapacityNeeded)) {
                this.copyBuffer = ByteBuffer.allocateDirect(copyCapacityNeeded);
            }
            if (this.yuvTextures == null) {
                this.yuvTextures = new int[3];
                for (int i2 = 0; i2 < 3; i2++) {
                    this.yuvTextures[i2] = GlUtil.generateTexture(3553);
                }
            }
            for (int i3 = 0; i3 < 3; i3++) {
                GLES20.glActiveTexture(33984 + i3);
                GLES20.glBindTexture(3553, this.yuvTextures[i3]);
                if (strides[i3] == planeWidths[i3]) {
                    byteBuffer = planes[i3];
                } else {
                    YuvHelper.copyPlane(planes[i3], strides[i3], this.copyBuffer, planeWidths[i3], planeWidths[i3], planeHeights[i3]);
                    byteBuffer = this.copyBuffer;
                }
                ByteBuffer packedByteBuffer = byteBuffer;
                GLES20.glTexImage2D(3553, 0, 6409, planeWidths[i3], planeHeights[i3], 0, 6409, 5121, packedByteBuffer);
            }
            return this.yuvTextures;
        }

        @Nullable
        public int[] uploadFromBuffer(VideoFrame.I420Buffer buffer) {
            int[] strides = {buffer.getStrideY(), buffer.getStrideU(), buffer.getStrideV()};
            ByteBuffer[] planes = {buffer.getDataY(), buffer.getDataU(), buffer.getDataV()};
            return uploadYuvData(buffer.getWidth(), buffer.getHeight(), strides, planes);
        }

        @Nullable
        public int[] getYuvTextures() {
            return this.yuvTextures;
        }

        public void release() {
            this.copyBuffer = null;
            if (this.yuvTextures != null) {
                GLES20.glDeleteTextures(3, this.yuvTextures, 0);
                this.yuvTextures = null;
            }
        }
    }

    private static int distance(float x0, float y0, float x1, float y1) {
        return (int) Math.round(Math.hypot(x1 - x0, y1 - y0));
    }

    private void calculateTransformedRenderSize(int frameWidth, int frameHeight, @Nullable Matrix renderMatrix) {
        if (renderMatrix == null) {
            this.renderWidth = frameWidth;
            this.renderHeight = frameHeight;
            return;
        }
        renderMatrix.mapPoints(this.dstPoints, srcPoints);
        for (int i = 0; i < 3; i++) {
            float[] fArr = this.dstPoints;
            int i2 = (i * 2) + 0;
            fArr[i2] = fArr[i2] * frameWidth;
            float[] fArr2 = this.dstPoints;
            int i3 = (i * 2) + 1;
            fArr2[i3] = fArr2[i3] * frameHeight;
        }
        this.renderWidth = distance(this.dstPoints[0], this.dstPoints[1], this.dstPoints[2], this.dstPoints[3]);
        this.renderHeight = distance(this.dstPoints[0], this.dstPoints[1], this.dstPoints[4], this.dstPoints[5]);
    }

    public void drawFrame(VideoFrame frame, RendererCommon.GlDrawer drawer) {
        drawFrame(frame, drawer, null);
    }

    public void drawFrame(VideoFrame frame, RendererCommon.GlDrawer drawer, Matrix additionalRenderMatrix) {
        drawFrame(frame, drawer, additionalRenderMatrix, 0, 0, frame.getRotatedWidth(), frame.getRotatedHeight());
    }

    public void drawFrame(VideoFrame frame, RendererCommon.GlDrawer drawer, @Nullable Matrix additionalRenderMatrix, int viewportX, int viewportY, int viewportWidth, int viewportHeight) {
        int width = frame.getRotatedWidth();
        int height = frame.getRotatedHeight();
        calculateTransformedRenderSize(width, height, additionalRenderMatrix);
        if (this.renderWidth <= 0 || this.renderHeight <= 0) {
            Logging.m3w(TAG, "Illegal frame size: " + this.renderWidth + "x" + this.renderHeight);
            return;
        }
        boolean isTextureFrame = frame.getBuffer() instanceof VideoFrame.TextureBuffer;
        this.renderMatrix.reset();
        this.renderMatrix.preTranslate(0.5f, 0.5f);
        if (!isTextureFrame) {
            this.renderMatrix.preScale(1.0f, -1.0f);
        }
        this.renderMatrix.preRotate(frame.getRotation());
        this.renderMatrix.preTranslate(-0.5f, -0.5f);
        if (additionalRenderMatrix != null) {
            this.renderMatrix.preConcat(additionalRenderMatrix);
        }
        if (isTextureFrame) {
            this.lastI420Frame = null;
            drawTexture(drawer, (VideoFrame.TextureBuffer) frame.getBuffer(), this.renderMatrix, this.renderWidth, this.renderHeight, viewportX, viewportY, viewportWidth, viewportHeight);
            return;
        }
        if (frame != this.lastI420Frame) {
            this.lastI420Frame = frame;
            VideoFrame.I420Buffer i420Buffer = frame.getBuffer().toI420();
            this.yuvUploader.uploadFromBuffer(i420Buffer);
            i420Buffer.release();
        }
        drawer.drawYuv(this.yuvUploader.getYuvTextures(), RendererCommon.convertMatrixFromAndroidGraphicsMatrix(this.renderMatrix), this.renderWidth, this.renderHeight, viewportX, viewportY, viewportWidth, viewportHeight);
    }

    public VideoFrame.Buffer prepareBufferForViewportSize(VideoFrame.Buffer buffer, int width, int height) {
        buffer.retain();
        return buffer;
    }

    public void release() {
        this.yuvUploader.release();
        this.lastI420Frame = null;
    }
}

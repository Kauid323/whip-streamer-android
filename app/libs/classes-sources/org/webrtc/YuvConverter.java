package org.webrtc;

import android.graphics.Matrix;
import android.opengl.GLES20;
import java.nio.ByteBuffer;
import org.webrtc.GlGenericDrawer;
import org.webrtc.ThreadUtils;
import org.webrtc.VideoFrame;

/* loaded from: classes.jar:org/webrtc/YuvConverter.class */
public class YuvConverter {
    private static final String FRAGMENT_SHADER = "uniform vec2 xUnit;\nuniform vec4 coeffs;\n\nvoid main() {\n  gl_FragColor.r = coeffs.a + dot(coeffs.rgb,\n      sample(tc - 1.5 * xUnit).rgb);\n  gl_FragColor.g = coeffs.a + dot(coeffs.rgb,\n      sample(tc - 0.5 * xUnit).rgb);\n  gl_FragColor.b = coeffs.a + dot(coeffs.rgb,\n      sample(tc + 0.5 * xUnit).rgb);\n  gl_FragColor.a = coeffs.a + dot(coeffs.rgb,\n      sample(tc + 1.5 * xUnit).rgb);\n}\n";
    private final ThreadUtils.ThreadChecker threadChecker;
    private final GlTextureFrameBuffer i420TextureFrameBuffer;
    private final ShaderCallbacks shaderCallbacks;
    private final GlGenericDrawer drawer;
    private final VideoFrameDrawer videoFrameDrawer;

    /* loaded from: classes.jar:org/webrtc/YuvConverter$ShaderCallbacks.class */
    private static class ShaderCallbacks implements GlGenericDrawer.ShaderCallbacks {
        private static final float[] yCoeffs = {0.256788f, 0.504129f, 0.0979059f, 0.0627451f};
        private static final float[] uCoeffs = {-0.148223f, -0.290993f, 0.439216f, 0.501961f};
        private static final float[] vCoeffs = {0.439216f, -0.367788f, -0.0714274f, 0.501961f};
        private int xUnitLoc;
        private int coeffsLoc;
        private float[] coeffs;
        private float stepSize;

        private ShaderCallbacks() {
        }

        public void setPlaneY() {
            this.coeffs = yCoeffs;
            this.stepSize = 1.0f;
        }

        public void setPlaneU() {
            this.coeffs = uCoeffs;
            this.stepSize = 2.0f;
        }

        public void setPlaneV() {
            this.coeffs = vCoeffs;
            this.stepSize = 2.0f;
        }

        @Override // org.webrtc.GlGenericDrawer.ShaderCallbacks
        public void onNewShader(GlShader shader) {
            this.xUnitLoc = shader.getUniformLocation("xUnit");
            this.coeffsLoc = shader.getUniformLocation("coeffs");
        }

        @Override // org.webrtc.GlGenericDrawer.ShaderCallbacks
        public void onPrepareShader(GlShader shader, float[] texMatrix, int frameWidth, int frameHeight, int viewportWidth, int viewportHeight) {
            GLES20.glUniform4fv(this.coeffsLoc, 1, this.coeffs, 0);
            GLES20.glUniform2f(this.xUnitLoc, (this.stepSize * texMatrix[0]) / frameWidth, (this.stepSize * texMatrix[1]) / frameWidth);
        }
    }

    public YuvConverter() {
        this(new VideoFrameDrawer());
    }

    public YuvConverter(VideoFrameDrawer videoFrameDrawer) {
        this.threadChecker = new ThreadUtils.ThreadChecker();
        this.i420TextureFrameBuffer = new GlTextureFrameBuffer(6408);
        this.shaderCallbacks = new ShaderCallbacks();
        this.drawer = new GlGenericDrawer(FRAGMENT_SHADER, this.shaderCallbacks);
        this.videoFrameDrawer = videoFrameDrawer;
        this.threadChecker.detachThread();
    }

    public VideoFrame.I420Buffer convert(VideoFrame.TextureBuffer inputTextureBuffer) {
        this.threadChecker.checkIsOnValidThread();
        VideoFrame.TextureBuffer preparedBuffer = (VideoFrame.TextureBuffer) this.videoFrameDrawer.prepareBufferForViewportSize(inputTextureBuffer, inputTextureBuffer.getWidth(), inputTextureBuffer.getHeight());
        int frameWidth = preparedBuffer.getWidth();
        int frameHeight = preparedBuffer.getHeight();
        int stride = ((frameWidth + 7) / 8) * 8;
        int uvHeight = (frameHeight + 1) / 2;
        int totalHeight = frameHeight + uvHeight;
        ByteBuffer i420ByteBuffer = JniCommon.nativeAllocateByteBuffer(stride * totalHeight);
        int viewportWidth = stride / 4;
        Matrix renderMatrix = new Matrix();
        renderMatrix.preTranslate(0.5f, 0.5f);
        renderMatrix.preScale(1.0f, -1.0f);
        renderMatrix.preTranslate(-0.5f, -0.5f);
        this.i420TextureFrameBuffer.setSize(viewportWidth, totalHeight);
        GLES20.glBindFramebuffer(36160, this.i420TextureFrameBuffer.getFrameBufferId());
        GlUtil.checkNoGLES2Error("glBindFramebuffer");
        this.shaderCallbacks.setPlaneY();
        VideoFrameDrawer.drawTexture(this.drawer, preparedBuffer, renderMatrix, frameWidth, frameHeight, 0, 0, viewportWidth, frameHeight);
        this.shaderCallbacks.setPlaneU();
        VideoFrameDrawer.drawTexture(this.drawer, preparedBuffer, renderMatrix, frameWidth, frameHeight, 0, frameHeight, viewportWidth / 2, uvHeight);
        this.shaderCallbacks.setPlaneV();
        VideoFrameDrawer.drawTexture(this.drawer, preparedBuffer, renderMatrix, frameWidth, frameHeight, viewportWidth / 2, frameHeight, viewportWidth / 2, uvHeight);
        GLES20.glReadPixels(0, 0, this.i420TextureFrameBuffer.getWidth(), this.i420TextureFrameBuffer.getHeight(), 6408, 5121, i420ByteBuffer);
        GlUtil.checkNoGLES2Error("YuvConverter.convert");
        GLES20.glBindFramebuffer(36160, 0);
        int uPos = 0 + (stride * frameHeight);
        int vPos = uPos + (stride / 2);
        i420ByteBuffer.position(0);
        i420ByteBuffer.limit(0 + (stride * frameHeight));
        ByteBuffer dataY = i420ByteBuffer.slice();
        i420ByteBuffer.position(uPos);
        int uvSize = (stride * (uvHeight - 1)) + (stride / 2);
        i420ByteBuffer.limit(uPos + uvSize);
        ByteBuffer dataU = i420ByteBuffer.slice();
        i420ByteBuffer.position(vPos);
        i420ByteBuffer.limit(vPos + uvSize);
        ByteBuffer dataV = i420ByteBuffer.slice();
        preparedBuffer.release();
        return JavaI420Buffer.wrap(frameWidth, frameHeight, dataY, stride, dataU, stride, dataV, stride, () -> {
            JniCommon.nativeFreeByteBuffer(i420ByteBuffer);
        });
    }

    public void release() {
        this.threadChecker.checkIsOnValidThread();
        this.drawer.release();
        this.i420TextureFrameBuffer.release();
        this.videoFrameDrawer.release();
        this.threadChecker.detachThread();
    }
}

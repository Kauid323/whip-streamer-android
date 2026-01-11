package org.webrtc;

import android.graphics.Point;
import android.opengl.Matrix;
import android.view.View;

/* loaded from: classes.jar:org/webrtc/RendererCommon.class */
public class RendererCommon {
    private static float BALANCED_VISIBLE_FRACTION = 0.5625f;

    /* loaded from: classes.jar:org/webrtc/RendererCommon$GlDrawer.class */
    public interface GlDrawer {
        void drawOes(int i, float[] fArr, int i2, int i3, int i4, int i5, int i6, int i7);

        void drawRgb(int i, float[] fArr, int i2, int i3, int i4, int i5, int i6, int i7);

        void drawYuv(int[] iArr, float[] fArr, int i, int i2, int i3, int i4, int i5, int i6);

        void release();
    }

    /* loaded from: classes.jar:org/webrtc/RendererCommon$RendererEvents.class */
    public interface RendererEvents {
        void onFirstFrameRendered();

        void onFrameResolutionChanged(int i, int i2, int i3);
    }

    /* loaded from: classes.jar:org/webrtc/RendererCommon$ScalingType.class */
    public enum ScalingType {
        SCALE_ASPECT_FIT,
        SCALE_ASPECT_FILL,
        SCALE_ASPECT_BALANCED
    }

    /* loaded from: classes.jar:org/webrtc/RendererCommon$VideoLayoutMeasure.class */
    public static class VideoLayoutMeasure {
        private float visibleFractionMatchOrientation = RendererCommon.convertScalingTypeToVisibleFraction(ScalingType.SCALE_ASPECT_BALANCED);
        private float visibleFractionMismatchOrientation = RendererCommon.convertScalingTypeToVisibleFraction(ScalingType.SCALE_ASPECT_BALANCED);

        public void setScalingType(ScalingType scalingType) {
            setScalingType(scalingType, scalingType);
        }

        public void setScalingType(ScalingType scalingTypeMatchOrientation, ScalingType scalingTypeMismatchOrientation) {
            this.visibleFractionMatchOrientation = RendererCommon.convertScalingTypeToVisibleFraction(scalingTypeMatchOrientation);
            this.visibleFractionMismatchOrientation = RendererCommon.convertScalingTypeToVisibleFraction(scalingTypeMismatchOrientation);
        }

        public void setVisibleFraction(float visibleFractionMatchOrientation, float visibleFractionMismatchOrientation) {
            this.visibleFractionMatchOrientation = visibleFractionMatchOrientation;
            this.visibleFractionMismatchOrientation = visibleFractionMismatchOrientation;
        }

        public Point measure(int widthSpec, int heightSpec, int frameWidth, int frameHeight) {
            float f;
            int maxWidth = View.getDefaultSize(Integer.MAX_VALUE, widthSpec);
            int maxHeight = View.getDefaultSize(Integer.MAX_VALUE, heightSpec);
            if (frameWidth == 0 || frameHeight == 0 || maxWidth == 0 || maxHeight == 0) {
                return new Point(maxWidth, maxHeight);
            }
            float frameAspect = frameWidth / frameHeight;
            float displayAspect = maxWidth / maxHeight;
            if ((frameAspect > 1.0f) == (displayAspect > 1.0f)) {
                f = this.visibleFractionMatchOrientation;
            } else {
                f = this.visibleFractionMismatchOrientation;
            }
            float visibleFraction = f;
            Point layoutSize = RendererCommon.getDisplaySize(visibleFraction, frameAspect, maxWidth, maxHeight);
            if (View.MeasureSpec.getMode(widthSpec) == 1073741824) {
                layoutSize.x = maxWidth;
            }
            if (View.MeasureSpec.getMode(heightSpec) == 1073741824) {
                layoutSize.y = maxHeight;
            }
            return layoutSize;
        }
    }

    public static float[] getLayoutMatrix(boolean mirror, float videoAspectRatio, float displayAspectRatio) {
        float scaleX = 1.0f;
        float scaleY = 1.0f;
        if (displayAspectRatio > videoAspectRatio) {
            scaleY = videoAspectRatio / displayAspectRatio;
        } else {
            scaleX = displayAspectRatio / videoAspectRatio;
        }
        if (mirror) {
            scaleX *= -1.0f;
        }
        float[] matrix = new float[16];
        Matrix.setIdentityM(matrix, 0);
        Matrix.scaleM(matrix, 0, scaleX, scaleY, 1.0f);
        adjustOrigin(matrix);
        return matrix;
    }

    public static android.graphics.Matrix convertMatrixToAndroidGraphicsMatrix(float[] matrix4x4) {
        float[] values = {matrix4x4[0], matrix4x4[4], matrix4x4[12], matrix4x4[1], matrix4x4[5], matrix4x4[13], matrix4x4[3], matrix4x4[7], matrix4x4[15]};
        android.graphics.Matrix matrix = new android.graphics.Matrix();
        matrix.setValues(values);
        return matrix;
    }

    public static float[] convertMatrixFromAndroidGraphicsMatrix(android.graphics.Matrix matrix) {
        float[] values = new float[9];
        matrix.getValues(values);
        float[] matrix4x4 = {values[0], values[3], 0.0f, values[6], values[1], values[4], 0.0f, values[7], 0.0f, 0.0f, 1.0f, 0.0f, values[2], values[5], 0.0f, values[8]};
        return matrix4x4;
    }

    public static Point getDisplaySize(ScalingType scalingType, float videoAspectRatio, int maxDisplayWidth, int maxDisplayHeight) {
        return getDisplaySize(convertScalingTypeToVisibleFraction(scalingType), videoAspectRatio, maxDisplayWidth, maxDisplayHeight);
    }

    private static void adjustOrigin(float[] matrix) {
        matrix[12] = matrix[12] - (0.5f * (matrix[0] + matrix[4]));
        matrix[13] = matrix[13] - (0.5f * (matrix[1] + matrix[5]));
        matrix[12] = matrix[12] + 0.5f;
        matrix[13] = matrix[13] + 0.5f;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static float convertScalingTypeToVisibleFraction(ScalingType scalingType) {
        switch (scalingType) {
            case SCALE_ASPECT_FIT:
                return 1.0f;
            case SCALE_ASPECT_FILL:
                return 0.0f;
            case SCALE_ASPECT_BALANCED:
                return BALANCED_VISIBLE_FRACTION;
            default:
                throw new IllegalArgumentException();
        }
    }

    public static Point getDisplaySize(float minVisibleFraction, float videoAspectRatio, int maxDisplayWidth, int maxDisplayHeight) {
        if (minVisibleFraction == 0.0f || videoAspectRatio == 0.0f) {
            return new Point(maxDisplayWidth, maxDisplayHeight);
        }
        int width = Math.min(maxDisplayWidth, Math.round((maxDisplayHeight / minVisibleFraction) * videoAspectRatio));
        int height = Math.min(maxDisplayHeight, Math.round((maxDisplayWidth / minVisibleFraction) / videoAspectRatio));
        return new Point(width, height);
    }
}

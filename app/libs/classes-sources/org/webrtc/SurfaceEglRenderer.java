package org.webrtc;

import android.view.SurfaceHolder;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import org.webrtc.EglBase;
import org.webrtc.RendererCommon;

/* loaded from: classes.jar:org/webrtc/SurfaceEglRenderer.class */
public class SurfaceEglRenderer extends EglRenderer implements SurfaceHolder.Callback {
    private static final String TAG = "SurfaceEglRenderer";
    private RendererCommon.RendererEvents rendererEvents;
    private final Object layoutLock;
    private boolean isRenderingPaused;
    private boolean isFirstFrameRendered;
    private int rotatedFrameWidth;
    private int rotatedFrameHeight;
    private int frameRotation;

    public SurfaceEglRenderer(String name) {
        super(name);
        this.layoutLock = new Object();
    }

    public void init(EglBase.Context sharedContext, RendererCommon.RendererEvents rendererEvents, int[] configAttributes, RendererCommon.GlDrawer drawer) {
        ThreadUtils.checkIsOnMainThread();
        this.rendererEvents = rendererEvents;
        synchronized (this.layoutLock) {
            this.isFirstFrameRendered = false;
            this.rotatedFrameWidth = 0;
            this.rotatedFrameHeight = 0;
            this.frameRotation = 0;
        }
        super.init(sharedContext, configAttributes, drawer);
    }

    @Override // org.webrtc.EglRenderer
    public void init(EglBase.Context sharedContext, int[] configAttributes, RendererCommon.GlDrawer drawer) {
        init(sharedContext, (RendererCommon.RendererEvents) null, configAttributes, drawer);
    }

    @Override // org.webrtc.EglRenderer
    public void setFpsReduction(float fps) {
        synchronized (this.layoutLock) {
            this.isRenderingPaused = fps == 0.0f;
        }
        super.setFpsReduction(fps);
    }

    @Override // org.webrtc.EglRenderer
    public void disableFpsReduction() {
        synchronized (this.layoutLock) {
            this.isRenderingPaused = false;
        }
        super.disableFpsReduction();
    }

    @Override // org.webrtc.EglRenderer
    public void pauseVideo() {
        synchronized (this.layoutLock) {
            this.isRenderingPaused = true;
        }
        super.pauseVideo();
    }

    @Override // org.webrtc.EglRenderer, org.webrtc.VideoSink
    public void onFrame(VideoFrame frame) {
        updateFrameDimensionsAndReportEvents(frame);
        super.onFrame(frame);
    }

    @Override // android.view.SurfaceHolder.Callback
    public void surfaceCreated(SurfaceHolder holder) {
        ThreadUtils.checkIsOnMainThread();
        createEglSurface(holder.getSurface());
    }

    @Override // android.view.SurfaceHolder.Callback
    public void surfaceDestroyed(SurfaceHolder holder) {
        ThreadUtils.checkIsOnMainThread();
        CountDownLatch completionLatch = new CountDownLatch(1);
        Objects.requireNonNull(completionLatch);
        releaseEglSurface(completionLatch::countDown);
        ThreadUtils.awaitUninterruptibly(completionLatch);
    }

    @Override // android.view.SurfaceHolder.Callback
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        ThreadUtils.checkIsOnMainThread();
        logD("surfaceChanged: format: " + format + " size: " + width + "x" + height);
    }

    private void updateFrameDimensionsAndReportEvents(VideoFrame frame) {
        synchronized (this.layoutLock) {
            if (this.isRenderingPaused) {
                return;
            }
            if (!this.isFirstFrameRendered) {
                this.isFirstFrameRendered = true;
                logD("Reporting first rendered frame.");
                if (this.rendererEvents != null) {
                    this.rendererEvents.onFirstFrameRendered();
                }
            }
            if (this.rotatedFrameWidth != frame.getRotatedWidth() || this.rotatedFrameHeight != frame.getRotatedHeight() || this.frameRotation != frame.getRotation()) {
                logD("Reporting frame resolution changed to " + frame.getBuffer().getWidth() + "x" + frame.getBuffer().getHeight() + " with rotation " + frame.getRotation());
                if (this.rendererEvents != null) {
                    this.rendererEvents.onFrameResolutionChanged(frame.getBuffer().getWidth(), frame.getBuffer().getHeight(), frame.getRotation());
                }
                this.rotatedFrameWidth = frame.getRotatedWidth();
                this.rotatedFrameHeight = frame.getRotatedHeight();
                this.frameRotation = frame.getRotation();
            }
        }
    }

    private void logD(String string) {
        Logging.m1d(TAG, this.name + ": " + string);
    }
}

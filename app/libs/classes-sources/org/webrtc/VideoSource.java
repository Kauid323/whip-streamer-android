package org.webrtc;

import android.support.annotation.Nullable;
import org.webrtc.VideoProcessor;

/* loaded from: classes.jar:org/webrtc/VideoSource.class */
public class VideoSource extends MediaSource {
    private final NativeAndroidVideoTrackSource nativeAndroidVideoTrackSource;
    private final Object videoProcessorLock;

    @Nullable
    private VideoProcessor videoProcessor;
    private boolean isCapturerRunning;
    private final CapturerObserver capturerObserver;

    /* loaded from: classes.jar:org/webrtc/VideoSource$AspectRatio.class */
    public static class AspectRatio {
        public static final AspectRatio UNDEFINED = new AspectRatio(0, 0);
        public final int width;
        public final int height;

        public AspectRatio(int width, int height) {
            this.width = width;
            this.height = height;
        }
    }

    public VideoSource(long nativeSource) {
        super(nativeSource);
        this.videoProcessorLock = new Object();
        this.capturerObserver = new CapturerObserver() { // from class: org.webrtc.VideoSource.1
            @Override // org.webrtc.CapturerObserver
            public void onCapturerStarted(boolean success) {
                VideoSource.this.nativeAndroidVideoTrackSource.setState(success);
                synchronized (VideoSource.this.videoProcessorLock) {
                    VideoSource.this.isCapturerRunning = success;
                    if (VideoSource.this.videoProcessor != null) {
                        VideoSource.this.videoProcessor.onCapturerStarted(success);
                    }
                }
            }

            @Override // org.webrtc.CapturerObserver
            public void onCapturerStopped() {
                VideoSource.this.nativeAndroidVideoTrackSource.setState(false);
                synchronized (VideoSource.this.videoProcessorLock) {
                    VideoSource.this.isCapturerRunning = false;
                    if (VideoSource.this.videoProcessor != null) {
                        VideoSource.this.videoProcessor.onCapturerStopped();
                    }
                }
            }

            @Override // org.webrtc.CapturerObserver
            public void onFrameCaptured(VideoFrame frame) {
                VideoProcessor.FrameAdaptationParameters parameters = VideoSource.this.nativeAndroidVideoTrackSource.adaptFrame(frame);
                synchronized (VideoSource.this.videoProcessorLock) {
                    if (VideoSource.this.videoProcessor != null) {
                        VideoSource.this.videoProcessor.onFrameCaptured(frame, parameters);
                        return;
                    }
                    VideoFrame adaptedFrame = VideoProcessor.applyFrameAdaptationParameters(frame, parameters);
                    if (adaptedFrame != null) {
                        VideoSource.this.nativeAndroidVideoTrackSource.onFrameCaptured(adaptedFrame);
                        adaptedFrame.release();
                    }
                }
            }
        };
        this.nativeAndroidVideoTrackSource = new NativeAndroidVideoTrackSource(nativeSource);
    }

    public void adaptOutputFormat(int width, int height, int fps) {
        int maxSide = Math.max(width, height);
        int minSide = Math.min(width, height);
        adaptOutputFormat(maxSide, minSide, minSide, maxSide, fps);
    }

    public void adaptOutputFormat(int landscapeWidth, int landscapeHeight, int portraitWidth, int portraitHeight, int fps) {
        adaptOutputFormat(new AspectRatio(landscapeWidth, landscapeHeight), Integer.valueOf(landscapeWidth * landscapeHeight), new AspectRatio(portraitWidth, portraitHeight), Integer.valueOf(portraitWidth * portraitHeight), Integer.valueOf(fps));
    }

    public void adaptOutputFormat(AspectRatio targetLandscapeAspectRatio, @Nullable Integer maxLandscapePixelCount, AspectRatio targetPortraitAspectRatio, @Nullable Integer maxPortraitPixelCount, @Nullable Integer maxFps) {
        this.nativeAndroidVideoTrackSource.adaptOutputFormat(targetLandscapeAspectRatio, maxLandscapePixelCount, targetPortraitAspectRatio, maxPortraitPixelCount, maxFps);
    }

    public void setIsScreencast(boolean isScreencast) {
        this.nativeAndroidVideoTrackSource.setIsScreencast(isScreencast);
    }

    public void setVideoProcessor(@Nullable VideoProcessor newVideoProcessor) {
        synchronized (this.videoProcessorLock) {
            if (this.videoProcessor != null) {
                this.videoProcessor.setSink(null);
                if (this.isCapturerRunning) {
                    this.videoProcessor.onCapturerStopped();
                }
            }
            this.videoProcessor = newVideoProcessor;
            if (newVideoProcessor != null) {
                newVideoProcessor.setSink(frame -> {
                    runWithReference(() -> {
                        this.nativeAndroidVideoTrackSource.onFrameCaptured(frame);
                    });
                });
                if (this.isCapturerRunning) {
                    newVideoProcessor.onCapturerStarted(true);
                }
            }
        }
    }

    public CapturerObserver getCapturerObserver() {
        return this.capturerObserver;
    }

    long getNativeVideoTrackSource() {
        return getNativeMediaSource();
    }

    @Override // org.webrtc.MediaSource
    public void dispose() {
        setVideoProcessor(null);
        super.dispose();
    }
}

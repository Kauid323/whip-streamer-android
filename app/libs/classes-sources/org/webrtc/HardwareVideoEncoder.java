package org.webrtc;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.opengl.GLES20;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.Surface;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import org.webrtc.EglBase14;
import org.webrtc.EncodedImage;
import org.webrtc.ThreadUtils;
import org.webrtc.VideoEncoder;
import org.webrtc.VideoFrame;

@TargetApi(19)
/* loaded from: classes.jar:org/webrtc/HardwareVideoEncoder.class */
class HardwareVideoEncoder implements VideoEncoder {
    private static final String TAG = "HardwareVideoEncoder";
    private static final int VIDEO_ControlRateConstant = 2;
    private static final String KEY_BITRATE_MODE = "bitrate-mode";
    private static final int VIDEO_AVC_PROFILE_HIGH = 8;
    private static final int VIDEO_AVC_LEVEL_3 = 256;
    private static final int MAX_VIDEO_FRAMERATE = 30;
    private static final int MAX_ENCODER_Q_SIZE = 2;
    private static final int MEDIA_CODEC_RELEASE_TIMEOUT_MS = 5000;
    private static final int DEQUEUE_OUTPUT_BUFFER_TIMEOUT_US = 100000;
    private final MediaCodecWrapperFactory mediaCodecWrapperFactory;
    private final String codecName;
    private final VideoCodecMimeType codecType;
    private final Integer surfaceColorFormat;
    private final Integer yuvColorFormat;
    private final YuvFormat yuvFormat;
    private final Map<String, String> params;
    private final int keyFrameIntervalSec;
    private final long forcedKeyFrameNs;
    private final BitrateAdjuster bitrateAdjuster;
    private final EglBase14.Context sharedContext;
    private final GlRectDrawer textureDrawer = new GlRectDrawer();
    private final VideoFrameDrawer videoFrameDrawer = new VideoFrameDrawer();
    private final BlockingDeque<EncodedImage.Builder> outputBuilders = new LinkedBlockingDeque();
    private final ThreadUtils.ThreadChecker encodeThreadChecker = new ThreadUtils.ThreadChecker();
    private final ThreadUtils.ThreadChecker outputThreadChecker = new ThreadUtils.ThreadChecker();
    private final BusyCount outputBuffersBusyCount = new BusyCount();
    private VideoEncoder.Callback callback;
    private boolean automaticResizeOn;

    @Nullable
    private MediaCodecWrapper codec;

    @Nullable
    private ByteBuffer[] outputBuffers;

    @Nullable
    private Thread outputThread;

    @Nullable
    private EglBase14 textureEglBase;

    @Nullable
    private Surface textureInputSurface;
    private int width;
    private int height;
    private boolean useSurfaceMode;
    private long lastKeyFrameNs;

    @Nullable
    private ByteBuffer configBuffer;
    private int adjustedBitrate;
    private volatile boolean running;

    @Nullable
    private volatile Exception shutdownException;

    /* loaded from: classes.jar:org/webrtc/HardwareVideoEncoder$BusyCount.class */
    private static class BusyCount {
        private final Object countLock;
        private int count;

        private BusyCount() {
            this.countLock = new Object();
        }

        public void increment() {
            synchronized (this.countLock) {
                this.count++;
            }
        }

        public void decrement() {
            synchronized (this.countLock) {
                this.count--;
                if (this.count == 0) {
                    this.countLock.notifyAll();
                }
            }
        }

        public void waitForZero() {
            boolean wasInterrupted = false;
            synchronized (this.countLock) {
                while (this.count > 0) {
                    try {
                        this.countLock.wait();
                    } catch (InterruptedException e) {
                        Logging.m4e(HardwareVideoEncoder.TAG, "Interrupted while waiting on busy count", e);
                        wasInterrupted = true;
                    }
                }
            }
            if (wasInterrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public HardwareVideoEncoder(MediaCodecWrapperFactory mediaCodecWrapperFactory, String codecName, VideoCodecMimeType codecType, Integer surfaceColorFormat, Integer yuvColorFormat, Map<String, String> params, int keyFrameIntervalSec, int forceKeyFrameIntervalMs, BitrateAdjuster bitrateAdjuster, EglBase14.Context sharedContext) {
        this.mediaCodecWrapperFactory = mediaCodecWrapperFactory;
        this.codecName = codecName;
        this.codecType = codecType;
        this.surfaceColorFormat = surfaceColorFormat;
        this.yuvColorFormat = yuvColorFormat;
        this.yuvFormat = YuvFormat.valueOf(yuvColorFormat.intValue());
        this.params = params;
        this.keyFrameIntervalSec = keyFrameIntervalSec;
        this.forcedKeyFrameNs = TimeUnit.MILLISECONDS.toNanos(forceKeyFrameIntervalMs);
        this.bitrateAdjuster = bitrateAdjuster;
        this.sharedContext = sharedContext;
        this.encodeThreadChecker.detachThread();
    }

    @Override // org.webrtc.VideoEncoder
    public VideoCodecStatus initEncode(VideoEncoder.Settings settings, VideoEncoder.Callback callback) {
        this.encodeThreadChecker.checkIsOnValidThread();
        this.callback = callback;
        this.automaticResizeOn = settings.automaticResizeOn;
        this.width = settings.width;
        this.height = settings.height;
        this.useSurfaceMode = canUseSurface();
        if (settings.startBitrate != 0 && settings.maxFramerate != 0) {
            this.bitrateAdjuster.setTargets(settings.startBitrate * 1000, settings.maxFramerate);
        }
        this.adjustedBitrate = this.bitrateAdjuster.getAdjustedBitrateBps();
        Logging.m1d(TAG, "initEncode: " + this.width + " x " + this.height + ". @ " + settings.startBitrate + "kbps. Fps: " + settings.maxFramerate + " Use surface mode: " + this.useSurfaceMode);
        return initEncodeInternal();
    }

    private VideoCodecStatus initEncodeInternal() {
        String profileLevelId;
        this.encodeThreadChecker.checkIsOnValidThread();
        this.lastKeyFrameNs = -1L;
        try {
            this.codec = this.mediaCodecWrapperFactory.createByCodecName(this.codecName);
            int colorFormat = (this.useSurfaceMode ? this.surfaceColorFormat : this.yuvColorFormat).intValue();
            try {
                MediaFormat format = MediaFormat.createVideoFormat(this.codecType.mimeType(), this.width, this.height);
                format.setInteger("bitrate", this.adjustedBitrate);
                format.setInteger(KEY_BITRATE_MODE, 2);
                format.setInteger("color-format", colorFormat);
                format.setInteger("frame-rate", this.bitrateAdjuster.getCodecConfigFramerate());
                format.setInteger("i-frame-interval", this.keyFrameIntervalSec);
                if (this.codecType == VideoCodecMimeType.H264) {
                    profileLevelId = this.params.get("profile-level-id");
                    if (profileLevelId == null) {
                        profileLevelId = "42e01f";
                    }
                    switch (profileLevelId) {
                        case "640c1f":
                            format.setInteger("profile", VIDEO_AVC_PROFILE_HIGH);
                            format.setInteger("level", VIDEO_AVC_LEVEL_3);
                            break;
                        case "42e01f":
                            break;
                        default:
                            Logging.m3w(TAG, "Unknown profile level id: " + profileLevelId);
                            break;
                    }
                }
                Logging.m1d(TAG, "Format: " + format);
                this.codec.configure(format, null, null, 1);
                if (this.useSurfaceMode) {
                    this.textureEglBase = EglBase.createEgl14(this.sharedContext, EglBase.CONFIG_RECORDABLE);
                    this.textureInputSurface = this.codec.createInputSurface();
                    this.textureEglBase.createSurface(this.textureInputSurface);
                    this.textureEglBase.makeCurrent();
                }
                this.codec.start();
                this.outputBuffers = this.codec.getOutputBuffers();
                this.running = true;
                this.outputThreadChecker.detachThread();
                this.outputThread = createOutputThread();
                this.outputThread.start();
                return VideoCodecStatus.OK;
            } catch (IllegalStateException e) {
                Logging.m4e(TAG, "initEncodeInternal failed", e);
                release();
                return VideoCodecStatus.FALLBACK_SOFTWARE;
            }
        } catch (IOException | IllegalArgumentException e2) {
            Logging.m2e(TAG, "Cannot create media encoder " + this.codecName);
            return VideoCodecStatus.FALLBACK_SOFTWARE;
        }
    }

    @Override // org.webrtc.VideoEncoder
    public VideoCodecStatus release() {
        VideoCodecStatus returnValue;
        this.encodeThreadChecker.checkIsOnValidThread();
        if (this.outputThread == null) {
            returnValue = VideoCodecStatus.OK;
        } else {
            this.running = false;
            if (!ThreadUtils.joinUninterruptibly(this.outputThread, 5000L)) {
                Logging.m2e(TAG, "Media encoder release timeout");
                returnValue = VideoCodecStatus.TIMEOUT;
            } else if (this.shutdownException != null) {
                Logging.m4e(TAG, "Media encoder release exception", this.shutdownException);
                returnValue = VideoCodecStatus.ERROR;
            } else {
                returnValue = VideoCodecStatus.OK;
            }
        }
        this.textureDrawer.release();
        this.videoFrameDrawer.release();
        if (this.textureEglBase != null) {
            this.textureEglBase.release();
            this.textureEglBase = null;
        }
        if (this.textureInputSurface != null) {
            this.textureInputSurface.release();
            this.textureInputSurface = null;
        }
        this.outputBuilders.clear();
        this.codec = null;
        this.outputBuffers = null;
        this.outputThread = null;
        this.encodeThreadChecker.detachThread();
        return returnValue;
    }

    @Override // org.webrtc.VideoEncoder
    public VideoCodecStatus encode(VideoFrame videoFrame, VideoEncoder.EncodeInfo encodeInfo) {
        VideoCodecStatus status;
        VideoCodecStatus returnValue;
        this.encodeThreadChecker.checkIsOnValidThread();
        if (this.codec == null) {
            return VideoCodecStatus.UNINITIALIZED;
        }
        VideoFrame.Buffer videoFrameBuffer = videoFrame.getBuffer();
        boolean isTextureBuffer = videoFrameBuffer instanceof VideoFrame.TextureBuffer;
        int frameWidth = videoFrame.getBuffer().getWidth();
        int frameHeight = videoFrame.getBuffer().getHeight();
        boolean shouldUseSurfaceMode = canUseSurface() && isTextureBuffer;
        if ((frameWidth != this.width || frameHeight != this.height || shouldUseSurfaceMode != this.useSurfaceMode) && (status = resetCodec(frameWidth, frameHeight, shouldUseSurfaceMode)) != VideoCodecStatus.OK) {
            return status;
        }
        if (this.outputBuilders.size() > 2) {
            Logging.m2e(TAG, "Dropped frame, encoder queue full");
            return VideoCodecStatus.NO_OUTPUT;
        }
        boolean requestedKeyFrame = false;
        for (EncodedImage.FrameType frameType : encodeInfo.frameTypes) {
            if (frameType == EncodedImage.FrameType.VideoFrameKey) {
                requestedKeyFrame = true;
            }
        }
        if (requestedKeyFrame || shouldForceKeyFrame(videoFrame.getTimestampNs())) {
            requestKeyFrame(videoFrame.getTimestampNs());
        }
        int bufferSize = ((videoFrameBuffer.getHeight() * videoFrameBuffer.getWidth()) * 3) / 2;
        EncodedImage.Builder builder = EncodedImage.builder().setCaptureTimeNs(videoFrame.getTimestampNs()).setCompleteFrame(true).setEncodedWidth(videoFrame.getBuffer().getWidth()).setEncodedHeight(videoFrame.getBuffer().getHeight()).setRotation(videoFrame.getRotation());
        this.outputBuilders.offer(builder);
        if (this.useSurfaceMode) {
            returnValue = encodeTextureBuffer(videoFrame);
        } else {
            returnValue = encodeByteBuffer(videoFrame, videoFrameBuffer, bufferSize);
        }
        if (returnValue != VideoCodecStatus.OK) {
            this.outputBuilders.pollLast();
        }
        return returnValue;
    }

    private VideoCodecStatus encodeTextureBuffer(VideoFrame videoFrame) {
        this.encodeThreadChecker.checkIsOnValidThread();
        try {
            GLES20.glClear(16384);
            VideoFrame derotatedFrame = new VideoFrame(videoFrame.getBuffer(), 0, videoFrame.getTimestampNs());
            this.videoFrameDrawer.drawFrame(derotatedFrame, this.textureDrawer, null);
            this.textureEglBase.swapBuffers(videoFrame.getTimestampNs());
            return VideoCodecStatus.OK;
        } catch (RuntimeException e) {
            Logging.m4e(TAG, "encodeTexture failed", e);
            return VideoCodecStatus.ERROR;
        }
    }

    private VideoCodecStatus encodeByteBuffer(VideoFrame videoFrame, VideoFrame.Buffer videoFrameBuffer, int bufferSize) {
        this.encodeThreadChecker.checkIsOnValidThread();
        long presentationTimestampUs = (videoFrame.getTimestampNs() + 500) / 1000;
        try {
            int index = this.codec.dequeueInputBuffer(0L);
            if (index == -1) {
                Logging.m1d(TAG, "Dropped frame, no input buffers available");
                return VideoCodecStatus.NO_OUTPUT;
            }
            try {
                ByteBuffer buffer = this.codec.getInputBuffers()[index];
                fillInputBuffer(buffer, videoFrameBuffer);
                try {
                    this.codec.queueInputBuffer(index, 0, bufferSize, presentationTimestampUs, 0);
                    return VideoCodecStatus.OK;
                } catch (IllegalStateException e) {
                    Logging.m4e(TAG, "queueInputBuffer failed", e);
                    return VideoCodecStatus.ERROR;
                }
            } catch (IllegalStateException e2) {
                Logging.m4e(TAG, "getInputBuffers failed", e2);
                return VideoCodecStatus.ERROR;
            }
        } catch (IllegalStateException e3) {
            Logging.m4e(TAG, "dequeueInputBuffer failed", e3);
            return VideoCodecStatus.ERROR;
        }
    }

    @Override // org.webrtc.VideoEncoder
    public VideoCodecStatus setRateAllocation(VideoEncoder.BitrateAllocation bitrateAllocation, int framerate) {
        this.encodeThreadChecker.checkIsOnValidThread();
        if (framerate > MAX_VIDEO_FRAMERATE) {
            framerate = MAX_VIDEO_FRAMERATE;
        }
        this.bitrateAdjuster.setTargets(bitrateAllocation.getSum(), framerate);
        return VideoCodecStatus.OK;
    }

    @Override // org.webrtc.VideoEncoder
    public VideoEncoder.ScalingSettings getScalingSettings() {
        this.encodeThreadChecker.checkIsOnValidThread();
        if (this.automaticResizeOn) {
            if (this.codecType == VideoCodecMimeType.VP8) {
                return new VideoEncoder.ScalingSettings(29, 95);
            }
            if (this.codecType == VideoCodecMimeType.H264) {
                return new VideoEncoder.ScalingSettings(24, 37);
            }
        }
        return VideoEncoder.ScalingSettings.OFF;
    }

    @Override // org.webrtc.VideoEncoder
    public String getImplementationName() {
        return "HWEncoder";
    }

    private VideoCodecStatus resetCodec(int newWidth, int newHeight, boolean newUseSurfaceMode) {
        this.encodeThreadChecker.checkIsOnValidThread();
        VideoCodecStatus status = release();
        if (status != VideoCodecStatus.OK) {
            return status;
        }
        this.width = newWidth;
        this.height = newHeight;
        this.useSurfaceMode = newUseSurfaceMode;
        return initEncodeInternal();
    }

    private boolean shouldForceKeyFrame(long presentationTimestampNs) {
        this.encodeThreadChecker.checkIsOnValidThread();
        return this.forcedKeyFrameNs > 0 && presentationTimestampNs > this.lastKeyFrameNs + this.forcedKeyFrameNs;
    }

    private void requestKeyFrame(long presentationTimestampNs) {
        this.encodeThreadChecker.checkIsOnValidThread();
        try {
            Bundle b = new Bundle();
            b.putInt("request-sync", 0);
            this.codec.setParameters(b);
            this.lastKeyFrameNs = presentationTimestampNs;
        } catch (IllegalStateException e) {
            Logging.m4e(TAG, "requestKeyFrame failed", e);
        }
    }

    private Thread createOutputThread() {
        return new Thread() { // from class: org.webrtc.HardwareVideoEncoder.1
            @Override // java.lang.Thread, java.lang.Runnable
            public void run() {
                while (HardwareVideoEncoder.this.running) {
                    HardwareVideoEncoder.this.deliverEncodedImage();
                }
                HardwareVideoEncoder.this.releaseCodecOnOutputThread();
            }
        };
    }

    protected void deliverEncodedImage() {
        ByteBuffer frameBuffer;
        EncodedImage.FrameType frameType;
        this.outputThreadChecker.checkIsOnValidThread();
        try {
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            int index = this.codec.dequeueOutputBuffer(info, 100000L);
            if (index < 0) {
                if (index == -3) {
                    this.outputBuffersBusyCount.waitForZero();
                    this.outputBuffers = this.codec.getOutputBuffers();
                    return;
                }
                return;
            }
            ByteBuffer codecOutputBuffer = this.outputBuffers[index];
            codecOutputBuffer.position(info.offset);
            codecOutputBuffer.limit(info.offset + info.size);
            if ((info.flags & 2) != 0) {
                Logging.m1d(TAG, "Config frame generated. Offset: " + info.offset + ". Size: " + info.size);
                this.configBuffer = ByteBuffer.allocateDirect(info.size);
                this.configBuffer.put(codecOutputBuffer);
            } else {
                this.bitrateAdjuster.reportEncodedFrame(info.size);
                if (this.adjustedBitrate != this.bitrateAdjuster.getAdjustedBitrateBps()) {
                    updateBitrate();
                }
                boolean isKeyFrame = (info.flags & 1) != 0;
                if (isKeyFrame) {
                    Logging.m1d(TAG, "Sync frame generated");
                }
                if (isKeyFrame && this.codecType == VideoCodecMimeType.H264) {
                    Logging.m1d(TAG, "Prepending config frame of size " + this.configBuffer.capacity() + " to output buffer with offset " + info.offset + ", size " + info.size);
                    frameBuffer = ByteBuffer.allocateDirect(info.size + this.configBuffer.capacity());
                    this.configBuffer.rewind();
                    frameBuffer.put(this.configBuffer);
                    frameBuffer.put(codecOutputBuffer);
                    frameBuffer.rewind();
                } else {
                    frameBuffer = codecOutputBuffer.slice();
                }
                if (isKeyFrame) {
                    frameType = EncodedImage.FrameType.VideoFrameKey;
                } else {
                    frameType = EncodedImage.FrameType.VideoFrameDelta;
                }
                EncodedImage.FrameType frameType2 = frameType;
                this.outputBuffersBusyCount.increment();
                EncodedImage.Builder builder = this.outputBuilders.poll();
                EncodedImage encodedImage = builder.setBuffer(frameBuffer, () -> {
                    try {
                        this.codec.releaseOutputBuffer(index, false);
                    } catch (Exception e) {
                        Logging.m4e(TAG, "releaseOutputBuffer failed", e);
                    }
                    this.outputBuffersBusyCount.decrement();
                }).setFrameType(frameType2).createEncodedImage();
                this.callback.onEncodedFrame(encodedImage, new VideoEncoder.CodecSpecificInfo());
                encodedImage.release();
            }
        } catch (IllegalStateException e) {
            Logging.m4e(TAG, "deliverOutput failed", e);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void releaseCodecOnOutputThread() {
        this.outputThreadChecker.checkIsOnValidThread();
        Logging.m1d(TAG, "Releasing MediaCodec on output thread");
        this.outputBuffersBusyCount.waitForZero();
        try {
            this.codec.stop();
        } catch (Exception e) {
            Logging.m4e(TAG, "Media encoder stop failed", e);
        }
        try {
            this.codec.release();
        } catch (Exception e2) {
            Logging.m4e(TAG, "Media encoder release failed", e2);
            this.shutdownException = e2;
        }
        this.configBuffer = null;
        Logging.m1d(TAG, "Release on output thread done");
    }

    private VideoCodecStatus updateBitrate() {
        this.outputThreadChecker.checkIsOnValidThread();
        this.adjustedBitrate = this.bitrateAdjuster.getAdjustedBitrateBps();
        try {
            Bundle params = new Bundle();
            params.putInt("video-bitrate", this.adjustedBitrate);
            this.codec.setParameters(params);
            return VideoCodecStatus.OK;
        } catch (IllegalStateException e) {
            Logging.m4e(TAG, "updateBitrate failed", e);
            return VideoCodecStatus.ERROR;
        }
    }

    private boolean canUseSurface() {
        return (this.sharedContext == null || this.surfaceColorFormat == null) ? false : true;
    }

    protected void fillInputBuffer(ByteBuffer buffer, VideoFrame.Buffer videoFrameBuffer) {
        this.yuvFormat.fillBuffer(buffer, videoFrameBuffer);
    }

    /* loaded from: classes.jar:org/webrtc/HardwareVideoEncoder$YuvFormat.class */
    private enum YuvFormat {
        I420 { // from class: org.webrtc.HardwareVideoEncoder.YuvFormat.1
            @Override // org.webrtc.HardwareVideoEncoder.YuvFormat
            void fillBuffer(ByteBuffer dstBuffer, VideoFrame.Buffer srcBuffer) {
                VideoFrame.I420Buffer i420 = srcBuffer.toI420();
                YuvHelper.I420Copy(i420.getDataY(), i420.getStrideY(), i420.getDataU(), i420.getStrideU(), i420.getDataV(), i420.getStrideV(), dstBuffer, i420.getWidth(), i420.getHeight());
                i420.release();
            }
        },
        NV12 { // from class: org.webrtc.HardwareVideoEncoder.YuvFormat.2
            @Override // org.webrtc.HardwareVideoEncoder.YuvFormat
            void fillBuffer(ByteBuffer dstBuffer, VideoFrame.Buffer srcBuffer) {
                VideoFrame.I420Buffer i420 = srcBuffer.toI420();
                YuvHelper.I420ToNV12(i420.getDataY(), i420.getStrideY(), i420.getDataU(), i420.getStrideU(), i420.getDataV(), i420.getStrideV(), dstBuffer, i420.getWidth(), i420.getHeight());
                i420.release();
            }
        };

        abstract void fillBuffer(ByteBuffer byteBuffer, VideoFrame.Buffer buffer);

        static YuvFormat valueOf(int colorFormat) {
            switch (colorFormat) {
                case 19:
                    return I420;
                case 21:
                case 2141391872:
                case 2141391876:
                    return NV12;
                default:
                    throw new IllegalArgumentException("Unsupported colorFormat: " + colorFormat);
            }
        }
    }
}

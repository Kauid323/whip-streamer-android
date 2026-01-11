package org.webrtc;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.SystemClock;
import android.support.annotation.Nullable;
import android.view.Surface;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import org.webrtc.EglBase;
import org.webrtc.EncodedImage;
import org.webrtc.ThreadUtils;
import org.webrtc.VideoDecoder;
import org.webrtc.VideoFrame;

/* loaded from: classes.jar:org/webrtc/AndroidVideoDecoder.class */
class AndroidVideoDecoder implements VideoDecoder, VideoSink {
    private static final String TAG = "AndroidVideoDecoder";
    private static final String MEDIA_FORMAT_KEY_STRIDE = "stride";
    private static final String MEDIA_FORMAT_KEY_SLICE_HEIGHT = "slice-height";
    private static final String MEDIA_FORMAT_KEY_CROP_LEFT = "crop-left";
    private static final String MEDIA_FORMAT_KEY_CROP_RIGHT = "crop-right";
    private static final String MEDIA_FORMAT_KEY_CROP_TOP = "crop-top";
    private static final String MEDIA_FORMAT_KEY_CROP_BOTTOM = "crop-bottom";
    private static final int MEDIA_CODEC_RELEASE_TIMEOUT_MS = 5000;
    private static final int DEQUEUE_INPUT_TIMEOUT_US = 500000;
    private static final int DEQUEUE_OUTPUT_BUFFER_TIMEOUT_US = 100000;
    private final MediaCodecWrapperFactory mediaCodecWrapperFactory;
    private final String codecName;
    private final VideoCodecMimeType codecType;
    private final BlockingDeque<FrameInfo> frameInfos;
    private int colorFormat;

    @Nullable
    private Thread outputThread;
    private ThreadUtils.ThreadChecker outputThreadChecker;
    private ThreadUtils.ThreadChecker decoderThreadChecker;
    private volatile boolean running;

    @Nullable
    private volatile Exception shutdownException;
    private int width;
    private int height;
    private int stride;
    private int sliceHeight;
    private boolean hasDecodedFirstFrame;
    private boolean keyFrameRequired;

    @Nullable
    private final EglBase.Context sharedContext;

    @Nullable
    private SurfaceTextureHelper surfaceTextureHelper;

    @Nullable
    private Surface surface;

    @Nullable
    private DecodedTextureMetadata renderedTextureMetadata;

    @Nullable
    private VideoDecoder.Callback callback;

    @Nullable
    private MediaCodecWrapper codec;
    private final Object dimensionLock = new Object();
    private final Object renderedTextureMetadataLock = new Object();

    /* loaded from: classes.jar:org/webrtc/AndroidVideoDecoder$FrameInfo.class */
    private static class FrameInfo {
        final long decodeStartTimeMs;
        final int rotation;

        FrameInfo(long decodeStartTimeMs, int rotation) {
            this.decodeStartTimeMs = decodeStartTimeMs;
            this.rotation = rotation;
        }
    }

    /* loaded from: classes.jar:org/webrtc/AndroidVideoDecoder$DecodedTextureMetadata.class */
    private static class DecodedTextureMetadata {
        final long presentationTimestampUs;
        final Integer decodeTimeMs;

        DecodedTextureMetadata(long presentationTimestampUs, Integer decodeTimeMs) {
            this.presentationTimestampUs = presentationTimestampUs;
            this.decodeTimeMs = decodeTimeMs;
        }
    }

    AndroidVideoDecoder(MediaCodecWrapperFactory mediaCodecWrapperFactory, String codecName, VideoCodecMimeType codecType, int colorFormat, @Nullable EglBase.Context sharedContext) {
        if (!isSupportedColorFormat(colorFormat)) {
            throw new IllegalArgumentException("Unsupported color format: " + colorFormat);
        }
        Logging.m1d(TAG, "ctor name: " + codecName + " type: " + codecType + " color format: " + colorFormat + " context: " + sharedContext);
        this.mediaCodecWrapperFactory = mediaCodecWrapperFactory;
        this.codecName = codecName;
        this.codecType = codecType;
        this.colorFormat = colorFormat;
        this.sharedContext = sharedContext;
        this.frameInfos = new LinkedBlockingDeque();
    }

    @Override // org.webrtc.VideoDecoder
    public VideoCodecStatus initDecode(VideoDecoder.Settings settings, VideoDecoder.Callback callback) {
        this.decoderThreadChecker = new ThreadUtils.ThreadChecker();
        this.callback = callback;
        if (this.sharedContext != null) {
            this.surfaceTextureHelper = createSurfaceTextureHelper();
            this.surface = new Surface(this.surfaceTextureHelper.getSurfaceTexture());
            this.surfaceTextureHelper.startListening(this);
        }
        return initDecodeInternal(settings.width, settings.height);
    }

    private VideoCodecStatus initDecodeInternal(int width, int height) {
        this.decoderThreadChecker.checkIsOnValidThread();
        Logging.m1d(TAG, "initDecodeInternal name: " + this.codecName + " type: " + this.codecType + " width: " + width + " height: " + height);
        if (this.outputThread != null) {
            Logging.m2e(TAG, "initDecodeInternal called while the codec is already running");
            return VideoCodecStatus.FALLBACK_SOFTWARE;
        }
        this.width = width;
        this.height = height;
        this.stride = width;
        this.sliceHeight = height;
        this.hasDecodedFirstFrame = false;
        this.keyFrameRequired = true;
        try {
            this.codec = this.mediaCodecWrapperFactory.createByCodecName(this.codecName);
            try {
                MediaFormat format = MediaFormat.createVideoFormat(this.codecType.mimeType(), width, height);
                if (this.sharedContext == null) {
                    format.setInteger("color-format", this.colorFormat);
                }
                this.codec.configure(format, this.surface, null, 0);
                this.codec.start();
                this.running = true;
                this.outputThread = createOutputThread();
                this.outputThread.start();
                Logging.m1d(TAG, "initDecodeInternal done");
                return VideoCodecStatus.OK;
            } catch (IllegalArgumentException | IllegalStateException e) {
                Logging.m4e(TAG, "initDecode failed", e);
                release();
                return VideoCodecStatus.FALLBACK_SOFTWARE;
            }
        } catch (IOException | IllegalArgumentException | IllegalStateException e2) {
            Logging.m2e(TAG, "Cannot create media decoder " + this.codecName);
            return VideoCodecStatus.FALLBACK_SOFTWARE;
        }
    }

    @Override // org.webrtc.VideoDecoder
    public VideoCodecStatus decode(EncodedImage frame, VideoDecoder.DecodeInfo info) {
        int width;
        int height;
        VideoCodecStatus status;
        this.decoderThreadChecker.checkIsOnValidThread();
        if (this.codec == null || this.callback == null) {
            Logging.m1d(TAG, "decode uninitalized, codec: " + (this.codec != null) + ", callback: " + this.callback);
            return VideoCodecStatus.UNINITIALIZED;
        }
        if (frame.buffer == null) {
            Logging.m2e(TAG, "decode() - no input data");
            return VideoCodecStatus.ERR_PARAMETER;
        }
        int size = frame.buffer.remaining();
        if (size == 0) {
            Logging.m2e(TAG, "decode() - input buffer empty");
            return VideoCodecStatus.ERR_PARAMETER;
        }
        synchronized (this.dimensionLock) {
            width = this.width;
            height = this.height;
        }
        if (frame.encodedWidth * frame.encodedHeight > 0 && ((frame.encodedWidth != width || frame.encodedHeight != height) && (status = reinitDecode(frame.encodedWidth, frame.encodedHeight)) != VideoCodecStatus.OK)) {
            return status;
        }
        if (this.keyFrameRequired) {
            if (frame.frameType != EncodedImage.FrameType.VideoFrameKey) {
                Logging.m2e(TAG, "decode() - key frame required first");
                return VideoCodecStatus.NO_OUTPUT;
            }
            if (!frame.completeFrame) {
                Logging.m2e(TAG, "decode() - complete frame required first");
                return VideoCodecStatus.NO_OUTPUT;
            }
        }
        try {
            int index = this.codec.dequeueInputBuffer(500000L);
            if (index < 0) {
                Logging.m2e(TAG, "decode() - no HW buffers available; decoder falling behind");
                return VideoCodecStatus.ERROR;
            }
            try {
                ByteBuffer buffer = this.codec.getInputBuffers()[index];
                if (buffer.capacity() < size) {
                    Logging.m2e(TAG, "decode() - HW buffer too small");
                    return VideoCodecStatus.ERROR;
                }
                buffer.put(frame.buffer);
                this.frameInfos.offer(new FrameInfo(SystemClock.elapsedRealtime(), frame.rotation));
                try {
                    this.codec.queueInputBuffer(index, 0, size, TimeUnit.NANOSECONDS.toMicros(frame.captureTimeNs), 0);
                    if (this.keyFrameRequired) {
                        this.keyFrameRequired = false;
                    }
                    return VideoCodecStatus.OK;
                } catch (IllegalStateException e) {
                    Logging.m4e(TAG, "queueInputBuffer failed", e);
                    this.frameInfos.pollLast();
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

    @Override // org.webrtc.VideoDecoder
    public boolean getPrefersLateDecoding() {
        return true;
    }

    @Override // org.webrtc.VideoDecoder
    public String getImplementationName() {
        return this.codecName;
    }

    @Override // org.webrtc.VideoDecoder
    public VideoCodecStatus release() {
        Logging.m1d(TAG, "release");
        VideoCodecStatus status = releaseInternal();
        if (this.surface != null) {
            releaseSurface();
            this.surface = null;
            this.surfaceTextureHelper.stopListening();
            this.surfaceTextureHelper.dispose();
            this.surfaceTextureHelper = null;
        }
        synchronized (this.renderedTextureMetadataLock) {
            this.renderedTextureMetadata = null;
        }
        this.callback = null;
        this.frameInfos.clear();
        return status;
    }

    private VideoCodecStatus releaseInternal() {
        if (!this.running) {
            Logging.m1d(TAG, "release: Decoder is not running.");
            return VideoCodecStatus.OK;
        }
        try {
            this.running = false;
            if (!ThreadUtils.joinUninterruptibly(this.outputThread, 5000L)) {
                Logging.m4e(TAG, "Media decoder release timeout", new RuntimeException());
                return VideoCodecStatus.TIMEOUT;
            }
            if (this.shutdownException == null) {
                return VideoCodecStatus.OK;
            }
            Logging.m4e(TAG, "Media decoder release error", new RuntimeException(this.shutdownException));
            this.shutdownException = null;
            return VideoCodecStatus.ERROR;
        } finally {
            this.codec = null;
            this.outputThread = null;
        }
    }

    private VideoCodecStatus reinitDecode(int newWidth, int newHeight) {
        this.decoderThreadChecker.checkIsOnValidThread();
        VideoCodecStatus status = releaseInternal();
        if (status != VideoCodecStatus.OK) {
            return status;
        }
        return initDecodeInternal(newWidth, newHeight);
    }

    private Thread createOutputThread() {
        return new Thread("AndroidVideoDecoder.outputThread") { // from class: org.webrtc.AndroidVideoDecoder.1
            @Override // java.lang.Thread, java.lang.Runnable
            public void run() {
                AndroidVideoDecoder.this.outputThreadChecker = new ThreadUtils.ThreadChecker();
                while (AndroidVideoDecoder.this.running) {
                    AndroidVideoDecoder.this.deliverDecodedFrame();
                }
                AndroidVideoDecoder.this.releaseCodecOnOutputThread();
            }
        };
    }

    protected void deliverDecodedFrame() {
        this.outputThreadChecker.checkIsOnValidThread();
        try {
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            int result = this.codec.dequeueOutputBuffer(info, 100000L);
            if (result == -2) {
                reformat(this.codec.getOutputFormat());
                return;
            }
            if (result < 0) {
                Logging.m6v(TAG, "dequeueOutputBuffer returned " + result);
                return;
            }
            FrameInfo frameInfo = this.frameInfos.poll();
            Integer decodeTimeMs = null;
            int rotation = 0;
            if (frameInfo != null) {
                decodeTimeMs = Integer.valueOf((int) (SystemClock.elapsedRealtime() - frameInfo.decodeStartTimeMs));
                rotation = frameInfo.rotation;
            }
            this.hasDecodedFirstFrame = true;
            if (this.surfaceTextureHelper != null) {
                deliverTextureFrame(result, info, rotation, decodeTimeMs);
            } else {
                deliverByteFrame(result, info, rotation, decodeTimeMs);
            }
        } catch (IllegalStateException e) {
            Logging.m4e(TAG, "deliverDecodedFrame failed", e);
        }
    }

    private void deliverTextureFrame(int index, MediaCodec.BufferInfo info, int rotation, Integer decodeTimeMs) {
        int width;
        int height;
        synchronized (this.dimensionLock) {
            width = this.width;
            height = this.height;
        }
        synchronized (this.renderedTextureMetadataLock) {
            if (this.renderedTextureMetadata != null) {
                this.codec.releaseOutputBuffer(index, false);
                return;
            }
            this.surfaceTextureHelper.setTextureSize(width, height);
            this.surfaceTextureHelper.setFrameRotation(rotation);
            this.renderedTextureMetadata = new DecodedTextureMetadata(info.presentationTimeUs, decodeTimeMs);
            this.codec.releaseOutputBuffer(index, true);
        }
    }

    @Override // org.webrtc.VideoSink
    public void onFrame(VideoFrame frame) {
        long timestampNs;
        Integer decodeTimeMs;
        synchronized (this.renderedTextureMetadataLock) {
            if (this.renderedTextureMetadata == null) {
                throw new IllegalStateException("Rendered texture metadata was null in onTextureFrameAvailable.");
            }
            timestampNs = this.renderedTextureMetadata.presentationTimestampUs * 1000;
            decodeTimeMs = this.renderedTextureMetadata.decodeTimeMs;
            this.renderedTextureMetadata = null;
        }
        VideoFrame frameWithModifiedTimeStamp = new VideoFrame(frame.getBuffer(), frame.getRotation(), timestampNs);
        this.callback.onDecodedFrame(frameWithModifiedTimeStamp, decodeTimeMs, null);
    }

    private void deliverByteFrame(int result, MediaCodec.BufferInfo info, int rotation, Integer decodeTimeMs) {
        int width;
        int height;
        int stride;
        int sliceHeight;
        VideoFrame.Buffer frameBuffer;
        synchronized (this.dimensionLock) {
            width = this.width;
            height = this.height;
            stride = this.stride;
            sliceHeight = this.sliceHeight;
        }
        if (info.size < ((width * height) * 3) / 2) {
            Logging.m2e(TAG, "Insufficient output buffer size: " + info.size);
            return;
        }
        if (info.size < ((stride * height) * 3) / 2 && sliceHeight == height && stride > width) {
            stride = (info.size * 2) / (height * 3);
        }
        ByteBuffer buffer = this.codec.getOutputBuffers()[result];
        buffer.position(info.offset);
        buffer.limit(info.offset + info.size);
        ByteBuffer buffer2 = buffer.slice();
        if (this.colorFormat == 19) {
            frameBuffer = copyI420Buffer(buffer2, stride, sliceHeight, width, height);
        } else {
            frameBuffer = copyNV12ToI420Buffer(buffer2, stride, sliceHeight, width, height);
        }
        this.codec.releaseOutputBuffer(result, false);
        long presentationTimeNs = info.presentationTimeUs * 1000;
        VideoFrame frame = new VideoFrame(frameBuffer, rotation, presentationTimeNs);
        this.callback.onDecodedFrame(frame, decodeTimeMs, null);
        frame.release();
    }

    private VideoFrame.Buffer copyNV12ToI420Buffer(ByteBuffer buffer, int stride, int sliceHeight, int width, int height) {
        return new NV12Buffer(width, height, stride, sliceHeight, buffer, null).toI420();
    }

    private VideoFrame.Buffer copyI420Buffer(ByteBuffer buffer, int stride, int sliceHeight, int width, int height) {
        if (stride % 2 != 0) {
            throw new AssertionError("Stride is not divisible by two: " + stride);
        }
        int chromaWidth = (width + 1) / 2;
        int chromaHeight = sliceHeight % 2 == 0 ? (height + 1) / 2 : height / 2;
        int uvStride = stride / 2;
        int yEnd = 0 + (stride * height);
        int uPos = 0 + (stride * sliceHeight);
        int uEnd = uPos + (uvStride * chromaHeight);
        int vPos = uPos + ((uvStride * sliceHeight) / 2);
        int vEnd = vPos + (uvStride * chromaHeight);
        VideoFrame.I420Buffer frameBuffer = allocateI420Buffer(width, height);
        buffer.limit(yEnd);
        buffer.position(0);
        copyPlane(buffer.slice(), stride, frameBuffer.getDataY(), frameBuffer.getStrideY(), width, height);
        buffer.limit(uEnd);
        buffer.position(uPos);
        copyPlane(buffer.slice(), uvStride, frameBuffer.getDataU(), frameBuffer.getStrideU(), chromaWidth, chromaHeight);
        if (sliceHeight % 2 == 1) {
            buffer.position(uPos + (uvStride * (chromaHeight - 1)));
            ByteBuffer dataU = frameBuffer.getDataU();
            dataU.position(frameBuffer.getStrideU() * chromaHeight);
            dataU.put(buffer);
        }
        buffer.limit(vEnd);
        buffer.position(vPos);
        copyPlane(buffer.slice(), uvStride, frameBuffer.getDataV(), frameBuffer.getStrideV(), chromaWidth, chromaHeight);
        if (sliceHeight % 2 == 1) {
            buffer.position(vPos + (uvStride * (chromaHeight - 1)));
            ByteBuffer dataV = frameBuffer.getDataV();
            dataV.position(frameBuffer.getStrideV() * chromaHeight);
            dataV.put(buffer);
        }
        return frameBuffer;
    }

    private void reformat(MediaFormat format) {
        int newWidth;
        int newHeight;
        this.outputThreadChecker.checkIsOnValidThread();
        Logging.m1d(TAG, "Decoder format changed: " + format.toString());
        if (format.containsKey(MEDIA_FORMAT_KEY_CROP_LEFT) && format.containsKey(MEDIA_FORMAT_KEY_CROP_RIGHT) && format.containsKey(MEDIA_FORMAT_KEY_CROP_BOTTOM) && format.containsKey(MEDIA_FORMAT_KEY_CROP_TOP)) {
            newWidth = (1 + format.getInteger(MEDIA_FORMAT_KEY_CROP_RIGHT)) - format.getInteger(MEDIA_FORMAT_KEY_CROP_LEFT);
            newHeight = (1 + format.getInteger(MEDIA_FORMAT_KEY_CROP_BOTTOM)) - format.getInteger(MEDIA_FORMAT_KEY_CROP_TOP);
        } else {
            newWidth = format.getInteger("width");
            newHeight = format.getInteger("height");
        }
        synchronized (this.dimensionLock) {
            if (this.hasDecodedFirstFrame && (this.width != newWidth || this.height != newHeight)) {
                stopOnOutputThread(new RuntimeException("Unexpected size change. Configured " + this.width + "*" + this.height + ". New " + newWidth + "*" + newHeight));
                return;
            }
            this.width = newWidth;
            this.height = newHeight;
            if (this.surfaceTextureHelper == null && format.containsKey("color-format")) {
                this.colorFormat = format.getInteger("color-format");
                Logging.m1d(TAG, "Color: 0x" + Integer.toHexString(this.colorFormat));
                if (!isSupportedColorFormat(this.colorFormat)) {
                    stopOnOutputThread(new IllegalStateException("Unsupported color format: " + this.colorFormat));
                    return;
                }
            }
            synchronized (this.dimensionLock) {
                if (format.containsKey(MEDIA_FORMAT_KEY_STRIDE)) {
                    this.stride = format.getInteger(MEDIA_FORMAT_KEY_STRIDE);
                }
                if (format.containsKey(MEDIA_FORMAT_KEY_SLICE_HEIGHT)) {
                    this.sliceHeight = format.getInteger(MEDIA_FORMAT_KEY_SLICE_HEIGHT);
                }
                Logging.m1d(TAG, "Frame stride and slice height: " + this.stride + " x " + this.sliceHeight);
                this.stride = Math.max(this.width, this.stride);
                this.sliceHeight = Math.max(this.height, this.sliceHeight);
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void releaseCodecOnOutputThread() {
        this.outputThreadChecker.checkIsOnValidThread();
        Logging.m1d(TAG, "Releasing MediaCodec on output thread");
        try {
            this.codec.stop();
        } catch (Exception e) {
            Logging.m4e(TAG, "Media decoder stop failed", e);
        }
        try {
            this.codec.release();
        } catch (Exception e2) {
            Logging.m4e(TAG, "Media decoder release failed", e2);
            this.shutdownException = e2;
        }
        Logging.m1d(TAG, "Release on output thread done");
    }

    private void stopOnOutputThread(Exception e) {
        this.outputThreadChecker.checkIsOnValidThread();
        this.running = false;
        this.shutdownException = e;
    }

    private boolean isSupportedColorFormat(int colorFormat) {
        for (int supported : MediaCodecUtils.DECODER_COLOR_FORMATS) {
            if (supported == colorFormat) {
                return true;
            }
        }
        return false;
    }

    protected SurfaceTextureHelper createSurfaceTextureHelper() {
        return SurfaceTextureHelper.create("decoder-texture-thread", this.sharedContext);
    }

    protected void releaseSurface() {
        this.surface.release();
    }

    protected VideoFrame.I420Buffer allocateI420Buffer(int width, int height) {
        return JavaI420Buffer.allocate(width, height);
    }

    protected void copyPlane(ByteBuffer src, int srcStride, ByteBuffer dst, int dstStride, int width, int height) {
        YuvHelper.copyPlane(src, srcStride, dst, dstStride, width, height);
    }
}

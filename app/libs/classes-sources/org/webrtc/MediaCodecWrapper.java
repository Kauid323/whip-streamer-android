package org.webrtc;

import android.media.MediaCodec;
import android.media.MediaCrypto;
import android.media.MediaFormat;
import android.os.Bundle;
import android.view.Surface;
import java.nio.ByteBuffer;

/* loaded from: classes.jar:org/webrtc/MediaCodecWrapper.class */
interface MediaCodecWrapper {
    void configure(MediaFormat mediaFormat, Surface surface, MediaCrypto mediaCrypto, int i);

    void start();

    void flush();

    void stop();

    void release();

    int dequeueInputBuffer(long j);

    void queueInputBuffer(int i, int i2, int i3, long j, int i4);

    int dequeueOutputBuffer(MediaCodec.BufferInfo bufferInfo, long j);

    void releaseOutputBuffer(int i, boolean z);

    MediaFormat getOutputFormat();

    ByteBuffer[] getInputBuffers();

    ByteBuffer[] getOutputBuffers();

    Surface createInputSurface();

    void setParameters(Bundle bundle);
}

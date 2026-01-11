package com.example.whiper

import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.os.Handler
import android.util.DisplayMetrics
import android.view.Surface
import org.webrtc.CapturerObserver
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoFrame
import org.webrtc.VideoSink

class MediaProjectionVideoCapturer(
    private val mediaProjection: MediaProjection,
    private val appContext: Context
) : VideoCapturer {

    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var capturerObserver: CapturerObserver? = null

    private var virtualDisplay: VirtualDisplay? = null
    private var surface: Surface? = null

    private var width: Int = 0
    private var height: Int = 0
    private var fps: Int = 0

    private var isStarted: Boolean = false

    override fun initialize(surfaceTextureHelper: SurfaceTextureHelper, context: Context, capturerObserver: CapturerObserver) {
        this.surfaceTextureHelper = surfaceTextureHelper
        this.capturerObserver = capturerObserver
    }

    override fun startCapture(width: Int, height: Int, framerate: Int) {
        this.width = width
        this.height = height
        this.fps = framerate

        val sth = surfaceTextureHelper ?: return
        val observer = capturerObserver ?: return

        val handler: Handler = sth.handler
        handler.post {
            if (isStarted) return@post

            sth.setTextureSize(width, height)
            val st = sth.surfaceTexture
            st.setDefaultBufferSize(width, height)

            surface = Surface(st)

            val metrics: DisplayMetrics = appContext.resources.displayMetrics
            virtualDisplay = mediaProjection.createVirtualDisplay(
                "WebRTC-Screen",
                width,
                height,
                metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                surface,
                null,
                handler
            )

            sth.startListening(object : VideoSink {
                override fun onFrame(frame: VideoFrame) {
                    observer.onFrameCaptured(frame)
                }
            })

            isStarted = true
            observer.onCapturerStarted(true)
        }
    }

    override fun stopCapture() {
        val sth = surfaceTextureHelper
        val observer = capturerObserver
        if (sth == null) {
            releaseInternal(null)
            observer?.onCapturerStopped()
            return
        }

        sth.handler.post {
            if (!isStarted) {
                observer?.onCapturerStopped()
                return@post
            }
            releaseInternal(sth)
            isStarted = false
            observer?.onCapturerStopped()
        }
    }

    private fun releaseInternal(sth: SurfaceTextureHelper?) {
        try {
            sth?.stopListening()
        } catch (_: Throwable) {
        }

        try {
            virtualDisplay?.release()
        } catch (_: Throwable) {
        } finally {
            virtualDisplay = null
        }

        try {
            surface?.release()
        } catch (_: Throwable) {
        } finally {
            surface = null
        }
    }

    override fun changeCaptureFormat(width: Int, height: Int, framerate: Int) {
        // Not supported; callers should stop/start capture if needed.
        this.width = width
        this.height = height
        this.fps = framerate
    }

    override fun dispose() {
        // Resources are released in stopCapture.
        capturerObserver = null
        surfaceTextureHelper = null
    }

    override fun isScreencast(): Boolean = true
}

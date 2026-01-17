package com.example.whiper

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.webrtc.*
import org.webrtc.audio.AudioDeviceModule
import org.webrtc.audio.JavaAudioDeviceModule
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class StreamService : Service() {

    private val notificationId = 101
    private val channelId = "WhipStreamChannel"

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var videoCapturer: VideoCapturer? = null
    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null
    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null
    private var eglBase: EglBase? = null
    private var sharedMediaProjection: MediaProjection? = null

    private var currentVideoBitrateKbps: Int = 2500
    private var currentVideoFps: Int = 30
    private var currentAudioBitrateKbps: Int = 64

    private var currentVideoCodec: String = "H264"
    private var currentVideoEncoderMode: String = "Auto"
    private var currentVideoCodecStrict: Boolean = true

    private val systemAudioFactoryInvokedOnce = AtomicBoolean(false)

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private val httpClient = OkHttpClient()
    
    // Prevent double stop
    private var isStopping = false

    private fun applySenderBitrates(videoBitrateKbps: Int, videoFps: Int, audioBitrateKbps: Int) {
        val pc = peerConnection ?: return

        fun applyToSender(sender: RtpSender, kind: String) {
            try {
                val p = sender.parameters
                val enc = p.encodings
                if (enc.isNullOrEmpty()) {
                    Log.w("StreamService", "applySenderBitrates: $kind encodings is empty; cannot apply")
                    return
                }

                when (kind) {
                    MediaStreamTrack.VIDEO_TRACK_KIND -> {
                        val bps = (videoBitrateKbps.coerceAtLeast(1)) * 1000
                        enc[0].maxBitrateBps = bps
                        enc[0].minBitrateBps = bps
                        enc[0].maxFramerate = videoFps.coerceAtLeast(1)
                    }

                    MediaStreamTrack.AUDIO_TRACK_KIND -> {
                        val bps = (audioBitrateKbps.coerceAtLeast(6)) * 1000
                        enc[0].maxBitrateBps = bps
                        enc[0].minBitrateBps = bps
                    }
                }

                sender.parameters = p
                val a = sender.parameters.encodings?.firstOrNull()
                Log.i(
                    "StreamService",
                    "applySenderBitrates: kind=$kind maxBitrateBps=${a?.maxBitrateBps} minBitrateBps=${a?.minBitrateBps} maxFramerate=${a?.maxFramerate}"
                )
            } catch (t: Throwable) {
                Log.w("StreamService", "applySenderBitrates: failed for $kind", t)
            }
        }

        pc.senders.forEach { sender ->
            val kind = sender.track()?.kind() ?: return@forEach
            if (kind == MediaStreamTrack.VIDEO_TRACK_KIND || kind == MediaStreamTrack.AUDIO_TRACK_KIND) {
                applyToSender(sender, kind)
            }
        }
    }

    private fun mungeOpusMaxAverageBitrate(sdp: String, audioBitrateKbps: Int): String {
        val bps = (audioBitrateKbps.coerceAtLeast(6)) * 1000
        val lines = sdp.split("\r\n").toMutableList()

        // Find opus payload type from rtpmap.
        val rtpmapRegex = Regex("^a=rtpmap:(\\d+) opus/48000(?:/2)?$", RegexOption.IGNORE_CASE)
        var opusPt: String? = null
        for (l in lines) {
            val m = rtpmapRegex.find(l.trim())
            if (m != null) {
                opusPt = m.groupValues[1]
                break
            }
        }
        if (opusPt == null) return sdp

        val fmtpPrefix = "a=fmtp:$opusPt "
        val fmtpIndex = lines.indexOfFirst { it.startsWith(fmtpPrefix) }
        if (fmtpIndex >= 0) {
            val cur = lines[fmtpIndex]
            val params = cur.removePrefix(fmtpPrefix)
            val kvs = params.split(";").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
            val filtered = kvs.filterNot { it.startsWith("maxaveragebitrate=", ignoreCase = true) }.toMutableList()
            filtered.add("maxaveragebitrate=$bps")
            lines[fmtpIndex] = fmtpPrefix + filtered.joinToString(";")
        } else {
            // Insert fmtp right after opus rtpmap line.
            val rtpmapIndex = lines.indexOfFirst { it.trim().equals("a=rtpmap:$opusPt opus/48000", ignoreCase = true) || it.trim().equals("a=rtpmap:$opusPt opus/48000/2", ignoreCase = true) }
            val insertAt = if (rtpmapIndex >= 0) rtpmapIndex + 1 else lines.size
            lines.add(insertAt, fmtpPrefix + "maxaveragebitrate=$bps")
        }

        Log.i("StreamService", "SDP munged opus pt=$opusPt maxaveragebitrate=$bps")
        return lines.joinToString("\r\n")
    }

    private fun mungePreferVideoCodec(sdp: String, preferredCodec: String): String {
        val target = normalizePreferredVideoCodecOrNull(preferredCodec) ?: return sdp

        val lines = sdp.split("\r\n").toMutableList()

        // Find video m-section range.
        val mVideo = lines.indexOfFirst { it.startsWith("m=video ") }
        if (mVideo < 0) return sdp
        val mNext = (mVideo + 1 until lines.size).firstOrNull { lines[it].startsWith("m=") } ?: lines.size

        // Collect payload types for target codec within the video section.
        val ptRegex = Regex("^a=rtpmap:(\\d+)\\s+([A-Za-z0-9]+)/", RegexOption.IGNORE_CASE)
        val targetPts = mutableListOf<String>()
        for (i in mVideo until mNext) {
            val m = ptRegex.find(lines[i]) ?: continue
            val pt = m.groupValues[1]
            val codec = m.groupValues[2].uppercase()
            if (codec == target) {
                targetPts.add(pt)
            }
        }
        if (targetPts.isEmpty()) {
            Log.w("StreamService", "Preferred codec $target not present in offer SDP; leaving SDP unchanged")
            return sdp
        }

        val parts = lines[mVideo].trim().split(" ")
        if (parts.size < 4) return sdp
        val header = parts.take(3)
        val pts = parts.drop(3)

        val reordered = buildList {
            addAll(header)
            addAll(pts.filter { it in targetPts })
            addAll(pts.filterNot { it in targetPts })
        }
        lines[mVideo] = reordered.joinToString(" ")

        Log.i("StreamService", "SDP munged video codec preferred=$target pts=${targetPts.joinToString(",")}")
        return lines.joinToString("\r\n")
    }

    private fun normalizePreferredVideoCodecOrNull(preferredCodec: String): String? {
        val normalized = preferredCodec.trim().uppercase()
        return when (normalized) {
            "H265", "HEVC" -> {
                Log.w("StreamService", "Requested video codec $preferredCodec is not supported by this WebRTC build; falling back to H264")
                "H264"
            }

            "H264", "VP8", "VP9" -> normalized
            else -> {
                Log.w("StreamService", "Unknown video codec '$preferredCodec'; falling back to H264")
                "H264"
            }
        }
    }

    private class FilteringVideoEncoderFactory(
        private val delegate: VideoEncoderFactory,
        private val codecNameUpper: String
    ) : VideoEncoderFactory {
        override fun createEncoder(videoCodecInfo: VideoCodecInfo?): VideoEncoder? {
            if (videoCodecInfo == null) return null
            if (!videoCodecInfo.name.equals(codecNameUpper, ignoreCase = true)) return null
            return delegate.createEncoder(videoCodecInfo)
        }

        override fun getSupportedCodecs(): Array<VideoCodecInfo> {
            return delegate.supportedCodecs.filter { it.name.equals(codecNameUpper, ignoreCase = true) }.toTypedArray()
        }
    }

    private class FilteringVideoDecoderFactory(
        private val delegate: VideoDecoderFactory,
        private val codecNameUpper: String
    ) : VideoDecoderFactory {
        override fun createDecoder(info: VideoCodecInfo?): VideoDecoder? {
            if (info == null) return null
            if (!info.name.equals(codecNameUpper, ignoreCase = true)) return null
            return delegate.createDecoder(info)
        }

        override fun getSupportedCodecs(): Array<VideoCodecInfo> {
            return delegate.supportedCodecs.filter { it.name.equals(codecNameUpper, ignoreCase = true) }.toTypedArray()
        }
    }

    private fun logSdpVideoSection(tag: String, sdp: String) {
        try {
            val lines = sdp.split("\r\n")
            val mVideo = lines.indexOfFirst { it.startsWith("m=video ") }
            if (mVideo < 0) {
                Log.i("StreamService", "$tag: no m=video section")
                return
            }
            val mNext = (mVideo + 1 until lines.size).firstOrNull { lines[it].startsWith("m=") } ?: lines.size
            val snippet = lines.subList(mVideo, mNext)
                .filter { it.startsWith("m=video") || it.startsWith("a=rtpmap") || it.startsWith("a=fmtp") }
                .joinToString(" | ")
            Log.i("StreamService", "$tag: $snippet")
        } catch (t: Throwable) {
            Log.w("StreamService", "logSdpVideoSection failed", t)
        }
    }

    private fun mungeRestrictVideoCodecIfPresent(sdp: String, preferredCodec: String): String {
        val normalized = preferredCodec.trim().uppercase()
        val target = when (normalized) {
            "H265", "HEVC" -> "H264"
            "H264", "VP8", "VP9" -> normalized
            else -> "H264"
        }

        val lines = sdp.split("\r\n").toMutableList()
        val mVideo = lines.indexOfFirst { it.startsWith("m=video ") }
        if (mVideo < 0) return sdp
        val mNext = (mVideo + 1 until lines.size).firstOrNull { lines[it].startsWith("m=") } ?: lines.size

        val ptRegex = Regex("^a=rtpmap:(\\d+)\\s+([A-Za-z0-9]+)/", RegexOption.IGNORE_CASE)
        val ptToCodec = mutableMapOf<String, String>()
        for (i in mVideo until mNext) {
            val m = ptRegex.find(lines[i]) ?: continue
            ptToCodec[m.groupValues[1]] = m.groupValues[2].uppercase()
        }

        val mParts = lines[mVideo].trim().split(" ")
        if (mParts.size < 4) return sdp
        val header = mParts.take(3)
        val pts = mParts.drop(3)

        val targetPts = pts.filter { ptToCodec[it] == target }
        if (targetPts.isEmpty()) {
            Log.w("StreamService", "WHIP answer does not include preferred codec $target; cannot force. Using negotiated codec from server.")
            return sdp
        }

        // Keep only preferred payload types in m=video line.
        lines[mVideo] = (header + targetPts).joinToString(" ")
        Log.i("StreamService", "SDP munged answer restrict video codec to $target pts=${targetPts.joinToString(",")}")

        // Remove rtpmap/fmtp/rtcp-fb lines for payload types not in targetPts within video section.
        val keepPts = targetPts.toSet()
        val attrPtRegex = Regex("^a=(rtpmap|fmtp|rtcp-fb):(\\d+)\\b", RegexOption.IGNORE_CASE)
        val newSection = mutableListOf<String>()
        for (i in mVideo until mNext) {
            val l = lines[i]
            val m = attrPtRegex.find(l)
            if (m != null) {
                val pt = m.groupValues[2]
                if (!keepPts.contains(pt)) {
                    continue
                }
            }
            newSection.add(l)
        }
        for (i in 0 until (mNext - mVideo)) {
            lines.removeAt(mVideo)
        }
        lines.addAll(mVideo, newSection)

        return lines.joinToString("\r\n")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY

        if (intent.action == "STOP") {
            stopStreaming()
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundNotification()

        val resultCode = intent.getIntExtra("code", 0)
        val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("data", Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("data")
        }
        val url = intent.getStringExtra("url")
        val token = intent.getStringExtra("token")
        val audioSrc = intent.getStringExtra("audioSource") ?: "mic"
        val audioBitrateKbps = intent.getIntExtra("audioBitrateKbps", 64)

        val videoCodec = intent.getStringExtra("videoCodec") ?: "H264"
        val videoEncoderMode = intent.getStringExtra("videoEncoderMode") ?: "Auto"

        val videoWidth = intent.getIntExtra("videoWidth", 1280)
        val videoHeight = intent.getIntExtra("videoHeight", 720)
        val videoFps = intent.getIntExtra("videoFps", 30)
        val videoBitrateKbps = intent.getIntExtra("videoBitrateKbps", 2500)

        // Persist for later (we don't have an Activity intent field in Service)
        currentVideoBitrateKbps = videoBitrateKbps
        currentVideoFps = videoFps
        currentAudioBitrateKbps = audioBitrateKbps

        currentVideoCodec = videoCodec
        currentVideoEncoderMode = videoEncoderMode

        if (resultCode == 0 || resultData == null || url.isNullOrEmpty()) {
            Log.e("StreamService", "Invalid intent extras")
            stopSelf()
            return START_NOT_STICKY
        }

        // Reset stopping flag for new session
        isStopping = false
        startStreaming(
            resultCode,
            resultData,
            url,
            token,
            audioSrc,
            videoWidth,
            videoHeight,
            videoFps,
            videoBitrateKbps,
            audioBitrateKbps
        )

        return START_STICKY
    }

    private fun startForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Streaming Service",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val stopIntent = Intent(this, StreamService::class.java).apply { action = "STOP" }
        val pendingStop = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("WHIP Streaming Active")
            .setContentText("Capturing via WebRTC...")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", pendingStop)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(notificationId, notification)
        }
    }

    private fun startStreaming(
        resultCode: Int,
        resultData: Intent,
        url: String,
        token: String?,
        audioSrc: String,
        videoWidth: Int,
        videoHeight: Int,
        videoFps: Int,
        videoBitrateKbps: Int,
        audioBitrateKbps: Int
    ) {
        serviceScope.launch(Dispatchers.Main) {
            // Create ONE MediaProjection and share it for BOTH:
            // - Video capture (via our custom MediaProjectionVideoCapturer)
            // - System audio (AudioPlaybackCapture)
            // This avoids Android 14+ token reuse issues caused by ScreenCapturerAndroid consuming resultData.
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            sharedMediaProjection = projectionManager.getMediaProjection(resultCode, resultData)

            initWebRTC(audioSrc, resultCode, resultData)

            // Video setup
            val mpCallback = object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.d("StreamService", "MediaProjection stopped")
                    stopStreaming()
                }
            }

            sharedMediaProjection?.registerCallback(mpCallback, null)

            val mp = sharedMediaProjection
            if (mp == null) {
                Log.e("StreamService", "MediaProjection is null; cannot start screen capture")
                stopStreaming()
                stopSelf()
                return@launch
            }

            videoCapturer = MediaProjectionVideoCapturer(mp, applicationContext)
            
            val surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBase!!.eglBaseContext)
            videoSource = peerConnectionFactory!!.createVideoSource(videoCapturer!!.isScreencast)
            videoCapturer!!.initialize(surfaceTextureHelper, applicationContext, videoSource!!.capturerObserver)
            
            videoCapturer!!.startCapture(videoWidth, videoHeight, videoFps)
            
            videoTrack = peerConnectionFactory!!.createVideoTrack("video_track", videoSource)

            // Audio setup
            if (audioSrc == "none") {
                audioSource = null
                audioTrack = null
            } else {
                val audioConstraints = MediaConstraints()
                audioSource = peerConnectionFactory!!.createAudioSource(audioConstraints)
                audioTrack = peerConnectionFactory!!.createAudioTrack("audio_track", audioSource)
            }

            createPeerConnectionAndOffer(url, token, videoBitrateKbps, audioBitrateKbps)
        }
    }

    private fun initWebRTC(audioSrc: String, resultCode: Int, resultData: Intent) {
        if (peerConnectionFactory != null) return

        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(applicationContext)
                .setEnableInternalTracer(true)
                .createInitializationOptions()
        )

        eglBase = EglBase.create()
        
        val options = PeerConnectionFactory.Options()

        val encoderMode = currentVideoEncoderMode
        val baseVideoEncoderFactory: VideoEncoderFactory = when (encoderMode) {
            "Hardware" -> HardwareVideoEncoderFactory(eglBase!!.eglBaseContext, true, true)
            "Software" -> SoftwareVideoEncoderFactory()
            else -> DefaultVideoEncoderFactory(eglBase!!.eglBaseContext, true, true)
        }

        val baseVideoDecoderFactory: VideoDecoderFactory = when (encoderMode) {
            "Hardware" -> HardwareVideoDecoderFactory(eglBase!!.eglBaseContext)
            "Software" -> SoftwareVideoDecoderFactory()
            else -> DefaultVideoDecoderFactory(eglBase!!.eglBaseContext)
        }

        val normalizedCodec = normalizePreferredVideoCodecOrNull(currentVideoCodec)
        val videoEncoderFactory: VideoEncoderFactory = if (currentVideoCodecStrict && normalizedCodec != null) {
            FilteringVideoEncoderFactory(baseVideoEncoderFactory, normalizedCodec)
        } else {
            baseVideoEncoderFactory
        }

        val videoDecoderFactory: VideoDecoderFactory = if (currentVideoCodecStrict && normalizedCodec != null) {
            FilteringVideoDecoderFactory(baseVideoDecoderFactory, normalizedCodec)
        } else {
            baseVideoDecoderFactory
        }

        Log.i(
            "StreamService",
            "Video encoder selection: codec=$currentVideoCodec strict=$currentVideoCodecStrict encoderMode=$currentVideoEncoderMode supportedEncoders=${videoEncoderFactory.supportedCodecs.joinToString { it.name }}"
        )

        val audioDeviceModule = createAudioDeviceModuleOrNull(audioSrc, resultCode, resultData)

        val builder = PeerConnectionFactory.builder()
            .setOptions(options)
            .setVideoEncoderFactory(videoEncoderFactory)
            .setVideoDecoderFactory(videoDecoderFactory)

        if (audioDeviceModule != null) {
            builder.setAudioDeviceModule(audioDeviceModule)
        }

        peerConnectionFactory = builder.createPeerConnectionFactory()
    }

    private fun createAudioDeviceModuleOrNull(audioSrc: String, resultCode: Int, resultData: Intent): AudioDeviceModule? {
        return try {
            val builder = JavaAudioDeviceModule.builder(applicationContext)

            val effectiveAudioSrc = when (audioSrc) {
                "mix" -> {
                    Log.w("StreamService", "Audio mode 'mix' selected. True mixing is not implemented yet; using system audio on Android 10+, otherwise falling back to mic.")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) "system" else "mic"
                }
                else -> audioSrc
            }

            Log.i("StreamService", "Requested audioSrc=$audioSrc, effectiveAudioSrc=$effectiveAudioSrc")

            if (effectiveAudioSrc == "system") {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    Log.w("StreamService", "System audio requested but Android < 10. Falling back to microphone.")
                } else {
                    val mp = sharedMediaProjection
                    if (mp == null) {
                        Log.w("StreamService", "Shared MediaProjection is null; falling back to microphone.")
                    } else {
                        val config = AudioPlaybackCaptureConfiguration.Builder(mp)
                            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                            .addMatchingUsage(AudioAttributes.USAGE_GAME)
                            .build()

                        try {
                            val audioRecordFactoryClass = Class.forName("org.webrtc.audio.JavaAudioDeviceModule\$AudioRecordFactory")
                            val setFactoryMethod = builder.javaClass.methods.firstOrNull {
                                it.name == "setAudioRecordFactory" && it.parameterTypes.size == 1
                            }

                            Log.i(
                                "StreamService",
                                "JavaAudioDeviceModule.Builder class=${builder.javaClass.name}, hasSetAudioRecordFactory=${setFactoryMethod != null}"
                            )

                            if (setFactoryMethod == null) {
                                Log.w(
                                    "StreamService",
                                    "This google-webrtc AAR does not expose JavaAudioDeviceModule.Builder.setAudioRecordFactory; system audio cannot be injected."
                                )
                            } else {
                                val factoryProxy = java.lang.reflect.Proxy.newProxyInstance(
                                    audioRecordFactoryClass.classLoader,
                                    arrayOf(audioRecordFactoryClass)
                                ) { _, method, args ->
                                    if (method.name == "createAudioRecord" && args != null && args.size >= 3) {
                                        val sampleRate = args[0] as Int
                                        val channelConfig = args[1] as Int
                                        val audioFormat = args[2] as Int

                                        if (systemAudioFactoryInvokedOnce.compareAndSet(false, true)) {
                                            Log.i(
                                                "StreamService",
                                                "AudioRecordFactory.createAudioRecord invoked. sampleRate=$sampleRate channelConfig=$channelConfig audioFormat=$audioFormat argsSize=${args.size}"
                                            )
                                        }

                                        val bufferSizeInBytes = if (args.size >= 4) {
                                            args[3] as Int
                                        } else {
                                            AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
                                        }

                                        val format = AudioFormat.Builder()
                                            .setEncoding(audioFormat)
                                            .setSampleRate(sampleRate)
                                            .setChannelMask(channelConfig)
                                            .build()

                                        val minBuffer = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
                                        AudioRecord.Builder()
                                            .setAudioFormat(format)
                                            .setBufferSizeInBytes(bufferSizeInBytes.coerceAtLeast(minBuffer))
                                            .setAudioPlaybackCaptureConfig(config)
                                            .build()
                                    } else {
                                        null
                                    }
                                }

                                setFactoryMethod.invoke(builder, factoryProxy)
                                Log.i("StreamService", "System audio AudioRecordFactory injected via reflection")
                            }
                        } catch (t: Throwable) {
                            Log.w(
                                "StreamService",
                                "Failed to inject system audio AudioRecordFactory. Falling back to microphone.",
                                t
                            )
                        }
                    }
                }
            }

            builder.createAudioDeviceModule()
        } catch (t: Throwable) {
            Log.e("StreamService", "Failed to create AudioDeviceModule", t)
            null
        }
    }

    private fun createPeerConnectionAndOffer(whipUrl: String, token: String?, videoBitrateKbps: Int, audioBitrateKbps: Int) {
        val rtcConfig = PeerConnection.RTCConfiguration(emptyList())
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        
        peerConnection = peerConnectionFactory!!.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                Log.d("StreamService", "ICE State: $state")
                if (state == PeerConnection.IceConnectionState.DISCONNECTED || state == PeerConnection.IceConnectionState.FAILED) {
                    stopStreaming()
                    stopSelf()
                }
            }
            override fun onIceConnectionReceivingChange(b: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidate(candidate: IceCandidate?) {}
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onAddStream(stream: MediaStream?) {}
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(channel: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {}
        })

        if (peerConnection == null) return

        // Add Tracks (Send Only)
        val videoTransceiver = peerConnection!!.addTransceiver(
            videoTrack,
            RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_ONLY)
        )
        val audioTrackLocal = audioTrack
        val audioTransceiver = if (audioTrackLocal != null) {
            peerConnection!!.addTransceiver(
                audioTrackLocal,
                RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_ONLY)
            )
        } else {
            null
        }

        // Apply bitrate caps immediately after tracks are attached.
        // Some devices / WebRTC builds may override sender params during negotiation,
        // so we re-apply again after local/remote descriptions are set.
        applySenderBitrates(videoBitrateKbps, currentVideoFps, audioBitrateKbps)

        peerConnection!!.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                desc?.let { offer ->
                    val mungedOpus = mungeOpusMaxAverageBitrate(offer.description, currentAudioBitrateKbps)
                    val mungedSdp = mungePreferVideoCodec(mungedOpus, currentVideoCodec)
                    val mungedOffer = SessionDescription(offer.type, mungedSdp)
                    peerConnection!!.setLocalDescription(object : SdpObserver {
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onSetSuccess() {
                            applySenderBitrates(videoBitrateKbps, currentVideoFps, audioBitrateKbps)
                            // After Setting Local Desc, Send to Server
                            sendWhipOffer(whipUrl, token, mungedSdp)
                        }
                        override fun onCreateFailure(p0: String?) {}
                        override fun onSetFailure(p0: String?) {}
                    }, mungedOffer)
                }
            }
            override fun onCreateFailure(p0: String?) {
                Log.e("StreamService", "Offer creation failed: $p0")
            }
            override fun onSetSuccess() {}
            override fun onSetFailure(p0: String?) {}
        }, MediaConstraints())
    }

    private fun sendWhipOffer(whipUrl: String, token: String?, sdp: String) {
        serviceScope.launch(Dispatchers.IO) {
            try {
                // Construct URL: User specifically requested appending the token to the URL.
                val finalUrl = if (!token.isNullOrEmpty()) {
                    if (whipUrl.endsWith("/")) "$whipUrl$token" else "$whipUrl/$token"
                } else {
                    whipUrl
                }
                
                Log.d("StreamService", "Sending WHIP Offer to: $finalUrl")
                
                val body = sdp.toRequestBody("application/sdp".toMediaType())
                val requestBuilder = Request.Builder()
                    .url(finalUrl)
                    .post(body)
                    .addHeader("Content-Type", "application/sdp")
                
                // Note: Removed Authorization header to prioritize URL-based authentication 
                // as requested by the user ("url followed by stream key"). 
                // Some servers strictly prefer one or the other.
                // if (!token.isNullOrEmpty()) {
                //    requestBuilder.addHeader("Authorization", "Bearer $token")
                // }

                val request = requestBuilder.build()
                val response = httpClient.newCall(request).execute()
                
                if (response.isSuccessful) {
                    val answerSdp = response.body?.string()
                    if (!answerSdp.isNullOrEmpty()) {
                        logSdpVideoSection("WHIP answer (raw)", answerSdp)
                        setRemoteAnswer(answerSdp)
                    } else {
                        Log.e("StreamService", "Empty answer from WHIP server")
                    }
                } else {
                    Log.e("StreamService", "WHIP Request failed: ${response.code} ${response.message}")
                    Log.e("StreamService", "Response Body: ${response.body?.string()}")
                    stopStreaming()
                    stopSelf()
                }
            } catch (e: Exception) {
                Log.e("StreamService", "WHIP Network error", e)
                e.printStackTrace()
                stopStreaming()
                stopSelf()
            }
        }
    }

    private fun setRemoteAnswer(sdp: String) {
        serviceScope.launch(Dispatchers.Main) {
            if (peerConnection == null) return@launch

            val target = normalizePreferredVideoCodecOrNull(currentVideoCodec)
            if (currentVideoCodecStrict && target != null) {
                val hasTarget = Regex("^a=rtpmap:(\\d+)\\s+$target/", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)).containsMatchIn(sdp)
                if (!hasTarget) {
                    Log.e("StreamService", "Strict codec=$target requested but WHIP answer doesn't include it. Failing fast.")
                    stopStreaming()
                    stopSelf()
                    return@launch
                }
            }

            val munged = mungeRestrictVideoCodecIfPresent(sdp, currentVideoCodec)
            if (munged != sdp) {
                logSdpVideoSection("WHIP answer (munged)", munged)
            }

            val answer = SessionDescription(SessionDescription.Type.ANSWER, munged)
            peerConnection?.setRemoteDescription(object : SdpObserver {
                override fun onCreateSuccess(p0: SessionDescription?) {}
                override fun onSetSuccess() {
                    Log.d("StreamService", "Remote Answer Set Successfully! Streaming should involve bytes now.")
                    applySenderBitrates(currentVideoBitrateKbps, currentVideoFps, currentAudioBitrateKbps)
                }
                override fun onCreateFailure(p0: String?) {}
                override fun onSetFailure(p0: String?) {
                    Log.e("StreamService", "Failed to set remote answer: $p0")
                    stopStreaming()
                    stopSelf()
                }
            }, answer)
        }
    }

    private fun stopStreaming() {
        if (isStopping) return
        isStopping = true
        
        Log.d("StreamService", "Stopping streaming...")

        try {
            videoCapturer?.stopCapture()
            videoCapturer?.dispose()
            videoCapturer = null

            videoSource?.dispose()
            videoSource = null

            audioSource?.dispose()
            audioSource = null

            sharedMediaProjection?.stop()
            sharedMediaProjection = null
            
            peerConnection?.close()
            peerConnection = null
            
            // CRITICAL: Do NOT dispose peerConnectionFactory or eglBase here.
            // Disposal of the factory often causes native crashes (SIGABRT) if internal threads are active.
            // Releasing eglBase while the factory (which holds an encoder factory using eglContext) is alive
            // will also cause crashes when the encoder thread tries to access the context.
            // Leaving these objects alive for the process lifetime is the safest strategy for this library version.
            
            // peerConnectionFactory?.dispose()
            // peerConnectionFactory = null
            
            // eglBase?.release()
            // eglBase = null
            
            Log.d("StreamService", "Streaming Stopped (Resources Released safely)")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        stopStreaming()
        serviceScope.coroutineContext.cancelChildren() // Cancel jobs
        super.onDestroy()
    }
    
    // Stub SdpObserver to reduce boilerplate in inline objects
    interface SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(p0: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(p0: String?) {}
        override fun onSetFailure(p0: String?) {}
    }
}

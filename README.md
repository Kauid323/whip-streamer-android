# 一个手机Whip推流工具

支持调视频分辨率，视频帧率，视频/音频码率

使用 **Kotlin+WebRTC+强大的AI力量** 构建。

目前还支持推送内部音频。

[WebRTC反编译“源码”](https://github.com/Kauid323/whip-streamer/tree/main/app/libs/classes-sources/org/webrtc)

[已修改的WebRTC的编译部分字节码](https://github.com/Kauid323/whip-streamer/tree/main/app/libs/patched_classes/org/webrtc/audio)

[已Patch支持采集内部音频的WebRTC AAR文件](https://github.com/Kauid323/whip-streamer/blob/main/app/libs/google-webrtc-1.0.32006-patched.aar)

# screenshot
![image](https://github.com/user-attachments/assets/94008479-4dd5-436f-a135-cb5349f064de)

![img](https://github.com/user-attachments/assets/dda68ad0-14e5-4306-8357-7f2bd6240a0a)

![image](https://github.com/user-attachments/assets/4cbfb36f-8822-410f-a0ed-6a061be3a9e4)


# 采集系统音频原理（Powered By AI）

- 不用麦克风，而是用 Android 10+ 的 AudioPlaybackCapture 把“系统正在播放的声音”当成一个 AudioRecord 的输入源，然后把这路 PCM 音频喂给 WebRTC 的音频采集链路，最终编码成 Opus 走 WebRTC 发送。

- MediaProjection 授权 + AudioPlaybackCapture = 系统音频录制输入流。

- WebRTC Android 的默认实现（WebRtcAudioRecord）一般会自己 new 一个 AudioRecord,音源是 VOICE_COMMUNICATION（麦克风路径）。

- 但“系统音频”必须用上面那套 AudioRecord.Builder().setAudioPlaybackCaptureConfig(...) 创建出来的 AudioRecord 才行，所以需要 WebRTC 提供一个注入点：

- JavaAudioDeviceModule.Builder.setAudioRecordFactory(...)

- 工厂负责按 WebRTC 要求的参数（sampleRate/channel/audioFormat/bufferSize）返回一个 AudioRecord

# 后记
> 大概就是这样，由于webrtc编译需要下一堆依赖，下载过程中因为梯子不好总是断流，所以才想出了直接让ai反编译改现成的aar，然后再次编译回去，所以不需要再次编译。



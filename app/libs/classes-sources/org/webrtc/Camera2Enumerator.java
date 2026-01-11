package org.webrtc;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Build;
import android.os.SystemClock;
import android.support.annotation.Nullable;
import android.util.AndroidException;
import android.util.Range;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.webrtc.CameraEnumerationAndroid;
import org.webrtc.CameraVideoCapturer;

@TargetApi(21)
/* loaded from: classes.jar:org/webrtc/Camera2Enumerator.class */
public class Camera2Enumerator implements CameraEnumerator {
    private static final String TAG = "Camera2Enumerator";
    private static final double NANO_SECONDS_PER_SECOND = 1.0E9d;
    private static final Map<String, List<CameraEnumerationAndroid.CaptureFormat>> cachedSupportedFormats = new HashMap();
    final Context context;

    @Nullable
    final CameraManager cameraManager;

    public Camera2Enumerator(Context context) {
        this.context = context;
        this.cameraManager = (CameraManager) context.getSystemService("camera");
    }

    @Override // org.webrtc.CameraEnumerator
    public String[] getDeviceNames() {
        try {
            return this.cameraManager.getCameraIdList();
        } catch (AndroidException e) {
            Logging.m2e(TAG, "Camera access exception: " + e);
            return new String[0];
        }
    }

    @Override // org.webrtc.CameraEnumerator
    public boolean isFrontFacing(String deviceName) {
        CameraCharacteristics characteristics = getCameraCharacteristics(deviceName);
        return characteristics != null && ((Integer) characteristics.get(CameraCharacteristics.LENS_FACING)).intValue() == 0;
    }

    @Override // org.webrtc.CameraEnumerator
    public boolean isBackFacing(String deviceName) {
        CameraCharacteristics characteristics = getCameraCharacteristics(deviceName);
        return characteristics != null && ((Integer) characteristics.get(CameraCharacteristics.LENS_FACING)).intValue() == 1;
    }

    @Override // org.webrtc.CameraEnumerator
    @Nullable
    public List<CameraEnumerationAndroid.CaptureFormat> getSupportedFormats(String deviceName) {
        return getSupportedFormats(this.context, deviceName);
    }

    @Override // org.webrtc.CameraEnumerator
    public CameraVideoCapturer createCapturer(String deviceName, CameraVideoCapturer.CameraEventsHandler eventsHandler) {
        return new Camera2Capturer(this.context, deviceName, eventsHandler);
    }

    @Nullable
    private CameraCharacteristics getCameraCharacteristics(String deviceName) {
        try {
            return this.cameraManager.getCameraCharacteristics(deviceName);
        } catch (AndroidException e) {
            Logging.m2e(TAG, "Camera access exception: " + e);
            return null;
        }
    }

    public static boolean isSupported(Context context) throws CameraAccessException {
        if (Build.VERSION.SDK_INT < 21) {
            return false;
        }
        CameraManager cameraManager = (CameraManager) context.getSystemService("camera");
        try {
            String[] cameraIds = cameraManager.getCameraIdList();
            for (String id : cameraIds) {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(id);
                if (((Integer) characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)).intValue() == 2) {
                    return false;
                }
            }
            return true;
        } catch (AndroidException e) {
            Logging.m2e(TAG, "Camera access exception: " + e);
            return false;
        }
    }

    static int getFpsUnitFactor(Range<Integer>[] fpsRanges) {
        return (fpsRanges.length != 0 && ((Integer) fpsRanges[0].getUpper()).intValue() >= 1000) ? 1 : 1000;
    }

    static List<Size> getSupportedSizes(CameraCharacteristics cameraCharacteristics) {
        StreamConfigurationMap streamMap = (StreamConfigurationMap) cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        int supportLevel = ((Integer) cameraCharacteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)).intValue();
        android.util.Size[] nativeSizes = streamMap.getOutputSizes(SurfaceTexture.class);
        List<Size> sizes = convertSizes(nativeSizes);
        if (Build.VERSION.SDK_INT < 22 && supportLevel == 2) {
            Rect activeArraySize = (Rect) cameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
            ArrayList<Size> filteredSizes = new ArrayList<>();
            for (Size size : sizes) {
                if (activeArraySize.width() * size.height == activeArraySize.height() * size.width) {
                    filteredSizes.add(size);
                }
            }
            return filteredSizes;
        }
        return sizes;
    }

    @Nullable
    static List<CameraEnumerationAndroid.CaptureFormat> getSupportedFormats(Context context, String cameraId) {
        return getSupportedFormats((CameraManager) context.getSystemService("camera"), cameraId);
    }

    @Nullable
    static List<CameraEnumerationAndroid.CaptureFormat> getSupportedFormats(CameraManager cameraManager, String cameraId) {
        int iRound;
        synchronized (cachedSupportedFormats) {
            if (cachedSupportedFormats.containsKey(cameraId)) {
                return cachedSupportedFormats.get(cameraId);
            }
            Logging.m1d(TAG, "Get supported formats for camera index " + cameraId + ".");
            long startTimeMs = SystemClock.elapsedRealtime();
            try {
                CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);
                StreamConfigurationMap streamMap = (StreamConfigurationMap) cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                Range<Integer>[] fpsRanges = (Range[]) cameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
                List<CameraEnumerationAndroid.CaptureFormat.FramerateRange> framerateRanges = convertFramerates(fpsRanges, getFpsUnitFactor(fpsRanges));
                List<Size> sizes = getSupportedSizes(cameraCharacteristics);
                int defaultMaxFps = 0;
                for (CameraEnumerationAndroid.CaptureFormat.FramerateRange framerateRange : framerateRanges) {
                    defaultMaxFps = Math.max(defaultMaxFps, framerateRange.max);
                }
                List<CameraEnumerationAndroid.CaptureFormat> formatList = new ArrayList<>();
                for (Size size : sizes) {
                    long minFrameDurationNs = 0;
                    try {
                        minFrameDurationNs = streamMap.getOutputMinFrameDuration(SurfaceTexture.class, new android.util.Size(size.width, size.height));
                    } catch (Exception e) {
                    }
                    if (minFrameDurationNs == 0) {
                        iRound = defaultMaxFps;
                    } else {
                        iRound = ((int) Math.round(NANO_SECONDS_PER_SECOND / minFrameDurationNs)) * 1000;
                    }
                    int maxFps = iRound;
                    formatList.add(new CameraEnumerationAndroid.CaptureFormat(size.width, size.height, 0, maxFps));
                    Logging.m1d(TAG, "Format: " + size.width + "x" + size.height + "@" + maxFps);
                }
                cachedSupportedFormats.put(cameraId, formatList);
                long endTimeMs = SystemClock.elapsedRealtime();
                Logging.m1d(TAG, "Get supported formats for camera index " + cameraId + " done. Time spent: " + (endTimeMs - startTimeMs) + " ms.");
                return formatList;
            } catch (Exception ex) {
                Logging.m2e(TAG, "getCameraCharacteristics(): " + ex);
                return new ArrayList();
            }
        }
    }

    private static List<Size> convertSizes(android.util.Size[] cameraSizes) {
        List<Size> sizes = new ArrayList<>();
        for (android.util.Size size : cameraSizes) {
            sizes.add(new Size(size.getWidth(), size.getHeight()));
        }
        return sizes;
    }

    static List<CameraEnumerationAndroid.CaptureFormat.FramerateRange> convertFramerates(Range<Integer>[] arrayRanges, int unitFactor) {
        List<CameraEnumerationAndroid.CaptureFormat.FramerateRange> ranges = new ArrayList<>();
        for (Range<Integer> range : arrayRanges) {
            ranges.add(new CameraEnumerationAndroid.CaptureFormat.FramerateRange(((Integer) range.getLower()).intValue() * unitFactor, ((Integer) range.getUpper()).intValue() * unitFactor));
        }
        return ranges;
    }
}

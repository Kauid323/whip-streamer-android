package org.webrtc;

/* loaded from: classes.jar:org/webrtc/NativeLibrary.class */
class NativeLibrary {
    private static String TAG = "NativeLibrary";
    private static Object lock = new Object();
    private static boolean libraryLoaded;

    NativeLibrary() {
    }

    /* loaded from: classes.jar:org/webrtc/NativeLibrary$DefaultLoader.class */
    static class DefaultLoader implements NativeLibraryLoader {
        DefaultLoader() {
        }

        @Override // org.webrtc.NativeLibraryLoader
        public boolean load(String name) {
            Logging.m1d(NativeLibrary.TAG, "Loading library: " + name);
            try {
                System.loadLibrary(name);
                return true;
            } catch (UnsatisfiedLinkError e) {
                Logging.m4e(NativeLibrary.TAG, "Failed to load native library: " + name, e);
                return false;
            }
        }
    }

    static void initialize(NativeLibraryLoader loader, String libraryName) {
        synchronized (lock) {
            if (libraryLoaded) {
                Logging.m1d(TAG, "Native library has already been loaded.");
            } else {
                Logging.m1d(TAG, "Loading native library: " + libraryName);
                libraryLoaded = loader.load(libraryName);
            }
        }
    }

    static boolean isLoaded() {
        boolean z;
        synchronized (lock) {
            z = libraryLoaded;
        }
        return z;
    }
}

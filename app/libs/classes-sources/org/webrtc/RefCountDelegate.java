package org.webrtc;

import android.support.annotation.Nullable;
import java.util.concurrent.atomic.AtomicInteger;

/* loaded from: classes.jar:org/webrtc/RefCountDelegate.class */
class RefCountDelegate implements RefCounted {
    private final AtomicInteger refCount = new AtomicInteger(1);

    @Nullable
    private final Runnable releaseCallback;

    public RefCountDelegate(@Nullable Runnable releaseCallback) {
        this.releaseCallback = releaseCallback;
    }

    @Override // org.webrtc.RefCounted
    public void retain() {
        int updated_count = this.refCount.incrementAndGet();
        if (updated_count < 2) {
            throw new IllegalStateException("retain() called on an object with refcount < 1");
        }
    }

    @Override // org.webrtc.RefCounted
    public void release() {
        int updated_count = this.refCount.decrementAndGet();
        if (updated_count < 0) {
            throw new IllegalStateException("release() called on an object with refcount < 1");
        }
        if (updated_count == 0 && this.releaseCallback != null) {
            this.releaseCallback.run();
        }
    }

    boolean safeRetain() {
        int i = this.refCount.get();
        while (true) {
            int currentRefCount = i;
            if (currentRefCount != 0) {
                if (this.refCount.weakCompareAndSet(currentRefCount, currentRefCount + 1)) {
                    return true;
                }
                i = this.refCount.get();
            } else {
                return false;
            }
        }
    }
}

package com.google.android.libraries.lens.ondevice.nativeapi;

import android.util.Log;

import java.util.Iterator;
import java.util.List;

public class LodeSplitRegistry {

    private static final String TAG = "LodeSplitRegistry";

    private volatile long nativeHandle;

    private native long nativeCreate();
    private native void nativeDestroy(long handle);
    private native void initializeOnDeviceSplitHandler(long handle, String moduleId);
    private native void initializePlayMlPackSplitHandler(long handle, String moduleId);

    public final synchronized long getNativeHandle() {
        return this.nativeHandle;
    }

    public final synchronized void destroy() {
        if (this.nativeHandle != 0) {
            Log.d(TAG, "Destroying LodeSplitRegistry. handle=" + this.nativeHandle);
            nativeDestroy(this.nativeHandle);
            this.nativeHandle = 0L;
            Log.d(TAG, "LodeSplitRegistry destroyed.");
        }
    }

    public final synchronized void create() {
        if (this.nativeHandle != 0) {
            Log.d(TAG, "Already initialized. handle=" + this.nativeHandle);
            return;
        }
        Log.d(TAG, "Creating LodeSplitRegistry.");
        this.nativeHandle = nativeCreate();
        Log.d(TAG, "LodeSplitRegistry initialized. handle=" + this.nativeHandle);
        if (this.nativeHandle == 0) {
            Log.w(TAG, "nativeCreate returned 0! Engine init will fail.");
        }
    }

    public final synchronized void registerModules(List<String> modules) {
        if (this.nativeHandle == 0) {
            Log.w(TAG, "Registry not created yet. Creating now...");
            create();
        }
        Log.d(TAG, "registerModules: handle=" + this.nativeHandle + " modules=" + modules);
        Iterator<String> it = modules.iterator();
        while (it.hasNext()) {
            String mod = it.next();
            Log.d(TAG, "Checking module: " + mod + " (hashCode=" + mod.hashCode() + ")");
            if (mod.equals("lens_ondevice_engine_play_ml_module")) {
                Log.d(TAG, "Calling initializePlayMlPackSplitHandler for: " + mod);
                try {
                    initializePlayMlPackSplitHandler(this.nativeHandle, mod);
                    Log.d(TAG, "initializePlayMlPackSplitHandler completed OK for: " + mod);
                } catch (Throwable t) {
                    Log.e(TAG, "initializePlayMlPackSplitHandler FAILED for: " + mod, t);
                }
            } else if (mod.equals("lens_ondevice_engine_feature_module")) {
                Log.d(TAG, "Calling initializeOnDeviceSplitHandler for: " + mod);
                try {
                    initializeOnDeviceSplitHandler(this.nativeHandle, mod);
                    Log.d(TAG, "initializeOnDeviceSplitHandler completed OK for: " + mod);
                } catch (Throwable t) {
                    Log.e(TAG, "initializeOnDeviceSplitHandler FAILED for: " + mod, t);
                }
            } else {
                Log.w(TAG, "Unknown module: " + mod);
            }
        }
    }
}

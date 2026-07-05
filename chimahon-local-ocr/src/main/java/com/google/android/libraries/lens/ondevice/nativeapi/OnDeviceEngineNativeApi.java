package com.google.android.libraries.lens.ondevice.nativeapi;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import java.nio.charset.StandardCharsets;

public class OnDeviceEngineNativeApi {

    private static final String TAG = "LENS_OCR";
    private static final String MODULE_ID  = "lens_ondevice_engine_play_ml_module";
    private static final String ASSET_PATH = "screenai_models/";
    private static final int    ENGINE_TYPE_MEDIAPIPE = 12;

    private long nativeHandle;
    private Throwable initStack;

    private static native long nativeCreateForMultiSplits(
            Context context, byte[] initProto, long registryHandle, String moduleId);
    private native void nativeDestroyForMultiSplits(long handle);
    private native byte[] nativeSendRequestForMultiSplits(
            long handle, byte[] requestProto);
    private native byte[] nativeSendRequestWithBitmapForMultiSplits(
            long handle, byte[] requestProto, Bitmap bitmap);

    public void init(Context context, LodeSplitRegistry registry) {
        Log.d(TAG, "=== nativeCreateForMultiSplits entry ===");
        Log.d(TAG, "  context=" + context + " (" + context.getClass().getName() + ")");
        Log.d(TAG, "  registry.handle=" + registry.getNativeHandle());
        Log.d(TAG, "  cacheDir=" + context.getCacheDir().getAbsolutePath());
        Log.d(TAG, "  moduleId=" + MODULE_ID);

        byte[] proto = buildInitProto(context);
        Log.d(TAG, "  initProto(" + proto.length + "b): " + hex(proto));
        Log.d(TAG, "  field15 (moduleId)=" + MODULE_ID);

        this.initStack = new Throwable("init stack trace");

        long result = nativeCreateForMultiSplits(
                context, proto, registry.getNativeHandle(), MODULE_ID);
        Log.d(TAG, "=== nativeCreateForMultiSplits result ===");
        Log.d(TAG, "  returned handle=" + result);

        if (result == 0) {
            Log.e(TAG, "  FAILED! Engine handle=0.");
            Log.e(TAG, "  Check asset_manager_util.cc:158 RET_CHECK in logcat above.");
            Log.e(TAG, "  Common causes:");
            Log.e(TAG, "    - Context.getCacheDir() returns null or blocked by MIUI");
            Log.e(TAG, "    - java ActivityThread.currentApplication() hidden API restricted");
            Log.e(TAG, "    - AAssetManager_fromJava failed");
            Log.e(TAG, "    - proto field2 should be empty submessage (12 00)");
        } else {
            Log.d(TAG, "  OK. engine handle=" + result);
        }
        this.nativeHandle = result;
    }

    public byte[] sendRequest(byte[] requestProto, Bitmap bitmap) {
        if (nativeHandle == 0) {
            Log.e(TAG, "sendRequest: not initialized");
            if (initStack != null) {
                Log.e(TAG, "  init was called from:", initStack);
            }
            return null;
        }
        Log.d(TAG, "sendRequest: handle=" + nativeHandle + " req=" + hex(requestProto));
        byte[] result = nativeSendRequestWithBitmapForMultiSplits(nativeHandle, requestProto, bitmap);
        Log.d(TAG, "  response(" + (result != null ? result.length : -1) + "b): " +
              (result != null ? hex(result) : "null"));
        return result;
    }

    public void destroy() {
        if (nativeHandle != 0) {
            nativeDestroyForMultiSplits(nativeHandle);
            nativeHandle = 0;
        }
    }

    public long getNativeHandle() {
        return nativeHandle;
    }

    private static byte[] buildInitProto(Context context) {
        byte[] modBytes   = MODULE_ID.getBytes(StandardCharsets.UTF_8);
        String copiedAssetRoot = context.getFilesDir().getAbsolutePath()
                + "/screenai_models/";
        byte[] mediaPipeConfigBytes = (copiedAssetRoot + "lots_multiscript_v8_runner_patched.binarypb")
                .getBytes(StandardCharsets.UTF_8);
        byte[] copiedAssetRootBytes = copiedAssetRoot.getBytes(StandardCharsets.UTF_8);

        // Lfska submessage
        byte[] ldhjj = encodeBytes(2, modBytes);
        byte[] ldhjs = encodeBytes(1, ldhjj);
        byte[] lfska = encodeBytes(5, ldhjs);

        // MediaPipe OCR config
        byte[] assetDirectoryConfig = concat(
                encodeBytes(1, copiedAssetRootBytes),
                encodeBytes(2, modBytes));
        byte[] mediaPipeConfig = concat(
                encodeBytes(1, mediaPipeConfigBytes),
                encodeBytes(14, assetDirectoryConfig));

        // Empty case selector for engine_type
        byte[] field2 = new byte[]{(byte)0x12, (byte)0x00};

        // Engine type = 12
        byte[] field5 = encodeBytes(5, encodeVarintField(1, ENGINE_TYPE_MEDIAPIPE));

        return concat(
            field2,
            encodeBytes(4,  lfska),
            field5,
            encodeBytes(12, mediaPipeConfig),
            encodeBytes(15, modBytes)
        );
    }

    private static byte[] encodeBytes(int field, byte[] data) {
        return concat(varint((field << 3) | 2), varint(data.length), data);
    }

    private static byte[] encodeVarintField(int field, int value) {
        return concat(varint(field << 3), varint(value));
    }

    private static byte[] varint(int v) {
        byte[] tmp = new byte[5]; int n = 0;
        while (v > 0x7F) { tmp[n++] = (byte)((v & 0x7F) | 0x80); v >>>= 7; }
        tmp[n++] = (byte) v;
        byte[] out = new byte[n];
        System.arraycopy(tmp, 0, out, 0, n);
        return out;
    }

    private static byte[] concat(byte[]... parts) {
        int len = 0; for (byte[] p : parts) len += p.length;
        byte[] out = new byte[len]; int pos = 0;
        for (byte[] p : parts) { System.arraycopy(p, 0, out, pos, p.length); pos += p.length; }
        return out;
    }

    public static String hex(byte[] b) {
        if (b == null) return "null";
        StringBuilder s = new StringBuilder();
        for (byte x : b) s.append(String.format("%02X", x));
        return s.toString();
    }
}

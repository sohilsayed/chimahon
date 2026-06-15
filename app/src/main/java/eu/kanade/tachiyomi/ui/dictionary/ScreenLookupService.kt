package eu.kanade.tachiyomi.ui.dictionary

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.view.Display
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.notificationBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import kotlin.coroutines.resume
import kotlin.math.roundToInt

object ScreenLookupServiceState {
    val isRunning = MutableStateFlow(false)
}

class ScreenLookupService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var projection: MediaProjection? = null
    private var projectionCallback: MediaProjection.Callback? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var virtualDisplaySize: CaptureSize? = null
    private var lastCaptureError: String? = null
    private var floatingButton: View? = null
    private var floatingButtonParams: WindowManager.LayoutParams? = null
    private var captureJob: Job? = null
    private var overlayController: ScreenLookupOverlayController? = null

    private val windowManager: WindowManager
        get() = getSystemService()!!

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val resultData = intent.getProjectionIntent()
                if (resultCode == 0 || resultData == null) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                if (!Settings.canDrawOverlays(this)) {
                    Toast.makeText(this, this.stringResource(MR.strings.screen_lookup_overlay_required), Toast.LENGTH_LONG).show()
                    stopSelf()
                    return START_NOT_STICKY
                }
                startLookupForeground()
                if (!startProjection(resultCode, resultData)) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                showFloatingButton()
                ScreenLookupServiceState.isRunning.value = true
                ScreenLookupTileService.requestUpdate(this)
            }
            ACTION_CAPTURE -> captureFromButton()
            ACTION_SHOW_BUTTON -> setFloatingButtonVisible(true)
            ACTION_STOP -> stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        captureJob?.cancel()
        overlayController?.release()
        overlayController = null
        scope.cancel()
        removeFloatingButton()
        releaseProjection()
        ScreenLookupServiceState.isRunning.value = false
        ScreenLookupTileService.requestUpdate(this)
        super.onDestroy()
    }

    private fun startLookupForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, ScreenLookupService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return notificationBuilder(Notifications.CHANNEL_COMMON) {
            setSmallIcon(R.drawable.ic_chimahon)
            setContentTitle(this@ScreenLookupService.stringResource(MR.strings.screen_lookup_notification_title))
            setContentText(this@ScreenLookupService.stringResource(MR.strings.screen_lookup_notification_text))
            setOngoing(true)
            setShowWhen(false)
            setCategory(NotificationCompat.CATEGORY_SERVICE)
            setPriority(NotificationCompat.PRIORITY_LOW)
            addAction(
                R.drawable.ic_close_24dp,
                this@ScreenLookupService.stringResource(MR.strings.action_stop),
                stopIntent,
            )
        }.build()
    }

    private fun startProjection(resultCode: Int, resultData: Intent): Boolean {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, this.stringResource(MR.strings.screen_lookup_overlay_required), Toast.LENGTH_LONG).show()
            return false
        }
        if (projection != null) return true

        val manager = getSystemService<MediaProjectionManager>() ?: return false
        val nextProjection = runCatching { manager.getMediaProjection(resultCode, resultData) }
            .onFailure { logcat(LogPriority.ERROR, it) { "Failed to start media projection" } }
            .getOrNull()
            ?: return false

        val callback = object : MediaProjection.Callback() {
            override fun onStop() {
                scope.launch { stopSelf() }
            }

            override fun onCapturedContentResize(width: Int, height: Int) {
                if (width > 0 && height > 0) {
                    mainHandler.post { resizeVirtualDisplay(CaptureSize(width, height)) }
                }
            }
        }
        nextProjection.registerCallback(callback, mainHandler)
        projection = nextProjection
        projectionCallback = callback
        return ensureVirtualDisplay()
    }

    private fun ensureVirtualDisplay(): Boolean {
        val mediaProjection = projection ?: return false
        val size = captureSize()
        if (virtualDisplay != null && imageReader != null) {
            if (virtualDisplaySize != size) resizeVirtualDisplay(size)
            return true
        }

        val reader = ImageReader.newInstance(size.width, size.height, PixelFormat.RGBA_8888, 2)
        imageReader = reader
        virtualDisplaySize = size
        virtualDisplay = runCatching {
            mediaProjection.createVirtualDisplay(
                "ChimahonScreenLookup",
                size.width,
                size.height,
                resources.configuration.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                mainHandler,
            )
        }.onFailure {
            logcat(LogPriority.ERROR, it) { "Failed to create screen lookup virtual display" }
        }.getOrNull()
        return virtualDisplay != null
    }

    private fun resizeVirtualDisplay(size: CaptureSize) {
        val display = virtualDisplay ?: return
        imageReader?.setOnImageAvailableListener(null, null)
        imageReader?.close()

        val reader = ImageReader.newInstance(size.width, size.height, PixelFormat.RGBA_8888, 2)
        imageReader = reader
        virtualDisplaySize = size
        display.resize(size.width, size.height, resources.configuration.densityDpi)
        display.setSurface(reader.surface)
    }

    private fun captureFromButton() {
        if (overlayController?.isShowing == true) {
            overlayController?.dismiss()
            return
        }
        if (captureJob?.isActive == true) return

        captureJob = scope.launch {
            setFloatingButtonVisible(false)

            val bitmap = captureWithProjection()
            if (bitmap == null) {
                setFloatingButtonVisible(true)
                toast(MR.strings.screen_lookup_capture_failed)
                lastCaptureError?.let { msg ->
                    Toast.makeText(this@ScreenLookupService, "Error: $msg", Toast.LENGTH_LONG).show()
                }
                return@launch
            }

            showLookupOverlay(bitmap)
        }
    }

    private suspend fun captureWithProjection(): Bitmap? {
        lastCaptureError = null
        if (projection == null) return null
        if (!ensureVirtualDisplay()) return null

        delay(64)
        return withContext(Dispatchers.Default) {
            runCatching { acquireBitmap() }
                .onFailure { e ->
                    lastCaptureError = e.message
                    logcat(LogPriority.ERROR, e) { "capture failed" }
                }
                .getOrNull()
        }
    }

    private suspend fun acquireBitmap(): Bitmap? {
        val reader = imageReader ?: return null
        var image = reader.acquireLatestImage()
        if (image == null) {
            delay(48)
            image = reader.acquireLatestImage() ?: return null
        }
        return image.use { it.toBitmap() }
    }

    private fun showLookupOverlay(bitmap: Bitmap) {
        val controller = overlayController ?: ScreenLookupOverlayController(
            context = this,
            windowManager = windowManager,
            onDismiss = { setFloatingButtonVisible(true) },
            onRecapture = { captureFromButton() },
        ).also { overlayController = it }
        controller.show(bitmap)
    }

    private fun Image.toBitmap(): Bitmap {
        val plane = planes.first()
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * width
        val paddedWidth = width + rowPadding / pixelStride
        val paddedBitmap = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
        paddedBitmap.copyPixelsFromBuffer(buffer)
        if (paddedWidth == width) return paddedBitmap

        return Bitmap.createBitmap(paddedBitmap, 0, 0, width, height).also {
            paddedBitmap.recycle()
        }
    }

    private fun showFloatingButton() {
        if (floatingButton != null || !Settings.canDrawOverlays(this)) return

        val size = BUTTON_SIZE_DP.dp
        val button = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(ContextCompat.getColor(this@ScreenLookupService, R.color.tachiyomi_primary))
            }
            elevation = 8.dp.toFloat()
            alpha = BUTTON_ALPHA
            contentDescription = this@ScreenLookupService.stringResource(MR.strings.screen_lookup_capture_button)
            addView(
                ImageView(this@ScreenLookupService).apply {
                    setImageResource(R.drawable.ic_chimahon)
                    imageTintList = ContextCompat.getColorStateList(this@ScreenLookupService, R.color.tachiyomi_onPrimary)
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                    setPadding(14.dp, 14.dp, 14.dp, 14.dp)
                },
                FrameLayout.LayoutParams(size, size),
            )
        }

        val metrics = captureSize()
        val params = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = metrics.width - size - 16.dp
            y = (metrics.height * 0.42f).roundToInt()
        }

        button.installDragHandler(params)
        floatingButton = button
        floatingButtonParams = params
        windowManager.addView(button, params)
    }

    private fun View.installDragHandler(params: WindowManager.LayoutParams) {
        val touchSlop = 8.dp
        var downRawX = 0f
        var downRawY = 0f
        var startX = 0
        var startY = 0
        var moved = false

        setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downRawX).roundToInt()
                    val dy = (event.rawY - downRawY).roundToInt()
                    moved = moved || kotlin.math.abs(dx) > touchSlop || kotlin.math.abs(dy) > touchSlop
                    val bounds = captureSize()
                    params.x = (startX + dx).coerceIn(0, (bounds.width - params.width).coerceAtLeast(0))
                    params.y = (startY + dy).coerceIn(0, (bounds.height - params.height).coerceAtLeast(0))
                    runCatching { windowManager.updateViewLayout(this, params) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) captureFromButton()
                    true
                }
                else -> false
            }
        }
    }

    private fun setFloatingButtonVisible(visible: Boolean) {
        floatingButton?.apply {
            alpha = if (visible) BUTTON_ALPHA else 0f
            isEnabled = visible
        }
    }

    private fun removeFloatingButton() {
        floatingButton?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        floatingButton = null
        floatingButtonParams = null
    }

    private fun releaseProjection() {
        imageReader?.setOnImageAvailableListener(null, null)
        virtualDisplay?.release()
        virtualDisplay = null
        virtualDisplaySize = null
        imageReader?.close()
        imageReader = null

        val callback = projectionCallback
        val currentProjection = projection
        if (callback != null && currentProjection != null) {
            runCatching { currentProjection.unregisterCallback(callback) }
        }
        projectionCallback = null
        runCatching { projection?.stop() }
        projection = null
    }

    private fun captureSize(): CaptureSize {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val display = getSystemService<DisplayManager>()
                    ?.getDisplay(Display.DEFAULT_DISPLAY)
                if (display != null) {
                    val wm = createDisplayContext(display)
                        .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
                        .getSystemService<WindowManager>()
                    val bounds = wm?.currentWindowMetrics?.bounds
                    if (bounds != null && bounds.width() > 0 && bounds.height() > 0) {
                        return CaptureSize(bounds.width(), bounds.height())
                    }
                }
            } catch (_: Exception) {}
        }
        val metrics = resources.displayMetrics
        return CaptureSize(metrics.widthPixels, metrics.heightPixels)
    }

    private fun Intent.getProjectionIntent(): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(EXTRA_RESULT_DATA)
        }
    }

    private fun toast(stringRes: dev.icerock.moko.resources.StringResource) {
        Toast.makeText(this, this.stringResource(stringRes), Toast.LENGTH_SHORT).show()
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).roundToInt()

    private data class CaptureSize(
        val width: Int,
        val height: Int,
    )

    companion object {
        private const val ACTION_START = "eu.kanade.tachiyomi.dictionary.SCREEN_LOOKUP_START"
        private const val ACTION_STOP = "eu.kanade.tachiyomi.dictionary.SCREEN_LOOKUP_STOP"
        private const val ACTION_SHOW_BUTTON = "eu.kanade.tachiyomi.dictionary.SCREEN_LOOKUP_SHOW_BUTTON"
        private const val ACTION_CAPTURE = "eu.kanade.tachiyomi.dictionary.SCREEN_LOOKUP_CAPTURE"
        private const val EXTRA_RESULT_CODE = "result_code"
        private const val EXTRA_RESULT_DATA = "result_data"
        private const val NOTIFICATION_ID = 320_420
        private const val BUTTON_SIZE_DP = 56
        private const val BUTTON_ALPHA = 0.92f
        private const val IMAGE_TIMEOUT_MS = 1_500L

        fun start(context: Context, resultCode: Int, resultData: Intent) {
            val intent = Intent(context, ScreenLookupService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_RESULT_DATA, resultData)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, ScreenLookupService::class.java).setAction(ACTION_STOP),
            )
        }

        fun showButton(context: Context) {
            context.startService(
                Intent(context, ScreenLookupService::class.java).setAction(ACTION_SHOW_BUTTON),
            )
        }

        fun capture(context: Context) {
            context.startService(
                Intent(context, ScreenLookupService::class.java).setAction(ACTION_CAPTURE),
            )
        }
    }
}

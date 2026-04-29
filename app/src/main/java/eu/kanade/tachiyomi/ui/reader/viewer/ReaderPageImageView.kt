package eu.kanade.tachiyomi.ui.reader.viewer

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.drawable.Animatable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.annotation.AttrRes
import androidx.annotation.CallSuper
import androidx.annotation.StyleRes
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.os.postDelayed
import androidx.core.view.isVisible
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import chimahon.DictionaryRepository
import coil3.BitmapImage
import coil3.asDrawable
import coil3.dispose
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.size.Precision
import coil3.size.ViewSizeResolver
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView.EASE_IN_OUT_QUAD
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView.EASE_OUT_QUAD
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE
import com.github.chrisbanes.photoview.PhotoView
import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.data.coil.cropBorders
import eu.kanade.tachiyomi.data.coil.customDecoder
import eu.kanade.tachiyomi.ui.dictionary.getDictionaryBootstrapHtml
import eu.kanade.tachiyomi.ui.dictionary.getDictionaryPaths
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences.LandscapeZoomScaleType
import eu.kanade.tachiyomi.util.system.animatorDurationScale
import eu.kanade.tachiyomi.util.view.isVisibleOnScreen
import logcat.LogPriority
import okio.BufferedSource
import tachiyomi.core.common.util.system.ImageUtil
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * A wrapper view for showing page image.
 *
 * Animated image will be drawn by [PhotoView] while [SubsamplingScaleImageView] will take non-animated image.
 *
 * @param isWebtoon if true, [WebtoonSubsamplingImageView] will be used instead of [SubsamplingScaleImageView]
 * and [AppCompatImageView] will be used instead of [PhotoView]
 */
open class ReaderPageImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttrs: Int = 0,
    @StyleRes defStyleRes: Int = 0,
    private val isWebtoon: Boolean = false,
) : FrameLayout(context, attrs, defStyleAttrs, defStyleRes) {

    private val alwaysDecodeLongStripWithSSIV by lazy {
        Injekt.get<BasePreferences>().alwaysDecodeLongStripWithSSIV().get()
    }

    internal var pageView: View? = null

    private var config: Config? = null

    // ==================== OCR State ====================
    var ocrEnabled: Boolean = false
        set(value) {
            field = value
            logcat { "OCR toggle: enabled=$value blocks=${ocrBlocks.size}" }
            if (!value) {
                activeOcrBlock = null
                ocrLayoutCache = null
                ocrPopupLookupString = null
                onDismissOcrPopup?.invoke()
            }
            (pageView as? SubsamplingScaleImageView)?.invalidate()
        }

    var ocrOutlineVisible: Boolean = false
        set(value) {
            field = value
            (pageView as? SubsamplingScaleImageView)?.invalidate()
        }

    var ocrBoxScale: Float = 1.0f
        set(value) {
            field = value
            (pageView as? SubsamplingScaleImageView)?.invalidate()
        }

    internal var ocrBlocks: List<OcrTextBlock> = emptyList()
    internal var activeOcrBlock: OcrTextBlock? = null
    internal var ocrLayoutCache: Pair<OcrTextBlock, StaticLayout>? = null
    internal var ocrPopupLookupString: String? = null

    var onOcrLookup: ((String) -> Unit)? = null
    var onDismissOcrPopup: (() -> Unit)? = null

    var onShowOcrPopup: (
        (
            lookupString: String,
            fullText: String,
            charOffset: Int,
            screenX: Float,
            screenY: Float,
            mediaInfo: chimahon.MediaInfo?,
        ) -> Unit
    )? = null

    var onImageLoaded: (() -> Unit)? = null
    var onImageLoadError: ((Throwable?) -> Unit)? = null
    var onScaleChanged: ((newScale: Float) -> Unit)? = null
    var onViewClicked: (() -> Unit)? = null

    /**
     * For automatic background. Will be set as background color when [onImageLoaded] is called.
     */
    var pageBackground: Drawable? = null

    @CallSuper
    open fun onImageLoaded() {
        onImageLoaded?.invoke()
        background = pageBackground
    }

    @CallSuper
    open fun onImageLoadError(error: Throwable?) {
        onImageLoadError?.invoke(error)
    }

    @CallSuper
    open fun onScaleChanged(newScale: Float) {
        onScaleChanged?.invoke(newScale)
        dismissActiveOcrBlock()
    }

    @CallSuper
    open fun onViewClicked() {
        onViewClicked?.invoke()
    }

    open fun onPageSelected(forward: Boolean) {
        with(pageView as? SubsamplingScaleImageView) {
            if (this == null) return
            if (isReady) {
                landscapeZoom(forward)
            } else {
                setOnImageEventListener(
                    object : SubsamplingScaleImageView.DefaultOnImageEventListener() {
                        override fun onReady() {
                            setupZoom(config)
                            landscapeZoom(forward)
                            this@ReaderPageImageView.onImageLoaded()
                        }

                        override fun onImageLoadError(e: Exception) {
                            onImageLoadError(e)
                        }
                    },
                )
            }
        }
    }

    private fun SubsamplingScaleImageView.landscapeZoom(forward: Boolean) {
        val config = config
        if (config != null &&
            config.landscapeZoom &&
            config.minimumScaleType == SCALE_TYPE_CENTER_INSIDE &&
            sWidth > sHeight &&
            scale == minScale
        ) {
            handler?.postDelayed(500) {
                val point = when (config.zoomStartPosition) {
                    ZoomStartPosition.LEFT -> if (forward) PointF(0F, 0F) else PointF(sWidth.toFloat(), 0F)
                    ZoomStartPosition.RIGHT -> if (forward) PointF(sWidth.toFloat(), 0F) else PointF(0F, 0F)
                    ZoomStartPosition.CENTER -> center
                }

                val targetScale = /* KMK --> */ when (config.landscapeZoomScaleType) {
                    LandscapeZoomScaleType.DOUBLE -> scale * 2
                    // KMK <--
                    else -> height.toFloat() / sHeight.toFloat()
                }
                (animateScaleAndCenter(targetScale, point) ?: return@postDelayed)
                    .withDuration(500)
                    .withEasing(EASE_IN_OUT_QUAD)
                    .withInterruptible(true)
                    .start()
            }
        }
    }

    fun setImage(drawable: Drawable, config: Config) {
        this.config = config
        if (drawable is Animatable) {
            prepareAnimatedImageView()
            setAnimatedImage(drawable, config)
        } else {
            prepareNonAnimatedImageView()
            setNonAnimatedImage(drawable, config)
        }
    }

    fun setImage(source: BufferedSource, isAnimated: Boolean, config: Config) {
        this.config = config
        if (isAnimated) {
            prepareAnimatedImageView()
            setAnimatedImage(source, config)
        } else {
            prepareNonAnimatedImageView()
            setNonAnimatedImage(source, config)
        }
    }

    fun recycle() = pageView?.let {
        when (it) {
            is SubsamplingScaleImageView -> it.recycle()
            is AppCompatImageView -> it.dispose()
        }
        it.isVisible = false
    }

    /**
     * Captures the currently visible page content as a bitmap at screen resolution.
     */
    fun captureVisibleBitmap(): android.graphics.Bitmap? {
        val view = pageView ?: return null
        return try {
            val local = android.graphics.Rect()
            if (!view.getLocalVisibleRect(local)) return null

            val width = local.width()
            val height = local.height()
            if (width <= 0 || height <= 0) return null

            // Cap the bitmap size to double the screen size just in case,
            // to avoid OOM even with faulty visible rects.
            val screenMetrics = context.resources.displayMetrics
            val maxWidth = screenMetrics.widthPixels * 2
            val maxHeight = screenMetrics.heightPixels * 2
            val finalWidth = width.coerceAtMost(maxWidth)
            val finalHeight = height.coerceAtMost(maxHeight)

            val bitmap = android.graphics.Bitmap.createBitmap(finalWidth, finalHeight, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)

            // Translate the canvas so that the top-left of the visible rect
            // aligns with (0,0) in our new bitmap.
            canvas.translate(-local.left.toFloat(), -local.top.toFloat())

            view.draw(canvas)
            bitmap
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to capture visible bitmap" }
            null
        }
    }

    /**
     * Check if the image can be panned to the left
     */
    fun canPanLeft(): Boolean = canPan { it.left }

    /**
     * Check if the image can be panned to the right
     */
    fun canPanRight(): Boolean = canPan { it.right }

    // KMK -->
    /**
     * Check if the image can be panned up
     */
    fun canPanUp(): Boolean = canPan { it.top }

    /**
     * Check if the image can be panned down
     */
    fun canPanDown(): Boolean = canPan { it.bottom }
    // KMK <--

    /**
     * Check whether the image can be panned.
     * @param fn a function that returns the direction to check for
     */
    private fun canPan(fn: (RectF) -> Float): Boolean {
        (pageView as? SubsamplingScaleImageView)?.let { view ->
            RectF().let {
                view.getPanRemaining(it)
                return fn(it) > 1
            }
        }
        return false
    }

    /**
     * Pans the image to the left by a screen's width worth.
     */
    fun panLeft() {
        pan { center, view -> center.also { it.x -= view.width / view.scale } }
    }

    /**
     * Pans the image to the right by a screen's width worth.
     */
    fun panRight() {
        pan { center, view -> center.also { it.x += view.width / view.scale } }
    }

    // KMK -->
    /**
     * Pans the image down by a screen's height worth.
     */
    fun panDown() {
        pan { center, view -> center.also { it.y += view.height / view.scale } }
    }

    /**
     * Pans the image up by a screen's height worth.
     */
    fun panUp() {
        pan { center, view -> center.also { it.y -= view.height / view.scale } }
    }
    // KMK <--

    /**
     * Pans the image.
     * @param fn a function that computes the new center of the image
     */
    private fun pan(fn: (PointF, SubsamplingScaleImageView) -> PointF) {
        (pageView as? SubsamplingScaleImageView)?.let { view ->

            val target = fn(view.center ?: return, view)
            view.animateCenter(target)!!
                .withEasing(EASE_OUT_QUAD)
                .withDuration(250)
                .withInterruptible(true)
                .start()
        }
    }

    private fun prepareNonAnimatedImageView() {
        if (pageView is SubsamplingScaleImageView) return
        removeView(pageView)

        pageView = OcrSubsamplingImageView(context).also {
            it.ocrHost = this@ReaderPageImageView
            it.forwardTouchToSuper = !isWebtoon
        }.apply {
            setMaxTileSize(ImageUtil.hardwareBitmapThreshold)
            setDoubleTapZoomStyle(SubsamplingScaleImageView.ZOOM_FOCUS_CENTER)
            setPanLimit(SubsamplingScaleImageView.PAN_LIMIT_INSIDE)
            setMinimumTileDpi(180)
            setOnStateChangedListener(
                object : SubsamplingScaleImageView.OnStateChangedListener {
                    override fun onScaleChanged(newScale: Float, origin: Int) {
                        this@ReaderPageImageView.onScaleChanged(newScale)
                    }

                    override fun onCenterChanged(newCenter: PointF?, origin: Int) {
                        dismissActiveOcrBlock()
                    }
                },
            )
            setOnClickListener { this@ReaderPageImageView.onViewClicked() }
        }
        // In webtoon mode, use WRAP_CONTENT height to respect image dimensions
        // In paged mode, use MATCH_PARENT to fill the pager
        val ssivWidth = MATCH_PARENT
        val ssivHeight = if (isWebtoon) WRAP_CONTENT else MATCH_PARENT
        addView(pageView, ssivWidth, ssivHeight)
    }

    private fun SubsamplingScaleImageView.setupZoom(config: Config?) {
        // 5x zoom
        maxScale = scale * MAX_ZOOM_SCALE
        // KMK -->
        if (config?.disableZoomIn == true) {
            isZoomEnabled = false
        } else {
            if (config?.doubleTapZoom == false) {
                setDoubleTapZoomScale(scale)
            } else {
                // KMK <--
                setDoubleTapZoomScale(scale * 2)
            }
        }

        when (config?.zoomStartPosition) {
            ZoomStartPosition.LEFT -> setScaleAndCenter(scale, PointF(0F, 0F))
            ZoomStartPosition.RIGHT -> setScaleAndCenter(scale, PointF(sWidth.toFloat(), 0F))
            ZoomStartPosition.CENTER -> setScaleAndCenter(scale, center)
            null -> {}
        }
    }

    private fun setNonAnimatedImage(
        data: Any,
        config: Config,
    ) = (pageView as? SubsamplingScaleImageView)?.apply {
        setDoubleTapZoomDuration(config.zoomDuration.getSystemScaledDuration())
        setMinimumScaleType(config.minimumScaleType)
        setMinimumDpi(1) // Just so that very small image will be fit for initial load
        setCropBorders(config.cropBorders)
        setOnImageEventListener(
            object : SubsamplingScaleImageView.DefaultOnImageEventListener() {
                override fun onReady() {
                    setupZoom(config)
                    if (isVisibleOnScreen()) landscapeZoom(true)
                    this@ReaderPageImageView.onImageLoaded()
                }

                override fun onImageLoadError(e: Exception) {
                    this@ReaderPageImageView.onImageLoadError(e)
                }
            },
        )

        when (data) {
            is BitmapDrawable -> {
                setImage(ImageSource.bitmap(data.bitmap))
                isVisible = true
            }
            is BufferedSource -> {
                if (!isWebtoon || alwaysDecodeLongStripWithSSIV) {
                    setHardwareConfig(ImageUtil.canUseHardwareBitmap(data))
                    setImage(ImageSource.inputStream(data.inputStream()))
                    isVisible = true
                    return@apply
                }

                ImageRequest.Builder(context)
                    .data(data)
                    .memoryCachePolicy(CachePolicy.DISABLED)
                    .diskCachePolicy(CachePolicy.DISABLED)
                    .target(
                        onSuccess = { result ->
                            val image = result as BitmapImage
                            setImage(ImageSource.bitmap(image.bitmap))
                            isVisible = true
                        },
                    )
                    .listener(
                        onError = { _, result ->
                            onImageLoadError(result.throwable)
                        },
                    )
                    .size(ViewSizeResolver(this@ReaderPageImageView))
                    .precision(Precision.INEXACT)
                    .cropBorders(config.cropBorders)
                    .customDecoder(true)
                    .crossfade(false)
                    .build()
                    .let(context.imageLoader::enqueue)
            }
            else -> {
                throw IllegalArgumentException("Not implemented for class ${data::class.simpleName}")
            }
        }
    }

    private fun prepareAnimatedImageView() {
        if (pageView is AppCompatImageView) return
        removeView(pageView)

        pageView = if (isWebtoon) {
            AppCompatImageView(context)
        } else {
            PhotoView(context)
        }.apply {
            adjustViewBounds = true

            if (this is PhotoView) {
                setScaleLevels(1F, 2F, MAX_ZOOM_SCALE)
                // Force 2 scale levels on double tap
                setOnDoubleTapListener(
                    object : GestureDetector.SimpleOnGestureListener() {
                        override fun onDoubleTap(e: MotionEvent): Boolean {
                            if (scale > 1F) {
                                setScale(1F, e.x, e.y, true)
                            } else {
                                setScale(2F, e.x, e.y, true)
                            }
                            return true
                        }

                        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                            this@ReaderPageImageView.onViewClicked()
                            return super.onSingleTapConfirmed(e)
                        }
                    },
                )
                setOnScaleChangeListener { _, _, _ ->
                    this@ReaderPageImageView.onScaleChanged(scale)
                }
            }
        }
        addView(pageView, MATCH_PARENT, MATCH_PARENT)
    }

    private fun setAnimatedImage(
        data: Any,
        config: Config,
    ) = (pageView as? AppCompatImageView)?.apply {
        if (this is PhotoView) {
            setZoomTransitionDuration(config.zoomDuration.getSystemScaledDuration())
        }

        val request = ImageRequest.Builder(context)
            .data(data)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .diskCachePolicy(CachePolicy.DISABLED)
            .target(
                onSuccess = { result ->
                    val drawable = result.asDrawable(context.resources)
                    setImageDrawable(drawable)
                    (drawable as? Animatable)?.start()
                    isVisible = true
                    this@ReaderPageImageView.onImageLoaded()
                },
            )
            .listener(
                onError = { _, result ->
                    onImageLoadError(result.throwable)
                },
            )
            .crossfade(false)
            // KMK -->
            .allowHardware(false) // Disable hardware bitmaps for GIFs
            // KMK <--
            .build()
        context.imageLoader.enqueue(request)
    }

    private fun Int.getSystemScaledDuration(): Int {
        return (this * context.animatorDurationScale).toInt().coerceAtLeast(1)
    }

    // ==================== OCR Methods ====================

    /**
     * Set OCR blocks for this page. Called by [PagerPageHolder] after image loads.
     */
    fun setOcrBlocks(blocks: List<OcrTextBlock>) {
        ocrBlocks = blocks
        activeOcrBlock = null
        ocrLayoutCache = null
        logcat { "OCR blocks attached: count=${blocks.size}" }
        (pageView as? SubsamplingScaleImageView)?.invalidate()
    }

    /**
     * Clear all OCR state. Called by [PagerPageHolder] when page is detached.
     */
    fun clearOcr() {
        if (ocrBlocks.isNotEmpty() || activeOcrBlock != null) {
            logcat { "OCR clear: blocks=${ocrBlocks.size} active=${activeOcrBlock != null}" }
        }
        ocrBlocks = emptyList()
        activeOcrBlock = null
        ocrLayoutCache = null
        ocrPopupLookupString = null
        onDismissOcrPopup?.invoke()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // Keep OCR resources alive across RecyclerView detach/attach cycles in continuous mode.
        // They are released in lifecycle onDestroy via [releaseOcrResources].
    }

    /**
     * Dismiss active OCR block on pan or zoom. Called from onCenterChanged and onScaleChanged listeners.
     */
    internal fun dismissActiveOcrBlock() {
        if (activeOcrBlock != null) {
            logcat { "OCR dismiss active block on pan/zoom" }
            activeOcrBlock = null
            ocrLayoutCache = null
            onDismissOcrPopup?.invoke()
            (pageView as? SubsamplingScaleImageView)?.invalidate()
        }
    }

    /**
     * Check if the given point in view coordinates hits any OCR block.
     * Used by viewers to filter out generic tap zones.
     */
    fun isPointOnOcrBlock(viewX: Float, viewY: Float): Boolean {
        val ssiv = pageView as? OcrSubsamplingImageView ?: return false
        return ssiv.dismissHandledInThisGesture || ssiv.isPointOnActiveOrAnyOcrBlock(viewX, viewY)
    }

    /**
     * Handle tap on OCR block: activate and immediately trigger dictionary lookup in one tap.
     *
     * Called by [OcrSubsamplingImageView.OcrGestureListener.onSingleTapUp].
     */
    internal fun handleOcrTap(
        block: OcrTextBlock,
        viewX: Float,
        viewY: Float,
        screenX: Float,
        screenY: Float,
    ): Boolean {
        val wasActive = activeOcrBlock

        // Activate the block (or switch to it) and redraw the text overlay
        if (wasActive != block) {
            logcat {
                "OCR tap: activate block vertical=${block.vertical} chars=${block.fullText.length} x=$viewX y=$viewY"
            }
            activeOcrBlock = block
            ocrLayoutCache = null
            ocrPopupLookupString = null
            (pageView as? SubsamplingScaleImageView)?.invalidate()
        }

        // Immediately trigger dictionary popup at the tapped character position
        val ssiv = pageView as? SubsamplingScaleImageView ?: return true
        val charOffset = getCharOffset(block, viewX, viewY, ssiv) ?: 0
        if (charOffset !in block.fullText.indices) {
            logcat(LogPriority.WARN) { "OCR char offset out of bounds: offset=$charOffset len=${block.fullText.length}" }
            return true
        }
        val tappedChar = block.fullText[charOffset]
        if (!isLookupStartChar(tappedChar)) {
            logcat { "OCR tap ignored on punctuation/non-word char '$tappedChar' at offset=$charOffset" }
            return true
        }
        val lookupString = block.fullText.substring(charOffset)
        logcat {
            "OCR tap: lookup offset=$charOffset remainingChars=${lookupString.length} x=$viewX y=$viewY"
        }
        if (lookupString.isBlank()) {
            logcat(LogPriority.WARN) { "OCR lookup string is blank" }
            return true
        }

        ocrPopupLookupString = lookupString
        onShowOcrPopup?.invoke(lookupString, block.fullText, charOffset, screenX, screenY, null)
        return true
    }

    private fun isLookupStartChar(char: Char): Boolean {
        if (char.isWhitespace()) return false
        // Skip punctuation/symbol taps (., 。, 、, !, ?, brackets, etc.).
        val type = Character.getType(char)
        return type != Character.CONNECTOR_PUNCTUATION.toInt() &&
            type != Character.DASH_PUNCTUATION.toInt() &&
            type != Character.START_PUNCTUATION.toInt() &&
            type != Character.END_PUNCTUATION.toInt() &&
            type != Character.INITIAL_QUOTE_PUNCTUATION.toInt() &&
            type != Character.FINAL_QUOTE_PUNCTUATION.toInt() &&
            type != Character.OTHER_PUNCTUATION.toInt() &&
            type != Character.MATH_SYMBOL.toInt() &&
            type != Character.CURRENCY_SYMBOL.toInt() &&
            type != Character.MODIFIER_SYMBOL.toInt() &&
            type != Character.OTHER_SYMBOL.toInt()
    }

    /**
     * Get character offset at tap position using StaticLayout or fallback uniform grid.
     *
     * Returns the byte offset in [block.fullText] for the character at the tap point.
     * Used to determine what text to pass to hoshidicts for lookup.
     */
    private fun getCharOffset(
        block: OcrTextBlock,
        viewX: Float,
        viewY: Float,
        ssiv: SubsamplingScaleImageView,
    ): Int? {
        val geometries = block.lineGeometries
        if (geometries != null && geometries.size == block.lines.size) {
            // Per-line hit testing
            for (i in geometries.indices) {
                val geo = geometries[i]
                val offset = getCharOffsetInLine(block.lines[i], geo, viewX, viewY, ssiv, block.vertical)
                if (offset != null) {
                    return block.lines.take(i).sumOf { it.length } + offset
                }
            }
        }

        val tl = ssiv.sourceToViewCoord(block.xmin * ssiv.sWidth, block.ymin * ssiv.sHeight)
            ?: return null
        val br = ssiv.sourceToViewCoord(block.xmax * ssiv.sWidth, block.ymax * ssiv.sHeight)
            ?: return null

        val screenW = br.x - tl.x
        val screenH = br.y - tl.y

        val localX = viewX - tl.x
        val localY = viewY - tl.y

        val layout = ocrLayoutCache?.takeIf { it.first == block }?.second
            ?: return uniformCharOffset(block, localX, localY, screenW, screenH)

        val (lx, ly) = if (block.vertical) {
            // Undo 90° rotation: screen X maps to layout Y, screen Y maps to layout X
            Pair(localY, screenW - localX)
        } else {
            Pair(localX, localY)
        }

        val line = layout.getLineForVertical(ly.toInt())
        return layout.getOffsetForHorizontal(line, lx)
    }

    private fun getCharOffsetInLine(
        text: String,
        geo: OcrLineGeometry,
        viewX: Float,
        viewY: Float,
        ssiv: SubsamplingScaleImageView,
        isVertical: Boolean,
    ): Int? {
        val centerX = (geo.xmin + geo.xmax) / 2f
        val centerY = (geo.ymin + geo.ymax) / 2f
        val width = geo.xmax - geo.xmin
        val height = geo.ymax - geo.ymin
        val boxScale = ocrBoxScale

        val srcXMin = (centerX - (width * boxScale) / 2f) * ssiv.sWidth
        val srcYMin = (centerY - (height * boxScale) / 2f) * ssiv.sHeight
        val srcXMax = (centerX + (width * boxScale) / 2f) * ssiv.sWidth
        val srcYMax = (centerY + (height * boxScale) / 2f) * ssiv.sHeight

        val tl = ssiv.sourceToViewCoord(srcXMin, srcYMin) ?: return null
        val br = ssiv.sourceToViewCoord(srcXMax, srcYMax) ?: return null
        val sW = br.x - tl.x
        val sH = br.y - tl.y

        // Translate and rotate tap point to local space of the line box
        val cx = tl.x + sW / 2f
        val cy = tl.y + sH / 2f

        // Inverse rotation: rotate point around center by -rotation
        val rad = Math.toRadians(-geo.rotation.toDouble())
        val dx = (viewX - cx).toDouble()
        val dy = (viewY - cy).toDouble()
        val rx = (dx * Math.cos(rad) - dy * Math.sin(rad)).toFloat()
        val ry = (dx * Math.sin(rad) + dy * Math.cos(rad)).toFloat()

        // Local coordinates relative to top-left of the line box (at 0 deg)
        val lx = rx + sW / 2f
        val ly = ry + sH / 2f

        // Hit test
        if (lx < 0 || lx > sW || ly < 0 || ly > sH) return null

        // Calculate index based on orientation
        return if (isVertical) {
            (ly / (sH / text.length.coerceAtLeast(1)))
                .toInt().coerceIn(0, text.length - 1)
        } else {
            (lx / (sW / text.length.coerceAtLeast(1)))
                .toInt().coerceIn(0, text.length - 1)
        }
    }

    /**
     * Fallback caret detection: approximate character position using uniform grid.
     *
     * Used when layout is not yet built (first tap before onDraw renders text).
     * Divides box into uniform character grid based on line count and line lengths.
     */
    private fun uniformCharOffset(
        block: OcrTextBlock,
        localX: Float,
        localY: Float,
        screenW: Float,
        screenH: Float,
    ): Int {
        if (block.vertical) {
            val columns = block.lines.ifEmpty { listOf(block.fullText) }
            if (columns.isEmpty()) return 0

            val columnWidth = (screenW / columns.size.coerceAtLeast(1)).coerceAtLeast(1f)
            val maxChars = columns.maxOfOrNull { it.length }?.coerceAtLeast(1) ?: 1
            val rowHeight = (screenH / maxChars).coerceAtLeast(1f)
            val contentTop = (screenH - rowHeight * maxChars) / 2f

            val fromRight = (screenW - localX).coerceIn(0f, screenW)
            val columnIndex = (fromRight / columnWidth)
                .toInt()
                .coerceIn(0, columns.lastIndex)

            val columnText = columns[columnIndex]
            val charsInColumn = columnText.length.coerceAtLeast(1)
            val yInColumn = (localY - contentTop)
                .coerceIn(0f, (rowHeight * charsInColumn - 0.001f).coerceAtLeast(0f))
            val charIndex = (yInColumn / rowHeight)
                .toInt()
                .coerceIn(0, charsInColumn - 1)

            return columns.take(columnIndex).sumOf { it.length } + charIndex
        }

        val lineCount = block.lines.size.coerceAtLeast(1)
        val lineIndex = (localY / (screenH / lineCount))
            .toInt().coerceIn(0, lineCount - 1)
        val line = block.lines[lineIndex]
        val charIndex = (localX / (screenW / line.length.coerceAtLeast(1)))
            .toInt().coerceIn(0, line.length - 1)
        return block.lines.take(lineIndex).sumOf { it.length } + charIndex
    }

    /**
     * All of the config except [zoomDuration] will only be used for non-animated image.
     */
    data class Config(
        val zoomDuration: Int,
        val minimumScaleType: Int = SCALE_TYPE_CENTER_INSIDE,
        val cropBorders: Boolean = false,
        val zoomStartPosition: ZoomStartPosition = ZoomStartPosition.CENTER,
        val landscapeZoom: Boolean = false,
        // KMK -->
        val disableZoomIn: Boolean = false,
        val doubleTapZoom: Boolean = true,
        val landscapeZoomScaleType: LandscapeZoomScaleType = LandscapeZoomScaleType.FIT,
        // KMK <--
    )

    enum class ZoomStartPosition {
        LEFT,
        CENTER,
        RIGHT,
    }
}

private const val MAX_ZOOM_SCALE = 5F

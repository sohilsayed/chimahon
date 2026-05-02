package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import android.content.res.Resources
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import androidx.annotation.ColorInt
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updateMargins
import chimahon.MediaInfo
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import eu.kanade.presentation.util.formattedMessage
import eu.kanade.tachiyomi.databinding.ReaderErrorBinding
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.viewer.OcrCoordinateMapper
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderPageImageView
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderProgressIndicator
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import eu.kanade.tachiyomi.util.system.dpToPx
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import logcat.LogPriority
import okio.Buffer
import okio.BufferedSource
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.ImageUtil
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR

/**
 * Holder of the webtoon reader for a single page of a chapter.
 *
 * @param frame the root view for this holder.
 * @param viewer the webtoon viewer.
 * @constructor creates a new webtoon holder.
 */
class WebtoonPageHolder(
    private val frame: ReaderPageImageView,
    viewer: WebtoonViewer,
    // KMK -->
    @ColorInt private val seedColor: Int? = null,
    // KMK <--
) : WebtoonBaseHolder(frame, viewer) {

    /**
     * Loading progress bar to indicate the current progress.
     */
    private val progressIndicator = createProgressIndicator()

    /**
     * Progress bar container. Needed to keep a minimum height size of the holder, otherwise the
     * adapter would create more views to fill the screen, which is not wanted.
     */
    private lateinit var progressContainer: ViewGroup

    /**
     * Error layout to show when the image fails to load.
     */
    private var errorLayout: ReaderErrorBinding? = null

    /**
     * Getter to retrieve the height of the recycler view.
     */
    private val parentHeight
        get() = viewer.recycler.height

    /**
     * Page of a chapter.
     */
    private var page: ReaderPage? = null

    private val scope = MainScope()

    /**
     * Job for loading the page.
     */
    private var loadJob: Job? = null

    private var ocrLoadJob: Job? = null

    init {
        refreshLayoutParams()

        frame.onImageLoaded = { onImageDecoded() }
        frame.onImageLoadError = { error -> setError(error) }
        frame.onScaleChanged = { viewer.activity.hideMenu() }
        frame.onShowOcrPopup = { lookupString, fullText, charOffset, anchorX, anchorY, anchorWidth, anchorHeight, isVertical, mediaInfo ->
            viewer.onShowOcrPopup?.invoke(lookupString, fullText, charOffset, anchorX, anchorY, anchorWidth, anchorHeight, isVertical, mediaInfo)
        }
        frame.onDismissOcrPopup = {
            viewer.onDismissOcrPopup?.invoke()
        }
    }

    /**
     * Binds the given [page] with this view holder, subscribing to its state.
     */
    fun bind(page: ReaderPage) {
        this.page = page
        loadJob?.cancel()
        loadJob = scope.launch { loadPageAndProcessStatus() }
        refreshLayoutParams()
    }

    private fun refreshLayoutParams() {
        frame.layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            if (!viewer.isContinuous) {
                bottomMargin = 15.dpToPx
            }

            val margin = Resources.getSystem().displayMetrics.widthPixels * (viewer.config.sidePadding / 100f)
            marginEnd = margin.toInt()
            marginStart = margin.toInt()
        }
    }

    /**
     * Called when the view is recycled and added to the view pool.
     */
    override fun recycle() {
        loadJob?.cancel()
        loadJob = null
        ocrLoadJob?.cancel()
        ocrLoadJob = null

        removeErrorLayout()
        frame.clearOcr()
        frame.recycle()
        progressIndicator.setProgress(0)
        progressContainer.isVisible = true
    }

    /**
     * Loads the page and processes changes to the page's status.
     *
     * Returns immediately if there is no page or the page has no PageLoader.
     * Otherwise, this function does not return. It will continue to process status changes until
     * the Job is cancelled.
     */
    private suspend fun loadPageAndProcessStatus() {
        val page = page ?: return
        val loader = page.chapter.pageLoader ?: return
        supervisorScope {
            launchIO {
                loader.loadPage(page)
            }
            page.statusFlow.collectLatest { state ->
                when (state) {
                    Page.State.Queue -> setQueued()
                    Page.State.LoadPage -> setLoading()
                    Page.State.DownloadImage -> {
                        setDownloading()
                        page.progressFlow.collectLatest { value ->
                            progressIndicator.setProgress(value)
                        }
                    }
                    Page.State.Ready -> setImage()
                    is Page.State.Error -> setError(state.error)
                }
            }
        }
    }

    /**
     * Called when the page is queued.
     */
    private fun setQueued() {
        progressContainer.isVisible = true
        progressIndicator.show()
        removeErrorLayout()
    }

    /**
     * Called when the page is loading.
     */
    private fun setLoading() {
        progressContainer.isVisible = true
        progressIndicator.show()
        removeErrorLayout()
    }

    /**
     * Called when the page is downloading
     */
    private fun setDownloading() {
        progressContainer.isVisible = true
        progressIndicator.show()
        removeErrorLayout()
    }

    /**
     * Called when the page is ready.
     */
    private suspend fun setImage() {
        progressIndicator.setProgress(0)

        val streamFn = page?.stream ?: return

        try {
            val (source, isAnimated) = withIOContext {
                val source = streamFn().use { process(Buffer().readFrom(it)) }
                val isAnimated = ImageUtil.isAnimatedAndSupported(source)
                Pair(source, isAnimated)
            }
            withUIContext {
                frame.setImage(
                    source,
                    isAnimated,
                    ReaderPageImageView.Config(
                        zoomDuration = viewer.config.doubleTapAnimDuration,
                        minimumScaleType = if (viewer.config.webtoonScaleType == ReaderPreferences.WebtoonScaleType.FIT) {
                            SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE
                        } else {
                            SubsamplingScaleImageView.SCALE_TYPE_FIT_WIDTH
                        },
                        cropBorders =
                        (viewer.config.imageCropBorders && viewer.isContinuous) ||
                            (viewer.config.continuousCropBorders && !viewer.isContinuous),
                    ),
                )
                removeErrorLayout()
            }
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e)
            withUIContext {
                setError(e)
            }
        }
    }

    private fun process(imageSource: BufferedSource): BufferedSource {
        if (viewer.config.dualPageRotateToFit) {
            return rotateDualPage(imageSource)
        }

        if (viewer.config.dualPageSplit) {
            val isDoublePage = ImageUtil.isWideImage(imageSource)
            if (isDoublePage) {
                val upperSide = if (viewer.config.dualPageInvert) ImageUtil.Side.LEFT else ImageUtil.Side.RIGHT
                return ImageUtil.splitAndMerge(imageSource, upperSide)
            }
        }

        return imageSource
    }

    private fun rotateDualPage(imageSource: BufferedSource): BufferedSource {
        val isDoublePage = ImageUtil.isWideImage(imageSource)
        return if (isDoublePage) {
            val rotation = if (viewer.config.dualPageRotateToFitInvert) -90f else 90f
            ImageUtil.rotateImage(imageSource, rotation)
        } else {
            imageSource
        }
    }

    /**
     * Called when the page has an error.
     */
    private fun setError(error: Throwable?) {
        progressContainer.isVisible = false
        initErrorLayout(error)
    }

    /**
     * Called when the image is decoded and going to be displayed.
     */
    private fun onImageDecoded() {
        progressContainer.isVisible = false
        removeErrorLayout()
        applyOcrEnabled(viewer.activity.viewModel.isOcrEnabled())
    }

    fun applyOcrEnabled(enabled: Boolean) {
        frame.ocrOutlineVisible = viewer.activity.viewModel.isOcrOutlineVisible()
        frame.ocrBoxScale = viewer.activity.viewModel.getOcrBoxScale()
        frame.ocrEnabled = enabled
        if (!enabled) {
            ocrLoadJob?.cancel()
            ocrLoadJob = null
            frame.clearOcr()
            return
        }

        if (frame.ocrBlocks.isNotEmpty()) {
            return
        }

        val targetPage = page ?: return
        ocrLoadJob?.cancel()
        ocrLoadJob = scope.launch {
            loadOcrWithTransform(targetPage)
        }
    }

    private suspend fun loadOcrWithTransform(targetPage: ReaderPage) {
        val viewModel = viewer.activity.viewModel
        val dualSplitEnabled = viewer.config.dualPageSplit
        val cropBordersEnabled = (viewer.config.imageCropBorders && viewer.isContinuous) ||
            (viewer.config.continuousCropBorders && !viewer.isContinuous)

        logcat { "OCR request start (webtoon): chapter=${targetPage.chapter.chapter.id} page=${targetPage.index}" }
        var blocks = viewModel.getOcrBlocks(targetPage)

        if (blocks.isEmpty()) {
            frame.setOcrBlocks(blocks)
            return
        }

        // ── Case 1: Split and Merge (Webtoon special) ───────────────────────────────
        if (dualSplitEnabled && !viewer.config.dualPageRotateToFit) {
            val streamFn = targetPage.stream
            if (streamFn != null) {
                val isWide = withIOContext {
                    streamFn().use { ImageUtil.isWideImage(okio.Buffer().readFrom(it)) }
                }
                if (isWide) {
                    val upperSide = if (viewer.config.dualPageInvert) ImageUtil.Side.LEFT else ImageUtil.Side.RIGHT
                    blocks = OcrCoordinateMapper.mapToSplitAndMerge(
                        blocks = blocks,
                        upperIsRight = upperSide == ImageUtil.Side.RIGHT,
                    )
                    logcat { "OCR splitAndMerge (upperIsRight=${upperSide == ImageUtil.Side.RIGHT}): ${blocks.size} blocks after remap" }
                }
            }
        }

        // ── Case 2: Crop borders ────────────────────────────────────────────────────
        if (cropBordersEnabled && blocks.isNotEmpty()) {
            val streamFn = targetPage.stream
            if (streamFn != null) {
                blocks = withIOContext {
                    try {
                        streamFn().use { stream ->
                            OcrCoordinateMapper.mapToCropped(blocks, okio.Buffer().readFrom(stream))
                        }
                    } catch (e: Exception) {
                        logcat(LogPriority.WARN, e) { "OCR crop detection failed (webtoon), using raw blocks" }
                        blocks
                    }
                }
                logcat { "OCR crop-remap done (webtoon): ${blocks.size} blocks" }
            }
        }

        frame.setOcrBlocks(blocks)
    }

    /**
     * Creates a new progress bar.
     */
    private fun createProgressIndicator(): ReaderProgressIndicator {
        progressContainer = FrameLayout(context)
        frame.addView(progressContainer, MATCH_PARENT, parentHeight)

        val progress = ReaderProgressIndicator(
            context,
            // KMK -->
            seedColor = seedColor,
            // KMK <--
        ).apply {
            updateLayoutParams<FrameLayout.LayoutParams> {
                updateMargins(top = parentHeight / 4)
            }
        }
        progressContainer.addView(progress)
        return progress
    }

    /**
     * Initializes a button to retry pages.
     */
    private fun initErrorLayout(error: Throwable?): ReaderErrorBinding {
        if (errorLayout == null) {
            errorLayout = ReaderErrorBinding.inflate(LayoutInflater.from(context), frame, true)
            errorLayout?.root?.layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, (parentHeight * 0.8).toInt())
            errorLayout?.actionRetry?.setOnClickListener {
                page?.let { it.chapter.pageLoader?.retryPage(it) }
            }
        }

        val imageUrl = page?.imageUrl
        errorLayout?.actionOpenInWebView?.isVisible = imageUrl != null
        if (imageUrl != null) {
            if (imageUrl.startsWith("http", true)) {
                errorLayout?.actionOpenInWebView?.setOnClickListener {
                    val sourceId = viewer.activity.viewModel.manga?.source

                    val intent = WebViewActivity.newIntent(context, imageUrl, sourceId)
                    context.startActivity(intent)
                }
            }
        }

        errorLayout?.errorMessage?.text = with(context) { error?.formattedMessage }
            ?: context.stringResource(MR.strings.decode_image_error)

        return errorLayout!!
    }

    /**
     * Removes the decode error layout from the holder, if found.
     */
    private fun removeErrorLayout() {
        errorLayout?.let {
            frame.removeView(it.root)
            errorLayout = null
        }
    }

    /**
     * Check if the given point in view coordinates hits any OCR block.
     * Delegates to the underlying ReaderPageImageView.
     */
    fun isPointOnOcrBlock(x: Float, y: Float): Boolean {
        return frame.isPointOnOcrBlock(x, y)
    }

    /** True if this page currently has an active (highlighted) OCR block. */
    val hasActiveOcrBlock: Boolean
        get() = frame.activeOcrBlock != null

    /**
     * Dismiss the active OCR block on this page, clearing the highlight and closing the popup.
     * No-op when no block is active.
     */
    fun dismissActiveOcrBlock() {
        frame.dismissActiveOcrBlock()
    }

    /**
     * Refine the active OCR block highlight to a specific character count.
     */
    fun refineActiveOcrBlock(charCount: Int) {
        frame.refineActiveOcrBlock(charCount)
    }
}

package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.content.Context
import android.os.Parcelable
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.Scroller
import androidx.viewpager.widget.DirectionalViewPager
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.viewer.GestureDetectorWithLongTap
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Pager implementation that listens for tap and long tap and allows temporarily disabling touch
 * events in order to work with child views that need to disable touch events on this parent. The
 * pager can also be declared to be vertical by creating it with [isHorizontal] to false.
 */
open class Pager(
    context: Context,
    isHorizontal: Boolean = true,
) : DirectionalViewPager(context, isHorizontal) {

    /**
     * Tap listener function to execute when a tap is detected.
     */
    var tapListener: ((MotionEvent) -> Unit)? = null

    /**
     * Long tap listener function to execute when a long tap is detected.
     */
    var longTapListener: ((MotionEvent) -> Boolean)? = null

    // SY -->
    var isRestoring = false

    override fun onRestoreInstanceState(state: Parcelable?) {
        isRestoring = true
        val currentItem = currentItem
        super.onRestoreInstanceState(state)
        setCurrentItem(currentItem, false)
        isRestoring = false
    }
    // SY <--

    private var defaultScroller: Scroller? = null
    private var defaultTouchSlop: Int? = null
    private var defaultMinimumVelocity: Int? = null
    private var defaultFlingDistance: Int? = null

    var eInkMode = Injekt.get<ReaderPreferences>().eInkMode().get()
        set(value) {
            if (field == value) return
            field = value
            applyEInkMode()
        }

    /**
     * Cached reflection access to DirectionalViewPager's private `mScroller` field.
     * DirectionalViewPager re-declares its own `mScroller` shadow field, separate
     * from ViewPager's, so we use DirectionalViewPager's class explicitly.
     */
    private val scrollerField by lazy {
        runCatching {
            DirectionalViewPager::class.java
                .getDeclaredField("mScroller")
                .apply { isAccessible = true }
        }.getOrNull()
    }

    /**
     * Safety net: in E-Ink mode, force any in-progress scroll to snap instantly.
     * This guarantees no visible scroll animation even if the Scroller override
     * from updatePageScroller() was lost or never applied.
     */
    override fun computeScroll() {
        if (eInkMode) {
            (scrollerField?.get(this) as? Scroller)?.let { scroller ->
                if (!scroller.isFinished) {
                    scrollTo(scroller.finalX, scroller.finalY)
                    scroller.abortAnimation()
                }
            }
        }
        super.computeScroll()
    }

    /**
     * Gesture listener that implements tap and long tap events.
     */
    private val gestureListener = object : GestureDetectorWithLongTap.Listener() {
        override fun onDown(ev: MotionEvent): Boolean {
            return true
        }

        override fun onSingleTapConfirmed(ev: MotionEvent): Boolean {
            tapListener?.invoke(ev)
            return true
        }

        override fun onLongTapConfirmed(ev: MotionEvent) {
            val listener = longTapListener
            if (listener != null && listener.invoke(ev)) {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
        }
    }

    /**
     * Gesture detector which handles motion events.
     */
    private val gestureDetector = GestureDetectorWithLongTap(context, gestureListener)

    init {
        applyEInkMode()
    }

    private fun applyEInkMode() {
        updateSwipeThresholds(eInkMode)
        updatePageScroller(eInkMode)
    }

    private fun updatePageScroller(instantScroll: Boolean) {
        try {
            val scroller = DirectionalViewPager::class.java
                .getDeclaredField("mScroller")
                .apply { isAccessible = true }
            defaultScroller = defaultScroller ?: scroller.get(this) as? Scroller
            scroller.set(
                this,
                if (instantScroll) {
                    object : Scroller(context) {
                        override fun startScroll(startX: Int, startY: Int, dx: Int, dy: Int, duration: Int) {
                            super.startScroll(startX, startY, dx, dy, 0)
                        }
                    }
                } else {
                    defaultScroller ?: Scroller(context)
                },
            )
        } catch (_: Exception) {}
    }

    private fun updateSwipeThresholds(reduceThresholds: Boolean) {
        try {
            val density = resources.displayMetrics.density
            val ctx = context
            val clazz = DirectionalViewPager::class.java
            val touchSlop = clazz.getDeclaredField("mTouchSlop").apply { isAccessible = true }
            defaultTouchSlop = defaultTouchSlop ?: touchSlop.getInt(this)
            val minVel = clazz.getDeclaredField("mMinimumVelocity").apply { isAccessible = true }
            defaultMinimumVelocity = defaultMinimumVelocity ?: minVel.getInt(this)
            val flingDist = clazz.getDeclaredField("mFlingDistance").apply { isAccessible = true }
            defaultFlingDistance = defaultFlingDistance ?: flingDist.getInt(this)

            if (reduceThresholds) {
                touchSlop.setInt(this, ViewConfiguration.get(ctx).scaledTouchSlop / 2)
                minVel.setInt(this, (75 * density).toInt())
                flingDist.setInt(this, (4 * density).toInt())
            } else {
                touchSlop.setInt(this, defaultTouchSlop ?: ViewConfiguration.get(ctx).scaledTouchSlop)
                minVel.setInt(this, defaultMinimumVelocity ?: minVel.getInt(this))
                flingDist.setInt(this, defaultFlingDistance ?: flingDist.getInt(this))
            }
        } catch (_: Exception) {
        }
    }

    /**
     * Whether the gesture detector is currently enabled.
     */
    private var isGestureDetectorEnabled = true

    /**
     * Dispatches a touch event.
     */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val handled = super.dispatchTouchEvent(ev)
        if (isGestureDetectorEnabled) {
            gestureDetector.onTouchEvent(ev)
        }
        return handled
    }

    /**
     * Whether the given [ev] should be intercepted. Only used to prevent crashes when child
     * views manipulate [requestDisallowInterceptTouchEvent].
     */
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        return try {
            super.onInterceptTouchEvent(ev)
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    /**
     * Handles a touch event. Only used to prevent crashes when child views manipulate
     * [requestDisallowInterceptTouchEvent].
     */
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        return try {
            super.onTouchEvent(ev)
        } catch (e: NullPointerException) {
            false
        } catch (e: IndexOutOfBoundsException) {
            false
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    /**
     * Executes the given key event when this pager has focus. Just do nothing because the reader
     * already dispatches key events to the viewer and has more control than this method.
     */
    override fun executeKeyEvent(event: KeyEvent): Boolean {
        // Disable viewpager's default key event handling
        return false
    }

    /**
     * Enables or disables the gesture detector.
     */
    fun setGestureDetectorEnabled(enabled: Boolean) {
        isGestureDetectorEnabled = enabled
    }
}

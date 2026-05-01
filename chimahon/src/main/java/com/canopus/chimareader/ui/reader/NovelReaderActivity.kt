package com.canopus.chimareader.ui.reader

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import com.canopus.chimareader.data.BookMetadata
import com.canopus.chimareader.data.BookStorage
import com.canopus.chimareader.data.NovelReaderSettings
import java.io.File

open class NovelReaderActivity : ComponentActivity() {

    companion object {
        internal const val EXTRA_BOOK_DIR = "extra_book_dir"

        /**
         * Set to [ChimaReaderActivity] from AppModule so that BookshelfScreen's
         * existing [launch] call lands in the app-side subclass (which has the
         * lookup popup), without requiring chimahon to import from app.
         */
        var activityClass: Class<out ComponentActivity> = NovelReaderActivity::class.java

        fun launch(context: Context, bookDir: File) {
            val intent = Intent(context, activityClass).apply {
                putExtra(EXTRA_BOOK_DIR, bookDir.absolutePath)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(intent)
        }
    }

    /** Controls whether the reader interactions (taps, selection) are enabled. */
    protected var isPopupActive by androidx.compose.runtime.mutableStateOf(false)

    protected var readerViewModel by androidx.compose.runtime.mutableStateOf<ReaderViewModel?>(null)

    protected open fun handleVolumeKey(forward: Boolean): Boolean {
        val vm = readerViewModel ?: return false
        vm.bridge.paginate(forward)
        return true
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent): Boolean {
        if (isPopupActive) return super.onKeyDown(keyCode, event)
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: android.view.KeyEvent): Boolean {
        if (isPopupActive) return super.onKeyUp(keyCode, event)

        when (keyCode) {
            android.view.KeyEvent.KEYCODE_VOLUME_UP -> {
                if (handleVolumeKey(false)) return true
            }
            android.view.KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (handleVolumeKey(true)) return true
            }
            android.view.KeyEvent.KEYCODE_DPAD_UP, android.view.KeyEvent.KEYCODE_PAGE_UP -> {
                handleVolumeKey(false)
                return true
            }
            android.view.KeyEvent.KEYCODE_DPAD_DOWN, android.view.KeyEvent.KEYCODE_PAGE_DOWN -> {
                handleVolumeKey(true)
                return true
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    /** Override in subclass to receive text selection events from the reader. */
    protected open fun onLookupRequested(word: String, sentence: String, x: Float, y: Float) = Unit

    @Composable
    protected open fun PopupOverlay() {}

    @Composable
    protected open fun AdditionalAppearanceSettings() {}

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()

        super.onCreate(savedInstanceState)

        val path = intent.getStringExtra(EXTRA_BOOK_DIR)
        if (path.isNullOrEmpty()) {
            finish()
            return
        }

        val root = File(path)

        if (!root.exists() || !root.isDirectory) {
            finish()
            return
        }

        val settings = NovelReaderSettings(this)

        val metadata = BookStorage.loadMetadata(root) ?: BookMetadata(folder = root.name)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            ReaderScreen(
                book = metadata,
                onBack = { finish() },
                onLookupRequested = ::onLookupRequested,
                isPopupActive = isPopupActive,
                onViewModelReady = { readerViewModel = it },
                additionalSettings = { AdditionalAppearanceSettings() },
            )
            PopupOverlay()
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-apply fullscreen to ensure bars don't pop back in unexpectedly
        setSystemBarsVisibility(false)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            setSystemBarsVisibility(false)
        }
    }

    private fun setSystemBarsVisibility(visible: Boolean) {
        val windowInsetsController = androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
        if (visible) {
            windowInsetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        } else {
            windowInsetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            windowInsetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}

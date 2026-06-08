package eu.kanade.tachiyomi.ui.dictionary

import android.content.Context
import android.webkit.WebView
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.canopus.chimareader.data.FontManager
import com.materialkolor.PaletteStyle
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.theme.colorscheme.CustomColorScheme
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

internal fun getDictionaryBootstrapHtml(
    context: Context,
    colorScheme: androidx.compose.material3.ColorScheme? = null,
    isDark: Boolean? = null,
    seedColor: Int? = null,
    isAmoled: Boolean = false,
    fontFamily: String = "",
    eInkMode: Boolean = false,
    paginatedScrolling: Boolean = false,
    languageCode: String = "",
): String {
    val css = dictionaryBaseCss.getOrPut(Unit) {
        readTextAsset(context.applicationContext, "dictionary/base.css")
    }
    val js = dictionaryRendererJs.getOrPut(Unit) {
        readTextAsset(context.applicationContext, "dictionary/renderer.js").replace("</script", "<\\/script")
    }

    val fontUrl = FontManager.getFontUri(context, fontFamily)
    val fontFaceCss = if (fontUrl != null) {
        """
          @font-face {
            font-family: 'HoshiCustomFont';
            src: url('$fontUrl');
          }
          :root, body, #entries {
            font-family: 'HoshiCustomFont' !important;
          }
        """.trimIndent()
    } else if (fontFamily.isNotBlank()) {
        var ff = fontFamily
        if (ff == "System Serif") ff = "serif"
        else if (ff == "System Sans-Serif") ff = "sans-serif"
        """
          :root, body, #entries {
            font-family: '$ff' !important;
          }
        """.trimIndent()
    } else ""

    val dynamicThemeCss = if (colorScheme != null) {
        val accentHex = "#%06X".format(0xFFFFFF and colorScheme.primary.toArgb())
        val onAccentHex = "#%06X".format(0xFFFFFF and colorScheme.onPrimary.toArgb())
        val fgHex = "#%06X".format(0xFFFFFF and colorScheme.onSurface.toArgb())
        val bgHex = if (isAmoled && isDark == true) "#000000" else "#%06X".format(0xFFFFFF and colorScheme.surface.toArgb())
        val secondaryHex = "#%06X".format(0xFFFFFF and colorScheme.onSurfaceVariant.toArgb())
        val borderHex = if (isAmoled && isDark == true) "rgba(255,255,255,0.10)" else "#%06X".format(0xFFFFFF and colorScheme.outlineVariant.toArgb())
        val hoverHex = if (isAmoled && isDark == true) "rgba(255,255,255,0.07)" else "#%06X".format(0xFFFFFF and colorScheme.surfaceVariant.toArgb())
        val tabBgHex = if (isAmoled && isDark == true) "#0d0d0d" else "#%06X".format(0xFFFFFF and colorScheme.surfaceContainer.toArgb())
        """
          <style id="dynamic-theme">
            :root, :root[data-theme="dark"], :root[data-theme="light"] {
                --accent: $accentHex;
                --on-accent: $onAccentHex;
                --fg: $fgHex;
                --bg: $bgHex;
                --secondary: $secondaryHex;
                --border: $borderHex;
                --hover-bg: $hoverHex;
                --tab-bg: $tabBgHex;
                --pronunciation-annotation-color: $fgHex;
            }
          </style>
        """
    } else ""

    val eInkAttr = if (eInkMode) "true" else "false"
    val paginatedScrollingAttr = if (paginatedScrolling) "true" else "false"
    val themeAttr = if (isDark == true) "dark" else "light"
    val langAttr = if (languageCode.isNotEmpty()) """lang="$languageCode" """ else ""

    return """
        <!doctype html>
        <html $langAttr data-theme="$themeAttr" data-chima-eink-mode="$eInkAttr" data-chima-paginated-scrolling="$paginatedScrollingAttr">
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no, viewport-fit=cover">
          <style>$css</style>$dynamicThemeCss
          <style>$fontFaceCss</style>
          <style id="dictionary-styles"></style>
          <style id="chima-custom-css"></style>
          <script>
            window.AnkiBridge = {
              addToAnki: function(index, glossary, selectedDict, popupSelection) {
                var url = "anki://add?index=" + index;
                if (glossary !== undefined && glossary !== null) url += "&glossary=" + glossary;
                if (selectedDict) url += "&selected_dict=" + encodeURIComponent(selectedDict);
                if (popupSelection) url += "&popup_selection=" + encodeURIComponent(popupSelection);
                window.location.href = url;
              },
              openInAnki: function(index, glossary, selectedDict, popupSelection) {
                var url = "anki://open?index=" + index;
                if (glossary !== undefined && glossary !== null) url += "&glossary=" + glossary;
                if (selectedDict) url += "&selected_dict=" + encodeURIComponent(selectedDict);
                if (popupSelection) url += "&popup_selection=" + encodeURIComponent(popupSelection);
                window.location.href = url;
              }
            };
          </script>
        </head>
        <body>
          <main id="entries" class="entries"></main>
          <script>$js</script>
        </body>
        </html>
    """.trimIndent()
}

private fun readTextAsset(context: Context, assetPath: String): String {
    return context.assets.open(assetPath).use { input ->
        input.readBytes().toString(Charsets.UTF_8)
    }
}

private val dictionaryBaseCss = java.util.concurrent.ConcurrentHashMap<Unit, String>()
private val dictionaryRendererJs = java.util.concurrent.ConcurrentHashMap<Unit, String>()

fun getDictionaryColorScheme(
    isDark: Boolean,
    isAmoled: Boolean,
    seedColor: Int,
): ColorScheme {
    val uiPreferences = Injekt.get<UiPreferences>()
    val baseScheme = CustomColorScheme(
        seed = Color(seedColor),
        style = PaletteStyle.TonalSpot,
    )
    return baseScheme.getColorScheme(isDark, isAmoled, false)
}

internal fun stopDictionaryAudio(webView: WebView) {
    (webView.tag as? DictionaryWebViewState)?.wordAudioBridge?.stopAudio()
}

package com.canopus.chimareader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.canopus.chimareader.ui.library.BookshelfScreen
import com.canopus.chimareader.ui.theme.ChimaReaderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChimaReaderTheme {
                BookshelfScreen()
            }
        }
    }
}

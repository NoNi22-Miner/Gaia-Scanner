package com.gaiascanner.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.gaiascanner.app.ui.GaiaNavHost
import com.gaiascanner.app.ui.theme.GaiaTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIncoming(intent)
        setContent {
            GaiaTheme {
                GaiaNavHost()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncoming(intent)
    }

    /** Copies shared files in right away, while this activity still holds access to them. */
    private fun handleIncoming(intent: Intent?) {
        val uris = IncomingFiles.uris(intent)
        if (uris.isEmpty()) return
        val container = appContainer
        lifecycleScope.launch {
            container.postMessage(IncomingFiles.import(container, uris))
        }
    }
}

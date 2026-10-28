package com.belgetarayici

import com.belgetarayici.i18n.L
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import com.belgetarayici.ui.AppRoot
import com.belgetarayici.ui.AppViewModel

class MainActivity : FragmentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Follows a phone language change made while the app was in the background.
        L.apply(container.settings.uiLanguage, resources.configuration.locales[0])
        // No screenshots, screen recording or recents thumbnails of documents.
        if (!BuildConfig.ALLOW_SCREENSHOTS) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent { AppRoot(vm, this) }
    }

    // The activity's own start/stop (not ProcessLifecycleOwner, which reports a stop ~700 ms late)
    // so even a quick switch away and back is seen by auto-lock. Rotation is not leaving the app.
    override fun onStart() {
        super.onStart()
        container.lock.onForeground()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) container.lock.onBackground()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val uri: Uri? = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> if (android.os.Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            else -> null
        }
        if (uri != null) vm.onIncoming(uri)
    }
}

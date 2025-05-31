package com.sukisu.ultra.ui.webui

import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.dergoogler.mmrl.platform.Platform
import com.dergoogler.mmrl.webui.activity.WXActivity
import com.dergoogler.mmrl.webui.util.WebUIOptions
import com.dergoogler.mmrl.webui.view.WebUIXView
import com.sukisu.ultra.BuildConfig
import com.sukisu.ultra.ui.theme.getColorScheme
import com.sukisu.ultra.ui.theme.isSystemInDarkTheme
import kotlinx.coroutines.launch

class WebUIXActivity : WXActivity() {
    private val userAgent
        get(): String {
            val ksuVersion = BuildConfig.VERSION_CODE

            val platform = Platform.get("Unknown") {
                platform.name
            }

            val platformVersion = Platform.get(-1) {
                moduleManager.versionCode
            }

            val osVersion = Build.VERSION.RELEASE
            val deviceModel = Build.MODEL

            return "SukiSU Ultra/$ksuVersion (Linux; Android $osVersion; $deviceModel; $platform/$platformVersion)"
        }

    override fun onRender(savedInstanceState: Bundle?) {
        super.onRender(savedInstanceState)

        if (this.modId == null) {
            val msg = "ModId cannot be null"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            throw IllegalArgumentException(msg)
        }

        // Cast since we check it
        val modId = this.modId!!

        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val webDebugging = prefs.getBoolean("enable_web_debugging", false)
        val erudaInject = prefs.getBoolean("use_webuix_eruda", false)


        val options = WebUIOptions(
            modId = modId,
            context = this,
            debug = webDebugging,
            appVersionCode = BuildConfig.VERSION_CODE,
            isDarkMode = isSystemInDarkTheme(),
            enableEruda = erudaInject,
            cls = WebUIXActivity::class.java,
            userAgentString = userAgent,
            colorScheme = getColorScheme()
        )

        val view = WebUIXView(options).apply {
            wx.addJavascriptInterface(WebViewInterface.factory())
            wx.loadDomain()
        }

        this.options = options
        this.view = view


        // Ensure type safety
        val name = intent.getStringExtra("name")
        if (name != null) {
            setActivityTitle("SukiSU Ultra - $name")
        }

        val loading = createLoadingRenderer()
        setContentView(loading)

        lifecycleScope.launch {
            val deferred = Platform.getAsyncDeferred(this, null) {
                view
            }

            setContentView(deferred.await())
        }
    }
}
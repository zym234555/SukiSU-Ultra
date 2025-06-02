package com.sukisu.ultra.ui

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ramcosta.composedestinations.DestinationsNavHost
import com.ramcosta.composedestinations.generated.NavGraphs
import com.ramcosta.composedestinations.generated.destinations.ExecuteModuleActionScreenDestination
import com.ramcosta.composedestinations.spec.NavHostGraphSpec
import io.sukisu.ultra.UltraToolInstall
import com.sukisu.ultra.ksuApp
import zako.zako.zako.zakoui.activity.util.AppData
import com.sukisu.ultra.ui.theme.*
import zako.zako.zako.zakoui.activity.util.*
import zako.zako.zako.zakoui.activity.component.BottomBar
import com.sukisu.ultra.ui.util.LocalSnackbarHost
import com.sukisu.ultra.ui.util.install
import com.sukisu.ultra.ui.viewmodel.HomeViewModel
import com.sukisu.ultra.ui.viewmodel.SuperUserViewModel
import com.sukisu.ultra.ui.webui.initPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var superUserViewModel: SuperUserViewModel
    private lateinit var homeViewModel: HomeViewModel
    internal val settingsStateFlow = MutableStateFlow(SettingsState())

    data class SettingsState(
        val isHideOtherInfo: Boolean = false,
        val showKpmInfo: Boolean = true
    )

    private lateinit var themeChangeObserver: ThemeChangeContentObserver

    override fun attachBaseContext(newBase: Context) {
        val context = LocaleUtils.applyLocale(newBase)
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 确保应用正确的语言设置
        LocaleUtils.applyLanguageSetting(this)

        // 应用自定义 DPI
        DisplayUtils.applyCustomDpi(this)

        // Enable edge to edge
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        super.onCreate(savedInstanceState)

        // 初始化 SuperUserViewModel
        superUserViewModel = SuperUserViewModel()

        lifecycleScope.launch {
            superUserViewModel.fetchAppList()
        }

        // 初始化 HomeViewModel
        homeViewModel = HomeViewModel()

        // 预加载数据
        lifecycleScope.launch {
            homeViewModel.initializeData()
        }

        // 数据刷新协程
        DataRefreshUtils.startDataRefreshCoroutine(lifecycleScope)

        DataRefreshUtils.startSettingsMonitorCoroutine(lifecycleScope, this, settingsStateFlow)

        // 初始化主题相关设置
        ThemeUtils.initializeThemeSettings(this, settingsStateFlow)

        // 设置主题变化监听器
        themeChangeObserver = ThemeUtils.registerThemeChangeObserver(this)

        val isManager = AppData.isManager(ksuApp.packageName)
        if (isManager) {
            install()
            UltraToolInstall.tryToInstall()
        }

        setContent {
            KernelSUTheme {
                val navController = rememberNavController()
                val snackBarHostState = remember { SnackbarHostState() }
                val currentDestination = navController.currentBackStackEntryAsState().value?.destination

                val showBottomBar = when (currentDestination?.route) {
                    ExecuteModuleActionScreenDestination.route -> false // Hide for ExecuteModuleActionScreen
                    else -> true
                }

                // pre-init platform to faster start WebUI X activities
                LaunchedEffect(Unit) {
                    initPlatform()
                }

                CompositionLocalProvider(
                    LocalSnackbarHost provides snackBarHostState
                ) {
                    Scaffold(
                        bottomBar = {
                            AnimatedBottomBar.AnimatedBottomBarWrapper(
                                showBottomBar = showBottomBar,
                                content = { BottomBar(navController) }
                            )
                        },
                        contentWindowInsets = WindowInsets(0, 0, 0, 0)
                    ) { innerPadding ->
                        DestinationsNavHost(
                            modifier = Modifier.padding(innerPadding),
                            navGraph = NavGraphs.root as NavHostGraphSpec,
                            navController = navController,
                            defaultTransitions = NavigationUtils.defaultTransitions()
                        )
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        ThemeUtils.onActivityPause(this)
    }

    override fun onResume() {
        super.onResume()
        LocaleUtils.applyLanguageSetting(this)
        ThemeUtils.onActivityResume()

        lifecycleScope.launch {
            superUserViewModel.fetchAppList()
        }

        lifecycleScope.launch {
            homeViewModel.initializeData()
        }

        DataRefreshUtils.refreshData(lifecycleScope)
    }

    override fun onDestroy() {
        ThemeUtils.unregisterThemeChangeObserver(this, themeChangeObserver)
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        LocaleUtils.applyLanguageSetting(this)
    }
}
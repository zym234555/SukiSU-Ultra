package shirkneko.zako.sukisu.ui.theme

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.zIndex
import coil.compose.rememberAsyncImagePainter
import androidx.compose.foundation.background
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object ThemeConfig {
    var customBackgroundUri by mutableStateOf<Uri?>(null)
    var forceDarkMode by mutableStateOf<Boolean?>(null)
    var currentTheme by mutableStateOf<ThemeColors>(ThemeColors.Default)
    var useDynamicColor by mutableStateOf(false)
}

@Composable
private fun getDarkColorScheme() = darkColorScheme(
    primary = ThemeConfig.currentTheme.Primary.copy(alpha = 0.8f),
    onPrimary = Color.White,
    primaryContainer = ThemeConfig.currentTheme.PrimaryContainer.copy(alpha = 0.15f),
    onPrimaryContainer = Color.White,
    secondary = ThemeConfig.currentTheme.Secondary.copy(alpha = 0.8f),
    onSecondary = Color.White,
    secondaryContainer = ThemeConfig.currentTheme.SecondaryContainer.copy(alpha = 0.15f),
    onSecondaryContainer = Color.White,
    tertiary = ThemeConfig.currentTheme.Tertiary.copy(alpha = 0.8f),
    onTertiary = Color.White,
    tertiaryContainer = ThemeConfig.currentTheme.TertiaryContainer.copy(alpha = 0.15f),
    onTertiaryContainer = Color.White,
    onBackground = Color.White,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF2F2F2F),
    onSurfaceVariant = Color.White.copy(alpha = 0.78f),
    outline = Color.White.copy(alpha = 0.12f),
    outlineVariant = Color.White.copy(alpha = 0.12f)
)

@Composable
private fun getLightColorScheme() = lightColorScheme(
    primary = ThemeConfig.currentTheme.Primary,
    onPrimary = ThemeConfig.currentTheme.OnPrimary,
    primaryContainer = ThemeConfig.currentTheme.PrimaryContainer,
    onPrimaryContainer = ThemeConfig.currentTheme.OnPrimaryContainer,
    secondary = ThemeConfig.currentTheme.Secondary,
    onSecondary = ThemeConfig.currentTheme.OnSecondary,
    secondaryContainer = ThemeConfig.currentTheme.SecondaryContainer,
    onSecondaryContainer = ThemeConfig.currentTheme.OnSecondaryContainer,
    tertiary = ThemeConfig.currentTheme.Tertiary,
    onTertiary = ThemeConfig.currentTheme.OnTertiary,
    tertiaryContainer = ThemeConfig.currentTheme.TertiaryContainer,
    onTertiaryContainer = ThemeConfig.currentTheme.OnTertiaryContainer,
    background = Color.Transparent,
    surface = Color.Transparent,
    onBackground = Color.Black,
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFF5F5F5),
    onSurfaceVariant = Color.Black.copy(alpha = 0.78f),
    outline = Color.Black.copy(alpha = 0.12f),
    outlineVariant = Color.Black.copy(alpha = 0.12f)
)

// 复制图片到应用内部存储
fun Context.copyImageToInternalStorage(uri: Uri): Uri? {
    try {
        val contentResolver: ContentResolver = contentResolver
        val inputStream: InputStream = contentResolver.openInputStream(uri)!!
        val fileName = "custom_background.jpg"
        val file = File(filesDir, fileName)
        val outputStream = FileOutputStream(file)
        val buffer = ByteArray(4 * 1024)
        var read: Int
        while (inputStream.read(buffer).also { read = it } != -1) {
            outputStream.write(buffer, 0, read)
        }
        outputStream.flush()
        outputStream.close()
        inputStream.close()
        return Uri.fromFile(file)
    } catch (e: Exception) {
        Log.e("ImageCopy", "Failed to copy image: ${e.message}")
        return null
    }
}

@Composable
fun KernelSUTheme(
    darkTheme: Boolean = when(ThemeConfig.forceDarkMode) {
        true -> true
        false -> false
        null -> isSystemInDarkTheme()
    },
    dynamicColor: Boolean = ThemeConfig.useDynamicColor,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    context.loadCustomBackground()
    context.loadThemeColors()
    context.loadDynamicColorState()

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context).copy(
                background = Color.Transparent,
                surface = Color.Transparent,
                onBackground = Color.White,
                onSurface = Color.White,
                onPrimary = Color.White,
                onSecondary = Color.White,
                onTertiary = Color.White,
                onPrimaryContainer = Color.White,
                onSecondaryContainer = Color.White,
                onTertiaryContainer = Color.White
            ) else dynamicLightColorScheme(context).copy(
                background = Color.Transparent,
                surface = Color.Transparent
            )
        }
        darkTheme -> getDarkColorScheme()
        else -> getLightColorScheme()
    }

    val isDarkModeWithCustomBackground = darkTheme && ThemeConfig.customBackgroundUri != null

    if (darkTheme && !dynamicColor) {
        CardConfig.setDarkModeDefaults()
    } else {
        CardConfig.cardAlpha = 1f
        CardConfig.cardElevation = CardConfig.defaultElevation
    }

    CardConfig.updateShadowEnabled(!isDarkModeWithCustomBackground)

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 背景图层
            ThemeConfig.customBackgroundUri?.let { uri ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(-1f)
                ) {
                    // 背景图片
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .paint(
                                painter = rememberAsyncImagePainter(
                                    model = uri,
                                    onError = {
                                        ThemeConfig.customBackgroundUri = null
                                        context.saveCustomBackground(null)
                                    }
                                ),
                                contentScale = ContentScale.Crop
                            )
                    )

                    // 亮度调节层
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                if (darkTheme) {
                                    Color.Black.copy(alpha = 0.4f)
                                } else {
                                    Color.White.copy(alpha = 0.1f)
                                }
                            )
                    )

                    // 边缘渐变遮罩层
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        if (darkTheme) {
                                            Color.Black.copy(alpha = 0.5f)
                                        } else {
                                            Color.Black.copy(alpha = 0.2f)
                                        }
                                    ),
                                    radius = 1200f
                                )
                            )
                    )
                }
            }
            // 内容图层
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(1f)
            ) {
                content()
            }
        }
    }
}

fun Context.saveCustomBackground(uri: Uri?) {
    val newUri = uri?.let { copyImageToInternalStorage(it) }
    getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
        .edit()
        .putString("custom_background", newUri?.toString())
        .apply()
    ThemeConfig.customBackgroundUri = newUri
}

fun Context.loadCustomBackground() {
    val uriString = getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
        .getString("custom_background", null)
    ThemeConfig.customBackgroundUri = uriString?.let { Uri.parse(it) }
}

fun Context.saveThemeMode(forceDark: Boolean?) {
    getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
        .edit()
        .putString("theme_mode", when(forceDark) {
            true -> "dark"
            false -> "light"
            null -> "system"
        })
        .apply()
    ThemeConfig.forceDarkMode = forceDark
}

fun Context.loadThemeMode() {
    val mode = getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
        .getString("theme_mode", "system")
    ThemeConfig.forceDarkMode = when(mode) {
        "dark" -> true
        "light" -> false
        else -> null
    }
}

fun Context.saveThemeColors(themeName: String) {
    getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
        .edit()
        .putString("theme_colors", themeName)
        .apply()

    ThemeConfig.currentTheme = when(themeName) {
        "blue" -> ThemeColors.Blue
        "green" -> ThemeColors.Green
        "purple" -> ThemeColors.Purple
        "orange" -> ThemeColors.Orange
        "pink" -> ThemeColors.Pink
        "gray" -> ThemeColors.Gray
        else -> ThemeColors.Default
    }
}

fun Context.loadThemeColors() {
    val themeName = getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
        .getString("theme_colors", "default")

    ThemeConfig.currentTheme = when(themeName) {
        "blue" -> ThemeColors.Blue
        "green" -> ThemeColors.Green
        "purple" -> ThemeColors.Purple
        "orange" -> ThemeColors.Orange
        "pink" -> ThemeColors.Pink
        "gray" -> ThemeColors.Gray
        else -> ThemeColors.Default
    }
}

fun Context.saveDynamicColorState(enabled: Boolean) {
    getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
        .edit()
        .putBoolean("use_dynamic_color", enabled)
        .apply()
    ThemeConfig.useDynamicColor = enabled
}

fun Context.loadDynamicColorState() {
    val enabled = getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
        .getBoolean("use_dynamic_color", true)
    ThemeConfig.useDynamicColor = enabled
}
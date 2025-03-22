package shirkneko.zako.sukisu.ui.theme

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.CardDefaults

object CardConfig {
    val defaultElevation: Dp = 0.dp

    var cardAlpha by mutableStateOf(1f)
    var cardElevation by mutableStateOf(defaultElevation)
    var isShadowEnabled by mutableStateOf(true)
    var isCustomAlphaSet by mutableStateOf(false)

    fun save(context: Context) {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putFloat("card_alpha", cardAlpha)
            putBoolean("custom_background_enabled", cardElevation == 0.dp)
            putBoolean("is_custom_alpha_set", isCustomAlphaSet)
            apply()
        }
    }

    fun load(context: Context) {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        cardAlpha = prefs.getFloat("card_alpha", 1f)
        cardElevation = if (prefs.getBoolean("custom_background_enabled", false)) 0.dp else defaultElevation
        isCustomAlphaSet = prefs.getBoolean("is_custom_alpha_set", false)
    }

    fun updateShadowEnabled(enabled: Boolean) {
        isShadowEnabled = enabled
        cardElevation = if (enabled) defaultElevation else 0.dp
    }

    fun setDarkModeDefaults() {
        if (!isCustomAlphaSet) {
        cardAlpha = 0.5f
        cardElevation = 0.dp
        }
    }
}


@Composable
fun getCardColors(originalColor: Color) = CardDefaults.elevatedCardColors(
    containerColor = originalColor.copy(alpha = CardConfig.cardAlpha),
    contentColor = if (originalColor.luminance() > 0.5) Color.Black else Color.White
)

fun getCardElevation() = CardConfig.cardElevation
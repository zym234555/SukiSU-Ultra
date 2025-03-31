package shirkneko.zako.sukisu.ui.util

import java.io.File

object KernelConfigUtils {

    fun isKpmEnabled(): Boolean {
        return try {
            val config = File("/proc/config.gz").readText()
            config.contains("CONFIG_KPM=y")
        } catch (e: Exception) {
            false
        }
    }
}
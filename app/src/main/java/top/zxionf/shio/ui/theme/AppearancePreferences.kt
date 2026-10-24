package top.zxionf.shio.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit

/** 主题模式：跟随系统、强制浅色、强制深色。 */
enum class ThemeMode(val label: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色"),
    DARK("深色"),
}

/** 配色方案：动态取色 + 5 个预设色板。 */
enum class Palette(val label: String) {
    DYNAMIC("动态取色"),
    SHIO_BLUE("Shio 蓝"),
    TEAL("青"),
    GREEN("绿"),
    PURPLE("紫"),
    ORANGE("橙"),
}

/**
 * 外观偏好：`SharedPreferences` 持久化，进程内单例。
 *
 * 字段是 Compose 快照状态，直接在组合中读取即可触发重组；写入必须走
 * [updateThemeMode] / [updatePalette]，保证内存与磁盘同时更新。
 *
 * 不引入 DataStore：两个枚举值不需要协程与额外的依赖与迁移成本。
 */
class AppearancePreferences private constructor(
    private val prefs: SharedPreferences,
) {
    var themeMode: ThemeMode by mutableStateOf(readThemeMode())
        private set

    var palette: Palette by mutableStateOf(readPalette())
        private set

    fun updateThemeMode(mode: ThemeMode) {
        if (mode == themeMode) return
        themeMode = mode
        prefs.edit { putString(KEY_THEME_MODE, mode.name) }
    }

    fun updatePalette(palette: Palette) {
        if (palette == this.palette) return
        this.palette = palette
        prefs.edit { putString(KEY_PALETTE, palette.name) }
    }

    private fun readThemeMode(): ThemeMode =
        prefs.getString(KEY_THEME_MODE, null).toEnumOrNull<ThemeMode>() ?: ThemeMode.SYSTEM

    private fun readPalette(): Palette =
        prefs.getString(KEY_PALETTE, null).toEnumOrNull<Palette>() ?: Palette.SHIO_BLUE

    companion object {
        private const val PREFS_NAME = "appearance"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_PALETTE = "palette"

        @Volatile
        private var instance: AppearancePreferences? = null

        /** 取进程内单例；只使用 application context，避免持有 Activity。 */
        fun get(context: Context): AppearancePreferences {
            instance?.let { return it }
            return synchronized(this) {
                instance ?: AppearancePreferences(
                    context.applicationContext.getSharedPreferences(
                        PREFS_NAME,
                        Context.MODE_PRIVATE,
                    ),
                ).also { instance = it }
            }
        }
    }
}

/** 反序列化枚举：名字不匹配或旧版本残留时返回 null，由调用方给默认值。 */
private inline fun <reified T : Enum<T>> String?.toEnumOrNull(): T? =
    this?.let { raw -> enumValues<T>().firstOrNull { it.name == raw } }

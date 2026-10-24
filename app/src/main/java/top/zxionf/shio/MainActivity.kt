package top.zxionf.shio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import top.zxionf.shio.app.AppNavHost
import top.zxionf.shio.ui.theme.AppearancePreferences
import top.zxionf.shio.ui.theme.ShioTheme

class MainActivity : ComponentActivity() {
    private val appearance: AppearancePreferences by lazy { AppearancePreferences.get(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ShioTheme(
                themeMode = appearance.themeMode,
                palette = appearance.palette,
            ) {
                AppNavHost()
            }
        }
    }
}

package top.zxionf.shio

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import top.zxionf.shio.app.AppNavHost
import top.zxionf.shio.ui.theme.AppearancePreferences
import top.zxionf.shio.ui.theme.ShioTheme
import top.zxionf.shio.vault.AutoLockController
import top.zxionf.shio.vault.AutoLockPreferences

class MainActivity : ComponentActivity() {
    private val appearance: AppearancePreferences by lazy { AppearancePreferences.get(this) }
    private val autoLock: AutoLockPreferences by lazy { AutoLockPreferences.get(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // release 禁止截屏、录屏与最近任务预览；debug 不设，便于截图验收。
        if (!BuildConfig.DEBUG) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE,
            )
        }
        setContent {
            ShioTheme(
                themeMode = appearance.themeMode,
                palette = appearance.palette,
            ) {
                AppNavHost()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // 回到前台：后台时长超过偏好就锁定（旋转不会走到这里超时）。
        AutoLockController.onActivityStarted(autoLock.timeout)
    }

    override fun onStop() {
        // 旋转等配置变更不算切后台，不触发自动锁定。
        AutoLockController.onActivityStopped(
            timeout = autoLock.timeout,
            isChangingConfigurations = isChangingConfigurations,
        )
        super.onStop()
    }
}

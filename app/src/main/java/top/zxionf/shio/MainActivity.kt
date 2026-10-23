package top.zxionf.shio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import top.zxionf.shio.demo.DemoScreen
import top.zxionf.shio.ui.theme.ShioTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ShioTheme {
                DemoScreen()
            }
        }
    }
}

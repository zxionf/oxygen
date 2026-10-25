package top.zxionf.shio.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 应用图标清单。
 *
 * 图标来自 `androidx.compose.material:material-icons-extended`（BOM 管版本），
 * 不再自绘路径、也不再使用字体图标；矢量图标由布局居中，不会出现垂直偏移。
 * 这层枚举只是把「业务名字」映射到官方 `ImageVector`，方便统一换图标。
 */
enum class AppIcons(val icon: ImageVector) {
    Add(Icons.Filled.Add),
    ArrowBack(Icons.AutoMirrored.Filled.ArrowBack),
    AutoAwesome(Icons.Filled.AutoAwesome),
    Check(Icons.Filled.Check),
    ChevronRight(Icons.AutoMirrored.Filled.KeyboardArrowRight),
    Close(Icons.Filled.Close),
    Copy(Icons.Filled.ContentCopy),
    Delete(Icons.Filled.Delete),
    Edit(Icons.Filled.Edit),
    Info(Icons.Filled.Info),
    Key(Icons.Filled.Key),
    Link(Icons.Filled.Link),
    Lock(Icons.Filled.Lock),
    MoreVert(Icons.Filled.MoreVert),
    Notes(Icons.AutoMirrored.Filled.Notes),
    Palette(Icons.Filled.Palette),
    Person(Icons.Filled.Person),
    Refresh(Icons.Filled.Refresh),
    Search(Icons.Filled.Search),
    Settings(Icons.Filled.Settings),
    Shield(Icons.Filled.Shield),
    Title(Icons.Filled.Title),
    Visibility(Icons.Filled.Visibility),
    VisibilityOff(Icons.Filled.VisibilityOff),
}

/** 统一尺寸与颜色的图标入口：默认 24dp，跟随 `LocalContentColor`。 */
@Composable
fun AppIcon(
    icon: AppIcons,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    size: Dp = 24.dp,
) {
    Icon(
        imageVector = icon.icon,
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        tint = tint,
    )
}

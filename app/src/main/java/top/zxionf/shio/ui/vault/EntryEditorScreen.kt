package top.zxionf.shio.ui.vault

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import top.zxionf.shio.ui.icons.ShioIcons
import top.zxionf.shio.ui.theme.ShioTheme
import top.zxionf.shio.vault.FakeVaultStore
import top.zxionf.shio.vault.SampleVaultEntries
import top.zxionf.shio.vault.VaultEntry

/**
 * 新建 / 编辑共用的条目编辑器。
 *
 * 设计语言：Material 3 Expressive，紫色 light scheme，Roboto，`MotionScheme.standard()`
 * 语义的动效（无回弹）。颜色全部走 scheme role，不写死到组件上。
 *
 * 结构（自上而下）：
 * 1. 64dp 顶栏：左侧关闭、右侧保存，标题「编辑条目 / 新建条目」；
 * 2. 「标题」字段项：leading 标题图标、headline 标题、supporting 当前值、trailing 编辑；
 * 3. 4 项字段列表：用户名 / 密码 / 连接 / 备注，3dp 间距，28dp 外角、8dp 内角；
 * 4. 点任意一项从底部 sheet 只编辑该字段；保存时校验标题与密码，失败弹 Snackbar。
 *
 * 数据：M6 仍用 [FakeVaultStore]（内存）；M11 起换成经内核持久化的仓库。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryEditorScreen(
    entryId: String?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val initial = remember(entryId) { entryId?.let { FakeVaultStore.find(it) } }
    val initialTitle = initial?.title.orEmpty()
    val initialUsername = initial?.username.orEmpty()
    val initialPassword = initial?.password.orEmpty()
    val initialUrl = initial?.url.orEmpty()
    val initialNotes = initial?.notes.orEmpty()

    var title by rememberSaveable(entryId) { mutableStateOf(initialTitle) }
    var username by rememberSaveable(entryId) { mutableStateOf(initialUsername) }
    var password by rememberSaveable(entryId) { mutableStateOf(initialPassword) }
    var url by rememberSaveable(entryId) { mutableStateOf(initialUrl) }
    var notes by rememberSaveable(entryId) { mutableStateOf(initialNotes) }
    var showErrors by rememberSaveable(entryId) { mutableStateOf(false) }
    var editingField by rememberSaveable(entryId) { mutableStateOf<EditorField?>(null) }
    var discardDialogVisible by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val dirty = title != initialTitle ||
        username != initialUsername ||
        password != initialPassword ||
        url != initialUrl ||
        notes != initialNotes

    val titleMissing = title.isBlank()
    val passwordMissing = password.isBlank()

    fun requestBack() {
        if (dirty) discardDialogVisible = true else onBack()
    }

    fun save() {
        if (titleMissing || passwordMissing) {
            showErrors = true
            val message = when {
                titleMissing && passwordMissing -> "请填写标题与密码"
                titleMissing -> "请填写标题"
                else -> "请填写密码"
            }
            scope.launch { snackbarHostState.showSnackbar(message) }
            return
        }
        FakeVaultStore.upsert(
            VaultEntry(
                id = initial?.id ?: FakeVaultStore.newId(),
                title = title.trim(),
                username = username.trim(),
                password = password,
                url = url.trim(),
                notes = notes.trim(),
            ),
        )
        onSaved()
    }

    BackHandler(onBack = ::requestBack)

    EditorTheme {
        Scaffold(
            modifier = modifier,
            containerColor = MaterialTheme.colorScheme.surface,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text(if (initial == null) "新建条目" else "编辑条目") },
                    navigationIcon = {
                        PressScaleIconButton(
                            onClick = ::requestBack,
                            icon = Icons.Filled.Close,
                            contentDescription = "关闭",
                        )
                    },
                    actions = {
                        PressScaleIconButton(
                            onClick = ::save,
                            icon = Icons.Filled.Check,
                            contentDescription = "保存",
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                )
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                FieldItem(
                    label = "标题",
                    value = title,
                    placeholder = "未命名条目",
                    icon = ShioIcons.Title,
                    shape = RoundedCornerShape(28.dp),
                    error = showErrors && titleMissing,
                    onClick = { editingField = EditorField.Title },
                )

                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    FieldItem(
                        label = "用户名",
                        value = username,
                        placeholder = "未填写",
                        icon = Icons.Filled.Person,
                        shape = StackedShapes.first,
                        onClick = { editingField = EditorField.Username },
                    )
                    FieldItem(
                        label = "密码",
                        value = password,
                        placeholder = "未填写",
                        masked = true,
                        icon = ShioIcons.Key,
                        shape = StackedShapes.middle,
                        error = showErrors && passwordMissing,
                        onClick = { editingField = EditorField.Password },
                    )
                    FieldItem(
                        label = "连接",
                        value = url,
                        placeholder = "未填写",
                        icon = ShioIcons.Link,
                        shape = StackedShapes.middle,
                        onClick = { editingField = EditorField.Url },
                    )
                    FieldItem(
                        label = "备注",
                        value = notes,
                        placeholder = "未填写",
                        icon = ShioIcons.Notes,
                        shape = StackedShapes.last,
                        singleLine = true,
                        onClick = { editingField = EditorField.Notes },
                    )
                }
            }
        }

        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        fun closeSheet() {
            scope.launch { sheetState.hide() }.invokeOnCompletion {
                if (!sheetState.isVisible) editingField = null
            }
        }

        editingField?.let { field ->
            ModalBottomSheet(
                onDismissRequest = { editingField = null },
                sheetState = sheetState,
            ) {
                FieldSheet(
                    field = field,
                    value = when (field) {
                        EditorField.Title -> title
                        EditorField.Username -> username
                        EditorField.Password -> password
                        EditorField.Url -> url
                        EditorField.Notes -> notes
                    },
                    onValueChange = { newValue ->
                        when (field) {
                            EditorField.Title -> title = newValue
                            EditorField.Username -> username = newValue
                            EditorField.Password -> password = newValue
                            EditorField.Url -> url = newValue
                            EditorField.Notes -> notes = newValue
                        }
                    },
                    onDone = ::closeSheet,
                )
            }
        }

        if (discardDialogVisible) {
            AlertDialog(
                onDismissRequest = { discardDialogVisible = false },
                shape = RoundedCornerShape(28.dp),
                title = { Text("放弃修改？") },
                text = { Text("当前修改尚未保存，返回后将丢失。") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            discardDialogVisible = false
                            onBack()
                        },
                    ) {
                        Text("放弃", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { discardDialogVisible = false }) { Text("继续编辑") }
                },
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 主题：按设计稿给定的紫色 light scheme，作用域限定在编辑器内。
// 编辑器只做 light 呈现，因此不读取系统的深浅色偏好。
// ---------------------------------------------------------------------------

private val EditorLightColors = lightColorScheme(
    primary = Color(0xFF6750A4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF21005D),
    secondary = Color(0xFF635A75),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF1D192B),
    tertiaryContainer = Color(0xFFFFD8E4),
    onTertiaryContainer = Color(0xFF31111D),
    surface = Color(0xFFFEF7FF),
    onSurface = Color(0xFF1D1B20),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F2FA),
    surfaceContainer = Color(0xFFF3EDF7),
    surfaceContainerHigh = Color(0xFFECE6F0),
    surfaceContainerHighest = Color(0xFFE6E0E9),
    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFCAC4D0),
    inverseSurface = Color(0xFF322F35),
    inverseOnSurface = Color(0xFFF5EFF7),
    inversePrimary = Color(0xFFD0BCFF),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

/** Expressive 的圆角阶梯：按钮胶囊、卡片 20dp、对话框 28dp。 */
private val EditorShapes = RoundedCornerShape(20.dp)

@Composable
private fun EditorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = EditorLightColors,
        shapes = MaterialTheme.shapes.copy(
            extraSmall = RoundedCornerShape(8.dp),
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(20.dp),
            extraLarge = RoundedCornerShape(28.dp),
        ),
        content = content,
    )
}

// ---------------------------------------------------------------------------
// 动效：对应 MotionScheme.standard() 的无回弹弹簧与短时长 tween。
// material3 1.4.0 的 MotionScheme 是 internal，这里按同样的参数复刻。
// ---------------------------------------------------------------------------

private val standardSpatialSpec: FiniteAnimationSpec<Float> = spring(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow,
)


// ---------------------------------------------------------------------------
// 字段项
// ---------------------------------------------------------------------------

/** 48dp 图标按钮：保留 M3 ripple，并叠一道按下缩放。 */
@Composable
private fun PressScaleIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = if (pressed) {
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
        } else {
            standardSpatialSpec
        },
        label = "iconPressScale",
    )

    IconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp).scale(scale),
        interactionSource = interactionSource,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(24.dp),
        )
    }
}

/** 固定 8 个点：不随真实密码长度变化，避免泄漏长度信息。 */
private const val PasswordMask = "••••••••"

private enum class EditorField(
    val label: String,
    val keyboardType: KeyboardType,
    val multiline: Boolean,
) {
    Title("标题", KeyboardType.Text, false),
    Username("用户名", KeyboardType.Text, false),
    Password("密码", KeyboardType.Password, false),
    Url("连接", KeyboardType.Uri, false),
    Notes("备注", KeyboardType.Text, true),
}

/** 3dp 间距堆叠列表的四种圆角：外角 28dp、内角 8dp。 */
private object StackedShapes {
    val first: CornerBasedShape = RoundedCornerShape(
        topStart = 28.dp,
        topEnd = 28.dp,
        bottomStart = 8.dp,
        bottomEnd = 8.dp,
    )
    val middle: CornerBasedShape = RoundedCornerShape(8.dp)
    val last: CornerBasedShape = RoundedCornerShape(
        topStart = 8.dp,
        topEnd = 8.dp,
        bottomStart = 28.dp,
        bottomEnd = 28.dp,
    )
}

/**
 * 72dp 字段项：40dp `primaryContainer` 圆形图标位 + 24dp 图标、`bodyLarge` 字段名、
 * `bodyMedium` 的值、trailing 编辑图标。整行可点，带 ripple 与轻微按下缩放。
 */
@Composable
private fun FieldItem(
    label: String,
    value: String,
    placeholder: String,
    icon: ImageVector,
    shape: Shape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    masked: Boolean = false,
    singleLine: Boolean = false,
    error: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = if (pressed) {
            // 按下用更快的空间曲线，回弹用默认曲线，与 standard scheme 的快慢关系一致。
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
        } else {
            standardSpatialSpec
        },
        label = "fieldPressScale",
    )

    val displayValue = when {
        error -> "必填"
        value.isEmpty() -> placeholder
        masked -> PasswordMask
        singleLine -> value.lineSequence().firstOrNull().orEmpty()
        else -> value
    }

    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .scale(scale),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        interactionSource = interactionSource,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = displayValue,
                    style = MaterialTheme.typography.bodyMedium,
                    color = when {
                        error -> MaterialTheme.colorScheme.error
                        value.isEmpty() -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = "编辑$label",
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 单字段编辑面板：只显示一个输入框，打开即聚焦，底部「完成」为胶囊按钮。 */
@Composable
private fun FieldSheet(
    field: EditorField,
    value: String,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit,
) {
    var revealed by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = field.label,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            singleLine = !field.multiline,
            minLines = if (field.multiline) 4 else 1,
            shape = EditorShapes,
            visualTransformation = if (field == EditorField.Password && !revealed) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = field.keyboardType,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            trailingIcon = if (field == EditorField.Password) {
                {
                    IconButton(onClick = { revealed = !revealed }) {
                        Icon(
                            imageVector = if (revealed) {
                                ShioIcons.VisibilityOff
                            } else {
                                ShioIcons.Visibility
                            },
                            contentDescription = if (revealed) "隐藏密码" else "显示密码",
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            } else {
                null
            },
        )
        Button(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = CircleShape,
        ) {
            Text(text = "完成", style = MaterialTheme.typography.titleMedium)
        }
    }

    LaunchedEffect(field) {
        focusRequester.requestFocus()
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun EntryEditorPreview() {
    val entry = SampleVaultEntries.first()
    ShioTheme {
        EditorTheme {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                FieldItem(
                    label = "标题",
                    value = entry.title,
                    placeholder = "未命名条目",
                    icon = ShioIcons.Title,
                    shape = RoundedCornerShape(28.dp),
                    onClick = {},
                )
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    FieldItem(
                        label = "用户名",
                        value = entry.username,
                        placeholder = "未填写",
                        icon = Icons.Filled.Person,
                        shape = StackedShapes.first,
                        onClick = {},
                    )
                    FieldItem(
                        label = "密码",
                        value = entry.password,
                        placeholder = "未填写",
                        masked = true,
                        icon = ShioIcons.Key,
                        shape = StackedShapes.middle,
                        onClick = {},
                    )
                    FieldItem(
                        label = "连接",
                        value = entry.url,
                        placeholder = "未填写",
                        icon = ShioIcons.Link,
                        shape = StackedShapes.middle,
                        onClick = {},
                    )
                    FieldItem(
                        label = "备注",
                        value = entry.notes,
                        placeholder = "未填写",
                        icon = ShioIcons.Notes,
                        shape = StackedShapes.last,
                        singleLine = true,
                        onClick = {},
                    )
                }
            }
        }
    }
}

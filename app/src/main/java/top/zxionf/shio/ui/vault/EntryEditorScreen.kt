package top.zxionf.shio.ui.vault

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberBottomSheetState
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import top.zxionf.shio.ui.components.AppIcons
import top.zxionf.shio.ui.components.AppTextField
import top.zxionf.shio.ui.components.AppIcon
import top.zxionf.shio.ui.theme.ListStackShapes
import top.zxionf.shio.vault.FakeVaultStore
import top.zxionf.shio.vault.VaultEntry

/**
 * 新建 / 编辑共用的条目编辑器（M3E）。
 *
 * 结构：64dp 顶栏（左关闭 / 右保存）→「标题」单项卡片 → 用户名/密码/连接/备注的
 * 3dp 堆叠列表（外角 28dp、内角 8dp）。点任意一项从底部 sheet 只编辑该字段。
 *
 * 形状、排版、动效全部来自全局 M3E 主题（`MaterialTheme.motionScheme`），
 * 页面不再自带配色或弹簧参数。
 *
 * 数据：M6 仍用 [FakeVaultStore]（内存，debug 构建播样例数据）；M11 起换成内核仓库。
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

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (initial == null) "新建条目" else "编辑条目",
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = ::requestBack) {
                        AppIcon(AppIcons.Close, contentDescription = "关闭")
                    }
                },
                actions = {
                    IconButton(onClick = ::save) {
                        AppIcon(AppIcons.Check, contentDescription = "保存")
                    }
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
                icon = AppIcons.Title,
                shape = ListStackShapes.single,
                error = showErrors && titleMissing,
                onClick = { editingField = EditorField.Title },
            )

            Column(verticalArrangement = Arrangement.spacedBy(ListStackShapes.gap)) {
                FieldItem(
                    label = "用户名",
                    value = username,
                    placeholder = "未填写",
                    icon = AppIcons.Person,
                    shape = ListStackShapes.first,
                    onClick = { editingField = EditorField.Username },
                )
                FieldItem(
                    label = "密码",
                    value = password,
                    placeholder = "未填写",
                    masked = true,
                    icon = AppIcons.Key,
                    shape = ListStackShapes.middle,
                    error = showErrors && passwordMissing,
                    onClick = { editingField = EditorField.Password },
                )
                FieldItem(
                    label = "连接",
                    value = url,
                    placeholder = "未填写",
                    icon = AppIcons.Link,
                    shape = ListStackShapes.middle,
                    onClick = { editingField = EditorField.Url },
                )
                FieldItem(
                    label = "备注",
                    value = notes,
                    placeholder = "未填写",
                    icon = AppIcons.Notes,
                    shape = ListStackShapes.last,
                    singleLine = true,
                    onClick = { editingField = EditorField.Notes },
                )
            }
        }
    }

    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)

    fun closeSheet() {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) editingField = null
        }
    }

    editingField?.let { field ->
        ModalBottomSheet(
            onDismissRequest = { editingField = null },
            sheetState = sheetState,
            // IME 弹起时让 sheet 整体上移；加在 modifier 上才会跟窗口一起重算，
            // 加在内容里会导致先上移、窗口调整尺寸后又被键盘盖住。
            modifier = Modifier.imePadding(),
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
            shape = MaterialTheme.shapes.extraLarge,
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

/** 固定 8 个点：不随真实密码长度变化，避免泄漏长度信息。 */
private const val PasswordMask = "••••••••"

private enum class EditorField(
    val label: String,
    val helper: String,
    val icon: AppIcons,
    val keyboardType: KeyboardType,
    val multiline: Boolean,
) {
    Title("标题", "必填，展示在条目列表里", AppIcons.Title, KeyboardType.Text, false),
    Username("用户名", "可留空", AppIcons.Person, KeyboardType.Text, false),
    Password("密码", "必填，只保存在本机", AppIcons.Key, KeyboardType.Password, false),
    Url("连接", "仅支持 http/https 链接", AppIcons.Link, KeyboardType.Uri, false),
    Notes("备注", "可留空，支持多行", AppIcons.Notes, KeyboardType.Text, true),
}

/**
 * 72dp 字段项：40dp `primaryContainer` 圆形图标位 + `bodyLarge` 字段名 +
 * `bodyMedium` 的值 + trailing 编辑图标。整行可点，ripple + 按下缩放。
 */
@Composable
private fun FieldItem(
    label: String,
    value: String,
    placeholder: String,
    icon: AppIcons,
    shape: CornerBasedShape,
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
            MaterialTheme.motionScheme.fastSpatialSpec()
        } else {
            MaterialTheme.motionScheme.defaultSpatialSpec()
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
                    AppIcon(icon = icon, contentDescription = null)
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
            AppIcon(
                icon = AppIcons.Edit,
                contentDescription = "编辑$label",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 单字段编辑面板：打开即聚焦，底部「完成」为胶囊按钮。 */
@Composable
private fun FieldSheet(
    field: EditorField,
    value: String,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit,
) {
    var revealed by remember { mutableStateOf(false) }
    var generating by rememberSaveable { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            // sheet 内容比可视区高时（键盘弹起）可以滚动，并保证不被键盘遮挡。
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (generating) {
            // M7 生成器：就地替换密码 sheet 的内容，避免嵌套两个 bottom sheet。
            PasswordGeneratorPanel(
                onUse = { generated ->
                    onValueChange(generated)
                    generating = false
                },
                onClose = { generating = false },
            )
        } else {
            AppTextField(
                value = value,
                onValueChange = onValueChange,
                label = field.label,
                modifier = Modifier.focusRequester(focusRequester),
                helperText = field.helper,
                singleLine = !field.multiline,
                minLines = if (field.multiline) 4 else 1,
                keyboardType = field.keyboardType,
                imeAction = ImeAction.Done,
                keyboardActions = KeyboardActions(onDone = { onDone() }),
                visualTransformation = if (field == EditorField.Password && !revealed) {
                    PasswordVisualTransformation()
                } else {
                    VisualTransformation.None
                },
                trailingContent = if (field == EditorField.Password) {
                    {
                        IconToggleButton(
                            checked = revealed,
                            onCheckedChange = { revealed = it },
                        ) {
                            AppIcon(
                                icon = if (revealed) {
                                    AppIcons.VisibilityOff
                                } else {
                                    AppIcons.Visibility
                                },
                                contentDescription = if (revealed) "隐藏密码" else "显示密码",
                            )
                        }
                    }
                } else {
                    null
                },
            )
            if (field == EditorField.Password) {
                TextButton(
                    onClick = { generating = true },
                    modifier = Modifier.align(Alignment.End),
                ) {
                    AppIcon(AppIcons.AutoAwesome, contentDescription = null, size = 20.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("生成密码")
                }
            }
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
    }

    LaunchedEffect(field, generating) {
        if (!generating) focusRequester.requestFocus()
    }
}

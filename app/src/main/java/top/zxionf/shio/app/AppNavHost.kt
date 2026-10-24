package top.zxionf.shio.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import top.zxionf.shio.demo.DemoScreen
import top.zxionf.shio.ui.settings.AppearanceScreen

/** 骨架阶段用于演示的假条目 ID；M11 起由内核返回。 */
private const val PLACEHOLDER_ENTRY_ID = "01a168ba-327b-7202-a239-e10c138df11a"

/**
 * 应用的唯一导航图。
 *
 * 返回栈规则（见 `.design/changes/20261023-password-vault/design.md`）：
 * 解锁/创建成功后 `vault` 成为新栈底；锁定时回到 `unlock` 并清空回退栈。
 */
@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = Routes.Gate,
        modifier = modifier,
    ) {
        composable(Routes.Gate) {
            // M1 只有骨架：gate 暂时固定进入密码库，并把 gate 移出回退栈。
            LaunchedEffect(navController) {
                navController.navigate(Routes.Vault) {
                    popUpTo(Routes.Gate) { inclusive = true }
                }
            }
            PlaceholderScreen(
                title = "启动分流",
                route = Routes.Gate,
                description = "检查 vault.db 是否存在：存在进解锁页，否则进创建页。当前固定进入密码库。",
            )
        }

        composable(Routes.Create) {
            PlaceholderScreen(
                title = "创建密码库",
                route = Routes.Create,
                description = "主口令 + 确认主口令；提示忘记主口令将无法恢复数据。",
            )
        }

        composable(Routes.Unlock) {
            PlaceholderScreen(
                title = "解锁",
                route = Routes.Unlock,
                description = "输入主口令解锁，错误显示在内联错误区（如 INVALID_KEY (-3)）。",
            )
        }

        composable(Routes.Vault) {
            PlaceholderScreen(
                title = "密码库",
                route = Routes.Vault,
                description = "条目列表、搜索、新建 FAB、锁定与设置入口。",
                actions = listOf(
                    PlaceholderAction("新建条目（editor）") {
                        navController.navigate(Routes.editor())
                    },
                    PlaceholderAction("演示条目详情（entry）") {
                        navController.navigate(Routes.entry(PLACEHOLDER_ENTRY_ID))
                    },
                    PlaceholderAction("设置（settings）") {
                        navController.navigate(Routes.Settings)
                    },
                    PlaceholderAction("外观（appearance）") {
                        navController.navigate(Routes.Appearance)
                    },
                    PlaceholderAction("原型 Demo（demo）") {
                        navController.navigate(Routes.Demo)
                    },
                ),
            )
        }

        composable(
            route = Routes.EntryPattern,
            arguments = listOf(navArgument(Routes.EntryIdArg) { type = NavType.StringType }),
        ) { backStackEntry ->
            val entryId = backStackEntry.arguments?.getString(Routes.EntryIdArg).orEmpty()
            PlaceholderScreen(
                title = "条目详情",
                route = Routes.entry(entryId),
                description = "字段行（用户名/密码/网址/备注）、复制、编辑、删除确认。",
                actions = listOf(
                    PlaceholderAction("编辑此条目（editor?entryId）") {
                        navController.navigate(Routes.editor(entryId))
                    },
                ),
            )
        }

        composable(
            route = Routes.EditorPattern,
            arguments = listOf(
                navArgument(Routes.EditorEntryIdArg) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { backStackEntry ->
            val entryId = backStackEntry.arguments?.getString(Routes.EditorEntryIdArg)
            PlaceholderScreen(
                title = if (entryId == null) "新建条目" else "编辑条目",
                route = Routes.editor(entryId),
                description = if (entryId == null) {
                    "新建：标题与密码必填，返回时若有未保存修改需确认。"
                } else {
                    "编辑 ID：$entryId；保存后回退并刷新来源页面。"
                },
            )
        }

        composable(Routes.Settings) {
            PlaceholderScreen(
                title = "设置",
                route = Routes.Settings,
                description = "自动锁定、外观入口、原型 Demo 入口、关于（版本/数据库路径/ABI）。",
                actions = listOf(
                    PlaceholderAction("外观（appearance）") {
                        navController.navigate(Routes.Appearance)
                    },
                    PlaceholderAction("原型 Demo（demo）") {
                        navController.navigate(Routes.Demo)
                    },
                ),
            )
        }

        composable(Routes.Appearance) {
            AppearanceScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.Demo) {
            // 原型 Demo 必须始终可打开，这里直接复用现有实现。
            DemoScreen()
        }
    }
}

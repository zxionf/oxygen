package top.zxionf.shio.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import top.zxionf.shio.demo.DemoScreen
import top.zxionf.shio.ui.settings.AppearanceScreen
import top.zxionf.shio.ui.settings.SettingsScreen
import top.zxionf.shio.ui.unlock.LockMode
import top.zxionf.shio.ui.unlock.LockScreen
import top.zxionf.shio.ui.vault.EntryDetailScreen
import top.zxionf.shio.ui.vault.EntryEditorScreen
import top.zxionf.shio.ui.vault.VaultListScreen
import top.zxionf.shio.vault.VaultPaths

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
            // 按 vault.db 是否存在分流：有库进 unlock，没有进 create；gate 自身立即移出回退栈。
            val context = LocalContext.current
            LaunchedEffect(navController) {
                val destination = if (VaultPaths.exists(context)) Routes.Unlock else Routes.Create
                navController.navigate(destination) {
                    popUpTo(Routes.Gate) { inclusive = true }
                }
            }
            PlaceholderScreen(
                title = "启动分流",
                route = Routes.Gate,
                description = "检查 vault.db 是否存在：存在进解锁页，否则进创建页。",
            )
        }

        composable(Routes.Create) {
            // 创建页可能是栈底（gate 分流来），此时不显示返回箭头：返回即退出应用。
            LockScreen(
                mode = LockMode.Create,
                onBack = if (navController.previousBackStackEntry != null) {
                    { navController.popBackStack() }
                } else {
                    null
                },
                onSuccess = {
                    navController.navigate(Routes.Vault) {
                        popUpTo(Routes.Create) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.Unlock) {
            // 解锁页永远是栈底：返回退出应用，不能退回密码库。
            LockScreen(
                mode = LockMode.Unlock,
                onBack = if (navController.previousBackStackEntry != null) {
                    { navController.popBackStack() }
                } else {
                    null
                },
                onSuccess = {
                    navController.navigate(Routes.Vault) {
                        popUpTo(Routes.Unlock) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.Vault) {
            VaultListScreen(
                onLock = {
                    // 锁定必须清空整个回退栈（列表、详情、设置……），解锁页成为新栈底，
                    // 否则返回键能直接退回已锁定内容。
                    navController.navigate(Routes.Unlock) {
                        popUpTo(navController.graph.id) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onSettings = { navController.navigate(Routes.Settings) },
                onCreateEntry = { navController.navigate(Routes.editor()) },
                onOpenEntry = { entryId -> navController.navigate(Routes.entry(entryId)) },
            )
        }

        composable(
            route = Routes.EntryPattern,
            arguments = listOf(navArgument(Routes.EntryIdArg) { type = NavType.StringType }),
        ) { backStackEntry ->
            val entryId = backStackEntry.arguments?.getString(Routes.EntryIdArg).orEmpty()
            EntryDetailScreen(
                entryId = entryId,
                onBack = { navController.popBackStack() },
                onEdit = { id -> navController.navigate(Routes.editor(id)) },
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
            EntryEditorScreen(
                entryId = entryId,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }

        composable(Routes.Settings) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onAppearance = { navController.navigate(Routes.Appearance) },
                onDemo = { navController.navigate(Routes.Demo) },
            )
        }

        composable(Routes.Appearance) {
            AppearanceScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.Demo) {
            // 原型 Demo 必须始终可打开，这里直接复用现有实现。
            DemoScreen(onBack = { navController.popBackStack() })
        }
    }
}

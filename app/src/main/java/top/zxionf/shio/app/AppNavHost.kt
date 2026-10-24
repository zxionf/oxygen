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
import top.zxionf.shio.ui.settings.SettingsScreen
import top.zxionf.shio.ui.unlock.LockMode
import top.zxionf.shio.ui.unlock.LockScreen
import top.zxionf.shio.ui.vault.EntryDetailScreen
import top.zxionf.shio.ui.vault.EntryEditorScreen
import top.zxionf.shio.ui.vault.VaultListScreen

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
            LockScreen(
                mode = LockMode.Create,
                onBack = { navController.popBackStack() },
                onSuccess = {
                    navController.navigate(Routes.Vault) {
                        popUpTo(Routes.Create) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.Unlock) {
            LockScreen(
                mode = LockMode.Unlock,
                onBack = { navController.popBackStack() },
                onSuccess = {
                    navController.navigate(Routes.Vault) {
                        popUpTo(Routes.Unlock) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.Vault) {
            VaultListScreen(
                onLock = { navController.navigate(Routes.Unlock) },
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

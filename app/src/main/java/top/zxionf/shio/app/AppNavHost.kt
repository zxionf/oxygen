package top.zxionf.shio.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import top.zxionf.shio.demo.DemoScreen
import top.zxionf.shio.ui.settings.AppearanceScreen
import top.zxionf.shio.ui.settings.ChangePasswordScreen
import top.zxionf.shio.ui.settings.SettingsScreen
import top.zxionf.shio.ui.settings.TagsScreen
import top.zxionf.shio.ui.unlock.LockMode
import top.zxionf.shio.ui.unlock.LockScreen
import top.zxionf.shio.ui.vault.EntryDetailScreen
import top.zxionf.shio.ui.vault.EntryEditorScreen
import top.zxionf.shio.ui.vault.VaultListPickerScreen
import top.zxionf.shio.ui.vault.VaultListScreen
import top.zxionf.shio.vault.AutoLockController
import top.zxionf.shio.vault.VaultComponent
import top.zxionf.shio.vault.VaultLibrary

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
    // 锁定（手动或自动）已由 AutoLockController 销毁句柄；这里只负责清栈并回到解锁页，
    // 保证「销毁会话」和「导航」各只有一处实现。
    val lockCount by AutoLockController.lockCount.collectAsStateWithLifecycle()
    val initialLockCount = remember { lockCount }
    LaunchedEffect(lockCount) {
        if (lockCount == initialLockCount) return@LaunchedEffect
        navController.navigate(Routes.Unlock) {
            popUpTo(navController.graph.id) { inclusive = true }
            launchSingleTop = true
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.Gate,
        modifier = modifier,
    ) {
        composable(Routes.Gate) {
            // 多库分流：先把旧 vault.db 注册进索引，再按库数量决定去哪。
            val context = LocalContext.current
            LaunchedEffect(navController) {
                VaultLibrary.ensureLegacyRegistered(context)
                val entries = VaultLibrary.list(context)
                val destination = when {
                    entries.isEmpty() -> Routes.Create
                    entries.size == 1 -> Routes.unlock(entries.first().id)
                    else -> Routes.Vaults
                }
                navController.navigate(destination) {
                    popUpTo(Routes.Gate) { inclusive = true }
                }
            }
            PlaceholderScreen(
                title = "启动分流",
                route = Routes.Gate,
                description = "没有库进创建页，一个库直接解锁，多个库先进库列表。",
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
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.Vaults) {
            VaultListPickerScreen(
                onBack = if (navController.previousBackStackEntry != null) {
                    { navController.popBackStack() }
                } else {
                    null
                },
                onUnlock = { vaultId -> navController.navigate(Routes.unlock(vaultId)) },
                onCreate = { navController.navigate(Routes.Create) },
            )
        }

        composable(
            route = Routes.UnlockPattern,
            arguments = listOf(
                navArgument(Routes.VaultIdArg) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { backStackEntry ->
            val vaultId = backStackEntry.arguments?.getString(Routes.VaultIdArg)
            // 解锁页永远是栈底：返回退出应用，不能退回密码库。
            LockScreen(
                mode = LockMode.Unlock,
                vaultId = vaultId,
                onBack = if (navController.previousBackStackEntry != null) {
                    { navController.popBackStack() }
                } else {
                    null
                },
                onSuccess = {
                    navController.navigate(Routes.Vault) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.Vault) {
            RequireUnlocked(navController) {
                VaultListScreen(
                    onLock = { AutoLockController.lockNow() },
                    onSettings = { navController.navigate(Routes.Settings) },
                    onCreateEntry = { navController.navigate(Routes.editor()) },
                    onOpenEntry = { entryId -> navController.navigate(Routes.entry(entryId)) },
                )
            }
        }

        composable(
            route = Routes.EntryPattern,
            arguments = listOf(navArgument(Routes.EntryIdArg) { type = NavType.StringType }),
        ) { backStackEntry ->
            val entryId = backStackEntry.arguments?.getString(Routes.EntryIdArg).orEmpty()
            RequireUnlocked(navController) {
                EntryDetailScreen(
                    entryId = entryId,
                    onBack = { navController.popBackStack() },
                    onEdit = { id -> navController.navigate(Routes.editor(id)) },
                )
            }
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
            RequireUnlocked(navController) {
                EntryEditorScreen(
                    entryId = entryId,
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                )
            }
        }

        composable(Routes.Settings) {
            RequireUnlocked(navController) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onAppearance = { navController.navigate(Routes.Appearance) },
                    onChangePassword = { navController.navigate(Routes.ChangePassword) },
                    onManageTags = { navController.navigate(Routes.Tags) },
                    // 切换库：回库列表。旧会话的句柄会在下次 open 时被销毁，不必先手动锁定。
                    onSwitchVault = {
                        navController.navigate(Routes.Vaults) {
                            popUpTo(navController.graph.id) { inclusive = true }
                        }
                    },
                    // 恢复后旧会话作废：清栈回解锁页，用备份时的主口令重新解锁。
                    onRestored = {
                        navController.navigate(Routes.Unlock) {
                            popUpTo(navController.graph.id) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onDemo = { navController.navigate(Routes.Demo) },
                )
            }
        }

        composable(Routes.Appearance) {
            RequireUnlocked(navController) {
                AppearanceScreen(onBack = { navController.popBackStack() })
            }
        }

        composable(Routes.ChangePassword) {
            RequireUnlocked(navController) {
                ChangePasswordScreen(
                    onBack = { navController.popBackStack() },
                    onChanged = { navController.popBackStack() },
                )
            }
        }

        composable(Routes.Tags) {
            RequireUnlocked(navController) {
                TagsScreen(onBack = { navController.popBackStack() })
            }
        }

        composable(Routes.Demo) {
            // 原型 Demo 必须始终可打开，这里直接复用现有实现。
            DemoScreen(onBack = { navController.popBackStack() })
        }
    }
}

/**
 * 未解锁守卫：进程被回收后系统按保存状态恢复导航栈时，可能直接落回 `vault`/`entry`/
 * `settings`，而内核句柄与内存会话已经没了。这里在进入受保护页面时检查会话，
 * 缺失就按 `vault.db` 是否存在重定向到 `unlock` / `create`，并清空回退栈。
 *
 * 锁定流程自己会先清栈再进解锁页，不会与这里冲突。
 */
@Composable
private fun RequireUnlocked(
    navController: NavHostController,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val unlocked = remember { VaultComponent.repository.isUnlocked }
    if (unlocked) {
        content()
        return
    }
    LaunchedEffect(navController) {
        // 会话丢失时回到「上次的库」或库列表：与 gate 的分流规则保持一致。
        VaultLibrary.ensureLegacyRegistered(context)
        val entries = VaultLibrary.list(context)
        val lastId = VaultLibrary.lastVaultId(context)
        val destination = when {
            entries.isEmpty() -> Routes.Create
            entries.any { it.id == lastId } -> Routes.unlock(lastId!!)
            entries.size == 1 -> Routes.unlock(entries.first().id)
            else -> Routes.Vaults
        }
        navController.navigate(destination) {
            popUpTo(navController.graph.id) { inclusive = true }
        }
    }
}

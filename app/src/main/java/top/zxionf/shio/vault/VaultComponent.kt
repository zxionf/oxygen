package top.zxionf.shio.vault

/**
 * 应用级密码库组件：整个进程共用一个 [VaultRepository]（进而是同一个内核句柄）。
 *
 * 放在这里而不是 ViewModel，是因为解锁状态要跨导航图与 Activity 配置变更存活：
 * 旋转屏幕不能把已解锁的库弄回未解锁，而导航回退栈销毁时也不应该顺带丢会话。
 * 锁定（[VaultRepository.lock]）会销毁句柄并清空 `session`。
 */
object VaultComponent {
    val repository: VaultRepository = VaultRepository()
}

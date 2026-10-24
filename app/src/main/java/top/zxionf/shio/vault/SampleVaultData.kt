package top.zxionf.shio.vault

/**
 * M4 假数据：覆盖「有用户名」「只有网址」「只有备注」「中文标题」几种列表项形态。
 * M13 接入真实数据后删除。
 */
internal val SampleVaultEntries = listOf(
    VaultEntry(
        id = "01a168ba-327b-7202-a239-e10c138df11a",
        title = "GitHub",
        username = "zxionf@example.com",
        password = "gh-fake-password-1",
        url = "https://github.com",
        notes = "两步验证已开启，恢复码在保险箱 recovery-codes.txt",
    ),
    VaultEntry(
        id = "01a168bb-11c2-7a41-9a82-4d8e0c7b5f10",
        title = "银行 App",
        username = "6222••••••••1234",
        password = "bank-fake-password",
        url = "",
        notes = "登录密码与取款密码不同",
    ),
    VaultEntry(
        id = "01a168bc-2f30-7c11-8b70-9a1d2c3e4f50",
        title = "公司 VPN",
        username = "zxionf",
        password = "vpn-fake-password",
        url = "https://vpn.example.com",
        notes = "",
    ),
    VaultEntry(
        id = "01a168bd-3a41-7d22-9c81-0b2e3d4f5a60",
        title = "路由器管理",
        username = "admin",
        password = "router-fake-password",
        url = "http://192.168.1.1",
        notes = "仅内网可访问",
    ),
    VaultEntry(
        id = "01a168be-4b52-7e33-8d92-1c3f4e5a6b70",
        title = "Netflix",
        username = "",
        password = "netflix-fake-password",
        url = "https://www.netflix.com",
        notes = "家庭共享账号",
    ),
    VaultEntry(
        id = "01a168bf-5c63-7f44-9ea3-2d4a5f6b7c80",
        title = "邮箱",
        username = "zxionf@example.com",
        password = "mail-fake-password",
        url = "https://mail.example.com",
        notes = "个人主邮箱",
    ),
    VaultEntry(
        id = "01a168c0-6d74-7045-8fb4-3e5b6a7c8d90",
        title = "开发服务器",
        username = "root",
        password = "server-fake-password",
        url = "",
        notes = "端口 22，仅跳板机可达",
    ),
    VaultEntry(
        id = "01a168c1-7e85-7156-9ac5-4f6c7b8d9ea0",
        title = "Wi-Fi 密码",
        username = "",
        password = "wifi-fake-password",
        url = "",
        notes = "办公室访客网络",
    ),
)

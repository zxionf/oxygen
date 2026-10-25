package top.zxionf.shio.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 列表搜索与排序：覆盖标题、用户名、网址、备注四个字段与忽略大小写，
 * 以及列表页使用的「按标题小写排序」规则。
 */
class VaultEntrySearchTest {

    private val github = VaultEntry(
        id = "1",
        title = "GitHub",
        username = "zxionf@example.com",
        password = "secret",
        url = "https://github.com",
        notes = "两步验证已开启，恢复码在 recovery-codes.txt",
    )

    private val bank = VaultEntry(
        id = "2",
        title = "银行 App",
        username = "6222****1234",
        password = "secret",
        url = "https://bank.example.com",
        notes = "",
    )

    private val wifi = VaultEntry(
        id = "3",
        title = "Wi-Fi 密码",
        username = "",
        password = "secret",
        url = "",
        notes = "家里路由器，5GHz",
    )

    private val all = listOf(github, bank, wifi)

    private fun search(keyword: String): List<VaultEntry> =
        all.filter { it.matches(keyword) }.sortedBy { it.title.lowercase() }

    @Test
    fun `空关键词返回全部条目`() {
        assertEquals(3, search("").size)
        assertEquals(3, search("   ").size)
    }

    @Test
    fun `标题命中且忽略大小写`() {
        assertEquals(listOf(github), search("git"))
        assertEquals(listOf(github), search("GITHUB"))
        assertEquals(listOf(wifi), search("wi-fi"))
    }

    @Test
    fun `用户名命中`() {
        assertEquals(listOf(bank), search("6222"))
        assertEquals(listOf(github), search("zxionf"))
    }

    @Test
    fun `网址命中`() {
        assertEquals(listOf(bank), search("bank.example"))
        assertEquals(listOf(github), search("https://github.com"))
    }

    @Test
    fun `备注命中`() {
        assertEquals(listOf(github), search("recovery"))
        assertEquals(listOf(wifi), search("5GHz"))
    }

    @Test
    fun `密码字段不参与搜索`() {
        // 明文密码不参与搜索，避免通过搜索框探测口令内容。
        assertFalse(github.matches("secret"))
    }

    @Test
    fun `无命中返回空列表`() {
        assertTrue(search("zzzz").isEmpty())
    }

    @Test
    fun `按标题小写排序`() {
        // "github" < "wi-fi 密码" < "银行 app"（中文按码位排在后）
        assertEquals(listOf(github, wifi, bank), search(""))
    }
}

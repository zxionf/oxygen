package top.zxionf.shio.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 导入导出编解码：往返一致、必填校验、格式识别、上限。 */
class VaultTransferTest {

    private val entries = listOf(
        VaultEntry(
            id = "",
            title = "GitHub",
            username = "zxionf",
            password = "p@ss,with\"quote",
            url = "https://github.com",
            notes = "第一行\n第二行含逗号,和\"引号\"",
        ),
        VaultEntry(
            id = "",
            title = "Wi-Fi 密码",
            username = "",
            password = "wifi123",
            url = "",
            notes = "",
        ),
    )

    @Test
    fun `CSV 往返后内容一致（含逗号 引号 换行）`() {
        val csv = VaultTransfer.encodeCsv(entries)
        val parsed = VaultTransfer.decodeCsv(csv)
        assertEquals(0, parsed.skipped)
        assertEquals(entries.size, parsed.entries.size)
        entries.zip(parsed.entries).forEach { (expected, actual) ->
            assertEquals(expected.title, actual.title)
            assertEquals(expected.username, actual.username)
            assertEquals(expected.password, actual.password)
            assertEquals(expected.url, actual.url)
            assertEquals(expected.notes, actual.notes)
        }
    }

    @Test
    fun `CSV 表头固定且以换行分隔`() {
        val csv = VaultTransfer.encodeCsv(entries)
        assertTrue(csv.startsWith("title,username,password,url,notes\n"))
        assertTrue(csv.endsWith("\n"))
    }

    @Test
    fun `JSON 往返后内容一致`() {
        val json = VaultTransfer.encodeJson(entries)
        val parsed = VaultTransfer.decodeJson(json)
        assertEquals(0, parsed.skipped)
        assertEquals(entries.size, parsed.entries.size)
        assertEquals(entries.map { it.notes }, parsed.entries.map { it.notes })
    }

    @Test
    fun `JSON 也接受单个对象`() {
        val json = """{"title":"单个","password":"p"}"""
        val parsed = VaultTransfer.decodeJson(json)
        assertEquals(1, parsed.entries.size)
        assertEquals("单个", parsed.entries.first().title)
    }

    @Test
    fun `格式识别按首字符分流`() {
        assertEquals(2, VaultTransfer.detectAndDecode(VaultTransfer.encodeJson(entries)).entries.size)
        assertEquals(2, VaultTransfer.detectAndDecode(VaultTransfer.encodeCsv(entries)).entries.size)
        // 带 BOM 与空白也要能识别成 JSON
        val withBom = "\uFEFF  " + VaultTransfer.encodeJson(entries)
        assertEquals(2, VaultTransfer.detectAndDecode(withBom).entries.size)
    }

    @Test
    fun `缺 title 或 password 的行被跳过并计数`() {
        val csv = """
            title,username,password,url,notes
            有密码,user,pass,,
            ,user,pass,,
            无密码,user,,,
        """.trimIndent()
        val parsed = VaultTransfer.decodeCsv(csv)
        assertEquals(1, parsed.entries.size)
        assertEquals(2, parsed.skipped)
    }

    @Test
    fun `空内容与只有表头都返回空结果`() {
        assertEquals(VaultTransfer.ParseResult(), VaultTransfer.decodeCsv(""))
        assertEquals(VaultTransfer.ParseResult(), VaultTransfer.decodeJson(""))
        val onlyHeader = VaultTransfer.decodeCsv("title,username,password,url,notes\n")
        assertTrue(onlyHeader.entries.isEmpty())
        assertEquals(0, onlyHeader.skipped)
    }

    @Test
    fun `超过上限的部分计入跳过`() {
        val many = (1..VaultTransfer.MAX_IMPORT_ENTRIES + 5).map { index ->
            VaultEntry(id = "", title = "t$index", password = "p$index")
        }
        val parsed = VaultTransfer.decodeCsv(VaultTransfer.encodeCsv(many))
        assertEquals(VaultTransfer.MAX_IMPORT_ENTRIES, parsed.entries.size)
        assertEquals(5, parsed.skipped)
    }

    @Test
    fun `CSV 缺列按空处理`() {
        val csv = "title,password\n只有两列,pass\n"
        val parsed = VaultTransfer.decodeCsv(csv)
        assertEquals(1, parsed.entries.size)
        assertEquals("", parsed.entries.first().url)
        assertEquals("", parsed.entries.first().notes)
    }
}

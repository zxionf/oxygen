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
        assertTrue(csv.startsWith("title,username,password,url,notes,tags,favorite\n"))
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
        assertEquals(1, parsed.items.size)
        assertEquals("", parsed.items.first().entry.url)
        assertEquals("", parsed.items.first().entry.notes)
        assertTrue(parsed.items.first().tagNames.isEmpty())
        assertEquals(false, parsed.items.first().entry.favorite)
    }

    // ---- 标签与收藏 ----

    private val taggedEntries = listOf(
        VaultEntry(
            id = "entry-1",
            title = "GitHub",
            password = "secret",
            tagIds = listOf("tag-a", "tag-gone", "tag-b"),
            favorite = true,
        ),
        VaultEntry(id = "entry-2", title = "路由器", password = "pw"),
    )

    private val tagNames = mapOf("tag-a" to "工作", "tag-b" to "财务;重要", "tag-c" to "备份")

    @Test
    fun `CSV 导出带标签与收藏，往返一致`() {
        val csv = VaultTransfer.encodeCsv(taggedEntries, tagNames)
        assertTrue(csv.lines()[1].endsWith(",工作;财务\\;重要,1"))
        val parsed = VaultTransfer.decodeCsv(csv)
        assertEquals(0, parsed.skipped)
        // 悬空 ID 被跳过，名字顺序跟随 tagIds
        assertEquals(listOf("工作", "财务;重要"), parsed.items[0].tagNames)
        assertEquals(true, parsed.items[0].entry.favorite)
        assertTrue(parsed.items[1].tagNames.isEmpty())
        assertEquals(false, parsed.items[1].entry.favorite)
    }

    @Test
    fun `JSON 导出带标签数组与布尔收藏`() {
        val json = VaultTransfer.encodeJson(taggedEntries, tagNames)
        assertTrue(json.contains("\"tags\""))
        assertTrue(json.contains("\"favorite\": true"))
        val parsed = VaultTransfer.decodeJson(json)
        assertEquals(listOf("工作", "财务;重要"), parsed.items[0].tagNames)
        assertEquals(true, parsed.items[0].entry.favorite)
    }

    @Test
    fun `标签名中的反斜杠与分号双向转义`() {
        val names = listOf("a;b", "c\\d", "e\\;f")
        val entry = VaultEntry(id = "x", title = "t", password = "p", tagIds = listOf("1", "2", "3"))
        val map = mapOf("1" to names[0], "2" to names[1], "3" to names[2])
        assertEquals(names, VaultTransfer.decodeCsv(VaultTransfer.encodeCsv(listOf(entry), map)).items[0].tagNames)
        assertEquals(names, VaultTransfer.decodeJson(VaultTransfer.encodeJson(listOf(entry), map)).items[0].tagNames)
    }

    @Test
    fun `标签名去空白且空片断被丢弃`() {
        val csv = "title,password,tags\n只有标签,pass, 工作 ;; 个人 ;\n"
        val parsed = VaultTransfer.decodeCsv(csv)
        assertEquals(listOf("工作", "个人"), parsed.items.first().tagNames)
    }

    @Test
    fun `favorite 兼容 0 1 布尔与文字`() {
        val csv = "title,password,favorite\na,pass,1\nb,pass,0\nc,pass,true\nd,pass,FALSE\ne,pass,\n"
        assertEquals(
            listOf(true, false, true, false, false),
            VaultTransfer.decodeCsv(csv).items.map { it.entry.favorite },
        )
        val json = """[{"title":"a","password":"p","favorite":1},{"title":"b","password":"p","favorite":"yes"},{"title":"c","password":"p"}]"""
        assertEquals(
            listOf(true, true, false),
            VaultTransfer.decodeJson(json).items.map { it.entry.favorite },
        )
    }

    @Test
    fun `JSON 的 tags 也接受分号字符串`() {
        val json = """{"title":"t","password":"p","tags":"工作; 个人"}"""
        assertEquals(listOf("工作", "个人"), VaultTransfer.decodeJson(json).items.first().tagNames)
    }

    @Test
    fun `旧格式（五列 CSV 与无新字段 JSON）仍可导入`() {
        val oldCsv = "title,username,password,url,notes\n旧条目,user,pass,https://a,备注\n"
        val fromCsv = VaultTransfer.decodeCsv(oldCsv)
        assertEquals(1, fromCsv.items.size)
        assertEquals(0, fromCsv.skipped)
        assertTrue(fromCsv.items.first().tagNames.isEmpty())
        assertEquals(false, fromCsv.items.first().entry.favorite)

        val oldJson = """[{"title":"旧条目","username":"user","password":"pass","url":"","notes":""}]"""
        val fromJson = VaultTransfer.decodeJson(oldJson)
        assertEquals(1, fromJson.items.size)
        assertTrue(fromJson.items.first().tagNames.isEmpty())
        assertEquals(false, fromJson.items.first().entry.favorite)
    }
}

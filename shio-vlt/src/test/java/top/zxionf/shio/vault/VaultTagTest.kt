package top.zxionf.shio.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 标签编解码与条目字段增量（`tagIds` / `favorite`）。 */
class VaultTagTest {

    @Test
    fun `标签编解码往返`() {
        val tag = VaultTag(id = "t1", name = "工作")
        val json = VaultTagCodec.encode(tag)
        assertEquals("""{"name":"工作"}""", json)
        assertEquals(tag, VaultTagCodec.decode("t1", json))
    }

    @Test
    fun `名称为空或非法 JSON 时解码返回 null`() {
        assertNull(VaultTagCodec.decode("t1", """{"name":"  "}"""))
        assertNull(VaultTagCodec.decode("t1", "not json"))
    }

    @Test
    fun `规范化名称去首尾空白并压缩内部空白`() {
        assertEquals("工作 项目", VaultTagCodec.normalizeName("  工作   项目  "))
        assertEquals("work", VaultTagCodec.normalizeName("work"))
    }

    @Test
    fun `空白超长与控制字符被拒绝`() {
        assertNull(VaultTagCodec.normalizeName(""))
        assertNull(VaultTagCodec.normalizeName("   "))
        assertNull(VaultTagCodec.normalizeName("a".repeat(VaultTagCodec.MAX_NAME_LENGTH + 1)))
        assertNull(VaultTagCodec.normalizeName("带\u0000控制字符"))
    }

    @Test
    fun `条目编解码保留标签与收藏`() {
        val entry = VaultEntry(
            id = "e1",
            title = "GitHub",
            password = "p",
            tagIds = listOf("t1", "t2"),
            favorite = true,
        )
        val json = VaultEntryCodec.encode(entry)
        val decoded = requireNotNull(VaultEntryCodec.decode("e1", json))
        assertEquals(listOf("t1", "t2"), decoded.tagIds)
        assertTrue(decoded.favorite)
    }

    @Test
    fun `旧记录没有标签与收藏字段时按默认值解码`() {
        val legacy = """{"title":"旧条目","username":"u","password":"p","url":"","notes":""}"""
        val decoded = requireNotNull(VaultEntryCodec.decode("e9", legacy))
        assertTrue(decoded.tagIds.isEmpty())
        assertFalse(decoded.favorite)
    }

    @Test
    fun `tagIds 里非法与重复项被忽略`() {
        val json = """{"title":"t","password":"p","tagIds":["a","","a",3]}"""
        val decoded = requireNotNull(VaultEntryCodec.decode("e2", json))
        assertEquals(listOf("a"), decoded.tagIds)
    }

    @Test
    fun `空标签与未收藏不写入 JSON`() {
        val json = VaultEntryCodec.encode(VaultEntry(id = "e3", title = "t", password = "p"))
        assertFalse(json.contains("tagIds"))
        assertFalse(json.contains("favorite"))
    }

    // ---- 导入时的标签名解析 ----

    private val existing = listOf(
        VaultTag(id = "t1", name = "工作"),
        VaultTag(id = "t2", name = "Finance"),
    )

    @Test
    fun `planImport 命中已有标签忽略大小写与空白`() {
        val plan = VaultTagCodec.planImport(listOf(" 工作 ", "finance"), existing, limit = 10)
        assertTrue(plan.toCreate.isEmpty())
        assertEquals("t1", plan.idOf("工作"))
        assertEquals("t2", plan.idOf("FINANCE"))
    }

    @Test
    fun `planImport 缺失的名字按顺序新建且去重`() {
        val plan = VaultTagCodec.planImport(
            listOf("财务", "工作", "财务", " 财务 ", "个人"),
            existing,
            limit = 10,
        )
        assertEquals(listOf("财务", "个人"), plan.toCreate)
        assertEquals("t1", plan.idOf("工作"))
        assertNull(plan.idOf("财务")) // 尚未创建，ID 由调用方插入后回填
    }

    @Test
    fun `planImport 丢弃非法名字并受上限约束`() {
        assertTrue(VaultTagCodec.planImport(listOf("  ", "a".repeat(99)), existing, 10).toCreate.isEmpty())
        val capped = VaultTagCodec.planImport(listOf("a", "b", "c"), existing, limit = 3)
        assertEquals(listOf("a"), capped.toCreate)
    }
}

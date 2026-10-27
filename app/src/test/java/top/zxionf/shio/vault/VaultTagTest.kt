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
}

package top.zxionf.shio.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 库索引的编解码与容错（纯函数部分，不依赖 Context）。 */
class VaultLibraryTest {

    private val entries = listOf(
        VaultLibraryEntry(id = "default", name = "默认密码库", fileName = "vault.db", createdAt = 1000L),
        VaultLibraryEntry(id = "b2", name = "工作", fileName = "b2.db", createdAt = 2000L),
    )

    @Test
    fun `编解码往返一致且按创建时间排序`() {
        val decoded = VaultLibrary.decodeEntries(VaultLibrary.encodeEntries(entries))
        assertEquals(entries, decoded)
    }

    @Test
    fun `空值与坏 JSON 返回空列表`() {
        assertTrue(VaultLibrary.decodeEntries(null).isEmpty())
        assertTrue(VaultLibrary.decodeEntries("").isEmpty())
        assertTrue(VaultLibrary.decodeEntries("not json").isEmpty())
        assertTrue(VaultLibrary.decodeEntries("""{"id":"x"}""").isEmpty())
    }

    @Test
    fun `缺字段或空 ID 的项被跳过`() {
        val raw = """
            [
              {"id":"ok","name":"好","fileName":"ok.db","createdAt":1},
              {"id":"","name":"缺ID","fileName":"x.db","createdAt":2},
              {"id":"no-file","name":"缺文件"},
              {"name":"缺ID字段","fileName":"y.db"}
            ]
        """.trimIndent()
        val decoded = VaultLibrary.decodeEntries(raw)
        assertEquals(1, decoded.size)
        assertEquals("ok", decoded.first().id)
    }

    @Test
    fun `重复 ID 只保留最后一条`() {
        val raw = """
            [
              {"id":"dup","name":"旧名","fileName":"dup.db","createdAt":1},
              {"id":"dup","name":"新名","fileName":"dup.db","createdAt":2}
            ]
        """.trimIndent()
        val decoded = VaultLibrary.decodeEntries(raw)
        assertEquals(1, decoded.size)
        assertEquals("新名", decoded.first().name)
    }

    @Test
    fun `名称为空时回退默认库名`() {
        val raw = """[{"id":"x","name":"  ","fileName":"x.db","createdAt":1}]"""
        val decoded = VaultLibrary.decodeEntries(raw)
        assertEquals(VaultLibrary.DEFAULT_NAME, decoded.first().name)
    }

    @Test
    fun `legacy 条目按 ID 识别`() {
        assertTrue(entries.first().isLegacy)
        assertTrue(!entries.last().isLegacy)
    }
}

package top.zxionf.shio.vault

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 外链白名单规则：只有 http/https 且带主机名时才允许打开。 */
class VaultUrlTest {

    @Test
    fun `http 与 https 允许打开`() {
        assertTrue(VaultUrl.isHttpUrl("https://github.com"))
        assertTrue(VaultUrl.isHttpUrl("http://example.com:8080/path?q=1"))
        assertTrue(VaultUrl.isHttpUrl("HTTPS://Example.COM"))
        assertTrue(VaultUrl.isHttpUrl("  https://example.com  "))
    }

    @Test
    fun `其它 scheme 一律拒绝`() {
        assertFalse(VaultUrl.isHttpUrl("javascript:alert(1)"))
        assertFalse(VaultUrl.isHttpUrl("file:///data/local/tmp/x"))
        assertFalse(VaultUrl.isHttpUrl("intent://scan/#Intent;scheme=zxing;end"))
        assertFalse(VaultUrl.isHttpUrl("content://top.zxionf.shio/secret"))
        assertFalse(VaultUrl.isHttpUrl("ftp://example.com"))
        assertFalse(VaultUrl.isHttpUrl("tel:+10086"))
    }

    @Test
    fun `缺少主机名或空值拒绝`() {
        assertFalse(VaultUrl.isHttpUrl(""))
        assertFalse(VaultUrl.isHttpUrl("   "))
        assertFalse(VaultUrl.isHttpUrl("https://"))
        assertFalse(VaultUrl.isHttpUrl("github.com"))
        assertFalse(VaultUrl.isHttpUrl("not a url"))
    }
}

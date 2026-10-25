package top.zxionf.shio.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 密码生成器单测：长度、字符集完整、排除易混淆、随机性。
 *
 * 对应 M7 的验收条件「`SecureRandom` 生成，保证每个勾选字符集至少一个」。
 */
class PasswordGeneratorTest {

    @Test
    fun `默认选项生成长度 20 且各字符集都出现`() {
        repeat(200) {
            val password = PasswordGenerator.generate(PasswordGenerator.Options())
            assertEquals(PasswordGenerator.DEFAULT_LENGTH, password.length)
            assertTrue(password.any { it in PasswordGenerator.UPPERCASE })
            assertTrue(password.any { it in PasswordGenerator.LOWERCASE })
            assertTrue(password.any { it in PasswordGenerator.DIGITS })
            assertTrue(password.any { it in PasswordGenerator.SYMBOLS })
        }
    }

    @Test
    fun `长度限制在 12 到 64`() {
        assertEquals(12, PasswordGenerator.generate(PasswordGenerator.Options(length = 4)).length)
        assertEquals(64, PasswordGenerator.generate(PasswordGenerator.Options(length = 99)).length)
    }

    @Test
    fun `只勾选部分字符集时只出现这些字符`() {
        val digitsOnly = PasswordGenerator.generate(
            PasswordGenerator.Options(uppercase = false, lowercase = false, symbols = false),
        )
        assertTrue(digitsOnly.all { it in PasswordGenerator.DIGITS })

        val lettersOnly = PasswordGenerator.generate(
            PasswordGenerator.Options(digits = false, symbols = false),
        )
        assertTrue(lettersOnly.all { it in PasswordGenerator.UPPERCASE + PasswordGenerator.LOWERCASE })

        val symbolsOnly = PasswordGenerator.generate(
            PasswordGenerator.Options(uppercase = false, lowercase = false, digits = false),
        )
        assertTrue(symbolsOnly.all { it in PasswordGenerator.SYMBOLS })
    }

    @Test
    fun `排除易混淆时不出现易混淆字符`() {
        val ambiguous = "Il1O0o|S5sB8b2Zz"
        repeat(200) {
            val password = PasswordGenerator.generate(
                PasswordGenerator.Options(excludeAmbiguous = true),
            )
            assertTrue("包含易混淆字符：$password", password.none { it in ambiguous })
        }
    }

    @Test
    fun `排除易混淆后各字符集仍然都出现`() {
        repeat(200) {
            val password = PasswordGenerator.generate(
                PasswordGenerator.Options(length = 12, excludeAmbiguous = true),
            )
            val pools = PasswordGenerator.Options(excludeAmbiguous = true).pools
            assertTrue(pools.all { pool -> password.any { it in pool } })
        }
    }

    @Test
    fun `一个字符集都不勾选时返回空串`() {
        val password = PasswordGenerator.generate(
            PasswordGenerator.Options(
                uppercase = false,
                lowercase = false,
                digits = false,
                symbols = false,
            ),
        )
        assertEquals("", password)
    }

    @Test
    fun `连续两次生成结果不同`() {
        assertNotEquals(
            PasswordGenerator.generate(PasswordGenerator.Options()),
            PasswordGenerator.generate(PasswordGenerator.Options()),
        )
    }
}

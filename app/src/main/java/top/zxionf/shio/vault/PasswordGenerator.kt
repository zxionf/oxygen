package top.zxionf.shio.vault

import java.security.SecureRandom

/**
 * 密码生成器（纯 Kotlin，无 Android 依赖）。
 *
 * 规则见 `.design/changes/20261023-password-vault/design.md` 的「密码生成器」：
 * 长度 12–64，大小写/数字/符号可分别开关，可排除易混淆字符；用 [SecureRandom]，
 * 每个勾选的字符集至少出现一次，再整体 Fisher–Yates 打乱，避免前几位固定。
 */
object PasswordGenerator {

    const val MIN_LENGTH = 12
    const val MAX_LENGTH = 64
    const val DEFAULT_LENGTH = 20

    const val UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    const val LOWERCASE = "abcdefghijklmnopqrstuvwxyz"
    const val DIGITS = "0123456789"
    const val SYMBOLS = "!#$%&*+-=?@^_"

    /** 易混淆字符：I l 1 0 O o | 5 S 8 B 2 Z。 */
    private const val AMBIGUOUS = "Il1O0o|S5sB8b2Zz"

    /** 生成选项；[length] 会被限制在 [MIN_LENGTH]..[MAX_LENGTH]。 */
    data class Options(
        val length: Int = DEFAULT_LENGTH,
        val uppercase: Boolean = true,
        val lowercase: Boolean = true,
        val digits: Boolean = true,
        val symbols: Boolean = true,
        val excludeAmbiguous: Boolean = false,
    ) {
        /** 已按 [excludeAmbiguous] 过滤后的字符集，顺序固定：大写、小写、数字、符号。 */
        val pools: List<String>
            get() = listOfNotNull(
                UPPERCASE.takeIf { uppercase },
                LOWERCASE.takeIf { lowercase },
                DIGITS.takeIf { digits },
                SYMBOLS.takeIf { symbols },
            ).map { pool -> if (excludeAmbiguous) pool.filterNot { it in AMBIGUOUS } else pool }
                .filter { it.isNotEmpty() }

        val hasSelection: Boolean
            get() = pools.isNotEmpty()
    }

    /**
     * 按 [options] 生成密码；一个字符集都没勾选时返回空串（调用方应禁用按钮）。
     */
    fun generate(options: Options, random: SecureRandom = SecureRandom()): String {
        val pools = options.pools
        if (pools.isEmpty()) return ""
        val length = options.length.coerceIn(MIN_LENGTH, MAX_LENGTH)
        val chars = CharArray(length)
        var index = 0
        // 每个勾选的字符集先各出一个，保证每种类型都出现。
        for (pool in pools) {
            if (index >= length) break
            chars[index++] = pool.randomChar(random)
        }
        val all = pools.joinToString(separator = "")
        while (index < length) {
            chars[index++] = all.randomChar(random)
        }
        // Fisher–Yates：让前几位不是固定的「各字符集首字符」顺序。
        for (i in chars.indices.reversed()) {
            val j = random.nextInt(i + 1)
            val tmp = chars[i]
            chars[i] = chars[j]
            chars[j] = tmp
        }
        return String(chars)
    }

    private fun String.randomChar(random: SecureRandom): Char = this[random.nextInt(length)]
}

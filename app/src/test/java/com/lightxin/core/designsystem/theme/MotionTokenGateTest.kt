package com.lightxin.core.designsystem.theme

import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * 动画时长门禁：`tween` / `spring` / `durationMillis` 的数字字面量只允许出现在
 * [LxMotion] 与插画（`*Illustration.kt`，显式例外）里，其余一律走 token。
 *
 * 这条门禁替代“请记得不要写死时长”的散文约束：漏一个就红。
 */
class MotionTokenGateTest {
    private val numericSpecs = listOf(
        Regex("""\btween\s*(<[^>]+>)?\s*\(\s*\d"""),
        Regex("""\bdurationMillis\s*=\s*\d"""),
        Regex("""\bspring\s*(<[^>]+>)?\s*\([^)]*stiffness\s*=\s*\d"""),
    )

    @Test
    fun animationTimingsComeFromLxMotion() {
        val root = sourceRoot()
        val offenders = mutableListOf<String>()
        var scanned = 0

        root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { it.name == "LxMotion.kt" }
            .filterNot { it.name.endsWith("Illustration.kt") }
            .forEach { file ->
                scanned++
                file.readLines().forEachIndexed { index, line ->
                    val trimmed = line.trim()
                    if (trimmed.startsWith("//") || trimmed.startsWith("*")) return@forEachIndexed
                    if (numericSpecs.any { it.containsMatchIn(line) }) {
                        offenders += "${file.relativeTo(root).invariantSeparatorsPath}:${index + 1}: $trimmed"
                    }
                }
            }

        assertTrue("扫描范围异常（只扫到 $scanned 个文件），门禁失效：${root.absolutePath}", scanned > 50)
        if (offenders.isNotEmpty()) {
            fail(
                "动画时长必须来自 LxMotion（白名单：LxMotion.kt、*Illustration.kt）：\n" +
                    offenders.joinToString("\n"),
            )
        }
    }

    private fun sourceRoot(): File =
        listOf(File("src/main/java"), File("app/src/main/java"))
            .firstOrNull { it.isDirectory }
            ?: error("找不到 app/src/main/java；当前工作目录：${File(".").absolutePath}")
}

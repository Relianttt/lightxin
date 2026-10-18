package com.lightxin.feature.running.domain

import com.lightxin.TestFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateImportResolverTest {

    private fun imported(
        fileId: String,
        name: String,
        points: List<TrackPoint> = TestFixtures.validPoints(),
    ) = ImportedTemplate(
        fileId = fileId,
        name = name,
        points = points,
        totalDistanceMeters = RouteTemplateCodec.totalDistanceMeters(points),
        durationSeconds = 100L,
        qualityStatus = RouteQualityStatus.PASS,
        qualityMessage = null,
        createdAtMillis = 100L,
    )

    private fun preview(vararg items: ImportedTemplate) = ImportPreview(
        valid = items.toList(),
        rejected = emptyList(),
        conflicts = emptySet(),
    )

    @Test
    fun `无冲突直接追加`() {
        val existing = listOf(TestFixtures.routeTemplate(id = "local", name = "本地"))
        val result = TemplateImportResolver.resolve(
            preview = preview(imported("new", "新路线")),
            decisions = emptyMap(),
            existing = existing,
            nowMillis = 999L,
        )
        assertEquals(2, result.templates.size)
        assertEquals(1, result.report.added)
        assertEquals("new", result.templates.last().id)
        assertEquals(RouteTemplateSource.IMPORTED, result.templates.last().source)
    }

    @Test
    fun `重名保留副本时追加导入后缀并递增`() {
        val existing = listOf(TestFixtures.routeTemplate(id = "local", name = "同名"))
        val preview = TemplateImportResolver.preview(
            RouteTemplateCodec.ParseResult(
                candidates = listOf(imported("f1", "同名"), imported("f2", "同名")),
                rejected = emptyList(),
            ),
            existing,
        )
        val decisions = preview.valid.associate { it.fileId to ConflictChoice.KEEP_COPY }
        val result = TemplateImportResolver.resolve(preview, decisions, existing)

        assertEquals(listOf("同名", "同名 (导入)", "同名 (导入 2)"), result.templates.map { it.name })
        assertEquals(2, result.report.added)
    }

    @Test
    fun `同 id 覆盖保留本地默认与创建时间`() {
        val existing = listOf(
            TestFixtures.routeTemplate(
                id = "same",
                name = "旧名字",
                isDefault = true,
                createdAtMillis = 111L,
            ),
        )
        val preview = TemplateImportResolver.preview(
            RouteTemplateCodec.ParseResult(listOf(imported("same", "新名字")), emptyList()),
            existing,
        )
        assertTrue(preview.conflicts.contains("same"))
        val result = TemplateImportResolver.resolve(
            preview,
            mapOf("same" to ConflictChoice.OVERWRITE),
            existing,
            nowMillis = 222L,
        )

        val target = result.templates.single()
        assertEquals(1, result.report.overwritten)
        assertEquals("same", target.id)
        assertEquals("新名字", target.name)
        assertTrue(target.isDefault)
        assertEquals(111L, target.createdAtMillis)
        assertEquals(222L, target.updatedAtMillis)
    }

    @Test
    fun `混合选择各自生效`() {
        val existing = listOf(
            TestFixtures.routeTemplate(id = "a", name = "A"),
            TestFixtures.routeTemplate(id = "b", name = "B"),
        )
        val preview = TemplateImportResolver.preview(
            RouteTemplateCodec.ParseResult(
                listOf(
                    imported("a", "A"),
                    imported("b", "B"),
                    imported("c", "C"),
                ),
                emptyList(),
            ),
            existing,
        )
        val result = TemplateImportResolver.resolve(
            preview,
            mapOf(
                "a" to ConflictChoice.OVERWRITE,
                "b" to ConflictChoice.SKIP,
            ),
            existing,
        )

        assertEquals(1, result.report.overwritten)
        assertEquals(1, result.report.skipped)
        assertEquals(1, result.report.added)
        assertFalse(result.templates.any { it.id == "b" && it.name != "B" })
        assertTrue(result.templates.any { it.name == "C" })
    }

    @Test
    fun `全部跳过时零写入`() {
        val existing = listOf(TestFixtures.routeTemplate(id = "a", name = "A"))
        val preview = TemplateImportResolver.preview(
            RouteTemplateCodec.ParseResult(listOf(imported("a", "A")), emptyList()),
            existing,
        )
        val result = TemplateImportResolver.resolve(
            preview,
            mapOf("a" to ConflictChoice.SKIP),
            existing,
        )
        assertSame(existing, result.templates)
        assertEquals(0, result.report.added + result.report.overwritten)
        assertEquals(1, result.report.skipped)
    }
}

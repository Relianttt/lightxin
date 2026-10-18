package com.lightxin.feature.running.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.lightxin.feature.running.domain.RouteTemplate
import com.lightxin.feature.running.domain.RouteTemplateCodec
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 导出 = 写缓存文件 + 系统分享（ACTION_SEND）；不碰剪贴板。 */
object RouteTemplateExport {

    fun shareSingle(context: Context, template: RouteTemplate) {
        val safeName = sanitize(template.name).ifBlank { "route-template" }
        share(context, listOf(template), "route-template-$safeName.json")
    }

    fun shareAll(context: Context, templates: List<RouteTemplate>) {
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm", Locale.CHINA))
        share(context, templates, "route-templates-$stamp.json")
    }

    private fun share(context: Context, templates: List<RouteTemplate>, fileName: String) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, fileName)
        file.writeText(RouteTemplateCodec.encode(templates), Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, fileName)
            clipData = ClipData.newRawUri(fileName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, "导出路线模板").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(chooser)
    }

    private fun sanitize(raw: String): String =
        raw.replace(Regex("""[\\/:*?"<>|\r\n\t]"""), "_").trim()
}

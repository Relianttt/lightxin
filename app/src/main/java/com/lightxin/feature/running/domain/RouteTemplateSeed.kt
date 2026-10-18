package com.lightxin.feature.running.domain

import java.util.UUID

/** 内置模板 → “系统模板”的纯映射，便于单测；注入策略（标记文件、仅空列表）在 Store 里。 */
object RouteTemplateSeed {

    const val SYSTEM_NAME = "系统模板"

    /** 仅在“没有标记文件”且“模板列表为空”时注入。 */
    fun shouldInject(markerExists: Boolean, current: List<RouteTemplate>): Boolean =
        !markerExists && current.isEmpty()

    fun map(parsed: RouteTemplateCodec.ParseResult, nowMillis: Long): List<RouteTemplate> =
        parsed.candidates.mapIndexed { index, imported ->
            RouteTemplate(
                id = UUID.randomUUID().toString(),
                name = SYSTEM_NAME,
                createdAtMillis = nowMillis,
                updatedAtMillis = nowMillis,
                totalDistanceMeters = imported.totalDistanceMeters,
                durationSeconds = imported.durationSeconds,
                source = RouteTemplateSource.SYSTEM,
                isDefault = index == 0,
                points = imported.points,
                qualityStatus = imported.qualityStatus,
                qualityMessage = imported.qualityMessage,
                lastUsedAtMillis = null,
            )
        }
}

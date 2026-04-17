package com.lightxin.feature.running.data

import android.content.Context
import com.google.gson.Gson
import com.lightxin.feature.running.domain.RouteTemplate
import com.lightxin.feature.running.domain.RouteTemplateSource
import com.lightxin.feature.running.domain.TrackPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 本地路线模板存储：单 JSON 文件 + Mutex 保护。
 * 不引入 Room，保持依赖最小。
 */
@Singleton
class RouteTemplateStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val file: File by lazy { File(context.filesDir, FILE_NAME) }
    private val gson = Gson()
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _templates = MutableStateFlow<List<RouteTemplate>>(emptyList())
    val templates: StateFlow<List<RouteTemplate>> = _templates.asStateFlow()

    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    init {
        scope.launch { load() }
    }

    private suspend fun load() = mutex.withLock {
        val list = withContext(Dispatchers.IO) { readFile() }
        _templates.value = list
        _isLoaded.value = true
    }

    suspend fun save(
        name: String,
        points: List<TrackPoint>,
        totalDistanceMeters: Double,
        durationSeconds: Long,
        qualityStatus: com.lightxin.feature.running.domain.RouteQualityStatus =
            com.lightxin.feature.running.domain.RouteQualityStatus.PASS,
        qualityMessage: String? = null,
    ): RouteTemplate = mutex.withLock {
        val now = System.currentTimeMillis()
        val current = _templates.value
        val template = RouteTemplate(
            id = UUID.randomUUID().toString(),
            name = name,
            createdAtMillis = now,
            updatedAtMillis = now,
            totalDistanceMeters = totalDistanceMeters,
            durationSeconds = durationSeconds,
            source = RouteTemplateSource.TEMPLATE_RECORDING,
            isDefault = current.isEmpty(),
            points = points,
            qualityStatus = qualityStatus,
            qualityMessage = qualityMessage,
            lastUsedAtMillis = null,
        )
        val next = current + template
        persist(next)
        template
    }

    suspend fun markUsed(id: String) = mutex.withLock {
        val now = System.currentTimeMillis()
        val next = _templates.value.map {
            if (it.id == id) it.copy(lastUsedAtMillis = now) else it
        }
        persist(next)
    }

    suspend fun rename(id: String, newName: String) = mutex.withLock {
        val next = _templates.value.map {
            if (it.id == id) it.copy(name = newName, updatedAtMillis = System.currentTimeMillis()) else it
        }
        persist(next)
    }

    suspend fun delete(id: String) = mutex.withLock {
        val filtered = _templates.value.filter { it.id != id }
        val wasDefault = _templates.value.firstOrNull { it.id == id }?.isDefault == true
        val next = if (wasDefault && filtered.isNotEmpty()) {
            filtered.mapIndexed { idx, t -> t.copy(isDefault = idx == 0) }
        } else {
            filtered
        }
        persist(next)
    }

    suspend fun setDefault(id: String) = mutex.withLock {
        val next = _templates.value.map { it.copy(isDefault = it.id == id) }
        persist(next)
    }

    private suspend fun persist(list: List<RouteTemplate>) {
        _templates.value = list
        withContext(Dispatchers.IO) { writeFile(list) }
    }

    private fun readFile(): List<RouteTemplate> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val json = file.readText()
            if (json.isBlank()) emptyList()
            else gson.fromJson(json, Array<RouteTemplate>::class.java).toList()
        }.getOrDefault(emptyList())
    }

    private fun writeFile(list: List<RouteTemplate>) {
        val tmp = File(file.parentFile, "$FILE_NAME.tmp")
        tmp.writeText(gson.toJson(list))
        if (file.exists()) file.delete()
        tmp.renameTo(file)
    }

    companion object {
        private const val FILE_NAME = "route_templates.json"
    }
}

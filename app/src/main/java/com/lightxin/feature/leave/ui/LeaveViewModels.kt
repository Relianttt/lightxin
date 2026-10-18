package com.lightxin.feature.leave.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.lightxin.core.auth.TokenManager
import com.lightxin.feature.leave.data.LeaveDetail
import com.lightxin.feature.leave.data.LeaveRepository
import com.lightxin.feature.leave.data.LeaveSummary
import com.lightxin.feature.leave.domain.LeaveAttachment
import com.lightxin.feature.leave.domain.LeaveFormBuilder
import com.lightxin.feature.leave.domain.LeaveFormField
import com.lightxin.feature.leave.domain.LeaveFormInput
import com.lightxin.feature.leave.domain.LeaveFormType
import com.lightxin.feature.leave.domain.LeaveFormValidator
import com.lightxin.feature.leave.domain.LeaveIdentity
import com.lightxin.feature.leave.domain.LeaveValidationError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

// ═══════════════ 列表 ═══════════════

data class LeaveListUiState(
    val items: List<LeaveSummary> = emptyList(),
    val status: String = "1",
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class LeaveListViewModel @Inject constructor(
    private val repository: LeaveRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LeaveListUiState())
    val uiState: StateFlow<LeaveListUiState> = _uiState.asStateFlow()
    private var page = 1

    init {
        load(reset = true)
    }

    fun setStatus(status: String) {
        if (_uiState.value.status == status) return
        _uiState.update { it.copy(status = status) }
        load(reset = true)
    }

    fun refresh() = load(reset = true)

    fun retry() = load(reset = true)

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isRefreshing || state.isLoadingMore || !state.hasMore) return
        load(reset = false)
    }

    private fun load(reset: Boolean) {
        viewModelScope.launch {
            if (reset) {
                page = 1
                _uiState.update {
                    it.copy(
                        isLoading = it.items.isEmpty() && !it.isRefreshing,
                        isRefreshing = it.items.isNotEmpty(),
                        error = null,
                    )
                }
            } else {
                _uiState.update { it.copy(isLoadingMore = true) }
            }
            val result = repository.myExecutions(page = page, status = _uiState.value.status)
            result.fold(
                onSuccess = { list ->
                    val merged = if (reset) list else _uiState.value.items + list
                    _uiState.update {
                        it.copy(
                            items = merged.distinctBy { item -> item.executionId },
                            isLoading = false,
                            isRefreshing = false,
                            isLoadingMore = false,
                            hasMore = list.size >= PAGE_SIZE,
                            error = null,
                        )
                    }
                    if (list.size >= PAGE_SIZE) page++
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            isLoadingMore = false,
                            error = error.message ?: "获取请假列表失败",
                        )
                    }
                },
            )
        }
    }

    private companion object {
        const val PAGE_SIZE = 20
    }
}

// ═══════════════ 发起 ═══════════════

data class LeaveCreateUiState(
    val isLoading: Boolean = true,
    val loadError: String? = null,
    val definitionId: String = "",
    val fields: List<LeaveFormField> = emptyList(),
    val defaults: JsonObject = JsonObject(),
    val values: Map<String, String> = emptyMap(),
    val dateRanges: Map<String, Pair<String, String>> = emptyMap(),
    val attachments: Map<String, List<LeaveAttachment>> = emptyMap(),
    val uploadingKeys: Set<String> = emptySet(),
    val errors: List<LeaveValidationError> = emptyList(),
    val isSubmitting: Boolean = false,
    val submitted: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class LeaveCreateViewModel @Inject constructor(
    private val repository: LeaveRepository,
    private val tokenManager: TokenManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LeaveCreateUiState())
    val uiState: StateFlow<LeaveCreateUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, loadError = null) }
            val definitionResult = repository.getDefinitionId()
            val definitionId = definitionResult.getOrElse {
                _uiState.update { state ->
                    state.copy(isLoading = false, loadError = it.message ?: "无法获取请假流程")
                }
                return@launch
            }
            val schemaDeferred = async { repository.getSchema(definitionId) }
            val defaultsDeferred = async { repository.getDefaults(definitionId) }
            val schema = schemaDeferred.await().getOrElse {
                _uiState.update { state ->
                    state.copy(isLoading = false, loadError = it.message ?: "无法获取请假表单")
                }
                return@launch
            }
            val defaults = defaultsDeferred.await().getOrDefault(JsonObject())
            val seed = seedFromDefaults(defaults, schema.fields)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    definitionId = definitionId,
                    fields = schema.fields,
                    defaults = defaults,
                    values = seed.values,
                    dateRanges = seed.dateRanges,
                    attachments = seed.attachments,
                )
            }
        }
    }

    fun setValue(key: String, value: String) {
        _uiState.update {
            it.copy(
                values = it.values + (key to value),
                errors = it.errors.filterNot { error -> error.key == key },
            )
        }
    }

    fun setDateRange(key: String, start: String, end: String) {
        _uiState.update {
            it.copy(
                dateRanges = it.dateRanges + (key to (start to end)),
                errors = it.errors.filterNot { error -> error.key == key },
            )
        }
    }

    fun addAttachment(key: String, file: File) {
        if (_uiState.value.uploadingKeys.contains(key)) return
        viewModelScope.launch {
            _uiState.update { it.copy(uploadingKeys = it.uploadingKeys + key) }
            repository.uploadAttachment(file).fold(
                onSuccess = { attachment ->
                    _uiState.update { state ->
                        val current = state.attachments[key].orEmpty()
                        state.copy(
                            attachments = state.attachments + (key to (current + attachment)),
                            uploadingKeys = state.uploadingKeys - key,
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            uploadingKeys = it.uploadingKeys - key,
                            message = error.message ?: "附件上传失败",
                        )
                    }
                },
            )
        }
    }

    fun removeAttachment(key: String, uid: String) {
        _uiState.update { state ->
            val next = state.attachments[key].orEmpty().filterNot { it.uid == uid }
            state.copy(attachments = state.attachments + (key to next))
        }
    }

    fun submit(draft: Boolean = false) {
        val state = _uiState.value
        if (state.isSubmitting) return
        if (!draft) {
            val errors = LeaveFormValidator.validate(state.fields, state.formInput())
            if (errors.isNotEmpty()) {
                _uiState.update { it.copy(errors = errors, message = errors.first().message) }
                return
            }
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errors = emptyList()) }
            val identity = buildIdentity(state.defaults)
            val dataJson = LeaveFormBuilder.build(
                defaults = state.defaults,
                fields = state.fields,
                input = state.formInput(),
                identity = identity,
            )
            repository.submit(
                definitionId = state.definitionId,
                dataJson = dataJson,
                executionId = null,
                draft = draft,
            ).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            submitted = !draft,
                            message = if (draft) "已暂存" else null,
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(isSubmitting = false, message = error.message ?: "提交失败")
                    }
                },
            )
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private suspend fun buildIdentity(defaults: JsonObject): LeaveIdentity {
        val credentials = tokenManager.snapshot()
        return LeaveIdentity(
            userCode = credentials.userCode.orEmpty(),
            userName = credentials.userName.orEmpty(),
            userPhone = defaults.getString("userPhone"),
            yxdm = defaults.getString("yxdm"),
            yxmc = defaults.getString("yxmc"),
            bjdm = defaults.getString("bjdm"),
        )
    }

    private fun LeaveCreateUiState.formInput() = LeaveFormInput(
        values = values,
        dateRanges = dateRanges,
        attachments = attachments,
    )

    private fun seedFromDefaults(defaults: JsonObject, fields: List<LeaveFormField>): Seed {
        val values = mutableMapOf<String, String>()
        val dateRanges = mutableMapOf<String, Pair<String, String>>()
        val attachments = mutableMapOf<String, List<LeaveAttachment>>()
        defaults.entrySet().forEach { (key, value) ->
            when {
                value.isJsonArray && key == "QJRQ" && value.asJsonArray.size() >= 2 -> {
                    dateRanges[key] = value.asJsonArray[0].asString to value.asJsonArray[1].asString
                }
                value.isJsonPrimitive -> values[key] = value.asString
                value.isJsonArray -> {
                    val list = value.asJsonArray.mapNotNull { item ->
                        if (!item.isJsonObject) return@mapNotNull null
                        val obj = item.asJsonObject
                        val url = obj.getString("fileUrl").ifBlank { obj.getString("url") }
                        if (url.isBlank()) return@mapNotNull null
                        LeaveAttachment(
                            uid = obj.getString("uid").ifBlank { url },
                            fileName = obj.getString("fileName").ifBlank { obj.getString("name") },
                            fileUrl = url,
                        )
                    }
                    if (list.isNotEmpty()) attachments[key] = list
                }
            }
        }
        fields.filter { it.type == LeaveFormType.TEXT_INPUT || it.type == LeaveFormType.TEXTAREA }
            .forEach { field ->
                val defaultValue = defaults.get(field.key)
                if (defaultValue != null && defaultValue.isJsonPrimitive && field.key !in values) {
                    values[field.key] = defaultValue.asString
                }
            }

        // findDefinitionBase 不返回动态字段 key，按语义从身份默认值补齐
        fun fill(field: LeaveFormField, value: String) {
            if (value.isNotBlank() && field.key !in values) values[field.key] = value
        }
        fields.forEach { field ->
            val label = field.label
            when {
                label.contains("学院") -> fill(field, defaults.getString("yxmc"))
                label.contains("班级") -> fill(field, defaults.getString("bjmc"))
                label.contains("姓名") || label.contains("申请人") -> fill(field, defaults.getString("userName"))
                label.contains("学号") -> fill(field, defaults.getString("userCode"))
                label.contains("手机") || label.contains("电话") -> fill(field, defaults.getString("userPhone"))
                label.contains("标题") -> fill(field, defaults.getString("flowTitle"))
                label.contains("编号") -> fill(field, defaults.getString("flowNumber"))
            }
        }
        return Seed(values, dateRanges, attachments)
    }

    private data class Seed(
        val values: Map<String, String>,
        val dateRanges: Map<String, Pair<String, String>>,
        val attachments: Map<String, List<LeaveAttachment>>,
    )
}

private fun JsonObject.getString(key: String): String =
    get(key)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()

// ═══════════════ 详情 ═══════════════

data class LeaveDetailUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val detail: LeaveDetail? = null,
    val canWithdraw: Boolean = false,
    val isWorking: Boolean = false,
    val xjxx: JsonObject? = null,
    val xjxxLoaded: Boolean = false,
    val withdrawn: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class LeaveDetailViewModel @Inject constructor(
    private val repository: LeaveRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LeaveDetailUiState())
    val uiState: StateFlow<LeaveDetailUiState> = _uiState.asStateFlow()
    private var executionId: String = ""

    fun load(id: String, force: Boolean = false) {
        if (!force && executionId == id && _uiState.value.detail != null) return
        executionId = id
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = it.detail == null, error = null) }
            val detailResult = repository.getForm(id)
            detailResult.fold(
                onSuccess = { loaded ->
                    val summary = repository.findSummary(id)
                    val detail = loaded.copy(
                        flowTitle = loaded.flowTitle.ifBlank { summary?.flowTitle.orEmpty() },
                        statusTxt = loaded.statusTxt.ifBlank { summary?.statusTxt.orEmpty() },
                        receiveTime = loaded.receiveTime.ifBlank { summary?.receiveTime.orEmpty() },
                        flowNumber = loaded.flowNumber.ifBlank { summary?.flowNumber.orEmpty() },
                    )
                    val withdrawable = repository.canWithdraw(id)
                    val isXj = detail.statusTxt.contains("销假")
                    val xjxx = if (isXj) repository.getXjxx(id).getOrNull() else null
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            detail = detail,
                            canWithdraw = withdrawable,
                            xjxx = xjxx,
                            xjxxLoaded = isXj,
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(isLoading = false, error = error.message ?: "获取请假详情失败")
                    }
                },
            )
        }
    }

    fun retry() {
        if (executionId.isNotBlank()) load(executionId, force = true)
    }

    fun withdraw() {
        if (_uiState.value.isWorking) return
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true) }
            repository.withdraw(executionId).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(isWorking = false, withdrawn = true, message = "已撤回")
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(isWorking = false, message = error.message ?: "撤回失败")
                    }
                },
            )
        }
    }

    fun loadXjxx() {
        if (_uiState.value.xjxxLoaded) return
        viewModelScope.launch {
            val result = repository.getXjxx(executionId)
            _uiState.update {
                it.copy(
                    xjxx = result.getOrNull(),
                    xjxxLoaded = true,
                    message = result.exceptionOrNull()?.message,
                )
            }
        }
    }

    fun submitXjxx() {
        val data = _uiState.value.xjxx ?: return
        if (_uiState.value.isWorking) return
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true) }
            val definitionId = repository.getDefinitionId().getOrDefault(
                LeaveRepository.FALLBACK_DEFINITION_ID,
            )
            repository.submit(
                definitionId = definitionId,
                dataJson = data,
                executionId = executionId,
                draft = false,
            ).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(isWorking = false, message = "销假已提交")
                    }
                    load(executionId, force = true)
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(isWorking = false, message = error.message ?: "销假提交失败")
                    }
                },
            )
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun consumeWithdrawn(): Boolean {
        val withdrawn = _uiState.value.withdrawn
        if (withdrawn) _uiState.update { it.copy(withdrawn = false) }
        return withdrawn
    }
}

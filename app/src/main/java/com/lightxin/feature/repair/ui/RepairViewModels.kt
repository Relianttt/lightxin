package com.lightxin.feature.repair.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightxin.feature.repair.data.REPAIR_ENDED_CODES
import com.lightxin.feature.repair.data.RepairDetail
import com.lightxin.feature.repair.data.RepairRepository
import com.lightxin.feature.repair.data.RepairSummary
import com.lightxin.feature.repair.domain.RepairCascade
import com.lightxin.feature.repair.domain.RepairDraft
import com.lightxin.feature.repair.domain.RepairNode
import com.lightxin.feature.repair.domain.RepairSubmitBuilder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

// ═══════════════ 列表 ═══════════════

data class RepairListUiState(
    val items: List<RepairSummary> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class RepairListViewModel @Inject constructor(
    private val repository: RepairRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RepairListUiState())
    val uiState: StateFlow<RepairListUiState> = _uiState.asStateFlow()

    init {
        load(refresh = false)
    }

    fun refresh() = load(refresh = true)

    fun retry() = load(refresh = false)

    fun openDetail(id: String, bxdh: String, onNavigate: () -> Unit) {
        viewModelScope.launch {
            repository.markRead(id)
            onNavigate()
        }
    }

    private fun load(refresh: Boolean) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = !refresh && it.items.isEmpty(),
                    isRefreshing = refresh,
                    error = null,
                )
            }
            repository.list().fold(
                onSuccess = { list ->
                    _uiState.update {
                        it.copy(
                            items = list,
                            isLoading = false,
                            isRefreshing = false,
                            error = null,
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = error.message ?: "获取报修列表失败",
                        )
                    }
                },
            )
        }
    }
}

// ═══════════════ 新建 ═══════════════

data class RepairCreateUiState(
    val isLoading: Boolean = true,
    val loadError: String? = null,
    val addressNodes: List<RepairNode> = emptyList(),
    val typeNodes: List<RepairNode> = emptyList(),
    val xqdm: String = "",
    val lyId: String = "",
    val qyfjh: String = "",
    val xxdz: String = "",
    val sjh: String = "",
    val xm: String = "",
    val gzms: String = "",
    val bxlx: String = "",
    val typeRoot: String = "",
    val images: List<File> = emptyList(),
    val isSubmitting: Boolean = false,
    val errors: Map<String, String> = emptyMap(),
    val submittedBxdh: String? = null,
    val message: String? = null,
) {
    val campusNodes: List<RepairNode> get() = RepairCascade.roots(addressNodes)
    val buildingNodes: List<RepairNode> get() = RepairCascade.childrenOf(addressNodes, xqdm)
    val roomNodes: List<RepairNode> get() = RepairCascade.childrenOf(addressNodes, lyId)
    val typeRoots: List<RepairNode> get() = RepairCascade.roots(typeNodes)
    val typeChildren: List<RepairNode> get() =
        if (typeRoot.isBlank()) emptyList() else RepairCascade.childrenOf(typeNodes, typeRoot)
    val campusName: String get() = campusNodes.firstOrNull { it.value == xqdm }?.name.orEmpty()
    val buildingName: String get() = buildingNodes.firstOrNull { it.value == lyId }?.name.orEmpty()
    val roomName: String get() = roomNodes.firstOrNull { it.value == qyfjh }?.name.orEmpty()
}

@HiltViewModel
class RepairCreateViewModel @Inject constructor(
    private val repository: RepairRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RepairCreateUiState())
    val uiState: StateFlow<RepairCreateUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, loadError = null) }
            val addresses = repository.addressNodes()
            val types = repository.typeNodes()
            val error = addresses.exceptionOrNull() ?: types.exceptionOrNull()
            if (error != null) {
                _uiState.update {
                    it.copy(isLoading = false, loadError = error.message ?: "加载报修资料失败")
                }
                return@launch
            }
            val nodes = addresses.getOrDefault(emptyList())
            val defaultCampus = repository.defaultCampus().getOrNull()
            val contact = repository.contact().getOrNull()
            // 联系人接口偶发拿不到号码：用列表里自己最近一条报修的 sjh 兜底，避免让用户重填。
            val fallbackPhone = contact?.second?.takeIf { it.isNotBlank() }
                ?: repository.list().getOrNull()?.firstOrNull()?.sjh.orEmpty()
            val campus = defaultCampus?.first?.takeIf { it.isNotBlank() }
                ?.let { xqdm -> nodes.firstOrNull { it.value == xqdm } }
                ?: RepairCascade.roots(nodes).firstOrNull()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    addressNodes = nodes,
                    typeNodes = types.getOrDefault(emptyList()),
                    xqdm = campus?.value.orEmpty(),
                    lyId = "",
                    qyfjh = "",
                    sjh = fallbackPhone,
                    xm = contact?.first.orEmpty(),
                )
            }
        }
    }

    fun selectCampus(value: String) {
        _uiState.update {
            it.copy(
                xqdm = value,
                lyId = "",
                qyfjh = "",
                errors = it.errors - "location",
            )
        }
    }

    fun selectBuilding(value: String) {
        _uiState.update {
            it.copy(
                lyId = value,
                qyfjh = "",
                errors = it.errors - "location",
            )
        }
    }

    fun selectRoom(value: String) {
        _uiState.update { it.copy(qyfjh = value, errors = it.errors - "location") }
    }

    fun selectTypeRoot(value: String) {
        val children = RepairCascade.childrenOf(_uiState.value.typeNodes, value)
        _uiState.update {
            it.copy(
                typeRoot = value,
                bxlx = if (children.isEmpty()) value else "",
                errors = it.errors - "bxlx",
            )
        }
    }

    fun selectType(value: String) {
        _uiState.update { it.copy(bxlx = value, errors = it.errors - "bxlx") }
    }

    fun setXxdz(value: String) {
        _uiState.update { it.copy(xxdz = value, errors = it.errors - "xxdz") }
    }

    fun setPhone(value: String) {
        _uiState.update { it.copy(sjh = value, errors = it.errors - "sjh") }
    }

    fun setGzms(value: String) {
        _uiState.update { it.copy(gzms = value, errors = it.errors - "gzms") }
    }

    fun addImage(file: File) {
        _uiState.update { state ->
            if (state.images.size >= MAX_IMAGES) state
            else state.copy(images = state.images + file)
        }
    }

    fun removeImage(file: File) {
        _uiState.update { state -> state.copy(images = state.images.filterNot { it == file }) }
    }

    fun submit() {
        val state = _uiState.value
        if (state.isSubmitting) return
        val draft = state.toDraft()
        val errors = RepairSubmitBuilder.validate(draft)
        if (errors.isNotEmpty()) {
            _uiState.update { it.copy(errors = errors, message = errors.values.first()) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errors = emptyMap()) }
            val ids = mutableListOf<String>()
            state.images.forEach { file ->
                val result = repository.upload(file)
                result.fold(
                    onSuccess = { ids += it },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(isSubmitting = false, message = error.message ?: "图片上传失败")
                        }
                        return@launch
                    },
                )
            }
            repository.submit(draft.copy(tpIds = ids)).fold(
                onSuccess = { (bxdh) ->
                    _uiState.update {
                        it.copy(isSubmitting = false, submittedBxdh = bxdh.ifBlank { "ok" })
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

    fun consumeSubmitted(): Boolean {
        val bxdh = _uiState.value.submittedBxdh
        if (bxdh != null) _uiState.update { it.copy(submittedBxdh = null) }
        return bxdh != null
    }

    private fun RepairCreateUiState.toDraft() = RepairDraft(
        xqdm = xqdm,
        lyId = lyId,
        qyfjh = qyfjh,
        xxdz = xxdz,
        sjh = sjh,
        xm = xm,
        gzms = gzms,
        bxlx = bxlx,
    )

    private companion object {
        const val MAX_IMAGES = 3
    }
}

// ═══════════════ 详情 ═══════════════

data class RepairDetailUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val detail: RepairDetail? = null,
    val isWorking: Boolean = false,
    val withdrawn: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class RepairDetailViewModel @Inject constructor(
    private val repository: RepairRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RepairDetailUiState())
    val uiState: StateFlow<RepairDetailUiState> = _uiState.asStateFlow()
    private var currentBxdh: String = ""

    fun load(bxdh: String, force: Boolean = false) {
        if (!force && currentBxdh == bxdh && _uiState.value.detail != null) return
        currentBxdh = bxdh
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = it.detail == null, error = null) }
            repository.detail(bxdh).fold(
                onSuccess = { loaded ->
                    val detail = mergeWithList(loaded, bxdh)
                    repository.markRead(detail.id)
                    _uiState.update { it.copy(isLoading = false, detail = detail) }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(isLoading = false, error = error.message ?: "获取报修详情失败")
                    }
                },
            )
        }
    }

    fun retry() {
        if (currentBxdh.isNotBlank()) load(currentBxdh, force = true)
    }

    fun withdraw() {
        if (_uiState.value.isWorking) return
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true) }
            repository.withdraw(currentBxdh).fold(
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

    fun consumeWithdrawn(): Boolean {
        val withdrawn = _uiState.value.withdrawn
        if (withdrawn) _uiState.update { it.copy(withdrawn = false) }
        return withdrawn
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    /**
     * 详情接口的状态/电话可能过期或缺失：HAR 里已完工的单详情仍返回 dqzt=9、wxySjh 为空，
     * 而列表同一时刻是 dqzt=4、wxySjh 有值。用列表摘要补齐，列表拿不到时才用详情原值。
     */
    private suspend fun mergeWithList(detail: RepairDetail, bxdh: String): RepairDetail {
        val summary = repository.cachedSummary(bxdh)
            ?: repository.list().getOrNull()?.firstOrNull { it.bxdh == bxdh }
            ?: return detail
        return detail.copy(
            dqzt = summary.dqzt.ifBlank { detail.dqzt },
            dqztMc = if (summary.dqzt.isBlank()) detail.dqztMc else summary.dqztMc.ifBlank { detail.dqztMc },
            wxr = detail.wxr.ifBlank { summary.wxyXm },
            wxySjh = detail.wxySjh.ifBlank { summary.wxySjh },
            completed = detail.completed || summary.dqzt in REPAIR_ENDED_CODES,
        )
    }
}

/** 把相册 Uri 复制到缓存，交给上传层读取。 */
fun copyRepairImage(context: android.content.Context, uri: Uri): File? = runCatching {
    val dir = File(context.cacheDir, "images").apply { mkdirs() }
    val file = File(dir, "repair_${System.currentTimeMillis()}.jpg")
    context.contentResolver.openInputStream(uri)?.use { input ->
        file.outputStream().use { output -> input.copyTo(output) }
    } ?: return@runCatching null
    file
}.getOrNull()

package com.lightxin.feature.holiday.data

/**
 * POST /app/holiday/getRegistrationPage 响应
 */
data class RegistrationPageResponse(
    val code: String?,
    val flag: Boolean?,
    val msg: String?,
    val data: Any?,
    val rows: List<HolidayRow>?,
    val total: Int?,
)

data class HolidayRow(
    val id: String?,
    val name: String?,
    val type: String?,
    val schoolYear: String?,
    val status: String?,
    val isApproval: String?,
    val allowStaySchool: String?,
    val registrationType: String?,
    val startDate: String?,
    val endDate: String?,
    val registerStartDate: String?,
    val registerEndDate: String?,
    val returnStartTime: String?,
    val returnEndTime: String?,
    val leaveDestinationEnable: String?,
    val leaveDestinationRequire: String?,
    val leaveEmergencyPhoneEnable: String?,
    val leaveEmergencyPhoneRequire: String?,
    val leaveProofMaterialsEnable: String?,
    val leaveProofMaterialsRequire: String?,
    val stayProofMaterialsEnable: String?,
    val stayProofMaterialsRequire: String?,
)

/**
 * GET /app/holiday/getHolidaySetById 响应
 */
data class HolidayDetailResponse(
    val code: String?,
    val flag: Boolean?,
    val msg: String?,
    val data: HolidayDetail?,
)

data class HolidayDetail(
    val id: String?,
    val name: String?,
    val allowStaySchool: String?,
    val isApproval: String?,
    val startDate: String?,
    val endDate: String?,
    val registerStartDate: String?,
    val registerEndDate: String?,
    val returnStartTime: String?,
    val returnEndTime: String?,
    val leaveDestinationEnable: String?,
    val leaveDestinationRequire: String?,
    val leaveEmergencyPhoneEnable: String?,
    val leaveEmergencyPhoneRequire: String?,
    val leaveProofMaterialsEnable: String?,
    val leaveProofMaterialsRequire: String?,
    val stayProofMaterialsEnable: String?,
    val stayProofMaterialsRequire: String?,
)

/**
 * POST /app/dict/list 响应
 */
data class DictResponse(
    val code: String?,
    val flag: Boolean?,
    val msg: String?,
    val data: List<DictItem>?,
)

data class DictItem(
    val label: String?,
    val value: String?,
)

/**
 * GET /app/holiday/getHolidayRegister 响应
 */
data class HolidayRegisterResponse(
    val code: String?,
    val flag: Boolean?,
    val msg: String?,
    val data: HolidayRegisterData?,
)

/**
 * 已有登记记录 — data 为 null 表示无记录，非 null 时结构待联调确认
 */
data class HolidayRegisterData(
    val id: String?,
    val holidayId: String?,
    val list: List<RegisterItem>?,
)

data class RegisterItem(
    val startDate: String?,
    val endDate: String?,
    val stroke: String?,
    val reason: String?,
    val destination: String?,
    val urgentPhone: String?,
    val enableAttachmentList: List<Any?>?,
    val requireAttachmentList: List<Any?>?,
)

/**
 * POST /app/holiday/save 响应
 */
data class SaveResponse(
    val code: String?,
    val flag: Boolean?,
    val msg: String?,
    val data: Any?,
)

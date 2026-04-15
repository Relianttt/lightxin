package com.lightxin.feature.checkin.data

import com.google.gson.annotations.SerializedName
import com.google.gson.JsonElement

/**
 * POST /app/dorm/pageStudentSignIn 响应
 */
data class SignInPageResponse(
    val code: String?,
    val msg: String?,
    val data: SignInPageData?,
    val rows: List<SignInTaskRow>?,
    val total: Int?,
)

data class SignInPageData(
    val list: List<SignInTaskRow>?,
    val totalPage: Int?,
    val totalCount: Int?,
)

data class SignInTaskRow(
    val id: String?,
    val title: String?,
    val taskName: String?,
    val taskId: String?,
    val taskDateId: String?,
    val signinStatus: String?,       // "0"=未签到, "1"=已签到
    @SerializedName("executionedStatus")
    val executionedStatus: String?,
    val startTime: String?,
    val endTime: String?,
    val timedStartTime: String?,
    val timedEndTime: String?,
    val collectionStartTime: String?,
    val collectionEndTime: String?,
    val taskMajorType: String?,
    val signinPhoto: String?,
    val signinPlace: String?,
    val latLng: String?,
    val userTaskId: String?,
)

/**
 * POST /app/dorm/getTaskInfoByDateId 响应
 */
data class TaskInfoResponse(
    val code: String?,
    val msg: String?,
    val data: TaskInfoData?,
)

data class TaskInfoData(
    val title: String?,
    val taskName: String?,
    val dateId: String?,
    val taskDateId: String?,
    val startTime: String?,
    val endTime: String?,
    val timedStartTime: String?,
    val timedEndTime: String?,
    val collectionStartTime: String?,
    val collectionEndTime: String?,
    val needPhoto: String?,          // "1"=需要拍照
    val photoRequire: String?,
    val signinPhoto: String?,
    val signinStatus: String?,
    val signStatus: String?,
    val signinPlace: String?,
    val locationRange: String?,      // 签到范围(米)
    val lng: String?,                // 签到中心经度
    val lat: String?,                // 签到中心纬度
    val address: String?,            // 签到地点描述
    val signinLocations: List<SigninLocationRow>?,
)

data class SigninLocationRow(
    val content: Any?,
    val lngLat: String?,
    val signinLocation: String?,
)

/**
 * POST /app/file/uploadFileToFastdfs 响应
 */
data class FileUploadResponse(
    val code: String?,
    val msg: String?,
    val data: JsonElement?,          // 上传结果，可能是字符串或对象
)

/**
 * POST /app/dorm/signIn 响应
 */
data class SignInSubmitResponse(
    val code: String?,
    val msg: String?,
    val data: Any?,
)

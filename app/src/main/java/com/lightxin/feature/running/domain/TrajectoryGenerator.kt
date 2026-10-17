package com.lightxin.feature.running.domain

import kotlin.random.Random

object TrajectoryGenerator {

    private val campusRoutes = listOf(
        listOf(
            RunningCampus.pointAt(150.0, 210.0),
            RunningCampus.pointAt(230.0, 110.0),
            RunningCampus.pointAt(250.0, -30.0),
            RunningCampus.pointAt(170.0, -180.0),
            RunningCampus.pointAt(20.0, -220.0),
            RunningCampus.pointAt(-130.0, -140.0),
            RunningCampus.pointAt(-180.0, 10.0),
            RunningCampus.pointAt(-90.0, 190.0),
        ),
        listOf(
            RunningCampus.pointAt(60.0, -230.0),
            RunningCampus.pointAt(210.0, -170.0),
            RunningCampus.pointAt(280.0, -20.0),
            RunningCampus.pointAt(240.0, 160.0),
            RunningCampus.pointAt(100.0, 240.0),
            RunningCampus.pointAt(-70.0, 210.0),
            RunningCampus.pointAt(-190.0, 60.0),
            RunningCampus.pointAt(-140.0, -140.0),
        ),
        listOf(
            RunningCampus.pointAt(190.0, -80.0),
            RunningCampus.pointAt(260.0, 40.0),
            RunningCampus.pointAt(220.0, 200.0),
            RunningCampus.pointAt(70.0, 250.0),
            RunningCampus.pointAt(-80.0, 180.0),
            RunningCampus.pointAt(-200.0, 40.0),
            RunningCampus.pointAt(-170.0, -140.0),
            RunningCampus.pointAt(-10.0, -210.0),
        ),
    )

    /**
     * 内置校园环线。顶点取自 GPX / 谷歌地球，即 WGS-84，和手机原始定位同源，
     * 所以同样要在上传前转成 BD-09 —— 见 [RunningCampus] 的坐标系说明。
     */
    fun generate(config: SimConfig): GeneratedTrajectory = GeneratedTrajectory(
        points = sampleClosedLoop(campusRoutes.random(Random(config.startTimeMillis)), config),
        isBd09 = false,
    )

    /** 用户录制的模板来自原生 GPS，即 WGS-84，上传前需要转换。 */
    fun generateFromTemplate(config: SimConfig, template: RouteTemplate): GeneratedTrajectory =
        GeneratedTrajectory(
            points = sampleClosedLoop(template.points, config),
            isBd09 = false,
        )

    private fun sampleClosedLoop(path: List<TrackPoint>, config: SimConfig): List<TrackPoint> {
        if (path.size < 2) return emptyList()
        // 采样按 polyline 总长取模循环，补上首尾闭合段才不会在回绕处瞬移
        return PolylineSampler.sampleFromTemplate(
            templatePoints = path + path.first(),
            targetDistanceMeters = config.distanceKm * 1000.0,
            startTimeMillis = config.startTimeMillis,
            durationSeconds = config.durationMinutes * 60L,
        )
    }
}

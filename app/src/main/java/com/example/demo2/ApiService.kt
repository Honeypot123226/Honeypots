package com.example.demo2

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface ApiService {

    // =========================
    // Mode1 영상 목록
    // =========================
    @GET("mode1/videos")
    fun getMode1Videos(): Call<ApiResponse>

    // =========================
    // Mode2 영상 목록
    // =========================
    @GET("mode2/videos")
    fun getMode2Videos(): Call<ApiResponse>

    // =========================
    // Mode2 촬영 시작
    // =========================
    @POST("mode2/start")
    fun startmode2Recording(): Call<BasicResponse>

    // =========================
    // Mode2 촬영 종료
    // =========================
    @POST("mode2/stop")
    fun stopmode2Recording(): Call<BasicResponse>

    // =========================
    // Mode3 영상 목록
    // =========================
    @GET("mode3/videos")
    fun getMode3Videos(): Call<ApiResponse>

    // =========================
    // Mode3 예약 등록
    // =========================
    @POST("schedule/mode3")
    fun scheduleMode3(
        @Body request: ScheduleRequest
    ): Call<BasicResponse>
}
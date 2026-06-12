package com.example.demo2

data class ScheduleRequest(
    val startTime: String,
    val endTime: String,
    val interval: Int = 300,
    val duration: Int
)
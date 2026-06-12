package com.example.demo2

data class ApiResponse(
    val status: String,
    val mode: String? = null,
    val videos: List<VideoItem>?
)
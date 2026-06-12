package com.example.demo2

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class Mode2Activity : ComponentActivity() {

    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var refreshButton: Button
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: VideoAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mode2)

        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)
        refreshButton = findViewById(R.id.refreshButton)

        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        startButton.setOnClickListener {
            startRecording()
        }

        stopButton.setOnClickListener {
            stopRecording()
        }

        refreshButton.setOnClickListener {
            loadMode2Videos()
        }

        loadMode2Videos()
    }

    private fun startRecording() {
        RetrofitClient.instance.startmode2Recording().enqueue(object : Callback<BasicResponse> {
            override fun onResponse(
                call: Call<BasicResponse>,
                response: Response<BasicResponse>
            ) {
                if (response.isSuccessful) {
                    Toast.makeText(this@Mode2Activity, "촬영 시작", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@Mode2Activity, "촬영 시작 실패: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<BasicResponse>, t: Throwable) {
                Toast.makeText(this@Mode2Activity, "통신 실패: ${t.message}", Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun stopRecording() {
        RetrofitClient.instance.stopmode2Recording().enqueue(object : Callback<BasicResponse> {
            override fun onResponse(
                call: Call<BasicResponse>,
                response: Response<BasicResponse>
            ) {
                if (response.isSuccessful) {
                    Toast.makeText(this@Mode2Activity, "촬영 중지", Toast.LENGTH_SHORT).show()
                    loadMode2Videos()
                } else {
                    Toast.makeText(this@Mode2Activity, "촬영 중지 실패: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<BasicResponse>, t: Throwable) {
                Toast.makeText(this@Mode2Activity, "통신 실패: ${t.message}", Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun loadMode2Videos() {
        RetrofitClient.instance.getMode2Videos().enqueue(object : Callback<ApiResponse> {
            override fun onResponse(
                call: Call<ApiResponse>,
                response: Response<ApiResponse>
            ) {
                if (response.isSuccessful) {
                    val list = response.body()?.videos ?: emptyList()
                    adapter = VideoAdapter(this@Mode2Activity, list)
                    recyclerView.adapter = adapter

                    if (list.isEmpty()) {
                        Toast.makeText(this@Mode2Activity, "Mode2 영상이 없습니다", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this@Mode2Activity, "목록 조회 실패: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                Toast.makeText(this@Mode2Activity, "통신 실패: ${t.message}", Toast.LENGTH_LONG).show()
            }
        })
    }
}
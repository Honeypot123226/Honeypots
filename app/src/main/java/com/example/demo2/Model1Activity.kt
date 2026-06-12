package com.example.demo2

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class Mode1Activity : ComponentActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: VideoAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mode1)

        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        loadMode1Videos()
    }

    private fun loadMode1Videos() {
        RetrofitClient.instance.getMode1Videos().enqueue(object : Callback<ApiResponse> {
            override fun onResponse(
                call: Call<ApiResponse>,
                response: Response<ApiResponse>
            ) {
                if (response.isSuccessful) {
                    val list = response.body()?.videos ?: emptyList()
                    adapter = VideoAdapter(this@Mode1Activity, list)
                    recyclerView.adapter = adapter

                    if (list.isEmpty()) {
                        Toast.makeText(this@Mode1Activity, "Mode1 영상이 없습니다", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this@Mode1Activity, "응답 실패: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                Toast.makeText(this@Mode1Activity, "통신 실패: ${t.message}", Toast.LENGTH_LONG).show()
            }
        })
    }
}
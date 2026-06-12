package com.example.demo2

import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.Calendar

class Mode3Activity : ComponentActivity() {

    private lateinit var startTimeButton: Button
    private lateinit var endTimeButton: Button
    private lateinit var scheduleButton: Button
    private lateinit var refreshButton: Button

    private lateinit var durationEditText: EditText

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: VideoAdapter

    private var startTime: String? = null
    private var endTime: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mode3)

        // 버튼 연결
        startTimeButton = findViewById(R.id.startTimeButton)
        endTimeButton = findViewById(R.id.endTimeButton)
        scheduleButton = findViewById(R.id.scheduleButton)
        refreshButton = findViewById(R.id.refreshButton)

        // 촬영 시간 입력칸
        durationEditText = findViewById(R.id.durationEditText)

        // RecyclerView
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        // 시작 시간 선택
        startTimeButton.setOnClickListener {
            showTimePicker { time ->
                startTime = time
                startTimeButton.text = "시작 시간: $time"
            }
        }

        // 종료 시간 선택
        endTimeButton.setOnClickListener {
            showTimePicker { time ->
                endTime = time
                endTimeButton.text = "종료 시간: $time"
            }
        }

        // 예약 등록
        scheduleButton.setOnClickListener {

            if (startTime == null || endTime == null) {
                Toast.makeText(
                    this,
                    "시작 시간과 종료 시간을 설정하세요",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            val durationText = durationEditText.text.toString()

            if (durationText.isEmpty()) {
                Toast.makeText(
                    this,
                    "촬영 시간을 입력하세요",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            val duration = durationText.toInt()

            val request = ScheduleRequest(
                startTime = startTime!!,
                endTime = endTime!!,
                interval = 300,
                duration = duration
            )

            RetrofitClient.instance.scheduleMode3(request)
                .enqueue(object : Callback<BasicResponse> {

                    override fun onResponse(
                        call: Call<BasicResponse>,
                        response: Response<BasicResponse>
                    ) {
                        if (response.isSuccessful) {

                            Toast.makeText(
                                this@Mode3Activity,
                                "예약 완료",
                                Toast.LENGTH_SHORT
                            ).show()

                        } else {

                            Toast.makeText(
                                this@Mode3Activity,
                                "예약 실패: ${response.code()}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    override fun onFailure(
                        call: Call<BasicResponse>,
                        t: Throwable
                    ) {
                        Toast.makeText(
                            this@Mode3Activity,
                            "통신 실패: ${t.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                })
        }

        // 목록 새로고침
        refreshButton.setOnClickListener {
            loadMode3Videos()
        }

        // 최초 목록 로딩
        loadMode3Videos()
    }

    // 시간 선택 다이얼로그
    private fun showTimePicker(onSelected: (String) -> Unit) {

        val now = Calendar.getInstance()

        val currentHour = now.get(Calendar.HOUR_OF_DAY)
        val currentMinute = now.get(Calendar.MINUTE)

        TimePickerDialog(
            this,
            { _, hour, minute ->

                val time = String.format("%02d:%02d", hour, minute)

                onSelected(time)
            },
            currentHour,
            currentMinute,
            true
        ).show()
    }

    // Mode3 영상 목록 조회
    private fun loadMode3Videos() {

        RetrofitClient.instance.getMode3Videos()
            .enqueue(object : Callback<ApiResponse> {

                override fun onResponse(
                    call: Call<ApiResponse>,
                    response: Response<ApiResponse>
                ) {

                    if (response.isSuccessful) {

                        val list = response.body()?.videos ?: emptyList()

                        adapter = VideoAdapter(
                            this@Mode3Activity,
                            list
                        )

                        recyclerView.adapter = adapter

                        if (list.isEmpty()) {

                            Toast.makeText(
                                this@Mode3Activity,
                                "Mode3 영상이 없습니다",
                                Toast.LENGTH_SHORT
                            ).show()
                        }

                    } else {

                        Toast.makeText(
                            this@Mode3Activity,
                            "목록 조회 실패: ${response.code()}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onFailure(
                    call: Call<ApiResponse>,
                    t: Throwable
                ) {

                    Toast.makeText(
                        this@Mode3Activity,
                        "통신 실패: ${t.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            })
    }
}
package com.example.demo2

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.activity.ComponentActivity

class MainActivity : ComponentActivity() {

    private lateinit var mode1Button: Button
    private lateinit var mode2Button: Button
    private lateinit var mode3Button: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        // 버튼 연결
        mode1Button = findViewById(R.id.mode1Button)
        mode2Button = findViewById(R.id.mode2Button)
        mode3Button = findViewById(R.id.mode3Button)

        // =========================
        // Mode1
        // =========================
        mode1Button.setOnClickListener {

            val intent = Intent(
                this,
                Mode1Activity::class.java
            )

            startActivity(intent)
        }

        // =========================
        // Mode2
        // =========================
        mode2Button.setOnClickListener {

            val intent = Intent(
                this,
                Mode2Activity::class.java
            )

            startActivity(intent)
        }

        // =========================
        // Mode3
        // =========================
        mode3Button.setOnClickListener {

            val intent = Intent(
                this,
                Mode3Activity::class.java
            )

            startActivity(intent)
        }
    }
}

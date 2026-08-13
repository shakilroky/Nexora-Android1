package com.nexora.app

import android.os.Bundle
import android.widget.TextView
import android.view.Gravity
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            // Programmatic TextView (layout file ছাড়াই নিরাপদে চলবে)
            val textView = TextView(this).apply {
                text = "Nexora Android App v1.0.0\nSMS Tracker & Quick Checkout Suite"
                textSize = 18f
                gravity = Gravity.CENTER
                setPadding(32, 32, 32, 32)
            }
            setContentView(textView)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

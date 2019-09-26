package com.example.immortarjourney

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat

import kotlinx.coroutines.*
import android.content.Intent

class Loading : AppCompatActivity() {
    private val viewModelJob = Job()
    private val uiScope = CoroutineScope(Dispatchers.Main + viewModelJob)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_loading)

        val description = findViewById<TextView>(R.id.loading_text)

        description.text = "Finding location"

        val activityContext = applicationContext
        val fast = false
        uiScope.launch {
            delay(if (fast) 150 else 1500)
            description.text = "Analyzing current weather"
            delay(if (fast) 150 else 1500)
            description.text = "Predicting weather"
            delay(if (fast) 150 else 3000)
            description.text = HtmlCompat.fromHtml("Mixing <del>cement</del>  mortar", HtmlCompat.FROM_HTML_MODE_COMPACT)
            delay(if (fast) 150 else 2000)

            val intent = Intent(activityContext, Mixing::class.java)
            //intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_TASK_ON_HOME)
            intent.putExtra("moisture", 0.33F)
            intent.putExtra("cement", 50F)
            startActivity(intent)
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModelJob.cancel()
    }
}

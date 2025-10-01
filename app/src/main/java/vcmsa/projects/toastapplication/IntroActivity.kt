package vcmsa.projects.toastapplication

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2

class IntroActivity : AppCompatActivity() {
    private lateinit var imageSlider: ViewPager2
    private lateinit var handler: Handler
    private var currentPage = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_intro)

        imageSlider = findViewById(R.id.imageSlider)

        // Images to show in the slider
        val images = listOf(
            R.drawable.event1,
            R.drawable.event2,
            R.drawable.event3
        )

        val adapter = ImageSliderAdapter(images)
        imageSlider.adapter = adapter

        // Auto-slide every 3 seconds
        handler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                if (currentPage == images.size) currentPage = 0
                imageSlider.currentItem = currentPage++
                handler.postDelayed(this, 3000)
            }
        }
        handler.post(runnable)

        // Next button
        val btnNext = findViewById<Button>(R.id.btnNext)
        btnNext.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
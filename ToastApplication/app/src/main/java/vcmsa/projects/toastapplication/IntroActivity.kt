package vcmsa.projects.toastapplication

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class IntroActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        enableEdgeToEdge()
//        setContentView(R.layout.activity_intro)
//        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
//            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
//            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
//            insets

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_intro)

        val btnNext = findViewById<Button>(R.id.btnNext)
        btnNext.setOnClickListener {
            // For now, go to LoginActivity (you’ll build later)
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
}
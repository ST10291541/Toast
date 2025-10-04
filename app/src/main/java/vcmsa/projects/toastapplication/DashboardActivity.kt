package vcmsa.projects.toastapplication

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity

class DashboardActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_dashboard)

        // Bottom nav
        findViewById<LinearLayout>(R.id.navHome).setOnClickListener {
            startActivity(Intent(this, DashboardActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.navEvents).setOnClickListener {
            startActivity(Intent(this, MyEventsActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.navProfile).setOnClickListener {
            startActivity(Intent(this, ProfileSettingsActivity::class.java))
        }

       findViewById<LinearLayout>(R.id.btnCreateEvent).setOnClickListener {
            startActivity(Intent(this, CreateEventActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.btnMyEvents).setOnClickListener {
            startActivity(Intent(this, MyEventsActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.btnMyProfile).setOnClickListener {
            startActivity(Intent(this, ProfileSettingsActivity::class.java))
        }
    }
}

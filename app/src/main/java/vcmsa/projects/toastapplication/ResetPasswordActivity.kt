package vcmsa.projects.toastapplication

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import vcmsa.projects.toastapplication.databinding.ActivityResetPasswordBinding

class ResetPasswordActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    //private lateinit var db: FirebaseFirestore
    private lateinit var binding: ActivityResetPasswordBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityResetPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        val emailInput = findViewById<EditText>(R.id.edtEmail)
        val oldPasswordInput = findViewById<EditText>(R.id.edtOldP)
        val newPasswordInput = findViewById<EditText>(R.id.edtNewP)
        val updateBtn = findViewById<Button>(R.id.btnUpdate)

        // prefill email if available
        auth.currentUser?.email?.let { emailInput.setText(it) }

        updateBtn.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val oldPwd = oldPasswordInput.text.toString()
            val newPwd = newPasswordInput.text.toString()

            if (email.isEmpty() || oldPwd.isEmpty() || newPwd.isEmpty()) {
                Toast.makeText(this, "Fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val user = auth.currentUser
            if (user == null) {
                Toast.makeText(this, "Not signed in", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // make sure user signed-in with password provider
            val providers = user.providerData.map { it.providerId }
            if (!providers.contains("password")) {
                Toast.makeText(this, "Password change is only for email/password accounts. Re-authenticate with your provider (e.g., Google) to change through that provider.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            // Reauthenticate
            val credential = EmailAuthProvider.getCredential(email, oldPwd)
            user.reauthenticate(credential)
                .addOnSuccessListener {
                    // Now update password
                    user.updatePassword(newPwd)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Password updated successfully", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Failed to update password: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Reauthentication failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }
    }
}
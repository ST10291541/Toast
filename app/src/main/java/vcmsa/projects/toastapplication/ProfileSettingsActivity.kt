package vcmsa.projects.toastapplication

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import com.bumptech.glide.Glide
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import vcmsa.projects.toastapplication.databinding.ActivityProfileSettingsBinding

class ProfileSettingsActivity : AppCompatActivity() {
    private val PICK_IMAGE_REQUEST = 1001
    private var selectedImageUri: Uri? = null

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var binding: ActivityProfileSettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityProfileSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "You must be signed in to edit profile.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Load profile info
        db = FirebaseFirestore.getInstance()
        loadUserProfile()

        // pick image
        binding.editProfileImage.setOnClickListener {
            pickImageFromDevice()
        }

        // save button
        binding.btnSave.setOnClickListener {
            saveDetails()
        }

        findViewById<Button>(R.id.changePasswordBtn).setOnClickListener {
            val intent = Intent(this, ResetPasswordActivity::class.java)
            startActivity(intent)
        }

        binding.deleteAccountBtn.setOnClickListener {
            val user = auth.currentUser

            if (user == null) {
                Toast.makeText(this, "No user signed in", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Confirm deletion
            AlertDialog.Builder(this)
                .setTitle("Delete Account")
                .setMessage("Are you sure you want to delete your account? This action cannot be undone.")
                .setPositiveButton("Delete") { _, _ ->

                    user.delete()
                        .addOnSuccessListener {
                            Toast.makeText(this, "Account deleted successfully", Toast.LENGTH_SHORT).show()

                            // Return to rlogin after 2 seconds
                            Handler(mainLooper).postDelayed({
                                val intent = Intent(this, LoginActivity::class.java)
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                startActivity(intent)
                                finish()
                            }, 2000)
                        }
                        .addOnFailureListener { e ->
                            if (e is FirebaseAuthRecentLoginRequiredException) {
                                Toast.makeText(
                                    this,
                                    "Please log in again to delete your account",
                                    Toast.LENGTH_LONG
                                ).show()

                                val intent = Intent(this, LoginActivity::class.java)
                                startActivity(intent)
                                finish()
                            } else {
                                Toast.makeText(this, "Failed to delete account: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }

                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun loadUserProfile() {
        val uid = auth.currentUser!!.uid
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                if (doc != null && doc.exists()) {
                    binding.firstName.setText(doc.getString("firstName") ?: "")
                    binding.lastName.setText(doc.getString("lastName") ?: "")
                    binding.phone.setText(doc.getString("phone") ?: "")

                    val imageUri = doc.getString("profileImageUri")
                    if (!imageUri.isNullOrBlank()) {
                        Glide.with(this).load(Uri.parse(imageUri)).into(binding.profileImage)
                    }
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load profile: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun pickImageFromDevice() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
        }
        startActivityForResult(Intent.createChooser(intent, "Select profile picture"), PICK_IMAGE_REQUEST)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK) {
            selectedImageUri = data?.data
            if (selectedImageUri != null) {
                Glide.with(this).load(selectedImageUri).into(binding.profileImage)
            }
        }
    }

    private fun saveDetails() {
        val uid = auth.currentUser!!.uid
        val first = binding.firstName.text.toString().trim()
        val last = binding.lastName.text.toString().trim()
        val phoneNum = binding.phone.text.toString().trim()

        if (first.isEmpty()) {
            binding.firstName.error = "Enter first name"
            return
        }

        val snack = Snackbar.make(binding.root, "Saving...", Snackbar.LENGTH_INDEFINITE)
        snack.show()

        // Save only fields and local URI string
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val existingImageUri = if (doc.exists()) doc.getString("profileImageUri") else null
                val imageUriToSave = selectedImageUri?.toString() ?: existingImageUri

                saveUserDoc(uid, first, last, phoneNum, imageUriToSave) {
                    snack.dismiss()
                }
            }
            .addOnFailureListener { e ->
                snack.dismiss()
                Toast.makeText(this, "Error saving: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveUserDoc(
        uid: String,
        first: String,
        last: String,
        phone: String,
        imageUri: String?,
        onComplete: () -> Unit
    ) {
        val map = hashMapOf<String, Any>(
            "firstName" to first,
            "lastName" to last,
            "phone" to phone,
            "updatedAt" to com.google.firebase.Timestamp.now()
        )
        if (!imageUri.isNullOrBlank()) map["profileImageUri"] = imageUri

        db.collection("users").document(uid)
            .set(map, com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener {
                onComplete()
                Toast.makeText(this, "Profile saved", Toast.LENGTH_SHORT).show()
                // Navigate back to Dashboard after 2 seconds
                Handler(Looper.getMainLooper()).postDelayed({
                    val intent = Intent(this, DashboardActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                    finish()
                }, 5000) // 5000 milliseconds = 5 seconds
            }
            .addOnFailureListener { e ->
                onComplete()
                Toast.makeText(this, "Failed to save: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
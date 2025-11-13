package vcmsa.projects.toastapplication

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import android.app.AlertDialog
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.ImageButton
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import vcmsa.projects.toastapplication.databinding.ActivityProfileSettingsBinding
import android.view.View
import android.widget.*

class ProfileSettingsActivity : AppCompatActivity() {
    private val PICK_IMAGE_REQUEST = 1001
    private var selectedImageUri: Uri? = null

    private lateinit var spinnerLanguages: Spinner
    private val languageMap = mapOf(
        "English" to "en",
        "Afrikaans" to "af",
        "Zulu" to "zu",
        "Xhosa" to "xh"
    )

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var binding: ActivityProfileSettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LocaleManager.applySavedLocale(this)

        binding = ActivityProfileSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, getString(R.string.must_be_signed_in), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            startActivity(Intent(this, DashboardActivity::class.java))
        }

        val btnLogout = findViewById<MaterialButton>(R.id.btnLogout)
        btnLogout.setOnClickListener {
            FirebaseAuth.getInstance().signOut() // Sign out the user
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
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
                Toast.makeText(this, getString(R.string.no_user_signed_in), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Confirm deletion
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.delete_account_title))
                .setMessage(getString(R.string.delete_account_message))
                .setPositiveButton(getString(R.string.delete)) { _, _ ->

                    user.delete()
                        .addOnSuccessListener {
                            Toast.makeText(this, getString(R.string.account_deleted), Toast.LENGTH_SHORT).show()

                            // Return to login after 2 seconds
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
                                    getString(R.string.please_login_again_delete),
                                    Toast.LENGTH_LONG
                                ).show()

                                val intent = Intent(this, LoginActivity::class.java)
                                startActivity(intent)
                                finish()
                            } else {
                                Toast.makeText(this, getString(R.string.failed_delete_account, e.message ?: ""), Toast.LENGTH_SHORT).show()
                            }
                        }

                }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
        }

        // 🌍 Set up the preferred language spinner
        spinnerLanguages = binding.spinnerLanguages
        val langAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, languageMap.keys.toList())
        langAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerLanguages.adapter = langAdapter

        // Set current selection based on user profile preference (Firestore)
        val uid = auth.currentUser!!.uid
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val currentLang = doc.getString("language") ?: "en"
                val currentIndex = languageMap.values.indexOf(currentLang)
                if (currentIndex >= 0) spinnerLanguages.setSelection(currentIndex)
            }

        spinnerLanguages.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedLangCode = languageMap.values.toList()[position]
                // Save to Firestore for this user and apply immediately for this session
                db.collection("users").document(uid)
                    .set(mapOf("language" to selectedLangCode), com.google.firebase.firestore.SetOptions.merge())
                LocaleManager.applyLanguageTag(selectedLangCode)
                recreate() // restart activity to apply language
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

    }

    // --- EXISTING FUNCTIONS ---
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
        startActivityForResult(Intent.createChooser(intent, getString(R.string.select_profile_picture)), PICK_IMAGE_REQUEST)
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
            binding.firstName.error = getString(R.string.enter_first_name)
            return
        }

        val snack = Snackbar.make(binding.root, getString(R.string.saving), Snackbar.LENGTH_INDEFINITE)
        snack.show()

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
                Toast.makeText(this, getString(R.string.error_saving, e.message ?: ""), Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this, getString(R.string.profile_saved), Toast.LENGTH_SHORT).show()
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

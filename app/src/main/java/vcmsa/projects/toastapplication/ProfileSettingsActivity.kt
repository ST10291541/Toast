package vcmsa.projects.toastapplication

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.firestore.FirebaseFirestore
import vcmsa.projects.toastapplication.databinding.ActivityProfileSettingsBinding
import android.app.AlertDialog

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
    private var isLanguageSpinnerInitialized = false

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var binding: ActivityProfileSettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {


        LocaleManager.applySavedLocale(this)

        super.onCreate(savedInstanceState)

        binding = ActivityProfileSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // User check
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "You must be signed in to edit profile.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupUI()
        loadUserProfile()
        setupLanguageSpinner()
    }


    // --- UI SETUP ---
    private fun setupUI() {
        binding.btnBack.setOnClickListener {
            startActivity(Intent(this, DashboardActivity::class.java))
        }

        binding.btnLogout.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        binding.editProfileImage.setOnClickListener { pickImageFromDevice() }

        binding.btnSave.setOnClickListener { saveDetails() }

        binding.changePasswordBtn.setOnClickListener {
            startActivity(Intent(this, ResetPasswordActivity::class.java))
        }

        binding.deleteAccountBtn.setOnClickListener { confirmDeleteAccount() }
    }


    private fun setupLanguageSpinner() {
        spinnerLanguages = binding.spinnerLanguages

        val langAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            languageMap.keys.toList()
        )
        langAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerLanguages.adapter = langAdapter

        // Load saved language
        val savedLang = LocaleManager.getSavedLanguage(this)
        val savedIndex = languageMap.values.toList().indexOf(savedLang)
        if (savedIndex >= 0) spinnerLanguages.setSelection(savedIndex, false)

        // Tell spinner "initial selection phase is complete"
        isLanguageSpinnerInitialized = true

        spinnerLanguages.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View?,
                position: Int,
                id: Long
            ) {
                if (!isLanguageSpinnerInitialized) return

                val selectedLangCode = languageMap.values.toList()[position]
                val currentLang = LocaleManager.getSavedLanguage(this@ProfileSettingsActivity)

                if (selectedLangCode != currentLang) {
                    LocaleManager.saveLanguage(this@ProfileSettingsActivity, selectedLangCode)
                    LocaleManager.applyLanguageTag(selectedLangCode)
                    recreate()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }




    // --- PROFILE FUNCTIONS ---
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
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }
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

        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val existingImageUri = if (doc.exists()) doc.getString("profileImageUri") else null
                val imageUriToSave = selectedImageUri?.toString() ?: existingImageUri

                saveUserDoc(uid, first, last, phoneNum, imageUriToSave) { snack.dismiss() }
            }
            .addOnFailureListener { e ->
                snack.dismiss()
                Toast.makeText(this, "Error saving: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveUserDoc(
        uid: String, first: String, last: String,
        phone: String, imageUri: String?, onComplete: () -> Unit
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
                Handler(Looper.getMainLooper()).postDelayed({
                    val intent = Intent(this, DashboardActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                    finish()
                }, 2000)
            }
            .addOnFailureListener { e ->
                onComplete()
                Toast.makeText(this, "Failed to save: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun confirmDeleteAccount() {
        val user = auth.currentUser ?: return

        AlertDialog.Builder(this)
            .setTitle("Delete Account")
            .setMessage("Are you sure you want to delete your account? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                user.delete()
                    .addOnSuccessListener {
                        Toast.makeText(this, "Account deleted successfully", Toast.LENGTH_SHORT).show()
                        Handler(Looper.getMainLooper()).postDelayed({
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
                            startActivity(Intent(this, LoginActivity::class.java))
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

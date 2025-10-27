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
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import java.util.Locale

class ProfileSettingsActivity : AppCompatActivity() {
    private val PICK_IMAGE_REQUEST = 1001
    private var selectedImageUri: Uri? = null

    private lateinit var inputText: EditText
    private lateinit var spinnerFrom: Spinner
    private lateinit var spinnerTo: Spinner
    private lateinit var btnTranslate: Button
    private lateinit var translatedText: TextView
    private lateinit var progressBar: ProgressBar

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

        // 🌍 Apply saved language BEFORE anything else
        setAppLocale(getPreferredLanguage())

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

        //  Initialize Translator UI elements
        inputText = findViewById(R.id.inputText)
        spinnerFrom = findViewById(R.id.spinnerFrom)
        spinnerTo = findViewById(R.id.spinnerTo)
        btnTranslate = findViewById(R.id.btnTranslate)
        translatedText = findViewById(R.id.translatedText)
        progressBar = findViewById(R.id.progressBar)

        // Set up the spinners
        val translatorLanguages = listOf("English", "Afrikaans")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, translatorLanguages)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        spinnerFrom.adapter = adapter
        spinnerTo.adapter = adapter

        // Default selection
        spinnerFrom.setSelection(translatorLanguages.indexOf("English"))
        spinnerTo.setSelection(translatorLanguages.indexOf("Afrikaans"))

        btnTranslate.setOnClickListener {
            val text = inputText.text.toString().trim()
            if (text.isEmpty()) {
                Toast.makeText(this, "Please enter text to translate", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val fromLangName = spinnerFrom.selectedItem.toString()
            val toLangName = spinnerTo.selectedItem.toString()

            val fromLangCode = when (fromLangName) {
                "English" -> TranslateLanguage.ENGLISH
                "Afrikaans" -> TranslateLanguage.AFRIKAANS
                else -> TranslateLanguage.ENGLISH
            }

            val toLangCode = when (toLangName) {
                "English" -> TranslateLanguage.ENGLISH
                "Afrikaans" -> TranslateLanguage.AFRIKAANS
                else -> TranslateLanguage.ENGLISH
            }

            if (fromLangCode == toLangCode) {
                translatedText.text = text
                return@setOnClickListener
            }

            // Show progress bar
            progressBar.visibility = View.VISIBLE
            translatedText.text = ""

            val options = TranslatorOptions.Builder()
                .setSourceLanguage(fromLangCode)
                .setTargetLanguage(toLangCode)
                .build()

            val translator = Translation.getClient(options)

            // Download the model if needed
            translator.downloadModelIfNeeded()
                .addOnSuccessListener {
                    translator.translate(text)
                        .addOnSuccessListener { translated ->
                            progressBar.visibility = View.GONE
                            translatedText.text = translated
                        }
                        .addOnFailureListener { e ->
                            progressBar.visibility = View.GONE
                            Toast.makeText(this, "Translation failed: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                }
                .addOnFailureListener {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this, "Failed to download language model. Check your internet.", Toast.LENGTH_LONG).show()
                }
        }

        // 🌍 Set up the preferred language spinner
        spinnerLanguages = binding.spinnerLanguages // add a spinner in XML for this
        val langAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, languageMap.keys.toList())
        langAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerLanguages.adapter = langAdapter

        // Set current selection based on saved preference
        val currentLang = getPreferredLanguage()
        val currentIndex = languageMap.values.indexOf(currentLang)
        if (currentIndex >= 0) spinnerLanguages.setSelection(currentIndex)

        spinnerLanguages.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedLangCode = languageMap.values.toList()[position]
                savePreferredLanguage(selectedLangCode)
                setAppLocale(selectedLangCode)
                recreate() // restart activity to apply language
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

    }

    // --- LANGUAGE HELPERS ---
    private fun savePreferredLanguage(langCode: String) {
        val prefs = getSharedPreferences("AppSettings", MODE_PRIVATE)
        prefs.edit().putString("App_Language", langCode).apply()
    }

    private fun getPreferredLanguage(): String {
        val prefs = getSharedPreferences("AppSettings", MODE_PRIVATE)
        return prefs.getString("App_Language", "en")!! // default English
    }

    private fun setAppLocale(langCode: String) {
        val locale = Locale(langCode)
        Locale.setDefault(locale)
        val config = resources.configuration
        config.setLocale(locale)
        resources.updateConfiguration(config, resources.displayMetrics)
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
                Handler(Looper.getMainLooper()).postDelayed({
                    val intent = Intent(this, DashboardActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                    finish()
                }, 5000)
            }
            .addOnFailureListener { e ->
                onComplete()
                Toast.makeText(this, "Failed to save: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}

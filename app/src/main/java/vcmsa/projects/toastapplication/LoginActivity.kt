package vcmsa.projects.toastapplication

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import vcmsa.projects.toastapplication.databinding.ActivityLoginBinding

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var binding: ActivityLoginBinding
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var biometricPrompt: BiometricPrompt
    private val cryptographyManager = CryptographyManager()

    private val ciphertextWrapper
        get() = cryptographyManager.getCiphertextWrapperFromSharedPrefs(
            applicationContext,
            SHARED_PREFS_FILENAME,
            android.content.Context.MODE_PRIVATE,
            CIPHERTEXT_WRAPPER
        )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Check biometric availability
        checkBiometricAvailability()

        // Google Sign-In options
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        binding.btnGoogle.setOnClickListener { signInWithGoogle() }

        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            if (email.isNotEmpty() && password.isNotEmpty()) {
                loginWithEmailPassword(email, password)
            } else {
                Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnRegister.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }
    }


    private fun checkBiometricAvailability() {
        val biometricManager = BiometricManager.from(this)
        when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                Log.d("Biometric", "Biometric authentication available")
                setupBiometricCard()
            }
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                Log.d("Biometric", "No biometric features available on this device")
                binding.useBiometrics.visibility = View.GONE
            }
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                Log.d("Biometric", "Biometric features are currently unavailable")
                binding.useBiometrics.visibility = View.GONE
            }
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                Log.d("Biometric", "No biometrics enrolled")
                binding.useBiometrics.visibility = View.GONE
            }
            else -> {
                Log.d("Biometric", "Biometric authentication not available")
                binding.useBiometrics.visibility = View.GONE
            }
        }
    }

    private fun setupBiometricCard() {
        binding.useBiometrics.visibility = View.VISIBLE

        // Remove any existing click listeners first
        binding.useBiometrics.setOnClickListener(null)

        // Set new click listener
        binding.useBiometrics.setOnClickListener {
            Log.d("Biometric", "Biometrics card clicked - checking credentials")

            // Debug: Check if ciphertextWrapper exists
            val hasCredentials = ciphertextWrapper != null
            Log.d("Biometric", "Has stored credentials: $hasCredentials")

            if (hasCredentials) {
                Log.d("Biometric", "Credentials found, showing biometric prompt")
                showBiometricPromptForDecryption()
            } else {
                Log.d("Biometric", "No credentials found, starting EnableBiometricLoginActivity")
                val intent = Intent(this, EnableBiometricLoginActivity::class.java)
                startActivity(intent)
            }
        }

        // Force the view to be clickable
        binding.useBiometrics.isClickable = true
        binding.useBiometrics.isFocusable = true
    }

    override fun onResume() {
        super.onResume()

        // Auto-show biometric prompt if credentials are stored
        if (ciphertextWrapper != null && auth.currentUser == null) {
            Log.d("Biometric", "Auto-showing biometric prompt on resume")
            showBiometricPromptForDecryption()
        }
    }

    // BIOMETRICS SECTION
    private fun showBiometricPromptForDecryption() {
        ciphertextWrapper?.let { textWrapper ->
            try {
                val cipher = cryptographyManager.getInitializedCipherForDecryption(
                    SECRET_KEY_NAME, textWrapper.initializationVector
                )
                biometricPrompt = BiometricPromptUtils.createBiometricPrompt(
                    this,
                    ::decryptServerTokenFromStorage
                )
                val promptInfo = BiometricPromptUtils.createPromptInfo(this)
                biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
                Log.d("Biometric", "Biometric prompt shown")
            } catch (e: Exception) {
                Log.e("Biometric", "Error showing biometric prompt: ${e.message}")
                Toast.makeText(this, "Error with biometric authentication", Toast.LENGTH_SHORT).show()
            }
        } ?: run {
            Log.d("Biometric", "No ciphertext wrapper found")
            Toast.makeText(this, "Biometric login not set up", Toast.LENGTH_SHORT).show()
        }
    }

    private fun decryptServerTokenFromStorage(authResult: BiometricPrompt.AuthenticationResult) {
        ciphertextWrapper?.let { textWrapper ->
            authResult.cryptoObject?.cipher?.let { cipher ->
                try {
                    val userToken = cryptographyManager.decryptData(textWrapper.ciphertext, cipher)
                    Log.d("Biometric", "Biometric authentication successful")
                    // For Firebase, we need to sign in again
                    // In a real app, you might store the actual Firebase token
                    // For now, we'll navigate to dashboard directly
                    applyUserLanguageThenNavigate()
                } catch (e: Exception) {
                    Log.e("Biometric", "Error decrypting token: ${e.message}")
                    Toast.makeText(this, "Authentication failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loginWithEmailPassword(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    applyUserLanguageThenNavigate()
                } else {
                    Toast.makeText(this, "Login failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun applyUserLanguageThenNavigate() {
        val user = auth.currentUser
        if (user == null) {
            navigateToDashboard()
            return
        }
        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { doc ->
                val lang = doc.getString("language")
                if (!lang.isNullOrBlank()) {
                    LocaleManager.applyLanguageTag(lang)
                    recreate()
                }
                navigateToDashboard()
            }
            .addOnFailureListener {
                navigateToDashboard()
            }
    }

    private fun navigateToDashboard() {
        val intent = Intent(this, DashboardActivity::class.java)
        startActivity(intent)
        finish()
    }

    // Google Sign-In methods
    private val signInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                firebaseAuthWithGoogle(account?.idToken ?: "")
            } catch (e: ApiException) {
                Toast.makeText(this, "Google sign-in failed: ${e.statusCode}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Google sign-in cancelled or failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun signInWithGoogle() {
        val signInIntent = googleSignInClient.signInIntent
        googleSignInClient.signOut()
        signInLauncher.launch(signInIntent)
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential).addOnCompleteListener(this) { task ->
            if (task.isSuccessful) {
                applyUserLanguageThenNavigate()
            } else {
                Toast.makeText(this, "Firebase auth failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        private const val SHARED_PREFS_FILENAME = "biometric_prefs"
        private const val CIPHERTEXT_WRAPPER = "ciphertext_wrapper"
        private const val SECRET_KEY_NAME = "biometric_key"
    }
}
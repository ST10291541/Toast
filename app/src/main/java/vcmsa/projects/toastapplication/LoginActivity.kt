package vcmsa.projects.toastapplication

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
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
        setContentView(R.layout.activity_login)

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Check biometric availability
        val canAuthenticate = BiometricManager.from(applicationContext).canAuthenticate()
        if (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS) {
            binding.useBiometrics.visibility = View.VISIBLE
            binding.useBiometrics.setOnClickListener {
                if (ciphertextWrapper != null) {
                    showBiometricPromptForDecryption()
                } else {
                    startActivity(Intent(this, EnableBiometricLoginActivity::class.java))
                }
            }
        } else {
            binding.useBiometrics.visibility = View.INVISIBLE
        }

        if (ciphertextWrapper == null) {
            // Normal login setup
            setupNormalLogin()
        }

        // Google Sign-In options
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        findViewById<com.google.android.gms.common.SignInButton>(R.id.btnGoogle)
            .setOnClickListener { signInWithGoogle() }

        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            if (email.isNotEmpty() && password.isNotEmpty()) {
                loginWithEmailPassword(email, password)
            } else {
                Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.btnRegister).setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()

        // Auto-show biometric prompt if credentials are stored
        if (ciphertextWrapper != null && auth.currentUser == null) {
            showBiometricPromptForDecryption()
        }
    }

    // BIOMETRICS SECTION
    private fun showBiometricPromptForDecryption() {
        ciphertextWrapper?.let { textWrapper ->
            val cipher = cryptographyManager.getInitializedCipherForDecryption(
                SECRET_KEY_NAME, textWrapper.initializationVector
            )
            biometricPrompt =
                BiometricPromptUtils.createBiometricPrompt(
                    this,
                    ::decryptServerTokenFromStorage
                )
            val promptInfo = BiometricPromptUtils.createPromptInfo(this)
            biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
        }
    }

    private fun decryptServerTokenFromStorage(authResult: BiometricPrompt.AuthenticationResult) {
        ciphertextWrapper?.let { textWrapper ->
            authResult.cryptoObject?.cipher?.let { cipher ->
                val userToken = cryptographyManager.decryptData(textWrapper.ciphertext, cipher)
                // For Firebase, we need to sign in again
                // In a real app, you might store the actual Firebase token
                // For now, we'll navigate to dashboard directly
                navigateToDashboard()
            }
        }
    }

    // NORMAL LOGIN SECTION
    private fun setupNormalLogin() {
        // Your existing normal login setup
    }

    private fun loginWithEmailPassword(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    navigateToDashboard()
                } else {
                    Toast.makeText(this, "Login failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun navigateToDashboard() {
        val intent = Intent(this, DashboardActivity::class.java)
        startActivity(intent)
        finish()
    }

    // Your existing Google Sign-In methods...
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
                navigateToDashboard()
            } else {
                Toast.makeText(this, "Firebase auth failed", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
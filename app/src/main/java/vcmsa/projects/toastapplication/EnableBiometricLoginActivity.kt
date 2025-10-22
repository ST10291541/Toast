package vcmsa.projects.toastapplication

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.lifecycle.Observer
import com.google.firebase.auth.FirebaseAuth
import vcmsa.projects.toastapplication.databinding.ActivityEnableBiometricLoginBinding

class EnableBiometricLoginActivity : AppCompatActivity() {

    private val TAG = "EnableBiometricLogin"
    private lateinit var cryptographyManager: CryptographyManager
    private lateinit var binding: ActivityEnableBiometricLoginBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEnableBiometricLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        binding.cancel.setOnClickListener { finish() }

        binding.authorize.setOnClickListener {
            val email = binding.username.text.toString().trim()
            val password = binding.password.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Authenticate with Firebase first
            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        // Firebase authentication successful, now show biometric prompt
                        showBiometricPromptForEncryption()
                    } else {
                        Toast.makeText(this, "Authentication failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
        }
    }

    private fun showBiometricPromptForEncryption() {
        val canAuthenticate = BiometricManager.from(applicationContext).canAuthenticate()
        if (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS) {
            cryptographyManager = CryptographyManager()
            val cipher = cryptographyManager.getInitializedCipherForEncryption(SECRET_KEY_NAME)
            val biometricPrompt =
                BiometricPromptUtils.createBiometricPrompt(this, ::encryptAndStoreServerToken)
            val promptInfo = BiometricPromptUtils.createPromptInfo(this)
            biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
        } else {
            Toast.makeText(this, "Biometric authentication not available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun encryptAndStoreServerToken(authResult: BiometricPrompt.AuthenticationResult) {
        authResult.cryptoObject?.cipher?.apply {
            // Use Firebase UID as the token to encrypt
            val userToken = auth.currentUser?.uid ?: ""
            if (userToken.isNotEmpty()) {
                Log.d(TAG, "The user token is $userToken")
                val encryptedServerTokenWrapper = cryptographyManager.encryptData(userToken, this)
                cryptographyManager.persistCiphertextWrapperToSharedPrefs(
                    encryptedServerTokenWrapper,
                    applicationContext,
                    SHARED_PREFS_FILENAME,
                    android.content.Context.MODE_PRIVATE,
                    CIPHERTEXT_WRAPPER
                )
                Toast.makeText(this@EnableBiometricLoginActivity, "Biometric login enabled!", Toast.LENGTH_SHORT).show()
            }
        }
        finish()
    }
}
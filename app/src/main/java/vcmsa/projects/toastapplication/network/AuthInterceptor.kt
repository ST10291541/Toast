package vcmsa.projects.toastapplication.network

import com.google.firebase.auth.FirebaseAuth
import okhttp3.Interceptor
import okhttp3.Response
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await

class AuthInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val requestBuilder = chain.request().newBuilder()
        val user = FirebaseAuth.getInstance().currentUser

        if (user != null) {
            val token = runBlocking { user.getIdToken(false).await().token }
            token?.let { requestBuilder.addHeader("Authorization", "Bearer $it") }
        }

        return chain.proceed(requestBuilder.build())
    }
}

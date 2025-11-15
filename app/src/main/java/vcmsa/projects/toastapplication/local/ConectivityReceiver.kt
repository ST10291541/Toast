package vcmsa.projects.toastapplication.local

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ConnectivityReceiver(val context: Context) {

    private val eventRepo = EventRepo(context)

    // One shared coroutine scope for sync work
    private val scope = CoroutineScope(Dispatchers.IO)

    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {

        // Fires when internet becomes available
        override fun onAvailable(network: Network) {
            if (isOnline()) {
                scope.launch {
                    eventRepo.syncEventsToFirestore()
                }
            }
        }

        // Optional: When network is lost
        override fun onLost(network: Network) {
            // No action needed, but prevents weird callback issues
        }
    }

    fun startObserving() {

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        cm.registerNetworkCallback(request, networkCallback)

        // Immediate check on app startup
        if (isOnline()) {
            scope.launch {
                eventRepo.syncEventsToFirestore()
            }
        }
    }

    // Stronger online check
    private fun isOnline(): Boolean {
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false

        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
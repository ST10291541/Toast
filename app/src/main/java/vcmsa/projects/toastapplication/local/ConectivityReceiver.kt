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

    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            // Device is online, sync events
            CoroutineScope(Dispatchers.IO).launch {
                EventRepo(context).syncEventsToFirestore()
            }
        }
    }

    fun startObserving() {
        val request = NetworkRequest.Builder().build()
        cm.registerNetworkCallback(request, networkCallback)

        // Immediately check if online
        if (isOnline()) {
            CoroutineScope(Dispatchers.IO).launch {
                EventRepo(context).syncEventsToFirestore()
            }
        }
    }

    private fun isOnline(): Boolean {
        val network = cm.activeNetwork
        val capabilities = cm.getNetworkCapabilities(network)
        return capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }
}
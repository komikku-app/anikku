package eu.kanade.tachiyomi.util.system

import android.content.Context
import android.net.ConnectivityManager.NetworkCallback
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow

data class NetworkState(
    val isConnected: Boolean,
    val isValidated: Boolean,
    val isWifi: Boolean,
    val isEthernet: Boolean,
) {
    val isOnline = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        isConnected && isValidated
    } else {
        isConnected
    }
}

@Suppress("DEPRECATION")
fun Context.activeNetworkState(): NetworkState {
    val capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
    return NetworkState(
        isConnected = connectivityManager.activeNetworkInfo?.isConnected ?: false,
        isValidated = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ?: false,
        isWifi = wifiManager.isWifiEnabled && capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ?: false,
        isEthernet = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ?: false,
    )
}

fun Context.networkStateFlow() = callbackFlow {
    val networkCallback = object : NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            trySend(activeNetworkState())
        }
        override fun onLost(network: Network) {
            trySend(activeNetworkState())
        }
    }

    connectivityManager.registerDefaultNetworkCallback(networkCallback)
    awaitClose {
        connectivityManager.unregisterNetworkCallback(networkCallback)
    }
}

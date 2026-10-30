package com.autosmsforwarder.worker

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

fun interface WebConnectivityChecker {
    fun isOnline(): Boolean
}

class AndroidWebConnectivityChecker(context: Context) : WebConnectivityChecker {
    private val manager = context.getSystemService(ConnectivityManager::class.java)

    override fun isOnline(): Boolean {
        val network = manager?.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}

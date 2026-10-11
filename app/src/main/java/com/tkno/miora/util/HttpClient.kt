package com.tkno.miora.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object HttpClient {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun isNetworkAvailable(context: Context): Boolean {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (connectivityManager != null) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    val network = connectivityManager.activeNetwork ?: return false
                    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                } else {
                    @Suppress("DEPRECATION")
                    val activeNetworkInfo = connectivityManager.activeNetworkInfo
                    @Suppress("DEPRECATION")
                    activeNetworkInfo != null && activeNetworkInfo.isConnected
                }
            } else {
                true
            }
        } catch (e: Exception) {
            true
        }
    }
}

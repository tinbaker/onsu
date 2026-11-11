package com.onsu.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities

/**
 * Trạng thái mạng của máy.
 *
 * App cần biết điều này vì gợi ý là nội dung online: khi mất mạng thì phải báo cho
 * người dùng biết và để trống danh sách, thay vì lấp bằng kho nhạc dự phòng.
 */
object NetworkMonitor {

    /**
     * `true` khi có mạng và mạng đó thật sự ra được Internet.
     *
     * Kiểm tra `VALIDATED` chứ không chỉ "có kết nối": máy vẫn có thể đang nối vào một
     * Wi-Fi không có Internet (quán cà phê, captive portal) — lúc đó gợi ý cũng không
     * tải được nên phải coi là offline.
     */
    fun isOnline(context: Context): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /**
     * Theo dõi mạng mặc định. [onAvailable] được gọi ngay khi đăng ký nếu đang có mạng.
     * Trả về callback để huỷ đăng ký, hoặc null nếu thiết bị không hỗ trợ.
     */
    fun observe(
        context: Context,
        onAvailable: () -> Unit,
        onLost: () -> Unit = {}
    ): ConnectivityManager.NetworkCallback? {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return null
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = onAvailable()
            override fun onLost(network: Network) = onLost()
        }
        return try {
            manager.registerDefaultNetworkCallback(callback)
            callback
        } catch (error: Exception) {
            null
        }
    }

    fun stopObserving(context: Context, callback: ConnectivityManager.NetworkCallback) {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        runCatching { manager?.unregisterNetworkCallback(callback) }
    }
}

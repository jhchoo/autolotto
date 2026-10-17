package com.umicorp.autolotto.dhlottery

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

sealed interface ServerHealth {
    data object Online : ServerHealth
    data object Offline : ServerHealth
    data class Maintenance(val reason: String? = null) : ServerHealth

    val isAvailable: Boolean get() = this is Online
}

object ServerHealthChecker {

    private val checkClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /**
     * 네트워크 상태 및 동행복권 서버 접속 가능 여부 체크
     */
    suspend fun checkHealth(context: Context): ServerHealth = withContext(Dispatchers.IO) {
        // 1. 기기 네트워크 연결 상태 확인
        if (!isNetworkConnected(context)) {
            return@withContext ServerHealth.Offline
        }

        // 2. 동행복권 서버 접속 체크
        runCatching {
            val req = Request.Builder()
                .url(ApiConstants.DEFAULT_BASE)
                .addHeader("User-Agent", "Mozilla/5.0 (Android)")
                .build()

            checkClient.newCall(req).execute().use { resp ->
                if (resp.code == 503 || resp.code == 500) {
                    return@withContext ServerHealth.Maintenance("서버 응답 코드: ${resp.code}")
                }
                val body = resp.body?.string().orEmpty()
                if (body.contains("서비스 점검") || body.contains("시스템 점검") || body.contains("공사중")) {
                    return@withContext ServerHealth.Maintenance("동행복권 시스템 점검 중")
                }
                if (!resp.isSuccessful) {
                    return@withContext ServerHealth.Maintenance("서버 연결 상태 불량 (${resp.code})")
                }
                ServerHealth.Online
            }
        }.getOrElse { e ->
            ServerHealth.Maintenance(e.message ?: "서버 연결 시간 초과")
        }
    }

    private fun isNetworkConnected(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

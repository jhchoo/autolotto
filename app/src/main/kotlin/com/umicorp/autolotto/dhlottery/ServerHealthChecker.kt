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
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val MAINTENANCE_KEYWORDS = listOf(
        "서비스 점검",
        "시스템 점검",
        "공사중",
        "정기점검",
        "서비스점검",
        "시스템작업",
        "서비스 점검 안내",
        "시스템 점검 안내",
        "서비스 이용이 일시 중단",
        "점검시간",
    )

    fun isMaintenanceText(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        return MAINTENANCE_KEYWORDS.any { text.contains(it) }
    }

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
                if (resp.code == 503 || resp.code == 502) {
                    return@withContext ServerHealth.Maintenance("서버 응답 코드: ${resp.code}")
                }
                val body = resp.body?.string().orEmpty()
                if (isMaintenanceText(body)) {
                    return@withContext ServerHealth.Maintenance("동행복권 시스템 점검 중")
                }
                if (resp.code == 500) {
                    return@withContext ServerHealth.Maintenance("서버 내부 오류 (500)")
                }
                if (!resp.isSuccessful) {
                    return@withContext ServerHealth.Offline
                }
                ServerHealth.Online
            }
        }.getOrElse { e ->
            // 네트워크 타임아웃/소켓에러 등은 점검이 아니라 연결 불안정(Offline)
            ServerHealth.Offline
        }
    }

    private fun isNetworkConnected(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

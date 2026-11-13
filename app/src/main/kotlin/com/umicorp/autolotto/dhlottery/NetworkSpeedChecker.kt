package com.umicorp.autolotto.dhlottery

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 기기 네트워크 속도 단계
 * - SLOW: 저속 (최대 30초 대기)
 * - MEDIUM: 중속 (최대 10초 대기)
 * - FAST: 고속 (최대 5초 이내)
 */
enum class NetworkSpeed(val timeoutMillis: Long, val label: String) {
    SLOW(30_000L, "저속"),
    MEDIUM(10_000L, "중속"),
    FAST(5_000L, "고속");
}

object NetworkSpeedChecker {

    // 테스트나 디버깅용 강제 속도 오버라이드
    @Volatile
    var overrideSpeed: NetworkSpeed? = null

    private val pingClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    @Volatile
    private var lastCheckedTime: Long = 0L

    @Volatile
    private var lastSpeed: NetworkSpeed = NetworkSpeed.SLOW

    /**
     * 현재 핸드폰의 네트워크 속도를 체크하여 [NetworkSpeed]를 반환한다.
     * 캐시 유효 시간: 10초
     */
    suspend fun checkSpeed(
        context: Context,
        testUrl: String = ApiConstants.DEFAULT_BASE,
    ): NetworkSpeed = withContext(Dispatchers.IO) {
        overrideSpeed?.let { return@withContext it }

        val now = SystemClock.elapsedRealtime()
        if (now - lastCheckedTime < 10_000L) {
            return@withContext lastSpeed
        }

        val cm = context.getSystemService(ConnectivityManager::class.java)
        val network = cm?.activeNetwork
        val caps = network?.let { cm.getNetworkCapabilities(it) }

        // 인터넷 연결 없음 또는 불확실 -> 안전하게 저속(30초) 적용
        if (caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            lastSpeed = NetworkSpeed.SLOW
            lastCheckedTime = now
            return@withContext NetworkSpeed.SLOW
        }

        val downstreamKbps = caps.linkDownstreamBandwidthKbps

        // 실제 RTT(응답 지연 시간) 측정
        val startTime = SystemClock.elapsedRealtime()
        val rttMs = runCatching {
            val req = Request.Builder()
                .url(testUrl)
                .head()
                .build()
            pingClient.newCall(req).execute().use {
                SystemClock.elapsedRealtime() - startTime
            }
        }.getOrElse {
            // HEAD 미지원 시 빠른 GET 시도
            val getStart = SystemClock.elapsedRealtime()
            runCatching {
                val req = Request.Builder()
                    .url(testUrl)
                    .get()
                    .build()
                pingClient.newCall(req).execute().use {
                    SystemClock.elapsedRealtime() - getStart
                }
            }.getOrElse { 9999L }
        }

        // 속도 판정:
        // - 저속 (SLOW, 30초):
        //     RTT >= 1200ms 또는 downstreamKbps < 3000(0 초과) 또는 핑 실패
        // - 고속 (FAST, 5초 이내):
        //     RTT < 500ms AND (downstreamKbps >= 15000 또는 downstreamKbps == 0 && RTT < 300ms)
        // - 중속 (MEDIUM, 10초):
        //     그 외 일반적인 환경
        val speed = when {
            rttMs >= 1200L || (downstreamKbps in 1..2999) -> NetworkSpeed.SLOW
            (rttMs < 500L && downstreamKbps >= 15000) || (rttMs < 300L && downstreamKbps == 0) -> NetworkSpeed.FAST
            else -> NetworkSpeed.MEDIUM
        }

        lastSpeed = speed
        lastCheckedTime = now
        speed
    }

    /** 캐시 초기화 (필요 시 즉시 재측정) */
    fun invalidateCache() {
        lastCheckedTime = 0L
    }
}

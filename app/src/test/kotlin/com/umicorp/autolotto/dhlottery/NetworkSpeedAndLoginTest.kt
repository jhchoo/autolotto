package com.umicorp.autolotto.dhlottery

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey

class NetworkSpeedAndLoginTest {

    private lateinit var server: MockWebServer
    private lateinit var modulusHex: String
    private lateinit var exponentHex: String

    @Before
    fun setUp() {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val pub = keyPair.public as RSAPublicKey
        modulusHex = pub.modulus.toString(16)
        exponentHex = pub.publicExponent.toString(16)
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
        NetworkSpeedChecker.overrideSpeed = null
    }

    private fun authFor(dispatcher: Dispatcher): AuthService {
        server.dispatcher = dispatcher
        val baseUrl = server.url("/").toString().trimEnd('/')
        val session = DhlotterySession(baseUrl = baseUrl, olottoUrl = baseUrl)
        return AuthService(session)
    }

    private fun ok(body: String) = MockResponse().setResponseCode(200).setBody(body)
    private fun rsaOk() = ok("{\"data\":{\"rsaModulus\":\"$modulusHex\",\"publicExponent\":\"$exponentHex\"}}")

    @Test
    fun `network speed timeouts match specifications`() {
        // 저속: 30초, 중속: 10초, 고속: 5초
        assertEquals(30_000L, NetworkSpeed.SLOW.timeoutMillis)
        assertEquals(10_000L, NetworkSpeed.MEDIUM.timeoutMillis)
        assertEquals(5_000L, NetworkSpeed.FAST.timeoutMillis)
    }

    @Test
    fun `maintenance text detection works accurately`() {
        assertTrue(ServerHealthChecker.isMaintenanceText("현재 시스템 점검 중입니다."))
        assertTrue(ServerHealthChecker.isMaintenanceText("동행복권 서비스 점검 안내"))
        assertTrue(ServerHealthChecker.isMaintenanceText("정기점검으로 인해 이용이 제한됩니다."))
        assertTrue(ServerHealthChecker.isMaintenanceText("홈페이지 공사중 안내"))
        assertFalse(ServerHealthChecker.isMaintenanceText("정상적인 로그인 화면"))
        assertFalse(ServerHealthChecker.isMaintenanceText(""))
        assertFalse(ServerHealthChecker.isMaintenanceText(null))
    }

    @Test
    fun `login detects maintenance during main page fetch`() = runBlocking {
        val auth = authFor(object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.path?.substringBefore('?') in listOf("/", "")) {
                    MockResponse().setResponseCode(503).setBody("서비스 점검")
                } else ok("")
        })

        val ex = try {
            auth.login("user", "pass")
            null
        } catch (e: Exception) {
            e
        }

        assertTrue(ex is DhlotteryException)
        assertTrue(ex?.message?.startsWith("MAINTENANCE") == true)
        assertFalse(auth.isLoggedIn)
    }

    @Test
    fun `login detects maintenance in RSA key response`() = runBlocking {
        val auth = authFor(object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                when (request.path?.substringBefore('?')) {
                    ApiConstants.RSA_MODULUS -> ok("<html>동행복권 시스템 점검 중 안내</html>")
                    else -> ok("")
                }
        })

        val ex = try {
            auth.login("user", "pass")
            null
        } catch (e: Exception) {
            e
        }

        assertTrue(ex is DhlotteryException)
        assertTrue(ex?.message?.startsWith("MAINTENANCE") == true)
        assertFalse(auth.isLoggedIn)
    }

    @Test
    fun `verify failure decides maintenance if server entered maintenance`() = runBlocking {
        var callCount = 0
        val auth = authFor(object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                when (request.path?.substringBefore('?')) {
                    ApiConstants.RSA_MODULUS -> rsaOk()
                    ApiConstants.LOGIN -> ok("").addHeader("Set-Cookie", "JSESSIONID=x; Domain=.dhlottery.co.kr; Path=/")
                    ApiConstants.GAME645 -> ok("").addHeader("Set-Cookie", "JSESSIONID=ol; Path=/")
                    ApiConstants.BALANCE -> MockResponse().setResponseCode(401) // 검증 실패
                    "/", "" -> {
                        callCount++
                        if (callCount > 1) {
                            ok("동행복권 서비스 점검 중")
                        } else {
                            ok("")
                        }
                    }
                    else -> ok("")
                }
        })

        val ex = try {
            auth.login("user", "pass")
            null
        } catch (e: Exception) {
            e
        }

        assertTrue(ex is DhlotteryException)
        assertTrue(ex?.message?.startsWith("MAINTENANCE") == true)
        assertFalse(auth.isLoggedIn)
    }

    @Test
    fun `verify failure decides invalid credentials if server is normal`() = runBlocking {
        val auth = authFor(object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                when (request.path?.substringBefore('?')) {
                    ApiConstants.RSA_MODULUS -> rsaOk()
                    ApiConstants.LOGIN -> ok("").addHeader("Set-Cookie", "JSESSIONID=x; Domain=.dhlottery.co.kr; Path=/")
                    ApiConstants.GAME645 -> ok("").addHeader("Set-Cookie", "JSESSIONID=ol; Path=/")
                    ApiConstants.BALANCE -> MockResponse().setResponseCode(401) // 검증 실패 (비밀번호 불일치)
                    else -> ok("정상 페이지")
                }
        })

        val ex = try {
            auth.login("user", "pass")
            null
        } catch (e: Exception) {
            e
        }

        assertTrue(ex is DhlotteryException)
        assertEquals("INVALID_CREDENTIALS", ex?.message)
        assertFalse(auth.isLoggedIn)
    }
}

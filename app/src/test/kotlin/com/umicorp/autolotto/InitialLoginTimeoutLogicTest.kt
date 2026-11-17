package com.umicorp.autolotto

import com.umicorp.autolotto.dhlottery.DhlotteryException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class InitialLoginTimeoutLogicTest {

    @Test
    fun `isTimeoutLoginFailure returns true for TimeoutCancellationException and SocketTimeoutException`() {
        val timeoutEx = runCatching {
            kotlinx.coroutines.runBlocking {
                withTimeout(1L) {
                    kotlinx.coroutines.delay(500L)
                }
            }
        }.exceptionOrNull()!!
        assertTrue(timeoutEx is TimeoutCancellationException)
        assertTrue(isTimeoutLoginFailure(timeoutEx))

        assertTrue(isTimeoutLoginFailure(SocketTimeoutException("Read timed out")))
        assertTrue(isTimeoutLoginFailure(RuntimeException(SocketTimeoutException("connect timed out"))))
        assertTrue(isTimeoutLoginFailure(InterruptedIOException("timeout")))
        assertTrue(isTimeoutLoginFailure(DhlotteryException("TIMEOUT:30")))
    }

    @Test
    fun `isTimeoutLoginFailure returns false for server errors, maintenance, credentials, and host errors`() {
        assertFalse(isTimeoutLoginFailure(DhlotteryException("SERVER_ERROR:500")))
        assertFalse(isTimeoutLoginFailure(DhlotteryException("MAINTENANCE:점검 중")))
        assertFalse(isTimeoutLoginFailure(DhlotteryException("INVALID_CREDENTIALS")))
        assertFalse(isTimeoutLoginFailure(DhlotteryException("OFFLINE:네트워크 연결이 필요합니다.")))
        assertFalse(isTimeoutLoginFailure(UnknownHostException("www.dhlottery.co.kr")))
        assertFalse(isTimeoutLoginFailure(ConnectException("Failed to connect")))
        assertFalse(isTimeoutLoginFailure(IOException("verify_http_502")))
        assertFalse(isTimeoutLoginFailure(InterruptedIOException("stream reset")))
    }

    @Test
    fun `shouldPromptInitialTimeoutRetry only returns true on first attempt with timeout failure`() {
        assertTrue(shouldPromptInitialTimeoutRetry(isFirstAutoLoginAttempt = true, isTimeoutFailure = true))
        assertFalse(shouldPromptInitialTimeoutRetry(isFirstAutoLoginAttempt = true, isTimeoutFailure = false))
        assertFalse(shouldPromptInitialTimeoutRetry(isFirstAutoLoginAttempt = false, isTimeoutFailure = true))
        assertFalse(shouldPromptInitialTimeoutRetry(isFirstAutoLoginAttempt = false, isTimeoutFailure = false))
    }
}

package com.umicorp.autolotto.dhlottery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerHealthCheckerTest {

    @Test
    fun serverHealth_onlineStateIsAvailable() {
        val online: ServerHealth = ServerHealth.Online
        assertTrue(online.isAvailable)
    }

    @Test
    fun serverHealth_offlineAndMaintenanceStatesAreNotAvailable() {
        val offline: ServerHealth = ServerHealth.Offline
        val maintenance: ServerHealth = ServerHealth.Maintenance("Under maintenance")

        assertFalse(offline.isAvailable)
        assertFalse(maintenance.isAvailable)
    }

    @Test
    fun serverHealth_offlineIsDistinctFromMaintenance() {
        val offline: ServerHealth = ServerHealth.Offline
        val maintenance: ServerHealth = ServerHealth.Maintenance("동행복권 시스템 점검 중")

        assertTrue(offline is ServerHealth.Offline)
        assertFalse(offline is ServerHealth.Maintenance)
        assertEquals("동행복권 시스템 점검 중", (maintenance as ServerHealth.Maintenance).reason)
    }
}

package com.umicorp.autolotto.dhlottery

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
}

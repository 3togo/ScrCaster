package io.github.togo3.scrcaster

import org.junit.Assert.assertEquals
import org.junit.Test

class TvRemoteAccessTest {
    @Test fun permissionWithoutBindingIsNotReportedAsWorking() {
        assertEquals(TvRemoteAccess.WAITING, tvRemoteAccess(enabled = true, connected = false))
        assertEquals(TvRemoteAccess.ACTIVE, tvRemoteAccess(enabled = true, connected = true))
    }

    @Test fun revocationIsVisibleEvenBeforeServiceUnbinds() {
        assertEquals(TvRemoteAccess.DISABLED, tvRemoteAccess(enabled = false, connected = true))
        assertEquals(TvRemoteAccess.DISABLED, tvRemoteAccess(enabled = false, connected = false))
    }
}

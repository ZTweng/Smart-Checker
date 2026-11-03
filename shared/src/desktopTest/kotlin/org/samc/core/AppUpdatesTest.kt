package org.samc.core

import kotlin.test.*

class AppUpdatesTest {
    @Test fun keepsMandatoryUpdateAfterFailureOrStaleResponse() {
        val required = AppUpdates.Release("6.26.4", "", AppUpdates.RELEASES)
        assertEquals(required, AppUpdates.requiredRelease(required, null))
        assertEquals(required, AppUpdates.requiredRelease(required, required.copy(version = "6.26.3")))
        assertEquals("7.0.0", AppUpdates.requiredRelease(required, required.copy(version = "7.0.0"))?.version)
        assertEquals(required, AppUpdates.requiredRelease(null, required))
        assertNull(AppUpdates.requiredRelease(null, required.copy(version = AppUpdates.VERSION)))
    }

    @Test fun restoresOnlyNewerCachedRequirements() {
        assertNotNull(AppUpdates.cachedRequirement("6.26.4"))
        assertNull(AppUpdates.cachedRequirement(AppUpdates.VERSION))
        assertNull(AppUpdates.cachedRequirement("6.26.2"))
        assertNull(AppUpdates.cachedRequirement("invalid"))
        assertNull(AppUpdates.cachedRequirement(null))
        assertEquals(AppInfo.VERSION, AppUpdates.VERSION)
    }
    @Test fun comparesNumericVersions() {
        assertTrue(AppUpdates.isNewer("v6.26.10", "6.26.2"))
        assertFalse(AppUpdates.isNewer("v6.26.2", "6.26.2"))
        assertFalse(AppUpdates.isNewer("v6.26.1", "6.26.2"))
        assertTrue(AppUpdates.isNewer("7.0.0", "6.26.2"))
        assertFalse(AppUpdates.isNewer("6.26.3-beta", "6.26.2"))
        assertFalse(AppUpdates.isNewer("invalid", "6.26.2"))
    }
}

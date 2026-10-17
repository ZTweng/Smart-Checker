package org.samc.core

import kotlin.test.*

class AppUpdatesTest {
    @Test fun comparesNumericVersions() {
        assertTrue(AppUpdates.isNewer("v6.26.10", "6.26.2"))
        assertFalse(AppUpdates.isNewer("v6.26.2", "6.26.2"))
        assertFalse(AppUpdates.isNewer("v6.26.1", "6.26.2"))
        assertTrue(AppUpdates.isNewer("7.0.0", "6.26.2"))
        assertFalse(AppUpdates.isNewer("6.26.3-beta", "6.26.2"))
        assertFalse(AppUpdates.isNewer("invalid", "6.26.2"))
    }
}

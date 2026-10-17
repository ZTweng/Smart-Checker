package org.samc.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SamcCoreTest {
    @Test
    fun filenameAndRatioAreValidated() {
        val result = SamcRules.checkFile(
            "广州大学学生社团.xlsx",
            CellGrid.of(
                "J4" to "广州大学学生社团",
                "F9" to "10",
                "J9" to "5",
                "M9" to "50%"
            ),
            ActivityType.CONVENTIONAL
        )
        assertFalse(result.passed)
        assertTrue(result.failures.any { it.title == "活动名称" })
    }
}

package org.samc.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppInfoTest {
    @Test fun templateNamesKeepOfficialNamingConvention() {
        assertEquals(2, AppInfo.TEMPLATE_NAMES.distinct().size)
        listOf("常规", "非常规").zip(AppInfo.TEMPLATE_NAMES).forEach { (type, name) ->
            assertEquals(
                "广州大学xx学生社团xx学年第x学期${type}活动跟进表-跟进人xx-xx年x月x日（2026.09）.xlsx",
                name
            )
            assertTrue(name.endsWith(".xlsx"))
        }
    }
}

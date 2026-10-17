package org.samc.android

import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.Assert.*
import org.junit.Test
import org.samc.core.ActivityType
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files

class AndroidWorkbookCheckerTest {
    @Test fun writesReadableCellNotesAndPreservesExistingNotes() {
        val bytes = ByteArrayOutputStream()
        XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet("活动")
            sheet.createRow(0).createCell(4).setCellValue("常规活动跟进表")
            sheet.createRow(3).createCell(9).setCellValue("测试学生社团")
            val cell = sheet.createRow(14).createCell(5)
            val comment = sheet.createDrawingPatriarch().createCellComment(workbook.creationHelper.createClientAnchor())
            comment.string = workbook.creationHelper.createRichTextString("原批注")
            comment.author = "原作者"
            cell.cellComment = comment
            workbook.write(bytes)
        }
        val directory = Files.createTempDirectory("smart-checker-test").toFile()
        try {
            val result = AndroidWorkbookChecker.check(ByteArrayInputStream(bytes.toByteArray()),
                "测试学生社团.xlsx", ActivityType.entries.toSet(), directory)
            assertFalse(result.passed)
            assertEquals(ActivityType.CONVENTIONAL, result.activityType)
            assertTrue(result.checkedAt.isNotBlank())
            XSSFWorkbook(result.annotatedFilePath!!).use { workbook ->
                val sheet = workbook.getSheetAt(0)
                val comment = sheet.getCellComment(org.apache.poi.ss.util.CellAddress("F15"))
                assertTrue(comment.string.string.contains("原批注"))
                assertTrue(comment.string.string.contains("学生社团活动负责人学号"))
                assertNotNull(sheet.getCellComment(org.apache.poi.ss.util.CellAddress("K15")))
                assertEquals("测试学生社团", sheet.getRow(3).getCell(9).stringCellValue)
            }
        } finally { directory.deleteRecursively() }
    }

    @Test fun recognizesUnconventionalAndRespectsSelection() {
        val data = ByteArrayOutputStream()
        XSSFWorkbook().use {
            it.createSheet().createRow(0).createCell(4).setCellValue("非常规活动跟进表")
            it.write(data)
        }
        val directory = Files.createTempDirectory("smart-checker-type").toFile()
        try {
            val result = AndroidWorkbookChecker.check(ByteArrayInputStream(data.toByteArray()), "活动.xlsx",
                setOf(ActivityType.CONVENTIONAL), directory)
            assertEquals(ActivityType.UNCONVENTIONAL, result.activityType)
            assertEquals("检查范围", result.failures.single().title)
            assertNull(result.annotatedFilePath)
        } finally { directory.deleteRecursively() }
    }
}

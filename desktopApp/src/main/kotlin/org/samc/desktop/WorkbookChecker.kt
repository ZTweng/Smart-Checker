package org.samc.desktop

import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.Comment
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.samc.core.ActivityType
import org.samc.core.CellGrid
import org.samc.core.CheckReport
import org.samc.core.FileCheckResult
import org.samc.core.SamcRules
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.system.measureTimeMillis

fun checkFolder(root: File, selectedTypes: Set<ActivityType>): CheckReport {
    return checkFolder(root, selectedTypes) { _, _, _ -> }
}

fun checkFolder(
    root: File,
    selectedTypes: Set<ActivityType>,
    onProgress: (Int, Int, String) -> Unit
): CheckReport {
    return checkFiles(
        root.walkTopDown()
            .filter { it.isFile && it.extension.lowercase() == "xlsx" && !it.name.endsWith("_批注版.xlsx") }
            .toList(),
        selectedTypes,
        onProgress
    )
}

fun checkPath(input: File, selectedTypes: Set<ActivityType>, onProgress: (Int, Int, String) -> Unit = { _, _, _ -> }): CheckReport {
    return if (input.isDirectory) {
        checkFolder(input, selectedTypes, onProgress)
    } else {
        checkFiles(listOf(input), selectedTypes, onProgress)
    }
}

fun checkPaths(inputs: List<File>, selectedTypes: Set<ActivityType>, onProgress: (Int, Int, String) -> Unit): CheckReport {
    val files = inputs.flatMap { input ->
        if (input.isDirectory) input.walkTopDown().filter { it.isFile && it.extension.equals("xlsx", true) && !it.name.endsWith("_批注版.xlsx") }.toList()
        else listOf(input)
    }.distinctBy { it.absolutePath }
    return checkFiles(files, selectedTypes, onProgress)
}

private fun checkFiles(files: List<File>, selectedTypes: Set<ActivityType>, onProgress: (Int, Int, String) -> Unit): CheckReport {
    val results = mutableListOf<FileCheckResult>()
    val elapsed = measureTimeMillis {
        files.forEachIndexed { index, file ->
                onProgress(index, files.size, file.name)
                val detectedType = detectTemplateType(readTitle(file))
                val result = if (detectedType != null) {
                    if (detectedType in selectedTypes) {
                        checkWorkbook(file, detectedType)
                    } else {
                        notSelectedResult(file, detectedType)
                    }
                } else {
                    unknownTemplateResult(file)
                }
                results += result.copy(
                    checkedAt = LocalDateTime.now().format(checkTimeFormatter)
                )
                onProgress(index + 1, files.size, file.name)
        }
    }
    return CheckReport(
        checkedFiles = results.size,
        passedFiles = results.count { it.passed },
        failedFiles = results.count { !it.passed },
        results = results,
        elapsedMillis = elapsed
    )
}

private val checkTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

private fun readTitle(file: File): String? = runCatching {
    FileInputStream(file).use { input ->
        XSSFWorkbook(input).use { workbook ->
            workbook.getSheetAt(0).getRow(0)?.getCell(4)?.stringCellValue
        }
    }
}.getOrNull()

private fun unknownTemplateResult(file: File): FileCheckResult =
    FileCheckResult(
        file.name,
        ActivityType.CONVENTIONAL,
        false,
        listOf(
            org.samc.core.CheckFailure(
                "表格类型",
                "无法从 E1 识别常规或非常规活动跟进表，未执行后续检查。",
                listOf("E1")
            )
        )
    )

private fun notSelectedResult(file: File, activityType: ActivityType): FileCheckResult =
    FileCheckResult(
        file.name,
        activityType,
        false,
        listOf(
            org.samc.core.CheckFailure(
                "检查范围",
                "检测为${activityType.label}，但当前未勾选此类型。",
                listOf("E1")
            )
        )
    )

private fun checkWorkbook(file: File, activityType: ActivityType): FileCheckResult {
    FileInputStream(file).use { input ->
        XSSFWorkbook(input).use { workbook ->
            val sheet = workbook.getSheetAt(0)
            val values = buildMap {
                for (row in sheet) {
                    for (cell in row) {
                        put(cell.reference(), cell.textValue())
                    }
                }
            }
            val actualType = detectTemplateType(values["E1"])
            val result = if (actualType != null && actualType != activityType) {
                FileCheckResult(
                    file.name,
                    activityType,
                    false,
                    listOf(
                        org.samc.core.CheckFailure(
                            "表格类型",
                            "文件实际为${actualType.label}，当前按${activityType.label}检查。请调整检查范围。",
                            listOf("E1")
                        )
                    )
                )
            } else {
                SamcRules.checkFile(file.name, CellGrid(values), activityType)
            }
            return if (!result.passed) {
                val output = writeAnnotatedCopy(file, workbook, result)
                result.copy(annotatedFilePath = output.absolutePath)
            } else result
        }
    }
}

private fun detectTemplateType(title: String?): ActivityType? = when {
    title?.contains("非常规活动跟进表") == true -> ActivityType.UNCONVENTIONAL
    title?.contains("常规活动跟进表") == true -> ActivityType.CONVENTIONAL
    else -> null
}

private val ActivityType.label: String
    get() = if (this == ActivityType.CONVENTIONAL) "常规活动" else "非常规活动"

private fun writeAnnotatedCopy(file: File, workbook: XSSFWorkbook, result: FileCheckResult): File {
    val sheet = workbook.getSheetAt(0)
    val red = workbook.createCellStyle().apply {
        fillForegroundColor = IndexedColors.ROSE.index
        fillPattern = FillPatternType.SOLID_FOREGROUND
    }
    result.failures.forEach { failure ->
        val refs = if (failure.cells.isEmpty()) listOf("E1") else failure.cells
        refs.forEach { ref ->
            val cell: Cell = sheet.getRow(ref.rowIndex())?.getCell(ref.columnIndex())
                ?: sheet.getRow(ref.rowIndex())?.createCell(ref.columnIndex())
                ?: return@forEach
            val old = cell.cellComment?.string?.string?.takeIf { it.isNotBlank() }
            val text = listOfNotNull(old, failure.displayText).joinToString("\n")
            val drawing = sheet.createDrawingPatriarch()
            val comment: Comment = drawing.createCellComment(workbook.creationHelper.createClientAnchor())
            comment.string = workbook.creationHelper.createRichTextString(text)
            comment.author = "SAMC CheckForm"
            cell.cellComment = comment
            cell.cellStyle = red
        }
    }
    val output = File(file.parentFile, "${file.nameWithoutExtension}_批注版.xlsx")
    FileOutputStream(output).use(workbook::write)
    return output
}

private fun Cell.reference(): String {
    val col = columnIndex
    var n = col + 1
    val letters = StringBuilder()
    while (n > 0) {
        val remainder = (n - 1) % 26
        letters.append(('A'.code + remainder).toChar())
        n = (n - 1) / 26
    }
    return letters.reverse().toString() + (rowIndex + 1)
}

private fun Cell.textValue(): String? = when (cellType) {
    CellType.STRING -> stringCellValue
    CellType.NUMERIC -> numericCellValue.toString()
    CellType.BOOLEAN -> booleanCellValue.toString()
    CellType.FORMULA -> cachedFormulaResultType?.let {
        when (it) {
            CellType.STRING -> stringCellValue
            CellType.NUMERIC -> numericCellValue.toString()
            else -> toString()
        }
    }
    else -> null
}

private fun String.rowIndex(): Int = substring(lettersEnd()).toInt() - 1

private fun String.columnIndex(): Int {
    var result = 0
    substring(0, lettersEnd()).forEach { result = result * 26 + (it.code - 'A'.code + 1) }
    return result - 1
}

private fun String.lettersEnd(): Int = indexOfFirst { it.isDigit() }

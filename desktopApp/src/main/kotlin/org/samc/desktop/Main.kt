package org.samc.desktop

import java.awt.Desktop
import java.awt.Frame
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.swing.SwingUtilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.swing.JList
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import org.samc.ui.SamcApp
import org.samc.ui.AppActions
import org.samc.core.AppUpdates
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.material3.*

fun main() = application {
    val updateScope = rememberCoroutineScope()
    var checkingUpdates by remember { mutableStateOf(false) }
    var updateMessage by remember { mutableStateOf<String?>(null) }
    var updatePage by remember { mutableStateOf<String?>(null) }
    fun checkUpdates(manual: Boolean) {
        if (checkingUpdates) return
        checkingUpdates = true
        updateScope.launch {
            try {
                val release = withContext(Dispatchers.IO) { AppUpdates.latest() }
                updatePage = release?.page
                if (release != null) updateMessage = "发现新版本 ${release.version}\n\n${release.notes}\n\n打开官方发布页下载并覆盖安装。"
                else if (manual) updateMessage = "当前版本 ${AppUpdates.VERSION}，暂无可用更新。"
            } catch (e: Exception) {
                if (manual) {
                    updatePage = AppUpdates.RELEASES
                    updateMessage = "${e.message ?: "检查更新失败"}\n可直接打开官方发布页查看最新安装包。"
                }
            } finally { checkingUpdates = false }
        }
    }
    LaunchedEffect(Unit) { checkUpdates(false) }
    var lastCheckedInputs: List<File> = emptyList()
    var lastSingleAnnotatedFile: File? = null
    Window(
        onCloseRequest = ::exitApplication,
        title = "Smart Checker",
        icon = painterResource("smart-checker.png"),
        state = rememberWindowState(width = 1180.dp, height = 760.dp)
    ) {
        updateMessage?.let { message ->
            MaterialTheme {
                AlertDialog(
                    onDismissRequest = { updateMessage = null },
                    title = { Text("软件更新") },
                    text = { Text(message) },
                    confirmButton = {
                        TextButton(onClick = {
                            updatePage?.let { Desktop.getDesktop().browse(java.net.URI(it)) }
                            updateMessage = null
                        }) { Text(if (updatePage == null) "确定" else "前往下载") }
                    },
                    dismissButton = { TextButton(onClick = { updateMessage = null }) { Text("关闭") } }
                )
            }
        }
        SamcApp(
            actions = AppActions(
                onCheckUpdates = { checkUpdates(true) },
                onChooseFolder = {
                    chooseInputs().map(File::getAbsolutePath)
                },
                onStartCheck = { inputPaths, types, onProgress ->
                    inputPaths.map(::File).takeIf { it.isNotEmpty() }?.let {
                        withContext(Dispatchers.IO) {
                            checkPaths(it, types) { current, total, file ->
                                SwingUtilities.invokeLater {
                                    onProgress(current, total, file)
                                }
                            }
                        }.also { report ->
                            lastCheckedInputs = it
                            lastSingleAnnotatedFile = report.results.singleOrNull()
                                ?.takeIf { result -> !result.passed }
                                ?.annotatedFilePath?.let(::File)
                            writeRunLog(it.first(), report)
                        }
                    }
                },
                onOpenResults = {
                    val singleResult = lastSingleAnnotatedFile
                    if (singleResult?.isFile == true) {
                        Desktop.getDesktop().open(singleResult)
                    } else {
                        lastCheckedInputs.firstOrNull()?.let(::openAnnotatedResult)
                    }
                },
                onOpenLog = {
                    lastCheckedInputs.firstOrNull()?.let(::openRunLog)
                },
                onClearInputs = {
                    lastCheckedInputs = emptyList()
                    lastSingleAnnotatedFile = null
                }
            )
        )
    }
}

private fun chooseInputs(): List<File> {
    val chooser = JFileChooser().apply {
        dialogTitle = "选择 Excel 表格或文件夹"
        fileSelectionMode = JFileChooser.FILES_AND_DIRECTORIES
        isAcceptAllFileFilterUsed = false
        selectedFiles
        addChoosableFileFilter(FileNameExtensionFilter("Excel 工作簿 (*.xlsx)", "xlsx"))
        setMultiSelectionEnabled(true)
    }
    return if (chooser.showOpenDialog(null as Frame?) == JFileChooser.APPROVE_OPTION) {
        val files = chooser.selectedFiles.toList().ifEmpty { listOfNotNull(chooser.selectedFile) }
        files.filter { it.isDirectory || it.extension.equals("xlsx", true) }
    } else emptyList()
}

private fun openAnnotatedResult(input: File) {
    val result = if (input.isFile) {
        File(input.parentFile, "${input.nameWithoutExtension}_批注版.xlsx")
    } else {
        input.walkTopDown().firstOrNull { it.isFile && it.name.endsWith("_批注版.xlsx") }
    }
    if (result?.isFile == true) {
        if (System.getProperty("os.name").contains("Windows", true)) {
            ProcessBuilder("explorer.exe", "/select,${result.absolutePath}").start()
        } else {
            Desktop.getDesktop().open(result)
        }
    } else if (input.exists()) {
        Desktop.getDesktop().open(input.parentFile ?: input)
    }
}

private fun logFile(input: File): File =
    File(if (input.isDirectory) input else input.parentFile ?: File("."), "Smart Checker-运行日志.txt")

private fun writeRunLog(input: File, report: org.samc.core.CheckReport) {
    val text = buildString {
        appendLine("Smart Checker 运行日志")
        appendLine("时间：${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}")
        appendLine("输入：${input.absolutePath}")
        appendLine("已检查：${report.checkedFiles}")
        appendLine("通过：${report.passedFiles}")
        appendLine("需复核：${report.failedFiles}")
        appendLine("耗时：${report.elapsedMillis} ms")
        report.results.filterNot { it.passed }.forEach { result ->
            appendLine()
            appendLine("[需复核] ${result.fileName}")
            result.failures.forEach { appendLine("- ${it.displayText}") }
        }
    }
    logFile(input).writeText(text, Charsets.UTF_8)
}

private fun openRunLog(input: File) {
    val log = logFile(input)
    if (!log.exists()) writeRunLog(input, org.samc.core.CheckReport(0, 0, 0, emptyList(), 0))
    if (System.getProperty("os.name").contains("Windows", true)) {
        ProcessBuilder("notepad.exe", log.absolutePath).start()
    } else {
        Desktop.getDesktop().open(log)
    }
}

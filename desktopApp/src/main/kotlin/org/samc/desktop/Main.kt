package org.samc.desktop

import java.awt.Desktop
import java.awt.Taskbar
import javax.imageio.ImageIO
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.swing.SwingUtilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.platform.LocalDensity
import org.samc.ui.SamcApp
import org.samc.ui.AppActions
import org.samc.ui.UpdateDialog
import org.samc.core.AppInfo
import kotlinx.coroutines.swing.Swing
import java.util.prefs.Preferences
import org.samc.core.AppUpdates
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.material3.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier

fun main() = application {
    val updateScope = rememberCoroutineScope()
    var checkingUpdates by remember { mutableStateOf(false) }
    var updateMessage by remember { mutableStateOf<String?>(null) }
    var updatePage by remember { mutableStateOf<String?>(null) }
    val preferences = remember { Preferences.userRoot().node("org/samc/checkform") }
    var requiredUpdate by remember {
        mutableStateOf(AppUpdates.cachedRequirement(preferences.get("requiredVersion", null)))
    }
    var downloadError by remember { mutableStateOf<String?>(null) }
    fun checkUpdates(manual: Boolean) {
        if (checkingUpdates) return
        checkingUpdates = true
        updateScope.launch {
            try {
                val release = withContext(Dispatchers.IO) { AppUpdates.latest() }
                requiredUpdate = AppUpdates.requiredRelease(requiredUpdate, release)
                requiredUpdate?.let { required ->
                    withContext(Dispatchers.IO) {
                        preferences.put("requiredVersion", required.version)
                        preferences.flush()
                    }
                }
                updatePage = release?.page
                if (release != null) updateMessage = null
                else if (manual) updateMessage = "当前版本 ${AppUpdates.VERSION}，暂无可用更新。"
            } catch (e: Exception) {
                if (manual) {
                    updatePage = AppUpdates.DOWNLOAD_PAGE
                    updateMessage = "${e.message ?: "检查更新失败"}\n可直接打开软件下载页查看最新安装包。"
                }
            } finally { checkingUpdates = false }
        }
    }
    LaunchedEffect(Unit) { checkUpdates(false) }
    var lastCheckedInputs by remember { mutableStateOf<List<File>>(emptyList()) }
    var lastSingleAnnotatedFile by remember { mutableStateOf<File?>(null) }
    Window(
        onCloseRequest = ::exitApplication,
        title = "Smart Checker",
        icon = painterResource("smart-checker.png"),
        state = rememberWindowState(
            placement = WindowPlacement.Maximized,
            width = 1180.dp,
            height = 760.dp
        )
    ) {
        LaunchedEffect(window) {
            val icon = checkNotNull(Thread.currentThread().contextClassLoader
                .getResourceAsStream("smart-checker.png")).use { ImageIO.read(it) }
            window.iconImages = listOf(icon)
            if (Taskbar.isTaskbarSupported()) {
                val taskbar = Taskbar.getTaskbar()
                if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
                    taskbar.iconImage = icon
                }
            }
        }
        val required = requiredUpdate
        val logoPixels = with(LocalDensity.current) { 112.dp.roundToPx() }
        val internalLogo = remember(logoPixels) {
            val sizes = listOf(112, 140, 168, 196, 224, 280, 336, 448)
            val pixels = sizes.firstOrNull { it >= logoPixels } ?: sizes.last()
            val bitmap = checkNotNull(Thread.currentThread().contextClassLoader
                .getResourceAsStream("samc-logo-$pixels.png")).use(::loadImageBitmap)
            BitmapPainter(bitmap, filterQuality = FilterQuality.High)
        }
        if (required == null) SamcApp(
            logo = internalLogo,
            actions = AppActions(
                onCheckUpdates = { checkUpdates(true) },
                onChooseFolder = {
                    withContext(Dispatchers.Swing) { chooseInputs(window).map(File::getAbsolutePath) }
                },
                onDownloadTemplates = {
                    val destination = withContext(Dispatchers.Swing) {
                        JFileChooser().apply {
                            dialogTitle = "选择模板保存文件夹"
                            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                        }.let { chooser ->
                            if (chooser.showSaveDialog(window) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
                        }
                    }
                    destination?.let { folder ->
                        withContext(Dispatchers.IO) {
                            val target = File(folder, "Smart Checker-模板-${AppInfo.VERSION}")
                            target.mkdirs()
                            AppInfo.TEMPLATE_NAMES.forEach { name ->
                                checkNotNull(Thread.currentThread().contextClassLoader.getResourceAsStream("templates/$name")) {
                                    "安装包缺少模板：$name"
                                }.use { input -> File(target, name).outputStream().use { input.copyTo(it) } }
                            }
                            Desktop.getDesktop().open(target)
                        }
                    }
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
                            withContext(Dispatchers.IO) { writeRunLog(it.first(), report) }
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
        else Surface(modifier = Modifier.fillMaxSize()) {}
        val message = required?.let {
            "发现新版本 ${it.version}（当前 ${AppUpdates.VERSION}）。\n\n${it.notes}\n\n请下载并覆盖安装新版后重新启动。未完成更新无法使用软件。"
        } ?: updateMessage
        message?.let {
            MaterialTheme {
                UpdateDialog(
                    message = it + (downloadError?.let { error -> "\n\n$error" } ?: ""),
                    required = required != null,
                    hasDownload = required != null || updatePage != null,
                    onDownload = {
                        updateScope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    Desktop.getDesktop().browse(java.net.URI(AppUpdates.DOWNLOAD_PAGE))
                                }
                                downloadError = null
                            } catch (e: Exception) {
                                downloadError = "无法打开浏览器，请重试：${e.message}"
                            }
                        }
                    },
                    onDismiss = { updateMessage = null; downloadError = null },
                    onExit = ::exitApplication
                )
            }
        }
    }
}

private fun chooseInputs(owner: java.awt.Window): List<File> {
    val chooser = JFileChooser().apply {
        dialogTitle = "选择 Excel 表格或文件夹"
        fileSelectionMode = JFileChooser.FILES_AND_DIRECTORIES
        isAcceptAllFileFilterUsed = false
        selectedFiles
        addChoosableFileFilter(FileNameExtensionFilter("Excel 工作簿 (*.xlsx)", "xlsx"))
        setMultiSelectionEnabled(true)
    }
    return if (chooser.showOpenDialog(owner) == JFileChooser.APPROVE_OPTION) {
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

package org.samc.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import android.net.Uri
import android.content.Intent
import android.provider.OpenableColumns
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.samc.core.*
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    private val inputs = mutableStateListOf<Uri>()
    private val picker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { files ->
        inputs.addAll(files.filterNot { it in inputs })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var report by remember { mutableStateOf<CheckReport?>(null) }
            var running by remember { mutableStateOf(false) }
            var completed by remember { mutableIntStateOf(0) }
            var current by remember { mutableStateOf("") }
            var error by remember { mutableStateOf<String?>(null) }
            var conventional by remember { mutableStateOf(true) }
            var unconventional by remember { mutableStateOf(true) }
            var manualDark by remember { mutableStateOf<Boolean?>(null) }
            val dark = manualDark ?: isSystemInDarkTheme()
            val scope = rememberCoroutineScope()
            var checkingUpdates by remember { mutableStateOf(false) }
            var updateMessage by remember { mutableStateOf<String?>(null) }
            var updatePage by remember { mutableStateOf<String?>(null) }
            fun checkUpdates(manual: Boolean) {
                if (checkingUpdates) return
                checkingUpdates = true
                scope.launch {
                    try {
                        val release = withContext(Dispatchers.IO) { AppUpdates.latest() }
                        updatePage = release?.page
                        if (release != null) updateMessage = "发现新版本 ${release.version}\n\n${release.notes}\n\n请在官方发布页下载 APK 并覆盖安装。"
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
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                updateMessage?.let { message ->
                    AlertDialog(onDismissRequest = { updateMessage = null },
                        title = { Text("软件更新") }, text = { Text(message) },
                        confirmButton = {
                            TextButton(onClick = {
                                updatePage?.let { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) }
                                updateMessage = null
                            }) { Text(if (updatePage == null) "确定" else "前往下载") }
                        },
                        dismissButton = { TextButton(onClick = { updateMessage = null }) { Text("关闭") } })
                }
                Surface(Modifier.fillMaxSize()) {
                    LazyColumn(
                        Modifier.fillMaxSize().systemBarsPadding().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Text("Smart Checker", style = MaterialTheme.typography.headlineMedium)
                            Text("广州大学学生社团活动跟进表智能检查 · v6.26.2")
                            TextButton(onClick = { checkUpdates(true) }, enabled = !checkingUpdates) {
                                Text(if (checkingUpdates) "正在检查更新…" else "检查更新")
                            }
                            TextButton(onClick = { manualDark = !dark }) { Text(if (dark) "切换浅色" else "切换深色") }
                            TextButton(onClick = { manualDark = null }) { Text("跟随系统") }
                        }
                        item {
                            Row {
                                Checkbox(conventional, { conventional = it }, enabled = !running)
                                Text("常规", Modifier.padding(top = 12.dp))
                                Checkbox(unconventional, { unconventional = it }, enabled = !running)
                                Text("非常规", Modifier.padding(top = 12.dp))
                            }
                            Row {
                                Button(onClick = {
                                    picker.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                                }, enabled = !running) { Text("添加表格") }
                                TextButton(onClick = { inputs.clear(); report = null }, enabled = !running) { Text("清除项目") }
                            }
                            Text("已添加 ${inputs.size} 个文件")
                        }
                        items(inputs.toList()) { uri ->
                            Row {
                                Text(fileName(uri), Modifier.weight(1f))
                                TextButton(onClick = { inputs.remove(uri); report = null }, enabled = !running) { Text("移除") }
                            }
                        }
                        item {
                            Button(
                                enabled = !running && inputs.isNotEmpty() && (conventional || unconventional),
                                onClick = {
                                    val snapshot = inputs.toList()
                                    val types = buildSet {
                                        if (conventional) add(ActivityType.CONVENTIONAL)
                                        if (unconventional) add(ActivityType.UNCONVENTIONAL)
                                    }
                                    scope.launch {
                                        running = true; completed = 0; error = null; report = null
                                        try {
                                            val start = System.currentTimeMillis()
                                            val results = mutableListOf<FileCheckResult>()
                                            val outputDir = File(filesDir, "results").apply { mkdirs() }
                                            for (uri in snapshot) {
                                                current = fileName(uri)
                                                val name = current
                                                val result = withContext(Dispatchers.IO) {
                                                    try {
                                                        require(name.endsWith(".xlsx", true)) { "仅支持 .xlsx 文件" }
                                                        val stream = contentResolver.openInputStream(uri) ?: error("无法读取文件")
                                                        stream.use { AndroidWorkbookChecker.check(it, name, types, outputDir) }
                                                    } catch (e: Exception) {
                                                        FileCheckResult(name, ActivityType.CONVENTIONAL, false,
                                                            listOf(CheckFailure("文件处理", e.message ?: "读取失败")),
                                                            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                                                    }
                                                }
                                                results += result
                                                completed++
                                                report = CheckReport(results.size, results.count { it.passed },
                                                    results.count { !it.passed }, results.toList(), System.currentTimeMillis() - start)
                                            }
                                            report = CheckReport(results.size, results.count { it.passed },
                                                results.count { !it.passed }, results, System.currentTimeMillis() - start)
                                            withContext(Dispatchers.IO) {
                                                File(outputDir, "运行日志.txt").writeText(results.joinToString("\n\n") {
                                                    "${it.fileName}\n${it.checkedAt}\n" + if (it.passed) "通过" else it.failures.joinToString("\n") { f -> f.displayText }
                                                })
                                            }
                                        } catch (e: Exception) { error = e.message ?: "检查失败" }
                                        finally { running = false }
                                    }
                                }
                            ) { Text(if (running) "检查中…" else "开始检查") }
                            if (running) {
                                LinearProgressIndicator(progress = { completed.toFloat() / inputs.size.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth())
                                Text("$completed / ${inputs.size} · $current")
                            }
                            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            if (report != null && !running) TextButton(onClick = {
                                share(File(filesDir, "results/运行日志.txt"), "text/plain")
                            }) { Text("查看/导出运行日志") }
                        }
                        item {
                            Row {
                                Text("检查概览", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                                TextButton(onClick = { report = null }, enabled = !running) { Text("清除结果") }
                            }
                            report?.let { Text("通过 ${it.passedFiles} · 需复核 ${it.failedFiles}") }
                        }
                        items(report?.results ?: emptyList()) { result ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(result.fileName, style = MaterialTheme.typography.titleSmall)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.extraLarge) {
                                            Text(if (result.activityType == ActivityType.CONVENTIONAL) "常规活动" else "非常规活动",
                                                Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
                                        }
                                        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.extraLarge) {
                                            Text(result.checkedAt, Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                    Text(if (result.passed) "通过" else "需复核", color = if (result.passed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                                    result.failures.forEach { Text(it.displayText) }
                                    result.annotatedFilePath?.let { path ->
                                        TextButton(onClick = { share(File(path), "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") }) { Text("打开/导出此批注") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun fileName(uri: Uri): String =
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: "表格.xlsx"

    private fun share(file: File, type: String) {
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            this.type = type
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "打开或保存文件"))
    }
}

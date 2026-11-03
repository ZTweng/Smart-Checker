package org.samc.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import android.net.Uri
import android.content.Intent
import android.provider.OpenableColumns
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.samc.core.*
import org.samc.ui.UpdateDialog
import org.samc.ui.BrandLogo
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.painter.BitmapPainter

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
            val preferences = remember { getSharedPreferences("updates", MODE_PRIVATE) }
            var requiredUpdate by remember {
                mutableStateOf(AppUpdates.cachedRequirement(preferences.getString("requiredVersion", null)))
            }
            var downloadError by remember { mutableStateOf<String?>(null) }
            fun checkUpdates(manual: Boolean) {
                if (checkingUpdates) return
                checkingUpdates = true
                scope.launch {
                    try {
                        val release = withContext(Dispatchers.IO) { AppUpdates.latest() }
                        requiredUpdate = AppUpdates.requiredRelease(requiredUpdate, release)
                        requiredUpdate?.let { preferences.edit().putString("requiredVersion", it.version).apply() }
                        updatePage = release?.page
                        if (release != null) updateMessage = null
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
                val required = requiredUpdate
                val message = required?.let {
                    "发现新版本 ${it.version}（当前 ${AppUpdates.VERSION}）。\n\n${it.notes}\n\n请下载 APK 并覆盖安装新版后重新启动。未完成更新无法使用软件。"
                } ?: updateMessage
                message?.let {
                    UpdateDialog(
                        message = it + (downloadError?.let { error -> "\n\n$error" } ?: ""),
                        required = required != null,
                        hasDownload = required != null || updatePage != null,
                        onDownload = {
                            try {
                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(required?.page ?: updatePage!!)))
                                downloadError = null
                            } catch (e: Exception) {
                                downloadError = "无法打开浏览器，请重试：${e.message}"
                            }
                        },
                        onDismiss = { updateMessage = null; downloadError = null },
                        onExit = { finish() }
                    )
                }
                Surface(Modifier.fillMaxSize()) {
                    if (required == null) LazyColumn(
                        Modifier.fillMaxSize().systemBarsPadding().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // ── 顶部品牌卡片 ──
                        item {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .035f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .08f))
                            ) {
                                Column(Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Image(
                                            painter = painterResource(id = org.samc.android.R.drawable.samc_logo),
                                            contentDescription = "Logo",
                                            modifier = Modifier.size(56.dp)
                                        )
                                        Spacer(Modifier.width(14.dp))
                                        Column {
                                            Text("Smart Checker", style = MaterialTheme.typography.headlineMedium)
                                            Text("广州大学学生社团活动跟进表智能检查 · v${AppInfo.VERSION}",
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f), style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        TextButton(onClick = { checkUpdates(true) }, enabled = !checkingUpdates,
                                            contentPadding = PaddingValues(horizontal = 8.dp)) {
                                            Text(if (checkingUpdates) "检查中…" else "检查更新")
                                        }
                                        TextButton(onClick = { manualDark = !dark }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                                            Text(if (dark) "切换浅色" else "切换深色")
                                        }
                                        TextButton(onClick = { manualDark = null }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                                            Text("跟随系统")
                                        }
                                    }
                                }
                            }
                        }

                        // ── 模板下载卡片 ──
                        item {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .035f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .08f))
                            ) {
                                Column(Modifier.padding(16.dp)) {
                                    Text("模板下载", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Spacer(Modifier.height(10.dp))
                                    OutlinedButton(
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp),
                                        enabled = !running,
                                        shape = RoundedCornerShape(14.dp),
                                        onClick = {
                                            scope.launch {
                                                try {
                                                    val zip = withContext(Dispatchers.IO) {
                                                        val output = File(filesDir, "results/检查表模板-${AppInfo.VERSION}.zip")
                                                        output.parentFile?.mkdirs()
                                                        ZipOutputStream(output.outputStream()).use { archive ->
                                                            AppInfo.TEMPLATE_NAMES.forEach { name ->
                                                                archive.putNextEntry(ZipEntry(name))
                                                                assets.open(name).use { it.copyTo(archive) }
                                                                archive.closeEntry()
                                                            }
                                                        }
                                                        output
                                                    }
                                                    share(zip, "application/zip")
                                                } catch (e: Exception) { error = e.message ?: "导出模板失败" }
                                            }
                                        }
                                    ) { Text("下载检查表模板") }
                                }
                            }
                        }

                        // ── 主操作卡片：表格文件 + 检查范围 + 检查操作 ──
                        item {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .035f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .08f))
                            ) {
                                Column(Modifier.padding(16.dp)) {
                                    Text("表格文件", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Spacer(Modifier.height(10.dp))
                                    Text("选择 Excel 文件，系统会根据表格标题自动识别常规或非常规活动。",
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f), fontSize = 12.sp, lineHeight = 18.sp)
                                    Spacer(Modifier.height(8.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("已添加 ${inputs.size} 个文件", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                                        TextButton(onClick = { inputs.clear(); report = null }, enabled = !running,
                                            contentPadding = PaddingValues(0.dp)) { Text("清除", fontSize = 11.sp) }
                                    }
                                    inputs.forEach { uri ->
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(fileName(uri), Modifier.weight(1f), fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f), maxLines = 1)
                                            TextButton(onClick = { inputs.remove(uri); report = null }, enabled = !running,
                                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                                                Text("移除", fontSize = 11.sp)
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(16.dp))
                                    Text("检查范围", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Spacer(Modifier.height(8.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(conventional, { conventional = it }, enabled = !running)
                                        Text("常规活动表")
                                        Spacer(Modifier.width(12.dp))
                                        Checkbox(unconventional, { unconventional = it }, enabled = !running)
                                        Text("非常规活动表")
                                    }
                                    Spacer(Modifier.height(16.dp))
                                    Text("检查操作", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Spacer(Modifier.height(10.dp))
                                    Button(
                                        onClick = {
                                            picker.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                                        },
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp),
                                        enabled = !running,
                                        shape = RoundedCornerShape(14.dp)
                                    ) { Text("添加表格") }
                                    Spacer(Modifier.height(9.dp))
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
                                        },
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp),
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                    ) { Text(if (running) "检查中…" else "开始检查") }
                                    if (running) {
                                        Spacer(Modifier.height(10.dp))
                                        LinearProgressIndicator(progress = { completed.toFloat() / inputs.size.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth())
                                        Text("$completed / ${inputs.size} · $current", fontSize = 11.sp)
                                    }
                                    error?.let {
                                        Spacer(Modifier.height(6.dp))
                                        Text(it, color = Color(0xFFE06C47), fontSize = 11.sp)
                                    }
                                    if (report != null && !running) {
                                        TextButton(onClick = {
                                            share(File(filesDir, "results/运行日志.txt"), "text/plain")
                                        }) { Text("查看/导出运行日志") }
                                    }
                                }
                            }
                        }

                        // ── 检查概览 ──
                        item {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("检查概览", style = MaterialTheme.typography.titleLarge)
                                TextButton(onClick = { report = null }, enabled = !running) { Text("清除结果") }
                            }
                            report?.let {
                                Text("检查完成 · 通过 ${it.passedFiles} · 需复核 ${it.failedFiles}",
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f), fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 4.dp))
                            }
                        }

                        // ── 结果列表 ──
                        items(report?.results ?: emptyList()) { result ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .045f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .08f))
                            ) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(result.fileName, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                        Text(
                                            if (result.passed) "通过" else "需复核",
                                            fontWeight = FontWeight.Bold,
                                            color = if (result.passed) Color(0xFF1E9E70) else Color(0xFFE06C47)
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                        val pillDark = MaterialTheme.colorScheme.background.red < 0.2f
                                        PillChip(
                                            if (result.activityType == ActivityType.CONVENTIONAL) "常规活动" else "非常规活动",
                                            pillDark
                                        )
                                        if (result.checkedAt.isNotBlank()) PillChip(result.checkedAt, pillDark)
                                    }
                                    if (!result.passed) {
                                        result.failures.forEach { Text("• ${it.displayText}", color = Color(0xFFE06C47), fontSize = 12.sp) }
                                    } else {
                                        Text("检查通过，未发现需要复核的项目", fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f))
                                    }
                                    result.annotatedFilePath?.let { path ->
                                        TextButton(onClick = { share(File(path), "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") }) {
                                            Text("打开/导出此批注")
                                        }
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

@Composable
private fun PillChip(text: String, isDark: Boolean) {
    Surface(
        modifier = Modifier.height(22.dp),
        shape = RoundedCornerShape(50),
        color = if (isDark) Color(0xFF303A57) else Color(0xFFE2DDFB),
        border = BorderStroke(
            1.dp,
            if (isDark) MaterialTheme.colorScheme.onSurface.copy(alpha = .12f)
            else Color(0xFFC5BBEE)
        )
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 9.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text,
                color = if (isDark) Color(0xFFD6CCFF) else Color(0xFF3A2D8F),
                fontSize = 11.sp,
                lineHeight = 14.sp,
                maxLines = 1,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

package org.samc.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.samc.core.CheckReport
import org.samc.core.FileCheckResult
import org.samc.core.ActivityType
import kotlinx.coroutines.launch

data class AppActions(
    val onChooseFolder: () -> List<String> = { emptyList() },
    val onStartCheck: suspend (List<String>, Set<ActivityType>, (Int, Int, String) -> Unit) -> CheckReport? = { _, _, _ -> null },
    val onOpenResults: () -> Unit = {},
    val onOpenLog: () -> Unit = {},
    val onClearInputs: () -> Unit = {},
    val onCheckUpdates: () -> Unit = {}
)

@Composable
fun SamcApp(actions: AppActions = AppActions()) {
    var report by remember { mutableStateOf<CheckReport?>(null) }
    var selectedInputs by remember { mutableStateOf<List<String>>(emptyList()) }
    var running by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var progressLabel by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var conventionalEnabled by remember { mutableStateOf(true) }
    var unconventionalEnabled by remember { mutableStateOf(true) }
    var manualDark by remember { mutableStateOf<Boolean?>(null) }
    val systemDark = isSystemInDarkTheme()
    val scope = rememberCoroutineScope()
    val dark = manualDark ?: systemDark
    val colors = if (dark) androidx.compose.material3.darkColorScheme(
        primary = Color(0xFFC9B8FF),
        secondary = Color(0xFF86D9FF),
        background = Color(0xFF0B1020),
        surface = Color(0xFF1A2338),
        onSurface = Color(0xFFF4F7FF),
        onBackground = Color(0xFFF4F7FF)
    ) else androidx.compose.material3.lightColorScheme(
        primary = Color(0xFF5946D8),
        secondary = Color(0xFF168EAF),
        background = Color(0xFFE9ECF6),
        surface = Color(0xFFF7F8FD),
        onSurface = Color(0xFF172033),
        onBackground = Color(0xFF172033)
    )

    MaterialTheme(colorScheme = colors) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.background,
                            if (dark) Color(0xFF111A2D) else Color(0xFFEDE9FE),
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
                .padding(22.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Header(
                    isDark = dark,
                    isFollowingSystem = manualDark == null,
                    onToggleTheme = { manualDark = !dark },
                    onFollowSystem = { manualDark = null },
                    onCheckUpdates = actions.onCheckUpdates
                )
                Spacer(Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Sidebar(
                        modifier = Modifier.width(250.dp),
                        selectedInputs = selectedInputs,
                        running = running,
                        progress = progress,
                        progressLabel = progressLabel,
                        errorMessage = errorMessage,
                        onChooseFolder = {
                            selectedInputs = (selectedInputs + actions.onChooseFolder()).distinct()
                            errorMessage = null
                        },
                        onClearInputs = {
                            actions.onClearInputs()
                            selectedInputs = emptyList()
                            report = null
                            progress = 0f
                            progressLabel = ""
                            errorMessage = null
                        },
                        conventionalEnabled = conventionalEnabled,
                        unconventionalEnabled = unconventionalEnabled,
                        onConventionalChanged = { conventionalEnabled = it },
                        onUnconventionalChanged = { unconventionalEnabled = it },
                        onStartCheck = {
                            val types = buildSet {
                                if (conventionalEnabled) add(ActivityType.CONVENTIONAL)
                                if (unconventionalEnabled) add(ActivityType.UNCONVENTIONAL)
                            }
                            if (types.isEmpty()) {
                                errorMessage = "请至少勾选一种检查类型。"
                            } else if (selectedInputs.isEmpty()) {
                                errorMessage = "请先选择 Excel 文件或文件夹。"
                            } else {
                                val inputsToCheck = selectedInputs.toList()
                                scope.launch {
                                    running = true
                                    report = null
                                    progress = 0f
                                    progressLabel = "准备检查…"
                                    errorMessage = null
                                    runCatching {
                                        actions.onStartCheck(inputsToCheck, types) { current, total, file ->
                                            progress = if (total == 0) 0f else current.toFloat() / total
                                            progressLabel = "正在检查 $current / $total：$file"
                                        }
                                    }
                                        .onSuccess { result -> report = result ?: report }
                                        .onFailure { errorMessage = it.message ?: "检查失败" }
                                    running = false
                                    progressLabel = if (report != null) "检查完成" else ""
                                }
                            }
                        },
                        onOpenResults = actions.onOpenResults,
                        onOpenLog = actions.onOpenLog
                    )
                    Dashboard(
                        modifier = Modifier.weight(1f),
                        report = report,
                        onDemoReport = { report = demoReport() },
                        canClear = !running,
                        onClearReport = {
                            report = null
                            progress = 0f
                            progressLabel = ""
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(
    isDark: Boolean,
    isFollowingSystem: Boolean,
    onToggleTheme: () -> Unit,
    onFollowSystem: () -> Unit,
    onCheckUpdates: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                "广州大学团委学生社团管理中心活动跟进表智能检查",
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "SAMC Activity Follow-up Form Smart Checker",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f),
                fontSize = 13.sp
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onCheckUpdates) { Text("检查更新") }
            OutlinedButton(onClick = onToggleTheme) {
                Text(if (isDark) "切换浅色" else "切换深色")
            }
            TextButton(onClick = onFollowSystem) {
                Text(if (isFollowingSystem) "跟随系统中" else "跟随系统")
            }
        }
    }
}

@Composable
private fun Sidebar(
    modifier: Modifier,
    selectedInputs: List<String>,
    running: Boolean,
    progress: Float,
    progressLabel: String,
    errorMessage: String?,
    onChooseFolder: () -> Unit,
    onClearInputs: () -> Unit,
    conventionalEnabled: Boolean,
    unconventionalEnabled: Boolean,
    onConventionalChanged: (Boolean) -> Unit,
    onUnconventionalChanged: (Boolean) -> Unit,
    onStartCheck: () -> Unit,
    onOpenResults: () -> Unit,
    onOpenLog: () -> Unit
) {
    GlassCard(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("工作区", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            "选择单个 Excel 文件或文件夹，系统会根据表格标题自动识别常规或非常规活动。",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f),
            fontSize = 12.sp,
            lineHeight = 18.sp
        )
        Box(Modifier.fillMaxWidth().height(116.dp)) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "已添加 ${selectedInputs.size} 个项目",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 11.sp
                )
                TextButton(
                    onClick = onClearInputs,
                    enabled = !running,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                ) {
                    Text("清除", fontSize = 11.sp)
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                selectedInputs.forEach {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f),
                        fontSize = 10.sp,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
            }
        }
        }
        errorMessage?.let {
            Text(
                it,
                color = Color(0xFFE06C47),
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        Spacer(Modifier.height(14.dp))
        Text("检查范围", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        CheckRow("常规活动表", conventionalEnabled, onConventionalChanged)
        CheckRow("非常规活动表", unconventionalEnabled, onUnconventionalChanged)
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = onChooseFolder,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) { Text("选择表格或文件夹") }
        Spacer(Modifier.height(9.dp))
        Button(
            onClick = onStartCheck,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary
            )
        ) { Text(if (running) "检查中…" else "开始检查") }
        if (running || progressLabel.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            androidx.compose.material3.LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth()
            )
            Text(progressLabel, fontSize = 10.sp, maxLines = 2)
        }
        Spacer(Modifier.height(20.dp))
        Text("快捷入口", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        TextButton(onClick = onOpenResults) { Text("打开批注结果") }
        TextButton(onClick = onOpenLog) { Text("查看运行日志") }
        Spacer(Modifier.height(16.dp))
        Text(
            "本地处理 · 不上传表格\n版本 v6.26.2",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f),
            fontSize = 11.sp
        )
        }
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.material3.Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
    }
}

@Composable
private fun Dashboard(
    modifier: Modifier,
    report: CheckReport?,
    onDemoReport: () -> Unit,
    canClear: Boolean,
    onClearReport: () -> Unit
) {
    Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
            MetricCard("已检查", report?.checkedFiles?.toString() ?: "0", modifier = Modifier.weight(1f))
            MetricCard("通过", report?.passedFiles?.toString() ?: "0", Color(0xFF1E9E70), Modifier.weight(1f))
            MetricCard("需复核", report?.failedFiles?.toString() ?: "0", Color(0xFFE06C47), Modifier.weight(1f))
        }
        GlassCard(Modifier.fillMaxWidth().weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("检查概览", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                    Text(
                        if (report == null) "准备好后开始检查，系统会先识别表格类型，再执行对应规则。"
                        else "检查完成 · 通过 ${report.passedFiles} · 需复核 ${report.failedFiles}",
                        color = if (report == null) {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = .62f)
                        } else if (report.failedFiles == 0) {
                            Color(0xFF159A70)
                        } else {
                            Color(0xFFE06C47)
                        },
                        fontSize = 12.sp
                    )
                }
                if (report == null) {
                    TextButton(onClick = onDemoReport) { Text("预览界面") }
                } else {
                    TextButton(onClick = onClearReport, enabled = canClear) { Text("清除结果") }
                }
            }
            Spacer(Modifier.height(16.dp))
            if (report == null) {
                EmptyState()
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(report.results) { ResultRow(it) }
                }
            }
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, tint: Color = MaterialTheme.colorScheme.primary, modifier: Modifier) {
    GlassCard(modifier) {
        Text(title, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .68f), fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        Text(value, color = tint, fontWeight = FontWeight.Bold, fontSize = 28.sp)
    }
}

@Composable
private fun ResultRow(result: FileCheckResult) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .045f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .08f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(result.fileName, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                Row(
                    modifier = Modifier.padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ActivityPill(
                        text = if (result.activityType == ActivityType.CONVENTIONAL) "常规活动" else "非常规活动",
                        isDark = MaterialTheme.colorScheme.background.red < 0.2f
                    )
                    if (result.checkedAt.isNotBlank()) {
                        ActivityPill(
                            text = "检查时间 · ${result.checkedAt}",
                            isDark = MaterialTheme.colorScheme.background.red < 0.2f
                        )
                    }
                }
                if (!result.passed) {
                    result.failures.forEach { failure ->
                        Text(
                            "• ${failure.displayText}",
                            color = Color(0xFFE06C47),
                            fontSize = 12.sp
                        )
                    }
                } else {
                    Text("检查通过，未发现需要复核的项目", fontSize = 11.sp)
                }
            }
            Text(
                if (result.passed) "通过" else "需复核",
                color = if (result.passed) Color(0xFF1E9E70) else Color(0xFFE06C47),
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ActivityPill(text: String, isDark: Boolean) {
    Surface(
        modifier = Modifier.height(22.dp),
        shape = RoundedCornerShape(50),
        color = if (isDark) Color(0xFF303A57) else Color(0xFFE8EAF6),
        tonalElevation = 0.dp
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 9.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text,
                color = if (isDark) Color(0xFFC9B8FF) else Color(0xFF5146A8),
                fontSize = 11.sp,
                lineHeight = 14.sp,
                maxLines = 1,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("⌁", fontSize = 48.sp, color = MaterialTheme.colorScheme.primary.copy(alpha = .7f))
        Spacer(Modifier.height(8.dp))
        Text("还没有检查记录", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
        Text(
            "选择表格或文件夹后开始检查",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f),
            fontSize = 13.sp
        )
    }
}

@Composable
private fun GlassCard(modifier: Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .08f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(18.dp)) {
            content()
        }
    }
}

private fun demoReport() = CheckReport(
    checkedFiles = 3,
    passedFiles = 2,
    failedFiles = 1,
    results = listOf(
        FileCheckResult("模拟社团活动跟进表.xlsx", org.samc.core.ActivityType.CONVENTIONAL, true),
        FileCheckResult(
            "学生社团活动跟进表.xlsx",
            org.samc.core.ActivityType.UNCONVENTIONAL,
            false,
            listOf(org.samc.core.CheckFailure("会员参与比例", "应为 0.50，实际为 0.35", listOf("M9")))
        ),
        FileCheckResult("青年志愿者协会.xlsx", org.samc.core.ActivityType.CONVENTIONAL, true)
    )
)

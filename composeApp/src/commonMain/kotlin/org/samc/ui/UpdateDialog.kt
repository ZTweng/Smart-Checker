package org.samc.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

@Composable
fun UpdateDialog(
    message: String,
    required: Boolean,
    hasDownload: Boolean,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
    onExit: () -> Unit
) {
    val transition = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }
    fun dismiss() {
        if (required || closing) return
        closing = true
        scope.launch {
            transition.animateTo(0f, tween(140))
            onDismiss()
        }
    }
    LaunchedEffect(Unit) {
        transition.animateTo(1f, tween(200, easing = FastOutSlowInEasing))
    }
    Dialog(
        onDismissRequest = { dismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !required,
            dismissOnClickOutside = !required
        )
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth().graphicsLayer {
                alpha = transition.value
                scaleX = .96f + .04f * transition.value
                scaleY = scaleX
            },
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text(if (required) "请更新后继续使用" else "软件更新", style = MaterialTheme.typography.titleLarge)
                Text(message, Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { if (required) onExit() else dismiss() }) {
                        Text(if (required) "退出软件" else "关闭")
                    }
                    Button(onClick = {
                        if (hasDownload) onDownload() else dismiss()
                    }, enabled = !closing) {
                        Text(if (hasDownload) "前往下载" else "确定")
                    }
                }
            }
        }
    }
}

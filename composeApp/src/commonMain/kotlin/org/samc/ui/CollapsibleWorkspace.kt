package org.samc.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp

@Composable
internal fun WorkspaceToggle(expanded: Boolean, onClick: () -> Unit) {
    val tint = MaterialTheme.colorScheme.primary
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(44.dp).semantics {
            contentDescription = if (expanded) "收起工作区" else "展开工作区"
        }
    ) {
        Canvas(Modifier.size(22.dp)) {
            drawRoundRect(tint, cornerRadius = CornerRadius(4.dp.toPx()), style = Stroke(1.5.dp.toPx()))
            drawLine(tint, Offset(size.width * .35f, 0f), Offset(size.width * .35f, size.height), 1.5.dp.toPx())
            if (!expanded) {
                drawRoundRect(
                    tint.copy(alpha = .25f), Offset(2.dp.toPx(), 2.dp.toPx()),
                    Size(size.width * .35f - 3.dp.toPx(), size.height - 4.dp.toPx()),
                    CornerRadius(2.dp.toPx())
                )
            }
        }
    }
}

@Composable
internal fun CollapsibleWorkspace(
    expanded: Boolean,
    onExpand: () -> Unit,
    content: @Composable () -> Unit
) {
    val reveal by animateFloatAsState(
        if (expanded) 1f else 0f,
        tween(280, easing = FastOutSlowInEasing)
    )
    // Keep the panel and its scroll state mounted at a fixed width throughout the animation.
    Layout(
        modifier = Modifier.fillMaxHeight().clipToBounds(),
        content = {
            Box(Modifier.graphicsLayer {
                alpha = reveal
                translationX = -(1f - reveal) * 320.dp.toPx()
            }) { content() }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp,
                modifier = Modifier.graphicsLayer {
                    alpha = 1f - reveal
                    scaleX = .85f + .15f * (1f - reveal)
                    scaleY = scaleX
                }
            ) {
                if (!expanded) WorkspaceToggle(false, onExpand)
            }
        }
    ) { measurables, constraints ->
        val panelWidth = 320.dp.roundToPx()
        val collapsedWidth = 52.dp.roundToPx()
        val panel = measurables[0].measure(Constraints.fixed(panelWidth, constraints.maxHeight))
        val floating = measurables[1].measure(Constraints.fixed(44.dp.roundToPx(), 44.dp.roundToPx()))
        val width = (collapsedWidth + (panelWidth - collapsedWidth) * reveal).toInt()
        layout(width, constraints.maxHeight) {
            if (reveal > 0f) panel.placeRelative(0, 0)
            if (!expanded) floating.placeRelative(4.dp.roundToPx(), 4.dp.roundToPx())
        }
    }
}

package com.glavakartela.iconrecolor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

@Composable
internal fun ColorDialog(title: String, initial: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val hsv = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial, it) } }
    var h by remember { mutableFloatStateOf(hsv[0]) }
    var s by remember { mutableFloatStateOf(hsv[1]) }
    var v by remember { mutableFloatStateOf(hsv[2]) }
    var hex by remember { mutableStateOf(hexOf(initial)) }
    fun current() = android.graphics.Color.HSVToColor(floatArrayOf(h, s, v))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(current())),
                )
                Slider(
                    value = h,
                    onValueChange = { h = it; hex = hexOf(current()) },
                    valueRange = 0f..360f,
                )
                Slider(value = s, onValueChange = { s = it; hex = hexOf(current()) })
                Slider(value = v, onValueChange = { v = it; hex = hexOf(current()) })
                OutlinedTextField(
                    value = hex,
                    onValueChange = { t ->
                        hex = t
                        parseHex(t)?.let { c ->
                            android.graphics.Color.colorToHSV(c, hsv)
                            h = hsv[0]
                            s = hsv[1]
                            v = hsv[2]
                        }
                    },
                    label = { Text("HEX") },
                    singleLine = true,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onPick(current()) }) { Text("Done") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Draw a custom shape: the stroke becomes a 24-point shape that can be shared as a code. */
@Composable
internal fun DrawShapeDialog(style: IconStyle, onSave: (List<Int>) -> Unit, onDismiss: () -> Unit) {
    var stroke by remember { mutableStateOf(listOf<Offset>()) }
    val profile = remember(stroke) { IconShapes.fromStroke(stroke.map { it.x to it.y }) }
    val fill = Color(style.bg)
    val line = MaterialTheme.colorScheme.primary
    val guide = MaterialTheme.colorScheme.outline

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Draw a shape") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Draw a closed line around the center with your finger. The shape will be smoothed and fitted to " +
                        "the icon square (the frame on the canvas).",
                    style = MaterialTheme.typography.bodySmall,
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { o -> stroke = listOf(o) },
                                onDrag = { change, _ ->
                                    stroke = stroke + change.position
                                    change.consume()
                                },
                            )
                        },
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val lim = size.minDimension / 2f * 0.92f

                        drawRect(
                            color = guide,
                            topLeft = Offset(cx - lim, cy - lim),
                            size = Size(lim * 2f, lim * 2f),
                            style = Stroke(width = 1.dp.toPx()),
                        )
                        if (stroke.size > 1) {
                            val p = Path()
                            p.moveTo(stroke[0].x, stroke[0].y)
                            for (i in 1 until stroke.size) p.lineTo(stroke[i].x, stroke[i].y)
                            drawPath(p, color = line.copy(alpha = 0.55f), style = Stroke(width = 2.dp.toPx()))
                        }
                        profile?.let { q ->
                            val pts = IconShapes.outline(q)
                            val path = Path()
                            for (k in 0 until pts.size / 2) {
                                val x = cx + pts[2 * k] * lim
                                val y = cy + pts[2 * k + 1] * lim
                                if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            }
                            path.close()
                            drawPath(path, color = fill.copy(alpha = 0.85f))
                            drawPath(path, color = line, style = Stroke(width = 2.dp.toPx()))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = profile != null, onClick = { profile?.let(onSave) }) { Text("Save") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { stroke = emptyList() }) { Text("Clear") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
internal fun ImportShapeDialog(onImport: (List<Int>) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val q = remember(text) { ShapeCodec.decode(text) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Shape code") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("icrec-…") },
                isError = text.isNotBlank() && q == null,
                supportingText = {
                    Text(
                        when {
                            text.isBlank() -> "Paste a code someone shared with you"
                            q == null -> "Code not recognized or corrupted"
                            else -> "Code valid"
                        },
                    )
                },
            )
        },
        confirmButton = {
            TextButton(enabled = q != null, onClick = { q?.let(onImport) }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun AppMethodDialog(
    app: AppEntry,
    current: Method,
    onSelect: (Method) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(app.label) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "How to recolor this icon. If the result looks off, try another method. " +
                        "Pinned shortcuts are refreshed with the “Update pinned” button.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                listOf(
                    Method.AUTO to "Auto",
                    Method.GLYPH to "Glyph: a simple glyph on a background",
                    Method.GRADIENT to "Gradient: keeps details, good for games",
                ).forEach { (m, name) ->
                    FilterChip(
                        selected = current == m,
                        onClick = { onSelect(m) },
                        label = { Text(name) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
internal fun SettingsDialog(
    themeMode: Int,
    onTheme: (Int) -> Unit,
    colorUi: Boolean,
    onColorUi: (Boolean) -> Unit,
    onResetMethods: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Theme", style = MaterialTheme.typography.titleSmall)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(0 to "System default", 1 to "Light", 2 to "Dark").forEach { (m, name) ->
                        FilterChip(
                            selected = themeMode == m,
                            onClick = { onTheme(m) },
                            label = { Text(name) },
                        )
                    }
                }
                SwitchRow("Wallpaper color in the UI", "Turn off for a gray UI", colorUi, onColorUi)
                OutlinedButton(onClick = onResetMethods, modifier = Modifier.fillMaxWidth()) {
                    Text("Reset recoloring methods")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

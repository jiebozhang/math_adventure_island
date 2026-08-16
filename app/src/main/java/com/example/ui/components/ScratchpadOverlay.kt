package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

data class PathPoint(val path: Path, val color: Color = Color.Black, val strokeWidth: Float = 6f)

@Composable
fun ScratchpadDialog(
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        var paths by remember { mutableStateOf(listOf<PathPoint>()) }
        var currentPath by remember { mutableStateOf<Path?>(null) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White.copy(alpha = 0.75f))
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val p = Path().apply { moveTo(offset.x, offset.y) }
                                currentPath = p
                            },
                            onDrag = { change, _ ->
                                currentPath?.lineTo(change.position.x, change.position.y)
                                change.consume()
                                // Force recomposition
                                paths = paths.toList()
                            },
                            onDragEnd = {
                                currentPath?.let {
                                    paths = paths + PathPoint(it)
                                }
                                currentPath = null
                            }
                        )
                    }
            ) {
                paths.forEach { pathPoint ->
                    drawPath(
                        path = pathPoint.path,
                        color = pathPoint.color,
                        style = Stroke(
                            width = pathPoint.strokeWidth,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
                currentPath?.let {
                    drawPath(
                        path = it,
                        color = Color.Black,
                        style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }
            }

            // Top Action Bar
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            paths = emptyList()
                            currentPath = null
                        }
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "清除")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("清除画板")
                    }

                    Button(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("关闭草稿纸")
                    }
                }
            }
        }
    }
}

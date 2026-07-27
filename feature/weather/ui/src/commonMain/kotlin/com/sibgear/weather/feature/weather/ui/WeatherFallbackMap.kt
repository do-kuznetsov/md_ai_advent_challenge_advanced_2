package com.sibgear.weather.feature.weather.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

@Composable
internal fun WeatherFallbackMap(
    onPointSelected: (WeatherMapPoint) -> Unit,
    modifier: Modifier = Modifier,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFDCEFF7))
            .pointerInput(Unit) {
                detectTapGestures { tapOffset ->
                    onPointSelected(tapOffset.toMapPoint(size))
                }
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    offset += pan
                    scale = (scale * zoom).coerceIn(MIN_MAP_SCALE, MAX_MAP_SCALE)
                }
            },
    ) {
        withTransform(
            transformBlock = {
                translate(left = offset.x, top = offset.y)
                scale(scaleX = scale, scaleY = scale, pivot = center)
            },
        ) {
            drawRect(color = Color(0xFFDCEFF7))
            drawMapGrid()
            drawLandMasses()
        }
    }
}

private fun Offset.toMapPoint(size: IntSize): WeatherMapPoint {
    val latitude = (90.0 - (y / size.height.coerceAtLeast(1)) * 180.0)
        .coerceIn(MIN_LATITUDE, MAX_LATITUDE)
    val longitude = ((x / size.width.coerceAtLeast(1)) * 360.0 - 180.0)
        .coerceIn(MIN_LONGITUDE, MAX_LONGITUDE)

    return WeatherMapPoint(latitude = latitude, longitude = longitude)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMapGrid() {
    val stroke = Stroke(width = 1.dp.toPx())
    val gridColor = Color(0xFF8AB6C8)

    repeat(GRID_LINES + 1) { index ->
        val x = size.width * index / GRID_LINES
        val y = size.height * index / GRID_LINES
        drawLine(
            color = gridColor,
            start = Offset(x = x, y = 0f),
            end = Offset(x = x, y = size.height),
            strokeWidth = stroke.width,
        )
        drawLine(
            color = gridColor,
            start = Offset(x = 0f, y = y),
            end = Offset(x = size.width, y = y),
            strokeWidth = stroke.width,
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLandMasses() {
    val landColor = Color(0xFF77B885)
    val borderColor = Color(0xFF3F7E55)

    drawOval(
        color = landColor,
        topLeft = Offset(x = size.width * 0.10f, y = size.height * 0.20f),
        size = Size(width = size.width * 0.28f, height = size.height * 0.24f),
    )
    drawOval(
        color = landColor,
        topLeft = Offset(x = size.width * 0.24f, y = size.height * 0.47f),
        size = Size(width = size.width * 0.18f, height = size.height * 0.28f),
    )
    drawOval(
        color = landColor,
        topLeft = Offset(x = size.width * 0.48f, y = size.height * 0.19f),
        size = Size(width = size.width * 0.36f, height = size.height * 0.22f),
    )
    drawOval(
        color = landColor,
        topLeft = Offset(x = size.width * 0.56f, y = size.height * 0.42f),
        size = Size(width = size.width * 0.22f, height = size.height * 0.26f),
    )
    drawOval(
        color = landColor,
        topLeft = Offset(x = size.width * 0.74f, y = size.height * 0.62f),
        size = Size(width = size.width * 0.18f, height = size.height * 0.12f),
    )
    drawRect(
        color = borderColor,
        style = Stroke(width = 2.dp.toPx()),
    )
}

private const val GRID_LINES: Int = 6
private const val MIN_MAP_SCALE: Float = 0.8f
private const val MAX_MAP_SCALE: Float = 4f
private const val MIN_LATITUDE: Double = -90.0
private const val MAX_LATITUDE: Double = 90.0
private const val MIN_LONGITUDE: Double = -180.0
private const val MAX_LONGITUDE: Double = 180.0

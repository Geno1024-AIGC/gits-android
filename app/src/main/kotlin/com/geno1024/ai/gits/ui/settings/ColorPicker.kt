package com.geno1024.ai.gits.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.geno1024.ai.gits.R

/** Colours the swatch row offers before anyone reaches for the sliders. */
private val presetColors = listOf(
    0xFFFB8C00L, 0xFFF44A6AL, 0xFFEC407AL, 0xFFE91E63L,
    0xFFAB47BFL, 0xFF7E57C2L, 0xFF5C6BC0L, 0xFF42A5F5L,
    0xFF29B6F6L, 0xFF26C6DAL, 0xFF26A69AL, 0xFF66BB6AL,
    0xFF9CCC65L, 0xFFD4E157L, 0xFFFFEE58L, 0xFFFFCA28L,
    0xFFFF7043L, 0xFF8D6E63L, 0xFF78909CL, 0xFF37474FL,
)

/**
 * What one slot of the custom scheme paints while nobody has set it.
 *
 * The same plain answer the custom scheme falls back to, spelled out here so the
 * picker can show what the swatch would be rather than a colour nobody chose.
 */
fun customColorDefault(slot: String, dark: Boolean): Long = when (slot) {
    "primary" -> if (dark) 0xFF7AA2F7L else 0xFF1F6FEBL
    "background" -> if (dark) 0xFF0D1117L else 0xFFFFFFFFL
    "surface" -> if (dark) 0xFF161B22L else 0xFFF6F8FAL
    "onBackground" -> if (dark) 0xFFC9D1D9L else 0xFF1F2328L
    "onSurface" -> if (dark) 0xFFC9D1D9L else 0xFF1F2328L
    "surfaceVariant" -> if (dark) 0xFF21262DL else 0xFFE8ECEFL
    "onSurfaceVariant" -> if (dark) 0xFF8B949EL else 0xFF636C76L
    else -> if (dark) 0xFFFFFFFFL else 0xFF000000L
}

/**
 * Takes one colour back from the person setting it.
 *
 * Offered as a handful of swatches, a hex field the way colours are written down, and
 * sliders for the rest, because nobody picks a shade by remembering its number and
 * nobody mixes one on a phone with a wheel alone. The default swatch takes the colour
 * back out, so a slot can be returned to what it was before it was touched.
 */
@Composable
fun ColorPickerDialog(
    title: String,
    initial: Long,
    defaultColor: Long = -1L,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialColor = Color(if (initial >= 0) initial.toInt() else android.graphics.Color.WHITE)
    var currentColor by remember { mutableStateOf(initialColor) }
    var rgbSpace by remember { mutableStateOf(true) }
    var hex by remember { mutableStateOf("#%06X".format(initialColor.toArgb() and 0xFFFFFF)) }

    fun applyColor(c: Color) {
        val argb = c.toArgb() or (0xFF shl 24)
        currentColor = Color(argb)
        hex = "#%06X".format(argb and 0xFFFFFF)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.color_presets),
                    style = MaterialTheme.typography.labelLarge,
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(8),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.height(80.dp),
                ) {
                    item {
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .background(
                                    if (defaultColor >= 0L) {
                                        Color(defaultColor.toInt())
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    CircleShape,
                                )
                                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                .clickable { onPick(-1L) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.color_default),
                                color = contrastColor(Color(defaultColor.toInt())),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                    items(presetColors) { color ->
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .background(Color(color.toInt()), CircleShape)
                                .clickable { applyColor(Color(color.toInt())) },
                        )
                    }
                }

                HorizontalDivider()

                Text(
                    text = stringResource(R.string.color_customize),
                    style = MaterialTheme.typography.labelLarge,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .background(currentColor, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = hex,
                        color = contrastColor(currentColor),
                        fontFamily = FontFamily.Monospace,
                    )
                }
                OutlinedTextField(
                    value = hex,
                    onValueChange = { input ->
                        val digits = input.trim().removePrefix("#")
                            .filter { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
                            .take(6)
                        hex = "#$digits"
                        if (digits.length == 6) {
                            applyColor(Color((0xFF shl 24) or digits.toLong(16).toInt()))
                        }
                    },
                    label = { Text(stringResource(R.string.color_hex)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = rgbSpace,
                        onClick = { rgbSpace = true },
                        label = { Text(stringResource(R.string.color_space_rgb)) },
                    )
                    FilterChip(
                        selected = !rgbSpace,
                        onClick = { rgbSpace = false },
                        label = { Text(stringResource(R.string.color_space_hsv)) },
                    )
                }

                if (rgbSpace) {
                    val red = currentColor.red
                    val green = currentColor.green
                    val blue = currentColor.blue
                    SliderRow(stringResource(R.string.color_red), red, { applyColor(Color(it, green, blue)) })
                    SliderRow(stringResource(R.string.color_green), green, { applyColor(Color(red, it, blue)) })
                    SliderRow(stringResource(R.string.color_blue), blue, { applyColor(Color(red, green, it)) })
                } else {
                    val hsv = remember(currentColor) {
                        val components = floatArrayOf(0f, 0f, 0f)
                        android.graphics.Color.colorToHSV(currentColor.toArgb(), components)
                        components
                    }
                    val hue = hsv[0]
                    val saturation = hsv[1]
                    val brightness = hsv[2]
                    SliderRow(
                        label = stringResource(R.string.color_hue),
                        value = hue,
                        onChange = {
                            applyColor(Color(android.graphics.Color.HSVToColor(floatArrayOf(it, saturation, brightness))))
                        },
                        valueRange = 0f..360f,
                    )
                    SliderRow(
                        label = stringResource(R.string.color_saturation),
                        value = saturation,
                        onChange = {
                            applyColor(Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, it, brightness))))
                        },
                    )
                    SliderRow(
                        label = stringResource(R.string.color_brightness),
                        value = brightness,
                        onChange = {
                            applyColor(Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, it))))
                        },
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.color_cancel)) }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val argb = currentColor.toArgb() and 0xFFFFFF
                    onPick(0xFF000000L or argb.toLong())
                },
            ) { Text(stringResource(R.string.color_apply)) }
        },
    )
}

/** One channel at a time, in the space people already argue colours in. */
@Composable
private fun SliderRow(
    label: String,
    value: Float,
    onChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.size(80.dp),
        )
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = valueRange,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Black or white, whichever the colour underneath can still be read against. */
private fun contrastColor(color: Color): Color {
    val luminance = 0.299f * color.red + 0.587f * color.green + 0.114f * color.blue
    return if (luminance > 0.5f) Color.Black else Color.White
}

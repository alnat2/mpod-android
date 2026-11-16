package com.example.mpod.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.mpod.ui.theme.InterFontFamily

/**
 * Figma node 1346:15503: PlayerMobileTimeInput ("Go to time" custom numeric pad)
 */
@Composable
fun PlayerMobileTimeInput(
    onDismiss: () -> Unit,
    onDone: (seekPositionMs: Long) -> Unit
) {
    var rawDigits by remember { mutableStateOf("") }

    val formattedTime = remember(rawDigits) {
        formatInputDigits(rawDigits)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .width(320.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(
                    width = 1.dp,
                    color = Color(0x1AFAFAFA),
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header (Figma 1346:15573)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 18.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Cancel",
                        fontFamily = InterFontFamily,
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clickable(onClick = onDismiss)
                            .padding(4.dp)
                    )

                    Text(
                        text = "Go to time",
                        fontFamily = InterFontFamily,
                        fontSize = 16.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = "Done",
                        fontFamily = InterFontFamily,
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable {
                                val ms = parseDigitsToMs(rawDigits)
                                onDone(ms)
                            }
                            .padding(4.dp)
                    )
                }

                // Display Box (Figma 1346:15583)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = formattedTime,
                            fontFamily = InterFontFamily,
                            fontSize = 28.sp,
                            lineHeight = 42.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            letterSpacing = 0.7.sp,
                            textAlign = TextAlign.Center
                        )
                    }

                    Text(
                        text = "Enter hours, minutes",
                        fontFamily = InterFontFamily,
                        fontSize = 10.sp,
                        lineHeight = 15.sp,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }

                // Numpad Grid (Figma 1346:15591)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Row 1: 1, 2, 3
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PadButton("1", modifier = Modifier.weight(1f)) {
                            if (rawDigits.length < 6) rawDigits += "1"
                        }
                        PadButton("2", modifier = Modifier.weight(1f)) {
                            if (rawDigits.length < 6) rawDigits += "2"
                        }
                        PadButton("3", modifier = Modifier.weight(1f)) {
                            if (rawDigits.length < 6) rawDigits += "3"
                        }
                    }

                    // Row 2: 4, 5, 6
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PadButton("4", modifier = Modifier.weight(1f)) {
                            if (rawDigits.length < 6) rawDigits += "4"
                        }
                        PadButton("5", modifier = Modifier.weight(1f)) {
                            if (rawDigits.length < 6) rawDigits += "5"
                        }
                        PadButton("6", modifier = Modifier.weight(1f)) {
                            if (rawDigits.length < 6) rawDigits += "6"
                        }
                    }

                    // Row 3: 7, 8, 9
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PadButton("7", modifier = Modifier.weight(1f)) {
                            if (rawDigits.length < 6) rawDigits += "7"
                        }
                        PadButton("8", modifier = Modifier.weight(1f)) {
                            if (rawDigits.length < 6) rawDigits += "8"
                        }
                        PadButton("9", modifier = Modifier.weight(1f)) {
                            if (rawDigits.length < 6) rawDigits += "9"
                        }
                    }

                    // Row 4: 00, 0, ⌫
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PadButton(
                            text = "00",
                            isMuted = true,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (rawDigits.isNotEmpty() && rawDigits.length <= 4) {
                                rawDigits += "00"
                            }
                        }
                        PadButton("0", modifier = Modifier.weight(1f)) {
                            if (rawDigits.isNotEmpty() && rawDigits.length < 6) {
                                rawDigits += "0"
                            }
                        }
                        PadButton(
                            text = "⌫",
                            isMuted = true,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (rawDigits.isNotEmpty()) {
                                rawDigits = rawDigits.dropLast(1)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PadButton(
    text: String,
    modifier: Modifier = Modifier,
    isMuted: Boolean = false,
    onClick: () -> Unit
) {
    val containerColor = if (isMuted) {
        MaterialTheme.colorScheme.background
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val textColor = if (isMuted) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontFamily = InterFontFamily,
            fontSize = 20.sp,
            lineHeight = 30.sp,
            fontWeight = FontWeight.Medium,
            color = textColor,
            textAlign = TextAlign.Center
        )
    }
}

internal fun formatInputDigits(digits: String): String {
    if (digits.isEmpty()) return "0:00"

    val padded = digits.padStart(3, '0')
    return when {
        padded.length <= 4 -> {
            val sec = padded.takeLast(2)
            val min = padded.dropLast(2)
            "$min:$sec"
        }
        else -> {
            val sec = padded.takeLast(2)
            val min = padded.dropLast(2).takeLast(2)
            val hr = padded.dropLast(4)
            "$hr:$min:$sec"
        }
    }
}

internal fun parseDigitsToMs(digits: String): Long {
    if (digits.isEmpty()) return 0L

    return when {
        digits.length <= 2 -> {
            digits.toLong() * 1000L
        }
        digits.length <= 4 -> {
            val sec = digits.takeLast(2).toLong()
            val min = digits.dropLast(2).toLong()
            (min * 60L + sec) * 1000L
        }
        else -> {
            val sec = digits.takeLast(2).toLong()
            val min = digits.dropLast(2).takeLast(2).toLong()
            val hr = digits.dropLast(4).toLong()
            (hr * 3600L + min * 60L + sec) * 1000L
        }
    }
}

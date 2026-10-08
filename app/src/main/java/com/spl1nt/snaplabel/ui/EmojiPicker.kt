package com.spl1nt.snaplabel.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Curated for a kid labeling toys and making iPad stickers: prank/funny-label
// favorites first (skull, explosion, warning, speech bubble...), then general
// reactions/food/animals/etc. Googly eyes gets its own pinned tile below
// rather than living in this list — see EmojiPickerSheet.
private val EMOJI_SET = listOf(
    "💀", "💥", "⚠️", "🚫", "☠️", "🗯️", "💣", "👽", "🤖", "👻", "🧟", "🧙",
    "🥷", "🦹", "🦸", "🐉", "🦖", "🕷️", "🐍", "🦂", "🏴‍☠️", "🤡", "👹", "👺",
    "🔥", "⚡", "💢", "💫", "🤯", "🥵", "🥶", "😤", "😈", "🙈", "💩", "🧢",
    "👑", "🏆", "🥇", "🎯", "🔒", "🔓", "✅", "❌", "💯", "⭐", "🌟", "✨",
    "😀", "😂", "😍", "😎", "🥳", "😭", "😡", "🤔", "👍", "👎", "👏", "🙌",
    "❤️", "💔", "🎉", "🎂", "🐶", "🐱", "🐼", "🦄", "🍕", "🍔", "⚽", "🚀",
    "🌈", "☀️", "🌙", "❄️", "🎄", "🎃", "🙊", "💎", "🎁", "🕵️", "🧨", "🧟‍♂️",
)

@Composable
fun EmojiPickerSheet(onDismiss: () -> Unit, onPick: (String) -> Unit, onPickGoogly: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(6),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .padding(12.dp),
        ) {
            item {
                GooglyEyesTile(onClick = { onDismiss(); onPickGoogly() })
            }
            items(EMOJI_SET) { emoji ->
                Text(
                    text = emoji,
                    fontSize = 28.sp,
                    modifier = Modifier
                        .padding(6.dp)
                        .clickable { onPick(emoji) },
                )
            }
        }
    }
}

/** A small preview of actual googly eyes (not a text glyph) so the tile reads as what it places. */
@Composable
private fun GooglyEyesTile(onClick: () -> Unit) {
    Canvas(
        modifier = Modifier
            .padding(6.dp)
            .size(32.dp)
            .clickable(onClick = onClick),
    ) {
        val r = size.minDimension * 0.28f
        val gap = size.minDimension * 0.1f
        val centerY = size.height / 2f
        val centerXs = listOf(size.width / 2f - (r + gap / 2f), size.width / 2f + (r + gap / 2f))
        val pupilOffsets = listOf(Offset(-r * 0.2f, r * 0.25f), Offset(r * 0.15f, -r * 0.2f))
        for (i in 0..1) {
            drawCircle(color = Color.White, radius = r, center = Offset(centerXs[i], centerY))
            drawCircle(color = Color.Black, radius = r, center = Offset(centerXs[i], centerY), style = Stroke(width = 2f))
            drawCircle(color = Color.Black, radius = r * 0.4f, center = Offset(centerXs[i] + pupilOffsets[i].x, centerY + pupilOffsets[i].y))
        }
    }
}

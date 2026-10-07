package com.spl1nt.snaplabel.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val EMOJI_SET = listOf(
    "😀", "😂", "😍", "😎", "🥳", "😭", "😡", "🤔", "👍", "👎", "👏", "🙌",
    "💯", "🔥", "✨", "⭐", "❤️", "💔", "🎉", "🎂", "🐶", "🐱", "🐼", "🦄",
    "🍕", "🍔", "☕", "🍺", "⚽", "🏆", "🚀", "🌈", "☀️", "🌙", "⚡", "❄️",
    "🎄", "🎃", "💀", "👻", "🤖", "👽", "💩", "🙈", "🧢", "👑", "💎", "🎁",
)

@Composable
fun EmojiPickerSheet(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(6),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp)
                .padding(12.dp),
        ) {
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

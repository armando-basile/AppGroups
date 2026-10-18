package it.armando.appgroups

import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

object AppIconBitmap {
    fun of(drawable: Drawable): ImageBitmap = drawable.toBitmap(192, 192).asImageBitmap()
}

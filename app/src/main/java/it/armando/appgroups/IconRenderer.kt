package it.armando.appgroups

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.Canvas as ComposeCanvas
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.graphics.createBitmap
import kotlin.math.roundToInt

/** Produces 108dp bitmaps suitable for Icon.createWithAdaptiveBitmap (the launcher applies its own mask). */
object IconRenderer {
    private fun sizePx(context: Context) = (108 * context.resources.displayMetrics.density).roundToInt()

    fun fromDrawable(context: Context, drawable: Drawable): Bitmap {
        val size = sizePx(context)
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        if (drawable is AdaptiveIconDrawable) {
            // Draw the raw layers: calling draw() on the adaptive drawable would apply the OEM mask twice.
            listOfNotNull(drawable.background, drawable.foreground).forEach {
                it.setBounds(0, 0, size, size)
                it.draw(canvas)
            }
        } else {
            canvas.drawColor(android.graphics.Color.WHITE)
            val inset = size * 18 / 108
            drawable.setBounds(inset, inset, size - inset, size - inset)
            drawable.draw(canvas)
        }
        return bitmap
    }

    fun fromSymbol(context: Context, painter: Painter, background: Int): Bitmap {
        val size = sizePx(context)
        val image = ImageBitmap(size, size)
        val iconSize = size * 0.5f
        CanvasDrawScope().draw(
            Density(context),
            LayoutDirection.Ltr,
            ComposeCanvas(image),
            Size(size.toFloat(), size.toFloat()),
        ) {
            drawRect(Color(background))
            translate((size - iconSize) / 2f, (size - iconSize) / 2f) {
                with(painter) { draw(Size(iconSize, iconSize), colorFilter = ColorFilter.tint(Color.White)) }
            }
        }
        return image.asAndroidBitmap()
    }
}

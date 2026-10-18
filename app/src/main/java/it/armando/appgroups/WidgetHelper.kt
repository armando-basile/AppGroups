package it.armando.appgroups

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.core.graphics.createBitmap
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin

object WidgetHelper {
    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, GroupWidgetProvider::class.java))
        ids.forEach { update(context, manager, it) }
    }

    fun update(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val repo = GroupRepository(context)
        val style = repo.widgetStyle()
        val group = repo.widgetGroupId(widgetId)?.let(repo::get)
        val sizePx = (style.iconDp * context.resources.displayMetrics.density).roundToInt()

        val source = group?.let { repo.iconBitmap(it.id) }
            ?: IconRenderer.fromDrawable(context, requireNotNull(context.getDrawable(R.mipmap.ic_launcher)))
        val label = group?.label ?: context.getString(R.string.widget_unconfigured)

        val views = RemoteViews(context.packageName, R.layout.widget_group).apply {
            setImageViewBitmap(R.id.widget_icon, squircle(source, sizePx))
            setTextViewText(R.id.widget_label, label)
            setTextViewTextSize(R.id.widget_label, TypedValue.COMPLEX_UNIT_SP, style.textSp)
            setTextColor(R.id.widget_label, if (style.lightText) 0xFFFFFFFF.toInt() else 0xFF000000.toInt())
            setOnClickPendingIntent(R.id.widget_root, clickIntent(context, widgetId, group))
        }
        manager.updateAppWidget(widgetId, views)
    }

    private fun clickIntent(context: Context, widgetId: Int, group: Group?): PendingIntent {
        val intent = if (group != null) {
            Intent(context, PopupActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .putExtra(ShortcutHelper.EXTRA_GROUP_ID, group.id)
        } else {
            Intent(context, WidgetConfigActivity::class.java)
                .setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        }
        return PendingIntent.getActivity(
            context,
            widgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Crops the visible 72/108 area of an adaptive bitmap and clips it to a superellipse like the launcher's mask. */
    private fun squircle(source: Bitmap, size: Int): Bitmap {
        val inset = source.width * 18 / 108
        val src = Rect(inset, inset, source.width - inset, source.height - inset)
        val output = createBitmap(size, size)
        val canvas = Canvas(output)
        val half = size / 2f
        val path = Path()
        val exponent = 5.0
        for (step in 0..360) {
            val t = Math.toRadians(step.toDouble())
            val x = half + half * sign(cos(t)).toFloat() * abs(cos(t)).pow(2 / exponent).toFloat()
            val y = half + half * sign(sin(t)).toFloat() * abs(sin(t)).pow(2 / exponent).toFloat()
            if (step == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawPath(path, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(source, src, Rect(0, 0, size, size), paint)
        return output
    }
}

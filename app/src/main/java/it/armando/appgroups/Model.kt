package it.armando.appgroups

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class AppRef(val pkg: String, val cls: String, val user: Long) {
    fun encode() = "$pkg|$cls|$user"

    companion object {
        fun decode(value: String): AppRef? {
            val parts = value.split("|")
            if (parts.size != 3) return null
            return AppRef(parts[0], parts[1], parts[2].toLongOrNull() ?: return null)
        }
    }
}

enum class IconKind { APP, SYMBOL }

data class Group(
    val id: String = UUID.randomUUID().toString(),
    val label: String = "",
    val iconKind: IconKind = IconKind.SYMBOL,
    val iconApp: AppRef? = null,
    val symbol: String = SymbolIcons.default,
    val color: Int = SymbolIcons.colors.first(),
    val apps: List<AppRef> = emptyList(),
) {
    val shortcutId get() = "group_$id"

    fun toJson() = JSONObject().apply {
        put("id", id)
        put("label", label)
        put("iconKind", iconKind.name)
        put("iconApp", iconApp?.encode())
        put("symbol", symbol)
        put("color", color)
        put("apps", JSONArray(apps.map { it.encode() }))
    }

    companion object {
        fun fromJson(o: JSONObject): Group {
            val apps = o.getJSONArray("apps")
            return Group(
                id = o.getString("id"),
                label = o.getString("label"),
                iconKind = IconKind.valueOf(o.getString("iconKind")),
                iconApp = o.optString("iconApp").takeIf { it.isNotEmpty() }?.let(AppRef::decode),
                symbol = o.getString("symbol"),
                color = o.getInt("color"),
                apps = (0 until apps.length()).mapNotNull { AppRef.decode(apps.getString(it)) },
            )
        }
    }
}

data class WidgetStyle(val iconDp: Int = 56, val textSp: Float = 14.5f, val lightText: Boolean = true)

data class PopupSettings(val columns: Int = 4, val rows: Int = 4) {
    companion object {
        val options = listOf(3, 4, 5)
    }
}

class GroupRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("groups", Context.MODE_PRIVATE)
    private val iconDir = File(context.applicationContext.filesDir, "icons").apply { mkdirs() }
    private val widgetPrefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("widgets", Context.MODE_PRIVATE)

    fun all(): List<Group> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        val array = JSONArray(raw)
        return (0 until array.length()).mapNotNull {
            runCatching { Group.fromJson(array.getJSONObject(it)) }.getOrNull()
        }
    }

    fun get(id: String) = all().firstOrNull { it.id == id }

    fun save(group: Group, icon: Bitmap) {
        val groups = all().toMutableList()
        val index = groups.indexOfFirst { it.id == group.id }
        if (index >= 0) groups[index] = group else groups.add(group)
        iconFile(group.id).outputStream().use { icon.compress(Bitmap.CompressFormat.PNG, 100, it) }
        write(groups)
    }

    fun delete(id: String) {
        write(all().filterNot { it.id == id })
        iconFile(id).delete()
    }

    fun iconBitmap(id: String): Bitmap? =
        iconFile(id).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }

    fun iconPng(id: String): ByteArray? = iconFile(id).takeIf { it.exists() }?.readBytes()

    /** Replaces every group and icon with the given ones. Callers must validate ids and PNG data beforehand. */
    fun replaceAll(groups: List<Group>, icons: Map<String, ByteArray>) {
        icons.forEach { (id, png) -> iconFile(id).writeBytes(png) }
        val keep = groups.map { it.id }.toSet()
        iconDir.listFiles()?.filter { it.nameWithoutExtension !in keep }?.forEach { it.delete() }
        write(groups)
    }

    fun widgetGroupId(widgetId: Int): String? = widgetPrefs.getString("w_$widgetId", null)

    fun setWidgetGroup(widgetId: Int, groupId: String) {
        widgetPrefs.edit().putString("w_$widgetId", groupId).apply()
    }

    fun removeWidgets(widgetIds: IntArray) {
        widgetPrefs.edit().apply { widgetIds.forEach { remove("w_$it") } }.apply()
    }

    fun widgetStyle() = WidgetStyle(
        iconDp = widgetPrefs.getInt("style_icon_dp", WidgetStyle().iconDp),
        textSp = widgetPrefs.getFloat("style_text_sp", WidgetStyle().textSp),
        lightText = widgetPrefs.getBoolean("style_light_text", WidgetStyle().lightText),
    )

    fun popupSettings() = PopupSettings(
        columns = widgetPrefs.getInt("popup_columns", PopupSettings().columns),
        rows = widgetPrefs.getInt("popup_rows", PopupSettings().rows),
    )

    fun savePopupSettings(settings: PopupSettings) {
        widgetPrefs.edit()
            .putInt("popup_columns", settings.columns)
            .putInt("popup_rows", settings.rows)
            .apply()
    }

    fun saveWidgetStyle(style: WidgetStyle) {
        widgetPrefs.edit()
            .putInt("style_icon_dp", style.iconDp)
            .putFloat("style_text_sp", style.textSp)
            .putBoolean("style_light_text", style.lightText)
            .apply()
    }

    private fun iconFile(id: String) = File(iconDir, "$id.png")

    private fun write(groups: List<Group>) {
        val array = JSONArray(groups.map { it.toJson() })
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    private companion object {
        const val KEY = "groups_json"
    }
}

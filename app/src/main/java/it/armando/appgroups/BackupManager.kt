package it.armando.appgroups

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.OutputStream

class BackupException(message: String) : Exception(message)

/** The remembered backup file was deleted or the app lost access to it. */
class FileUnavailableException : Exception("file not available")

class BackupData(
    val createdAt: Long,
    val groups: List<Group>,
    val icons: Map<String, ByteArray>,
    val popup: PopupSettings,
    val widget: WidgetStyle,
)

/**
 * Backup format: a single JSON file with the groups, the popup/widget look settings and the group icons
 * (PNG, base64). Widget-to-group assignments are not included, since widget ids are specific to the launcher.
 */
object BackupManager {
    const val DEFAULT_FILE_NAME = "AppGroupsConfigs.json"
    const val MIME = "application/json"

    private const val VERSION = 1
    private const val MAX_BYTES = 32L * 1024 * 1024
    private const val MAX_ICON_BYTES = 2L * 1024 * 1024
    private const val MAX_ICON_SIDE = 2048
    private const val MAX_GROUPS = 500
    private val ID_REGEX = Regex("[A-Za-z0-9-]{1,64}")

    fun serialize(context: Context): ByteArray {
        val repo = GroupRepository(context)
        val groups = repo.all()
        val icons = JSONObject()
        groups.forEach { group ->
            repo.iconPng(group.id)?.let { icons.put(group.id, Base64.encodeToString(it, Base64.NO_WRAP)) }
        }
        val popup = repo.popupSettings()
        val widget = repo.widgetStyle()
        val root = JSONObject().apply {
            put("version", VERSION)
            put("createdAt", System.currentTimeMillis())
            put("groups", JSONArray(groups.map { it.toJson() }))
            put("icons", icons)
            put("popup", JSONObject().put("columns", popup.columns).put("rows", popup.rows))
            put(
                "widget",
                JSONObject().put("iconDp", widget.iconDp).put("textSp", widget.textSp.toDouble())
                    .put("lightText", widget.lightText),
            )
        }
        return root.toString(2).toByteArray(Charsets.UTF_8)
    }

    fun write(context: Context, out: OutputStream) = out.write(serialize(context))

    /** Parses and fully validates a backup without touching the app data. */
    fun read(input: InputStream): BackupData {
        val root = try {
            JSONObject(String(readLimited(input, MAX_BYTES), Charsets.UTF_8))
        } catch (e: JSONException) {
            throw BackupException("invalid json")
        }
        if (root.optInt("version", 0) !in 1..VERSION) throw BackupException("unsupported version")

        val groups = try {
            val array = root.getJSONArray("groups")
            if (array.length() > MAX_GROUPS) throw BackupException("too many groups")
            (0 until array.length()).map { Group.fromJson(array.getJSONObject(it)) }
        } catch (e: JSONException) {
            throw BackupException("invalid groups")
        } catch (e: IllegalArgumentException) {
            throw BackupException("invalid groups")
        }
        val ids = groups.map { it.id }
        if (ids.any { !ID_REGEX.matches(it) } || ids.toSet().size != ids.size) throw BackupException("invalid ids")

        val icons = mutableMapOf<String, ByteArray>()
        root.optJSONObject("icons")?.let { json ->
            ids.forEach { id ->
                val png = json.optString(id).takeIf { it.isNotEmpty() }
                    ?.let { runCatching { Base64.decode(it, Base64.DEFAULT) }.getOrNull() }
                if (png != null && png.size <= MAX_ICON_BYTES && isValidPng(png)) icons[id] = png
            }
        }

        val popup = root.optJSONObject("popup")?.let {
            PopupSettings(
                columns = it.optInt("columns").takeIf { c -> c in PopupSettings.options } ?: PopupSettings().columns,
                rows = it.optInt("rows").takeIf { r -> r in PopupSettings.options } ?: PopupSettings().rows,
            )
        } ?: PopupSettings()
        val defaults = WidgetStyle()
        val widget = root.optJSONObject("widget")?.let {
            WidgetStyle(
                iconDp = it.optInt("iconDp", defaults.iconDp).coerceIn(40, 80),
                textSp = it.optDouble("textSp", defaults.textSp.toDouble()).toFloat().coerceIn(8f, 18f),
                lightText = it.optBoolean("lightText", defaults.lightText),
            )
        } ?: defaults

        return BackupData(root.optLong("createdAt", 0L), groups, icons, popup, widget)
    }

    fun apply(context: Context, data: BackupData) {
        val repo = GroupRepository(context)
        val before = repo.all()
        repo.replaceAll(data.groups, data.icons)
        repo.savePopupSettings(data.popup)
        repo.saveWidgetStyle(data.widget)

        val kept = data.groups.map { it.id }.toSet()
        before.filter { it.id !in kept }.forEach { runCatching { ShortcutHelper.disable(context, it) } }
        data.groups.forEach { runCatching { ShortcutHelper.updateIfPinned(context, it, repo) } }
        WidgetHelper.updateAll(context)
    }

    /** Writes [DEFAULT_FILE_NAME] in the folder, updating it if it is already there. */
    fun writeToFolder(context: Context, folder: Uri): String {
        val dir = DocumentFile.fromTreeUri(context, folder)?.takeIf { it.canWrite() }
            ?: throw BackupException("folder not writable")
        val bytes = serialize(context)
        val existing = dir.findFile(DEFAULT_FILE_NAME)?.takeIf { it.isFile }
        val target = existing ?: dir.createFile(MIME, DEFAULT_FILE_NAME) ?: throw BackupException("cannot create file")
        try {
            val out = context.contentResolver.openOutputStream(target.uri, "wt")
                ?: throw BackupException("cannot open file")
            out.use { it.write(bytes) }
        } catch (e: Exception) {
            if (existing == null) target.delete()
            throw e
        }
        return target.name ?: DEFAULT_FILE_NAME
    }

    /** JSON files in the folder, newest first. The user may have renamed the backup, so no name pattern is required. */
    fun listBackups(context: Context, folder: Uri): List<DocumentFile> {
        val dir = DocumentFile.fromTreeUri(context, folder)?.takeIf { it.canRead() }
            ?: throw BackupException("folder not readable")
        return dir.listFiles()
            .filter { it.isFile && it.name?.endsWith(".json", ignoreCase = true) == true }
            .sortedByDescending { it.lastModified() }
    }

    fun folderName(context: Context, folder: Uri): String? = DocumentFile.fromTreeUri(context, folder)?.name

    /** The remembered backup file, or null if it was deleted or access to it was lost. */
    fun existingFile(context: Context, uri: Uri): DocumentFile? = runCatching {
        DocumentFile.fromSingleUri(context, uri)?.takeIf { it.exists() && it.canWrite() }
    }.getOrNull()

    /** Overwrites an existing file. The content is built in memory first so a failure doesn't leave a half-written file. */
    fun updateFile(context: Context, uri: Uri): String {
        val file = existingFile(context, uri) ?: throw FileUnavailableException()
        val bytes = serialize(context)
        val out = try {
            context.contentResolver.openOutputStream(uri, "wt")
        } catch (e: SecurityException) {
            throw FileUnavailableException()
        } catch (e: FileNotFoundException) {
            throw FileUnavailableException()
        } ?: throw BackupException("cannot open file")
        out.use { it.write(bytes) }
        return file.name ?: ""
    }

    private fun isValidPng(bytes: ByteArray): Boolean {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        return options.outMimeType == "image/png" &&
            options.outWidth in 1..MAX_ICON_SIDE && options.outHeight in 1..MAX_ICON_SIDE
    }

    private fun readLimited(input: InputStream, limit: Long): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) throw BackupException("file too large")
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }
}

/** Remembers the backup folder the user picked (with its persisted access grant) and the last backup file. */
class BackupStore(private val context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("backup", Context.MODE_PRIVATE)

    fun folder(): Uri? {
        val uri = prefs.getString("folder", null)?.let(Uri::parse) ?: return null
        val granted = context.contentResolver.persistedUriPermissions
            .any { it.uri == uri && it.isReadPermission && it.isWritePermission }
        return uri.takeIf { granted }
    }

    fun setFolder(uri: Uri) {
        prefs.edit().putString("folder", uri.toString()).apply()
    }

    /** The file last created through "Save to file" (e.g. on Google Drive); it may no longer exist. */
    fun file(): Uri? = prefs.getString("file", null)?.let(Uri::parse)

    fun setFile(uri: Uri) {
        prefs.edit().putString("file", uri.toString()).apply()
    }

    fun clearFile() {
        prefs.edit().remove("file").apply()
    }
}

package it.armando.appgroups

import android.content.Intent
import android.net.Uri
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

@Composable
fun BackupDialog(onRestored: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { BackupStore(context) }
    var folder by remember { mutableStateOf(store.folder()) }
    var busy by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<BackupData?>(null) }
    var folderBackups by remember { mutableStateOf<List<DocumentFile>?>(null) }
    var savedFile by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        savedFile = withContext(Dispatchers.IO) {
            store.file()?.let { uri ->
                BackupManager.existingFile(context, uri)?.name ?: run { store.clearFile(); null }
            }
        }
    }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()
    val failedText = stringResource(R.string.backup_failed)
    val invalidText = stringResource(R.string.backup_invalid)

    fun loadFrom(uri: Uri) {
        scope.launch {
            busy = true
            runCatching {
                withContext(Dispatchers.IO) {
                    val input = context.contentResolver.openInputStream(uri) ?: throw BackupException("cannot open")
                    input.use { BackupManager.read(it) }
                }
            }.onSuccess { pending = it }
                .onFailure { toast(if (it is BackupException) invalidText else failedText) }
            busy = false
        }
    }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            store.setFolder(uri)
            folder = store.folder()
        }
    }
    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BackupManager.MIME)) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                runCatching {
                    withContext(Dispatchers.IO) {
                        val out = context.contentResolver.openOutputStream(uri, "wt") ?: throw BackupException("cannot open")
                        out.use { BackupManager.write(context, it) }
                        // Not every provider allows keeping access; if not, the next save falls back to the picker.
                        runCatching {
                            context.contentResolver.takePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                            )
                        }
                        store.setFile(uri)
                        BackupManager.existingFile(context, uri)?.name
                    }
                }.onSuccess {
                    savedFile = it
                    toast(context.getString(R.string.backup_saved_file))
                }.onFailure { toast(failedText) }
                busy = false
            }
        }
    }

    fun updateSavedFile() {
        val target = store.file()
        scope.launch {
            busy = true
            runCatching {
                withContext(Dispatchers.IO) { BackupManager.updateFile(context, target ?: throw FileUnavailableException()) }
            }.onSuccess { toast(context.getString(R.string.backup_updated_file, it)) }
                .onFailure {
                    if (it is FileUnavailableException) {
                        store.clearFile()
                        savedFile = null
                        toast(context.getString(R.string.backup_file_missing))
                        saveFile.launch(BackupManager.DEFAULT_FILE_NAME)
                    } else {
                        toast(failedText)
                    }
                }
            busy = false
        }
    }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) loadFrom(uri)
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text(stringResource(R.string.backup_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.backup_hint), style = MaterialTheme.typography.bodyMedium)

                Text(
                    stringResource(R.string.backup_folder_section),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Text(
                    text = folder?.let { BackupManager.folderName(context, it) }
                        ?: stringResource(R.string.backup_no_folder),
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(
                    onClick = { pickFolder.launch(folder) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(if (folder == null) R.string.backup_choose_folder else R.string.backup_change_folder))
                }
                Text(stringResource(R.string.backup_folder_hint), style = MaterialTheme.typography.bodySmall)
                Button(
                    enabled = folder != null && !busy,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val target = folder ?: return@Button
                        scope.launch {
                            busy = true
                            runCatching { withContext(Dispatchers.IO) { BackupManager.writeToFolder(context, target) } }
                                .onSuccess { toast(context.getString(R.string.backup_saved_folder, it)) }
                                .onFailure { toast(failedText) }
                            busy = false
                        }
                    },
                ) { Text(stringResource(R.string.backup_now)) }
                OutlinedButton(
                    enabled = folder != null && !busy,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val target = folder ?: return@OutlinedButton
                        scope.launch {
                            busy = true
                            runCatching { withContext(Dispatchers.IO) { BackupManager.listBackups(context, target) } }
                                .onSuccess {
                                    if (it.isEmpty()) toast(context.getString(R.string.backup_none_in_folder))
                                    else folderBackups = it
                                }
                                .onFailure { toast(failedText) }
                            busy = false
                        }
                    },
                ) { Text(stringResource(R.string.restore)) }

                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(stringResource(R.string.backup_file_section), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.backup_file_hint), style = MaterialTheme.typography.bodyMedium)
                savedFile?.let {
                    Text(stringResource(R.string.backup_saved_file_name, it), style = MaterialTheme.typography.bodyMedium)
                }
                if (savedFile != null) {
                    Button(enabled = !busy, onClick = ::updateSavedFile, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.backup_update_file))
                    }
                    OutlinedButton(
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { saveFile.launch(BackupManager.DEFAULT_FILE_NAME) },
                    ) { Text(stringResource(R.string.backup_to_new_file)) }
                } else {
                    Button(
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { saveFile.launch(BackupManager.DEFAULT_FILE_NAME) },
                    ) { Text(stringResource(R.string.backup_to_file)) }
                }
                OutlinedButton(
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { openFile.launch(arrayOf("*/*")) },
                ) { Text(stringResource(R.string.restore_from_file)) }
            }
        },
        confirmButton = { TextButton(onClick = onClose, enabled = !busy) { Text(stringResource(R.string.close)) } },
    )

    folderBackups?.let { files ->
        AlertDialog(
            onDismissRequest = { folderBackups = null },
            title = { Text(stringResource(R.string.restore_choose_backup)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    files.forEach { file ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    folderBackups = null
                                    loadFrom(file.uri)
                                }
                                .padding(vertical = 10.dp),
                        ) {
                            Text(file.name.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                DateFormat.getMediumDateFormat(context).format(Date(file.lastModified())) + " " +
                                    DateFormat.getTimeFormat(context).format(Date(file.lastModified())),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { folderBackups = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    pending?.let { data ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(stringResource(R.string.restore_confirm_title)) },
            text = {
                val date = if (data.createdAt > 0) {
                    DateFormat.getMediumDateFormat(context).format(Date(data.createdAt)) + " " +
                        DateFormat.getTimeFormat(context).format(Date(data.createdAt))
                } else {
                    "-"
                }
                Text(stringResource(R.string.restore_confirm_message, data.groups.size, date))
            },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    scope.launch {
                        busy = true
                        runCatching { withContext(Dispatchers.IO) { BackupManager.apply(context, data) } }
                            .onSuccess {
                                toast(context.getString(R.string.restore_done, data.groups.size))
                                onRestored()
                            }
                            .onFailure { toast(failedText) }
                        busy = false
                    }
                }) { Text(stringResource(R.string.restore)) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

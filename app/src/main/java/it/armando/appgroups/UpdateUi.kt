package it.armando.appgroups

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    class Available(val info: ReleaseInfo) : UpdateState
    class NeedsPermission(val info: ReleaseInfo) : UpdateState
    class Downloading(val info: ReleaseInfo, val progress: Float) : UpdateState
    data object Installing : UpdateState
    class Failed(@param:StringRes val message: Int) : UpdateState
}

class UpdateStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("updates", Context.MODE_PRIVATE)

    var checkOnStartup: Boolean
        get() = prefs.getBoolean("check_on_startup", false)
        set(value) = prefs.edit().putBoolean("check_on_startup", value).apply()
}

class UpdateController(private val context: Context, private val scope: CoroutineScope) {
    private val store = UpdateStore(context)
    private var job: Job? = null

    var state by mutableStateOf<UpdateState>(UpdateState.Idle)
        private set

    val installedVersion: String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()

    var checkOnStartup: Boolean
        get() = store.checkOnStartup
        set(value) {
            store.checkOnStartup = value
        }

    fun checkOnStartupIfEnabled() {
        if (store.checkOnStartup) check(manual = false)
    }

    /** A manual check reports every outcome; a startup check only speaks up when an update exists. */
    fun check(manual: Boolean) {
        if (state is UpdateState.Checking || state is UpdateState.Downloading || state is UpdateState.Installing) return
        job = scope.launch {
            if (manual) state = UpdateState.Checking
            val result = runCatching { withContext(Dispatchers.IO) { UpdateChecker.fetchLatest() } }
            state = result.fold(
                onSuccess = { info ->
                    when {
                        info != null && UpdateChecker.isNewer(info.tag, installedVersion) -> UpdateState.Available(info)
                        manual -> UpdateState.UpToDate
                        else -> UpdateState.Idle
                    }
                },
                onFailure = { if (manual) UpdateState.Failed(R.string.update_error_network) else UpdateState.Idle },
            )
        }
    }

    fun startUpdate(info: ReleaseInfo) {
        if (!context.packageManager.canRequestPackageInstalls()) {
            state = UpdateState.NeedsPermission(info)
            return
        }
        job = scope.launch {
            state = UpdateState.Downloading(info, 0f)
            try {
                val apk = UpdateInstaller.download(context, info) { state = UpdateState.Downloading(info, it) }
                state = UpdateState.Installing
                withContext(Dispatchers.IO) { UpdateInstaller.install(context, apk) }
                state = UpdateState.Idle
            } catch (e: CancellationException) {
                throw e
            } catch (e: UpdateException) {
                state = UpdateState.Failed(
                    when (e.kind) {
                        UpdateException.Kind.CHECKSUM -> R.string.update_error_checksum
                        UpdateException.Kind.SIGNATURE -> R.string.update_error_signature
                        UpdateException.Kind.INVALID_APK -> R.string.update_error_invalid
                        UpdateException.Kind.NOT_NEWER -> R.string.update_error_not_newer
                        UpdateException.Kind.TOO_LARGE -> R.string.update_error_too_large
                        UpdateException.Kind.DOWNLOAD -> R.string.update_error_download
                    },
                )
            } catch (e: Exception) {
                state = UpdateState.Failed(R.string.update_error_install)
            }
        }
    }

    fun openInstallPermissionSettings() {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        state = UpdateState.Idle
    }

    fun dismiss() {
        job?.cancel()
        state = UpdateState.Idle
    }
}

@Composable
fun UpdateDialogs(controller: UpdateController) {
    val context = LocalContext.current
    when (val state = controller.state) {
        UpdateState.Idle -> Unit

        UpdateState.Checking -> AlertDialog(
            onDismissRequest = controller::dismiss,
            text = { ProgressRow(stringResource(R.string.update_checking)) },
            confirmButton = { TextButton(onClick = controller::dismiss) { Text(stringResource(R.string.cancel)) } },
        )

        UpdateState.UpToDate -> AlertDialog(
            onDismissRequest = controller::dismiss,
            text = { Text(stringResource(R.string.update_up_to_date, controller.installedVersion)) },
            confirmButton = { TextButton(onClick = controller::dismiss) { Text(stringResource(R.string.ok)) } },
        )

        is UpdateState.Available -> AlertDialog(
            onDismissRequest = controller::dismiss,
            title = { Text(stringResource(R.string.update_available_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.update_available_message, state.info.tag, controller.installedVersion))
                    if (state.info.notes.isNotEmpty()) {
                        Text(state.info.notes.take(MAX_NOTES_CHARS), modifier = Modifier.padding(top = 12.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { controller.startUpdate(state.info) }) { Text(stringResource(R.string.update_now)) }
            },
            dismissButton = { TextButton(onClick = controller::dismiss) { Text(stringResource(R.string.update_later)) } },
        )

        is UpdateState.NeedsPermission -> AlertDialog(
            onDismissRequest = controller::dismiss,
            title = { Text(stringResource(R.string.update_permission_title)) },
            text = { Text(stringResource(R.string.update_permission_message)) },
            confirmButton = {
                TextButton(onClick = controller::openInstallPermissionSettings) {
                    Text(stringResource(R.string.update_open_settings))
                }
            },
            dismissButton = { TextButton(onClick = controller::dismiss) { Text(stringResource(R.string.cancel)) } },
        )

        is UpdateState.Downloading -> AlertDialog(
            onDismissRequest = controller::dismiss,
            title = { Text(stringResource(R.string.update_downloading, state.info.tag)) },
            text = {
                LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = { TextButton(onClick = controller::dismiss) { Text(stringResource(R.string.cancel)) } },
        )

        UpdateState.Installing -> AlertDialog(
            onDismissRequest = {},
            text = { ProgressRow(stringResource(R.string.update_installing)) },
            confirmButton = {},
        )

        is UpdateState.Failed -> AlertDialog(
            onDismissRequest = controller::dismiss,
            text = { Text(context.getString(state.message)) },
            confirmButton = { TextButton(onClick = controller::dismiss) { Text(stringResource(R.string.ok)) } },
        )
    }
}

@Composable
private fun ProgressRow(text: String) {
    androidx.compose.foundation.layout.Row(
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.padding(end = 16.dp))
        Text(text)
    }
}

private const val MAX_NOTES_CHARS = 800

package it.armando.appgroups

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddHome
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Slider
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import kotlin.math.roundToInt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var editGroupId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        editGroupId = intent.getStringExtra(EXTRA_EDIT_GROUP_ID)
        setContent {
            AppGroupsTheme { Root(editGroupId = editGroupId, onEditHandled = { editGroupId = null }) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        editGroupId = intent.getStringExtra(EXTRA_EDIT_GROUP_ID)
    }

    companion object {
        const val EXTRA_EDIT_GROUP_ID = "edit_group_id"
    }
}

@Composable
private fun Root(editGroupId: String?, onEditHandled: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { GroupRepository(context) }
    var groups by remember { mutableStateOf(repo.all()) }
    var editing by remember { mutableStateOf<Group?>(null) }
    val apps = remember { AppCatalog.load(context) }
    val updateScope = rememberCoroutineScope()
    val updates = remember { UpdateController(context, updateScope) }

    LaunchedEffect(Unit) {
        if (!startupUpdateCheckDone) {
            startupUpdateCheckDone = true
            updates.checkOnStartupIfEnabled()
        }
    }
    UpdateDialogs(updates)

    LaunchedEffect(editGroupId) {
        if (editGroupId != null) {
            groups = repo.all()
            repo.get(editGroupId)?.let { editing = it }
            onEditHandled()
        }
    }

    editing?.let { draft ->
        BackHandler { editing = null }
        EditorScreen(
            initial = draft,
            isNew = groups.none { it.id == draft.id },
            apps = apps,
            onSave = { group, icon ->
                repo.save(group, icon)
                ShortcutHelper.updateIfPinned(context, group, repo)
                WidgetHelper.updateAll(context)
                groups = repo.all()
                editing = null
            },
            onDelete = {
                repo.delete(draft.id)
                ShortcutHelper.disable(context, draft)
                WidgetHelper.updateAll(context)
                groups = repo.all()
                editing = null
            },
            onClose = { editing = null },
        )
        return
    }

    GroupListScreen(
        groups = groups,
        repo = repo,
        updates = updates,
        onAdd = { editing = Group() },
        onEdit = { editing = it },
        onRestored = { groups = repo.all() },
    )
}

/** The startup update check runs once per process, not on every return to the screen. */
private var startupUpdateCheckDone = false

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupListScreen(
    groups: List<Group>,
    repo: GroupRepository,
    updates: UpdateController,
    onAdd: () -> Unit,
    onEdit: (Group) -> Unit,
    onRestored: () -> Unit,
) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val pinHint = stringResource(R.string.pin_requested)
    val unsupported = stringResource(R.string.pin_unsupported)
    val settingsLabel = stringResource(R.string.settings)
    var showWidgetSettings by remember { mutableStateOf(false) }
    var showBackup by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.groups_title)) },
                actions = {
                    IconButton(onClick = { showBackup = true }) {
                        Icon(Icons.Filled.Backup, contentDescription = stringResource(R.string.backup_title))
                    }
                    IconButton(onClick = { showWidgetSettings = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.new_group))
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (groups.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.empty_groups))
            }
        } else {
            LazyColumn(Modifier.padding(padding)) {
                items(groups, key = { it.id }) { group ->
                    ListItem(
                        modifier = Modifier.clickable { onEdit(group) },
                        leadingContent = { GroupIcon(group, repo, 48) },
                        headlineContent = { Text(group.label) },
                        supportingContent = { Text(stringResource(R.string.apps_count, group.apps.size)) },
                        trailingContent = {
                            Row {
                                IconButton(onClick = {
                                    context.startActivity(
                                        Intent(context, PopupActivity::class.java)
                                            .setAction(Intent.ACTION_VIEW)
                                            .putExtra(ShortcutHelper.EXTRA_GROUP_ID, group.id),
                                    )
                                }) {
                                    Icon(
                                        Icons.Filled.Visibility,
                                        contentDescription = stringResource(R.string.preview_popup),
                                    )
                                }
                                IconButton(onClick = {
                                    val ok = ShortcutHelper.requestPin(context, group, repo)
                                    scope.launch {
                                        val result = snackbar.showSnackbar(
                                            message = if (ok) pinHint else unsupported,
                                            actionLabel = settingsLabel,
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            context.startActivity(
                                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                                    .setData(Uri.fromParts("package", context.packageName, null)),
                                            )
                                        }
                                    }
                                }) {
                                    Icon(Icons.Filled.AddHome, contentDescription = stringResource(R.string.add_to_home))
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    if (showWidgetSettings) SettingsDialog(repo, updates) { showWidgetSettings = false }
    if (showBackup) BackupDialog(onRestored = onRestored, onClose = { showBackup = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsDialog(repo: GroupRepository, updates: UpdateController, onClose: () -> Unit) {
    val context = LocalContext.current
    var checkOnStartup by remember { mutableStateOf(updates.checkOnStartup) }
    var style by remember { mutableStateOf(repo.widgetStyle()) }
    var popup by remember { mutableStateOf(repo.popupSettings()) }
    val change = { new: WidgetStyle ->
        style = new
        repo.saveWidgetStyle(new)
        WidgetHelper.updateAll(context)
    }
    val changePopup = { new: PopupSettings ->
        popup = new
        repo.savePopupSettings(new)
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.settings)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.popup_section), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.popup_columns), modifier = Modifier.padding(top = 8.dp))
                ChoiceRow(PopupSettings.options, popup.columns) { changePopup(popup.copy(columns = it)) }
                Text(stringResource(R.string.popup_rows), modifier = Modifier.padding(top = 8.dp))
                ChoiceRow(PopupSettings.options, popup.rows) { changePopup(popup.copy(rows = it)) }

                Text(
                    stringResource(R.string.widget_settings),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 24.dp),
                )
                Text(
                    stringResource(R.string.widget_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    stringResource(R.string.widget_icon_size, style.iconDp),
                    modifier = Modifier.padding(top = 12.dp),
                )
                Slider(
                    value = style.iconDp.toFloat(),
                    onValueChange = { change(style.copy(iconDp = it.roundToInt())) },
                    valueRange = 40f..80f,
                )
                Text(stringResource(R.string.widget_text_size, style.textSp))
                Slider(
                    value = style.textSp,
                    onValueChange = { change(style.copy(textSp = (it * 2).roundToInt() / 2f)) },
                    valueRange = 8f..18f,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.widget_light_text), modifier = Modifier.weight(1f))
                    Switch(checked = style.lightText, onCheckedChange = { change(style.copy(lightText = it)) })
                }

                Text(
                    stringResource(R.string.updates_section),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 24.dp),
                )
                Text(
                    stringResource(R.string.updates_version, updates.installedVersion),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.updates_auto), modifier = Modifier.weight(1f))
                    Switch(checked = checkOnStartup, onCheckedChange = {
                        checkOnStartup = it
                        updates.checkOnStartup = it
                    })
                }
                OutlinedButton(
                    onClick = { updates.check(manual = true) },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                ) { Text(stringResource(R.string.updates_check_now)) }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.close)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChoiceRow(options: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach {
            FilterChip(selected = it == selected, onClick = { onSelect(it) }, label = { Text(it.toString()) })
        }
    }
}

@Composable
fun GroupIcon(group: Group, repo: GroupRepository, size: Int) {
    val bitmap = remember(group) { repo.iconBitmap(group.id) }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.size(size.dp).clip(CircleShape),
        )
    } else {
        Box(Modifier.size(size.dp).clip(CircleShape).background(Color(group.color)))
    }
}

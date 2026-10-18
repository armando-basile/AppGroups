package it.armando.appgroups

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragIndicator
import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(
    initial: Group,
    isNew: Boolean,
    apps: List<AppInfo>,
    onSave: (Group, android.graphics.Bitmap) -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf(initial) }
    var query by remember { mutableStateOf("") }
    var showAppIconPicker by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showReorder by remember { mutableStateOf(false) }
    var iconTab by remember { mutableStateOf(if (initial.iconKind == IconKind.APP) 0 else 1) }

    val symbolPainter = rememberVectorPainter(SymbolIcons.vector(draft.symbol))
    val errorLabel = stringResource(R.string.error_label)
    val errorApps = stringResource(R.string.error_apps)

    if (showReorder) {
        BackHandler { showReorder = false }
        val byRef = apps.associateBy { it.ref }
        val shown = draft.apps.mapNotNull { byRef[it] }
        ReorderScreen(
            apps = shown,
            columns = remember { GroupRepository(context).popupSettings().columns },
            onDone = { ordered ->
                val missing = draft.apps.filterNot { it in byRef }
                draft = draft.copy(apps = ordered + missing)
                showReorder = false
            },
            onClose = { showReorder = false },
        )
        return
    }

    fun save() {
        val problem = when {
            draft.label.isBlank() -> errorLabel
            draft.apps.isEmpty() -> errorApps
            else -> null
        }
        if (problem != null) {
            scope.launch { snackbar.showSnackbar(problem) }
            return
        }
        val iconApp = draft.iconApp?.let { AppCatalog.find(context, it) }
        val bitmap = if (draft.iconKind == IconKind.APP && iconApp != null) {
            IconRenderer.fromDrawable(context, iconApp.getIcon(0))
        } else {
            IconRenderer.fromSymbol(context, symbolPainter, draft.color)
        }
        onSave(draft.copy(label = draft.label.trim()), bitmap)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (isNew) R.string.new_group else R.string.edit_group)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (!isNew) {
                        IconButton(onClick = { showDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete))
                        }
                    }
                    IconButton(onClick = ::save) {
                        Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.save))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val filtered = remember(query, apps) {
            apps.filter { it.label.contains(query, ignoreCase = true) }
        }
        LazyColumn(Modifier.padding(padding), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
            item {
                OutlinedTextField(
                    value = draft.label,
                    onValueChange = { draft = draft.copy(label = it) },
                    label = { Text(stringResource(R.string.label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Text(
                    stringResource(R.string.icon),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconPreview(draft, apps)
                }
                TabRow(selectedTabIndex = iconTab, modifier = Modifier.padding(top = 12.dp)) {
                    Tab(
                        selected = iconTab == 0,
                        onClick = { iconTab = 0 },
                        text = { Text(stringResource(R.string.icon_from_app)) },
                    )
                    Tab(
                        selected = iconTab == 1,
                        onClick = { iconTab = 1; draft = draft.copy(iconKind = IconKind.SYMBOL) },
                        text = { Text(stringResource(R.string.icon_symbols)) },
                    )
                }
                if (iconTab == 0) {
                    OutlinedButton(
                        onClick = { showAppIconPicker = true },
                        modifier = Modifier.padding(top = 12.dp),
                    ) { Text(stringResource(R.string.choose_app_icon)) }
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 12.dp),
                    ) {
                        SymbolIcons.colors.forEach { color ->
                            Box(
                                Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(color))
                                    .border(
                                        width = if (color == draft.color) 3.dp else 0.dp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        shape = CircleShape,
                                    )
                                    .clickable { draft = draft.copy(color = color) },
                            )
                        }
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 12.dp),
                    ) {
                        SymbolIcons.all.forEach { (name, vector) ->
                            val selected = draft.iconKind == IconKind.SYMBOL && draft.symbol == name
                            Box(
                                Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceVariant,
                                    )
                                    .clickable { draft = draft.copy(iconKind = IconKind.SYMBOL, symbol = name) },
                                contentAlignment = Alignment.Center,
                            ) { Icon(vector, contentDescription = name) }
                        }
                    }
                }
            }
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
                ) {
                    Text(
                        stringResource(R.string.group_apps, draft.apps.size),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { showReorder = true }, enabled = draft.apps.size > 1) {
                        Icon(Icons.Filled.DragIndicator, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.reorder), modifier = Modifier.padding(start = 6.dp))
                    }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            items(filtered, key = { it.ref.encode() }) { app ->
                val checked = app.ref in draft.apps
                val toggle = {
                    draft = draft.copy(apps = if (checked) draft.apps - app.ref else draft.apps + app.ref)
                }
                ListItem(
                    modifier = Modifier.clickable { toggle() },
                    leadingContent = {
                        Image(
                            bitmap = remember(app) { AppIconBitmap.of(app.activity.getIcon(0)) },
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                        )
                    },
                    headlineContent = { Text(app.label) },
                    trailingContent = { Checkbox(checked = checked, onCheckedChange = { toggle() }) },
                )
            }
        }
    }

    if (showAppIconPicker) {
        AppIconPickerDialog(
            apps = apps,
            onPick = {
                draft = draft.copy(iconKind = IconKind.APP, iconApp = it.ref)
                showAppIconPicker = false
            },
            onDismiss = { showAppIconPicker = false },
        )
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text(stringResource(R.string.delete_confirm_title)) },
            text = { Text(stringResource(R.string.delete_confirm_message)) },
            confirmButton = {
                TextButton(onClick = { showDelete = false; onDelete() }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun IconPreview(group: Group, apps: List<AppInfo>) {
    val modifier = Modifier.size(72.dp).clip(CircleShape)
    val app = if (group.iconKind == IconKind.APP) apps.firstOrNull { it.ref == group.iconApp } else null
    if (app != null) {
        Image(
            bitmap = remember(app) { AppIconBitmap.of(app.activity.getIcon(0)) },
            contentDescription = null,
            modifier = modifier,
        )
    } else {
        Box(modifier.background(Color(group.color)), contentAlignment = Alignment.Center) {
            Icon(SymbolIcons.vector(group.symbol), contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
        }
    }
}

@Composable
private fun AppIconPickerDialog(apps: List<AppInfo>, onPick: (AppInfo) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, apps) { apps.filter { it.label.contains(query, ignoreCase = true) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.choose_app_icon)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(64.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    gridItems(filtered, key = { it.ref.encode() }) { app ->
                        Image(
                            bitmap = remember(app) { AppIconBitmap.of(app.activity.getIcon(0)) },
                            contentDescription = app.label,
                            modifier = Modifier.size(56.dp).clickable { onPick(app) },
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

package it.armando.appgroups

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

class PopupActivity : ComponentActivity() {
    private var groupId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        groupId = intent.getStringExtra(ShortcutHelper.EXTRA_GROUP_ID)
        setContent {
            AppGroupsTheme {
                GroupSheet(groupId = groupId, onDismiss = ::finish)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        groupId = intent.getStringExtra(ShortcutHelper.EXTRA_GROUP_ID)
    }
}

/** Bottom panel over a dimmed scrim: taps outside close it, and it cannot be dragged away. */
@Composable
private fun BottomPanel(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .pointerInput(Unit) { detectTapGestures { } },
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp), content = content)
        }
    }
}

@Composable
private fun GroupSheet(groupId: String?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val group = remember(groupId) { groupId?.let { GroupRepository(context).get(it) } }
    val apps by produceState<List<PopupApp>?>(null, group) {
        value = withContext(Dispatchers.Default) {
            group?.apps.orEmpty()
                .mapNotNull { AppCatalog.find(context, it) }
                .map { PopupApp(it, it.label.toString(), AppIconBitmap.of(it.getIcon(0))) }
        }
    }
    val settings = remember(groupId) { GroupRepository(context).popupSettings() }

    BottomPanel(onDismiss) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
        ) {
            Text(
                text = group?.label ?: stringResource(R.string.group_not_found),
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
            if (group != null) {
                IconButton(onClick = {
                    openEditor(context, group)
                    onDismiss()
                }) {
                    Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.edit_group))
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close))
            }
        }
        val rowSpacing = 8.dp
        val itemHeight = with(LocalDensity.current) {
            56.dp + 4.dp + MaterialTheme.typography.labelMedium.lineHeight.toDp() * 2
        }
        val expectedRows = minOf(settings.rows, (group?.apps?.size ?: 0).let { (it + settings.columns - 1) / settings.columns })
        val loadedApps = apps
        if (loadedApps == null) {
            // Reserve the final height so the panel doesn't jump once the icons are ready.
            Spacer(Modifier.height(itemHeight * expectedRows + rowSpacing * (expectedRows - 1).coerceAtLeast(0) + 16.dp))
        } else if (loadedApps.isEmpty()) {
            Text(
                text = stringResource(R.string.empty_popup),
                modifier = Modifier.padding(vertical = 24.dp),
            )
        } else {
            val failed = stringResource(R.string.app_not_launchable)
            val gridState = rememberLazyGridState()
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(settings.columns),
                verticalArrangement = Arrangement.spacedBy(rowSpacing),
                flingBehavior = rememberSnapFlingBehavior(gridState, SnapPosition.Start),
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .heightIn(max = itemHeight * settings.rows + rowSpacing * (settings.rows - 1)),
            ) {
                items(loadedApps, key = { it.key }) { app ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .height(itemHeight)
                            .clickable {
                                if (AppCatalog.launch(context, app.info)) onDismiss()
                                else Toast.makeText(context, failed, Toast.LENGTH_SHORT).show()
                            },
                    ) {
                        Image(bitmap = app.icon, contentDescription = null, modifier = Modifier.size(56.dp))
                        Text(
                            text = app.label,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

private class PopupApp(val info: LauncherActivityInfo, val label: String, val icon: ImageBitmap) {
    val key = info.componentName.flattenToString() + info.user.hashCode()
}

private fun openEditor(context: Context, group: Group) {
    context.startActivity(
        Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_EDIT_GROUP_ID, group.id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

package it.armando.appgroups

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/** Lets the user pick which group a widget opens, both on placement and when re-assigning it later. */
class WidgetConfigActivity : ComponentActivity() {
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        setContent {
            AppGroupsTheme { GroupPicker(onPick = ::pick, onDismiss = ::finish) }
        }
    }

    private fun pick(group: Group) {
        GroupRepository(this).setWidgetGroup(widgetId, group.id)
        WidgetHelper.update(this, AppWidgetManager.getInstance(this), widgetId)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupPicker(onPick: (Group) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { GroupRepository(context) }
    val groups = remember { repo.all() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Box(Modifier.navigationBarsPadding()) {
            if (groups.isEmpty()) {
                Text(
                    text = stringResource(R.string.empty_groups_picker),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(24.dp),
                )
            } else {
                LazyColumn {
                    items(groups, key = { it.id }) { group ->
                        ListItem(
                            modifier = Modifier.clickable { onPick(group) },
                            leadingContent = { GroupIcon(group, repo, 48) },
                            headlineContent = { Text(group.label) },
                        )
                    }
                }
            }
        }
    }
}

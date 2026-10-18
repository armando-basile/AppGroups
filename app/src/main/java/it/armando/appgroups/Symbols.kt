package it.armando.appgroups

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext

object SymbolIcons {
    const val default = "apps"

    val colors = listOf(
        0xFF1E5BD8.toInt(), 0xFF00897B.toInt(), 0xFF43A047.toInt(), 0xFFF9A825.toInt(),
        0xFFEF6C00.toInt(), 0xFFD32F2F.toInt(), 0xFFC2185B.toInt(), 0xFF7B1FA2.toInt(),
        0xFF455A64.toInt(), 0xFF212121.toInt(),
    )

    val all: Map<String, ImageVector> = linkedMapOf(
        "apps" to Icons.Filled.Apps,
        "home" to Icons.Filled.Home,
        "work" to Icons.Filled.Work,
        "school" to Icons.Filled.School,
        "games" to Icons.Filled.SportsEsports,
        "chat" to Icons.AutoMirrored.Filled.Chat,
        "mail" to Icons.Filled.Mail,
        "call" to Icons.Filled.Call,
        "camera" to Icons.Filled.PhotoCamera,
        "music" to Icons.Filled.MusicNote,
        "movie" to Icons.Filled.Movie,
        "tv" to Icons.Filled.Tv,
        "headphones" to Icons.Filled.Headphones,
        "shopping" to Icons.Filled.ShoppingCart,
        "bank" to Icons.Filled.AccountBalance,
        "payments" to Icons.Filled.Payments,
        "tools" to Icons.Filled.Build,
        "settings" to Icons.Filled.Settings,
        "web" to Icons.Filled.Language,
        "folder" to Icons.Filled.Folder,
        "star" to Icons.Filled.Star,
        "favorite" to Icons.Filled.Favorite,
        "map" to Icons.Filled.Map,
        "car" to Icons.Filled.DirectionsCar,
        "flight" to Icons.Filled.Flight,
        "restaurant" to Icons.Filled.Restaurant,
        "fitness" to Icons.Filled.FitnessCenter,
        "book" to Icons.Filled.Book,
        "news" to Icons.Filled.Newspaper,
        "lock" to Icons.Filled.Lock,
        "security" to Icons.Filled.Security,
        "cloud" to Icons.Filled.Cloud,
        "code" to Icons.Filled.Code,
        "terminal" to Icons.Filled.Terminal,
        "dashboard" to Icons.Filled.Dashboard,
        "bolt" to Icons.Filled.Bolt,
        "pets" to Icons.Filled.Pets,
        "health" to Icons.Filled.MonitorHeart,
        "photo" to Icons.Filled.Image,
        "calendar" to Icons.Filled.CalendarMonth,
    )

    fun vector(name: String): ImageVector = all[name] ?: all.getValue(default)
}

@Composable
fun AppGroupsTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

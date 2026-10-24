package com.bipminder.app

import android.Manifest
import android.net.Uri
import android.os.Build
import android.content.Intent
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val tick = mutableIntStateOf(0)
    private val openId = mutableLongStateOf(-1L)
    private val vm: MainViewModel by viewModels()

    private val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { tick.intValue++ }
    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) vm.export(uri)
    }
    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.import(uri)
    }

    private fun applyBars(dark: Boolean) {
        val style = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    private fun readIntent(i: Intent?) {
        val id = i?.getLongExtra("openId", -1L) ?: -1L
        if (id > 0) openId.longValue = id
    }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        Notifier.ensureChannel(this)
        Fmt.h24 = DateFormat.is24HourFormat(this)
        readIntent(intent)
        setContent {
            val s by vm.settings.collectAsState()
            val dark = when (s.theme) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
            SideEffect { applyBars(dark) }
            BipTheme(dark) {
                BipApp(
                    vm, tick.intValue, openId.longValue, { openId.longValue = -1L },
                    { if (Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS) },
                    { exportLauncher.launch("BipMinder-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date()) + ".zip") },
                    { importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        Fmt.h24 = DateFormat.is24HourFormat(this)
        tick.intValue++
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", "Home", Icons.Outlined.Home),
    Tab("tasks", "Tasks", Icons.Outlined.TaskAlt),
    Tab("calendar", "Calendar", Icons.Outlined.CalendarMonth),
    Tab("gantt", "Gantt", Icons.Outlined.Timeline),
    Tab("progress", "Progress", Icons.Outlined.BarChart),
    Tab("settings", "Settings", Icons.Outlined.Settings)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BipApp(
    vm: MainViewModel, tick: Int, openId: Long, onOpened: () -> Unit,
    requestNotif: () -> Unit, exportBackup: () -> Unit, importBackup: () -> Unit
) {
    val ctx = LocalContext.current
    val nav = rememberNavController()
    val all by vm.items.collectAsState()
    val s by vm.settings.collectAsState()
    val snack = remember { SnackbarHostState() }
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val isTab = tabs.any { it.route == route }
    var quick by remember { mutableStateOf(false) }
    var calSel by rememberSaveable { mutableStateOf(today()) }

    LaunchedEffect(Unit) {
        vm.undo.collect { item ->
            val r = snack.showSnackbar("Deleted \"${item.title}\"", "Undo", duration = SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) vm.restore(item)
        }
    }
    LaunchedEffect(Unit) { vm.toast.collect { snack.showSnackbar(it, duration = SnackbarDuration.Short) } }
    LaunchedEffect(openId) {
        if (openId > 0) { nav.navigate("edit/$openId"); onOpened() }
    }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && !Notifier.canPost(ctx)) requestNotif()
    }

    val warn = remember(tick, s.notifications) {
        when {
            !s.notifications -> null
            !Notifier.canPost(ctx) -> "Notifications are blocked, so reminders cannot appear."
            !Alarms.canExact(ctx) -> "Exact alarms are off, so reminders may arrive late."
            else -> null
        }
    }

    fun goTab(r: String) = nav.navigate(r) {
        popUpTo("home") { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
    val open: (Item) -> Unit = { nav.navigate("edit/${it.id}") }
    fun leaveEditor() {
        if (nav.currentDestination?.route == "edit/{id}") nav.popBackStack()
    }

    Scaffold(
        containerColor = C.bg,
        snackbarHost = {
            SnackbarHost(snack) { data ->
                Snackbar(data, containerColor = C.text, contentColor = C.bg, actionColor = if (C.dark) C.accent else Color(0xFFB8BDF2))
            }
        },
        bottomBar = {
            if (isTab) {
                NavigationBar(containerColor = C.card, tonalElevation = 0.dp, modifier = Modifier.shadow(10.dp, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))) {
                    tabs.forEach { t ->
                        NavigationBarItem(
                            selected = route == t.route, onClick = { goTab(t.route) },
                            icon = { Icon(t.icon, t.label, modifier = Modifier.size(24.dp)) },
                            label = { BText(t.label, 10, 500, maxLines = 1) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = C.accent, selectedTextColor = C.accent,
                                unselectedIconColor = C.muted, unselectedTextColor = C.muted,
                                indicatorColor = C.accent.copy(alpha = 0.14f)
                            )
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (route in listOf("home", "tasks", "calendar", "gantt")) {
                FloatingActionButton(
                    onClick = { quick = true }, shape = CircleShape, containerColor = C.accent, contentColor = C.onAccent,
                    elevation = FloatingActionButtonDefaults.elevation(6.dp)
                ) { Icon(Icons.Outlined.Add, "Add", modifier = Modifier.size(28.dp)) }
            }
        }
    ) { pad ->
        NavHost(
            nav, startDestination = "home", modifier = Modifier.padding(pad),
            enterTransition = { fadeIn(tween(220)) }, exitTransition = { fadeOut(tween(160)) },
            popEnterTransition = { fadeIn(tween(220)) }, popExitTransition = { fadeOut(tween(160)) }
        ) {
            composable("home") {
                HomeScreen(all, s, warn, { goTab("settings") }, open, vm::toggle, vm::duplicate, { vm.delete(it) })
            }
            composable("tasks") { TasksScreen(all, open, vm::toggle, vm::duplicate, { vm.delete(it) }) }
            composable("calendar") {
                CalendarScreen(all, s, calSel, { calSel = it }, open, vm::toggle, vm::duplicate, { vm.delete(it) })
            }
            composable("gantt") { GanttScreen(all, s, open) }
            composable("progress") { ProgressScreen(all, s, open) }
            composable("settings") {
                SettingsScreen(s, tick, { f -> vm.updateSettings(f) }, requestNotif, exportBackup, importBackup, all.size)
            }
            composable(
                "edit/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType }),
                enterTransition = { slideInVertically(tween(260)) { it / 6 } + fadeIn(tween(220)) },
                exitTransition = { fadeOut(tween(160)) },
                popEnterTransition = { fadeIn(tween(200)) },
                popExitTransition = { slideOutVertically(tween(220)) { it / 6 } + fadeOut(tween(180)) }
            ) { be ->
                val id = be.arguments?.getLong("id") ?: 0L
                val existing = all.firstOrNull { it.id == id }
                if (id != 0L && existing == null) {
                    // item was deleted or not loaded yet
                    LaunchedEffect(id) { kotlinx.coroutines.delay(1500); leaveEditor() }
                } else {
                    val seed = remember(id) {
                        existing ?: vm.seed ?: Item(day = today(), reminders = defaultReminders(s))
                    }
                    EditorScreen(
                        initial = seed,
                        categories = all.map { it.category }.filter { it.isNotBlank() }.distinct(),
                        isNew = id == 0L,
                        onBack = { vm.seed = null; leaveEditor() },
                        onSave = { item -> vm.seed = null; vm.save(item) { leaveEditor() } },
                        onDelete = { item -> vm.seed = null; vm.delete(existing ?: item) { leaveEditor() } },
                        onDuplicate = { item -> vm.duplicate(item); leaveEditor() }
                    )
                }
            }
        }
    }

    if (quick) {
        QuickAddSheet(
            baseDay = if (route == "calendar") calSel else today(), s = s,
            onDismiss = { quick = false },
            onAdd = { item -> quick = false; vm.save(item); },
            onDetails = { item -> quick = false; vm.seed = item; nav.navigate("edit/0") }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickAddSheet(baseDay: Long, s: AppSettings, onDismiss: () -> Unit, onAdd: (Item) -> Unit, onDetails: (Item) -> Unit) {
    var text by remember { mutableStateOf("") }
    var type by remember { mutableIntStateOf(Type.TASK) }
    val fr = remember { FocusRequester() }
    val parsed = remember(text, type) { parseQuick(text, type, baseDay, s) }
    LaunchedEffect(Unit) { fr.requestFocus() }
    ModalBottomSheet(
        onDismissRequest = onDismiss, containerColor = C.card,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().imePadding().padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BText("Quick add", 22, 300)
            BField(text, { text = it }, "e.g. Call Sam tomorrow at 5pm", focus = fr, size = 16)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Task", "Event", "Note").forEachIndexed { i, n -> Pill(n, type == i, { type = i }, onCard = true) }
            }
            if (text.isNotBlank()) BText(previewQuick(parsed), 13, 500, C.accent)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onDetails(parsed.copy(title = if (text.isBlank()) "" else parsed.title)) }) {
                    BText("More options", 14, 500, C.muted)
                }
                Box(
                    Modifier.background(if (text.isBlank()) C.accent.copy(alpha = 0.4f) else C.accent, RoundedCornerShape(50))
                        .clickable(enabled = text.isNotBlank()) { onAdd(parsed) }
                        .padding(horizontal = 28.dp, vertical = 12.dp)
                ) { BText("Add", 15, 600, C.onAccent) }
            }
        }
    }
}

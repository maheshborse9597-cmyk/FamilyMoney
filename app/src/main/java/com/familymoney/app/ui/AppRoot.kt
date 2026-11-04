package com.familymoney.app.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familymoney.app.MainActivity
import com.familymoney.app.logic.Calc
import com.familymoney.app.logic.Dates
import kotlinx.coroutines.launch

@Composable
fun AppRoot(vm: AppViewModel, host: MainActivity) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val paired by vm.paired.collectAsStateWithLifecycle()
    val locked by vm.locked.collectAsStateWithLifecycle()
    val dark = when (settings.themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    FamilyMoneyTheme(dark) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when {
                !paired -> PairScreen(vm)
                locked -> LockScreen(vm, host)
                else -> MainShell(vm, host)
            }
        }
    }
}

/* ===================================================================================== */
/*  Pairing                                                                               */
/* ===================================================================================== */

@Composable
fun PairScreen(vm: AppViewModel) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val incoming by vm.incoming.collectAsStateWithLifecycle()
    var url by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var name by remember { mutableStateOf(android.os.Build.MODEL ?: "") }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    fun applyLink(text: String): Boolean {
        val uri = try { Uri.parse(text.trim()) } catch (e: Exception) { null }
        if (uri == null || uri.scheme != "familymoney" || uri.host != "pair") return false
        url = uri.getQueryParameter("u") ?: ""
        key = uri.getQueryParameter("k") ?: ""
        return url.isNotBlank() && key.isNotBlank()
    }

    LaunchedEffect(incoming) {
        val inc = incoming
        if (inc != null && inc.kind == "pair") {
            url = inc.url
            key = inc.key
            vm.incoming.value = null
        }
    }

    Column(
        Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(Pal.Brand, Pal.Brand2))).padding(24.dp)
        ) {
            Column {
                Text("Family Money", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                Text("Connect this phone to your family's spreadsheet.", color = Color.White.copy(alpha = 0.9f), modifier = Modifier.padding(top = 4.dp))
            }
        }

        Text("1. Open the web app on a computer: More › Android app › Pair a new phone.", fontSize = 13.5.sp)
        Text("2. Send the pairing link to this phone and tap it, or copy it and use the button below.", fontSize = 13.5.sp)

        OutlinedButton(onClick = {
            val text = clipboard.getText()?.text ?: ""
            if (applyLink(text)) error = "" else error = "No pairing link found on the clipboard."
        }, modifier = Modifier.fillMaxWidth()) { Text("Paste pairing link") }

        AppField(url, { url = it.trim() }, "API URL (ends with /exec)")
        AppField(key, { key = it.trim() }, "Device key")
        AppField(name, { name = it }, "Name of this phone")

        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)

        Button(
            enabled = !busy && url.isNotBlank() && key.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                busy = true
                error = ""
                scope.launch {
                    val result = vm.pair(url, key, name.ifBlank { "My phone" })
                    busy = false
                    if (result != null) error = result
                }
            }
        ) { Text(if (busy) "Connecting…" else "Connect", fontWeight = FontWeight.ExtraBold) }

        Text(
            "The key stays on this phone, encrypted. Lost the phone? Revoke it from the web app and it is locked out immediately.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/* ===================================================================================== */
/*  Lock                                                                                  */
/* ===================================================================================== */

@Composable
fun LockScreen(vm: AppViewModel, host: MainActivity) {
    var message by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { host.promptUnlock({ vm.unlock() }, { message = it }) }
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Family Money is locked", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(8.dp))
        if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        Spacer(Modifier.height(20.dp))
        Button(onClick = { host.promptUnlock({ vm.unlock() }, { message = it }) }) { Text("Unlock", fontWeight = FontWeight.ExtraBold) }
    }
}

/* ===================================================================================== */
/*  Main shell                                                                            */
/* ===================================================================================== */

private class NavItem(val id: String, val label: String)

private val NAV = listOf(
    NavItem("home", "Home"), NavItem("history", "History"), NavItem("dashboard", "Dashboard"),
    NavItem("bills", "Bills"), NavItem("more", "More")
)

@Composable
private fun NavIcon(id: String, badge: Int) {
    val icon = when (id) {
        "home" -> Icons.Filled.Home
        "history" -> Icons.AutoMirrored.Filled.List
        "dashboard" -> Icons.Filled.Dashboard
        "bills" -> Icons.Filled.Notifications
        else -> Icons.Filled.MoreHoriz
    }
    if (badge > 0) {
        BadgedBox(badge = { Badge { Text(badge.toString()) } }) { Icon(icon, contentDescription = null) }
    } else {
        Icon(icon, contentDescription = null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainShell(vm: AppViewModel, host: MainActivity) {
    val ui = remember { UiState() }
    val snap by vm.effective.collectAsStateWithLifecycle()
    val outbox by vm.outbox.collectAsStateWithLifecycle()
    val syncing by vm.syncing.collectAsStateWithLifecycle()
    val incoming by vm.incoming.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val wide = LocalConfiguration.current.screenWidthDp >= 840
    val today = Dates.today()

    // Messages (with optional Undo) from anywhere in the app.
    LaunchedEffect(Unit) {
        vm.messages.collect { m ->
            launch {
                val result = snackbar.showSnackbar(
                    message = m.text,
                    actionLabel = m.actionLabel,
                    duration = if (m.actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) m.action?.invoke()
            }
        }
    }

    // Links, shortcuts, shared photos and notification taps.
    LaunchedEffect(incoming) {
        val inc = incoming ?: return@LaunchedEffect
        when (inc.kind) {
            "add" -> ui.addTx(if (inc.type in listOf("Expense", "Income", "Investment", "Transfer")) inc.type else "Expense")
            "share" -> ui.addTx("Expense", photo = inc.uri)
            "open" -> ui.tab = if (inc.open == "bills") "bills" else "home"
            "pair" -> vm.emit("This phone is already paired. Unpair it in More > App settings first.", "warn")
        }
        vm.incoming.value = null
    }

    BackHandler(enabled = ui.tab != "home" && ui.panel == null) { ui.tab = "home" }

    val current = snap
    val urgentCount = if (current == null) 0 else Calc.urgentBills(current.bills, today).size
    val moreBadge = if (current == null) 0 else current.recurring.count { it.due } + outbox.count { it.status != "pending" }
    fun badgeFor(id: String): Int = when (id) { "bills" -> urgentCount; "more" -> moreBadge; else -> 0 }

    Box(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize()) {
            if (wide) {
                NavigationRail(
                    header = {
                        FloatingActionButton(onClick = { ui.addTx() }) { Icon(Icons.Filled.Add, contentDescription = "Add transaction") }
                    }
                ) {
                    for (n in NAV) {
                        NavigationRailItem(
                            selected = ui.tab == n.id, onClick = { ui.tab = n.id },
                            icon = { NavIcon(n.id, badgeFor(n.id)) }, label = { Text(n.label) }
                        )
                    }
                }
            }
            Scaffold(
                modifier = Modifier.weight(1f),
                containerColor = MaterialTheme.colorScheme.background,
                snackbarHost = { SnackbarHost(snackbar) },
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    when (ui.tab) {
                                        "home" -> "Family Money"
                                        "history" -> "Transactions"
                                        "dashboard" -> "Dashboard"
                                        "bills" -> "Bills & Reminders"
                                        else -> "More"
                                    },
                                    fontWeight = FontWeight.ExtraBold
                                )
                                val waiting = outbox.count { it.status == "pending" }
                                Text(
                                    if (waiting > 0) "$waiting change${if (waiting == 1) "" else "s"} waiting to sync" else Dates.label(today, true),
                                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        actions = {
                            if (syncing) {
                                CircularProgressIndicator(Modifier.padding(end = 14.dp).size(22.dp), strokeWidth = 2.dp)
                            } else {
                                IconButton(onClick = { vm.refresh(true) }) { Icon(Icons.Filled.Refresh, contentDescription = "Refresh") }
                            }
                        }
                    )
                },
                bottomBar = {
                    if (!wide) {
                        NavigationBar {
                            for (n in NAV) {
                                NavigationBarItem(
                                    selected = ui.tab == n.id, onClick = { ui.tab = n.id },
                                    icon = { NavIcon(n.id, badgeFor(n.id)) }, label = { Text(n.label, fontSize = 11.sp) }
                                )
                            }
                        }
                    }
                },
                floatingActionButton = {
                    if (!wide && ui.tab != "home" && current != null) {
                        FloatingActionButton(onClick = { ui.addTx() }) { Icon(Icons.Filled.Add, contentDescription = "Add transaction") }
                    }
                }
            ) { pad ->
                Box(Modifier.padding(pad).fillMaxSize()) {
                    if (current == null) {
                        Column(
                            Modifier.fillMaxSize().padding(32.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (syncing) {
                                CircularProgressIndicator()
                                Text("Loading your data…", modifier = Modifier.padding(top = 12.dp))
                            } else {
                                Text("No data yet", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                                Text("Connect to the internet to download your data.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
                                Button(onClick = { vm.refresh(true) }) { Text("Try again") }
                            }
                        }
                    } else {
                        when (ui.tab) {
                            "home" -> HomeScreen(current, outbox, vm, ui)
                            "history" -> HistoryScreen(current, vm, ui)
                            "dashboard" -> DashboardScreen(current, vm, ui)
                            "bills" -> BillsScreen(current, vm, ui)
                            else -> MoreScreen(current, outbox, ui)
                        }
                    }
                }
            }
        }

        // Full-screen panels (opened from More, Dashboard and Home) sit above the tabs.
        val panel = ui.panel
        if (panel != null && current != null) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                PanelScreen(panel, current, vm, ui, host)
            }
        }
    }

    if (current != null) Hosts(current, vm, ui)
}

package com.familymoney.app.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familymoney.app.BuildConfig
import com.familymoney.app.MainActivity
import com.familymoney.app.data.AppConfig
import com.familymoney.app.data.Goal
import com.familymoney.app.data.Holding
import com.familymoney.app.data.OutItem
import com.familymoney.app.data.Recurring
import com.familymoney.app.data.Snapshot
import com.familymoney.app.data.Template
import com.familymoney.app.data.arr
import com.familymoney.app.data.obj
import com.familymoney.app.logic.Calc
import com.familymoney.app.logic.Dates
import com.familymoney.app.logic.Fmt
import kotlinx.coroutines.launch
import java.io.File
import java.text.DateFormat
import java.util.Date

/* ===================================================================================== */
/*  More menu                                                                             */
/* ===================================================================================== */

private class MenuItem(val id: String, val icon: ImageVector, val title: String, val subtitle: String)

private val MENU = listOf(
    MenuItem("investments", Icons.Filled.PieChart, "Investments", "Current value and profit / loss"),
    MenuItem("goals", Icons.Filled.Flag, "Savings goals", "Targets with progress"),
    MenuItem("recurring", Icons.Filled.Repeat, "Recurring", "Salary, SIP, EMI posted automatically"),
    MenuItem("templates", Icons.Filled.Bolt, "Quick-add", "One-tap saved entries"),
    MenuItem("cards", Icons.Filled.CreditCard, "Credit cards", "Dues, limits and due dates"),
    MenuItem("budgets", Icons.Filled.Tune, "Budgets", "Set, copy and review"),
    MenuItem("lists", Icons.Filled.Category, "Categories & people", "Edit your dropdown lists"),
    MenuItem("export", Icons.Filled.Download, "Export & reports", "CSV and monthly PDF"),
    MenuItem("trash", Icons.Filled.Delete, "Trash", "Restore deleted items"),
    MenuItem("queue", Icons.Filled.Sync, "Sync queue", "Changes waiting to reach the server"),
    MenuItem("settings", Icons.Filled.Settings, "App settings", "Lock, notifications, theme, widget")
)

@Composable
fun MoreScreen(snap: Snapshot, outbox: List<OutItem>, ui: UiState) {
    val dueRecurring = snap.recurring.count { it.due }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(160.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(MENU) { m ->
            val badge = when (m.id) {
                "recurring" -> dueRecurring
                "trash" -> snap.trashCount
                "queue" -> outbox.size
                else -> 0
            }
            AppCard(onClick = { ui.panel = m.id }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    IconChip(m.icon, MaterialTheme.colorScheme.primary, 40.dp)
                    if (badge > 0) Pill(badge.toString(), badColor())
                }
                Spacer(Modifier.height(10.dp))
                Text(m.title, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                Text(m.subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/* ===================================================================================== */
/*  Panel host                                                                            */
/* ===================================================================================== */

private val PANEL_TITLES = mapOf(
    "investments" to "Investments", "goals" to "Savings goals", "recurring" to "Recurring entries",
    "templates" to "Quick-add templates", "cards" to "Credit cards", "budgets" to "Budgets",
    "lists" to "Categories & people", "export" to "Export & reports", "trash" to "Trash",
    "queue" to "Sync queue", "settings" to "App settings"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PanelScreen(name: String, snap: Snapshot, vm: AppViewModel, ui: UiState, host: MainActivity) {
    val onBack = { ui.panel = null }
    BackHandler(enabled = true) { onBack() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(PANEL_TITLES[name] ?: "", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = { onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { pad ->
        Box(Modifier.padding(pad)) {
            when (name) {
                "investments" -> InvestmentsPanel(snap, vm, ui)
                "goals" -> GoalsPanel(snap, vm, ui)
                "recurring" -> RecurringPanel(snap, vm, ui)
                "templates" -> TemplatesPanel(snap, vm, ui)
                "cards" -> CardsPanel(snap, vm, ui)
                "budgets" -> BudgetsPanel(snap, vm)
                "lists" -> ListsPanel(snap, vm)
                "export" -> ExportPanel(snap, vm)
                "trash" -> TrashPanel(vm)
                "queue" -> QueuePanel(vm)
                "settings" -> SettingsPanel(snap, vm, ui, host)
            }
        }
    }
}

private fun confirmDelete(ui: UiState, vm: AppViewModel, op: String, id: String, name: String) {
    ui.confirm = ConfirmSpec(
        title = "Delete this item?",
        message = "$name will move to Trash (restorable for 30 days).",
        confirmText = "Delete",
        danger = true
    ) { vm.deleteItem(op, id, "Delete $name") }
}

@Composable
private fun AddButton(label: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun EditDelete(onEdit: () -> Unit, onDelete: () -> Unit) {
    OutlinedButton(onClick = onEdit) { Text("Edit") }
    OutlinedButton(onClick = onDelete, colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Delete") }
}

@Composable
private fun Help(text: String) {
    Text(text, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/* ===================================================================================== */
/*  Investments, goals                                                                    */
/* ===================================================================================== */

private fun holdingForm(h: Holding?, cfg: AppConfig, vm: AppViewModel, ui: UiState) {
    ui.form = FormSpec(
        title = if (h == null) "Add investment" else "Edit investment",
        initial = if (h == null) emptyMap() else mapOf(
            "name" to h.name, "type" to h.type, "person" to h.person,
            "invested" to h.invested.toString().removeSuffix(".0"), "current" to h.current.toString().removeSuffix(".0"), "notes" to h.notes
        ),
        fields = listOf(
            Field("name", "Name", FType.Text, required = true, hint = "e.g. Nifty 50 index fund"),
            Field("type", "Type", FType.Select, cfg.investmentCategories, half = true),
            Field("person", "Owner", FType.Select, cfg.persons, half = true),
            Field("invested", "Total invested (₹)", FType.Number, required = true, half = true),
            Field("current", "Current value (₹)", FType.Number, required = true, half = true),
            Field("notes", "Notes", FType.Multiline)
        ),
        onSave = { v ->
            vm.run(
                "saveHolding",
                arr(obj("id" to (h?.id ?: ""), "name" to (v["name"] ?: ""), "type" to (v["type"] ?: ""), "person" to (v["person"] ?: ""),
                    "invested" to num(v["invested"]), "current" to num(v["current"]), "notes" to (v["notes"] ?: ""))),
                "Investment ${v["name"]}"
            )
        }
    )
}

@Composable
private fun InvestmentsPanel(snap: Snapshot, vm: AppViewModel, ui: UiState) {
    val invested = snap.holdings.sumOf { it.invested }
    val current = snap.holdings.sumOf { it.current }
    val pl = current - invested
    PageList {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Invested", Fmt.money(invested), Modifier.weight(1f))
                StatTile("Current value", Fmt.money(current), Modifier.weight(1f))
                StatTile(
                    "Profit / loss", Fmt.money(pl) + if (invested > 0) " (" + String.format(java.util.Locale.US, "%.1f", pl / invested * 100) + "%)" else "",
                    Modifier.weight(1f), if (pl < 0) badColor() else okColor()
                )
            }
        }
        item {
            AppCard {
                if (snap.holdings.isEmpty()) EmptyState("No investments yet", "Add your mutual funds, stocks, FDs and gold.")
                for (h in snap.holdings) {
                    val d = h.current - h.invested
                    PanelRow(
                        h.name, listOf(h.type, h.person).filter { it.isNotBlank() }.joinToString(" · ") + " · updated " + Dates.label(h.updated),
                        Fmt.money(h.current) + "\n" + (if (d >= 0) "+" else "") + Fmt.money(d), if (d < 0) badColor() else okColor()
                    ) { EditDelete({ holdingForm(h, snap.config, vm, ui) }, { confirmDelete(ui, vm, "deleteHolding", h.id, h.name) }) }
                }
            }
        }
        item { AddButton("Add investment") { holdingForm(null, snap.config, vm, ui) } }
        item { Help("Prices are not fetched automatically. Update “Current value” whenever you check your investment app.") }
    }
}

private fun goalForm(g: Goal?, vm: AppViewModel, ui: UiState) {
    ui.form = FormSpec(
        title = if (g == null) "New goal" else "Edit goal",
        initial = if (g == null) emptyMap() else mapOf(
            "name" to g.name, "target" to g.target.toString().removeSuffix(".0"), "saved" to g.saved.toString().removeSuffix(".0"),
            "targetDate" to g.targetDate, "notes" to g.notes
        ),
        fields = listOf(
            Field("name", "Goal name", FType.Text, required = true, hint = "e.g. Family trip"),
            Field("target", "Target (₹)", FType.Number, required = true, half = true),
            Field("saved", "Saved so far (₹)", FType.Number, half = true),
            Field("targetDate", "Target date (optional)", FType.Date, clearable = true),
            Field("notes", "Notes", FType.Multiline)
        ),
        onSave = { v ->
            vm.run(
                "saveGoal",
                arr(obj("id" to (g?.id ?: ""), "name" to (v["name"] ?: ""), "target" to num(v["target"]), "saved" to num(v["saved"]),
                    "targetDate" to (v["targetDate"] ?: ""), "notes" to (v["notes"] ?: ""))),
                "Goal ${v["name"]}"
            )
        }
    )
}

private fun goalMoney(g: Goal, vm: AppViewModel, ui: UiState) {
    ui.form = FormSpec(
        title = "Add money · " + g.name,
        saveText = "Add",
        intro = "Saved so far: ${Fmt.money(g.saved)} of ${Fmt.money(g.target)}. Use a negative number to withdraw.",
        fields = listOf(Field("amount", "Amount (₹)", FType.Number, required = true)),
        onSave = { v -> vm.run("addGoalMoney", arr(g.id, num(v["amount"])), "Goal ${g.name}") }
    )
}

@Composable
private fun GoalsPanel(snap: Snapshot, vm: AppViewModel, ui: UiState) {
    val today = Dates.today()
    PageList {
        item {
            AppCard {
                if (snap.goals.isEmpty()) EmptyState("No goals yet", "Create a goal like “Family trip” or “Emergency fund”.")
                for (g in snap.goals) {
                    val p = if (g.target > 0) (g.saved / g.target).toFloat() else 0f
                    Column(Modifier.padding(vertical = 8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(g.name, fontWeight = FontWeight.ExtraBold)
                            Text(Fmt.money(g.saved) + " / " + Fmt.money(g.target), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        AppProgress(p, if (p >= 1f) okColor() else MaterialTheme.colorScheme.primary, Modifier.padding(vertical = 6.dp), 10.dp)
                        val need = Calc.goalNeed(g, today)
                        Text(
                            "${(p * 100).toInt()}%" + if (need.isNotBlank()) " · $need" else "",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { goalMoney(g, vm, ui) }) { Text("+ Add money") }
                            EditDelete({ goalForm(g, vm, ui) }, { confirmDelete(ui, vm, "deleteGoal", g.id, g.name) })
                        }
                    }
                }
            }
        }
        item { AddButton("New goal") { goalForm(null, vm, ui) } }
        item { Help("Goal savings are tracked here only; they do not change your bank balances.") }
    }
}

/* ===================================================================================== */
/*  Recurring & templates (share the transaction field list)                              */
/* ===================================================================================== */

private fun txFields(cfg: AppConfig, schedule: Boolean): List<Field> {
    val accounts = cfg.banks + cfg.cards
    val allCategories = (cfg.expenseCategories + cfg.incomeCategories + cfg.investmentCategories).distinct()
    val notTransfer: (Map<String, String>) -> Boolean = { it["type"] != "Transfer" }
    val isTransfer: (Map<String, String>) -> Boolean = { it["type"] == "Transfer" }
    val list = ArrayList<Field>()
    list.add(Field("type", "Type", FType.Select, listOf("Expense", "Income", "Investment", "Transfer"), required = true, half = true))
    if (schedule) list.add(Field("frequency", "Repeats", FType.Select, cfg.recurringFrequencies, required = true, half = true))
    list.add(Field("amount", "Amount (₹)", FType.Number, required = true, half = true))
    if (schedule) {
        list.add(Field("nextDate", "Next date", FType.Date, required = true, half = true))
        list.add(Field("endDate", "Ends on (optional)", FType.Date, half = true, clearable = true))
    }
    list.add(Field("category", "Category", FType.Select, allCategories, required = true, half = true, showWhen = notTransfer))
    list.add(Field("description", "Description", FType.Text, required = true, half = true, showWhen = notTransfer))
    list.add(Field("paymentMode", "Payment mode", FType.Select, cfg.expenseModes, required = true, half = true))
    list.add(Field("person", "Person (From person for transfers)", FType.Select, cfg.persons, required = true, half = true))
    list.add(Field("bank", "Account / card (From for transfers)", FType.Select, accounts, required = true, half = true))
    list.add(Field("toBank", "To account / card", FType.Select, accounts, required = true, half = true, showWhen = isTransfer))
    list.add(Field("toPerson", "To person", FType.Select, cfg.persons, required = true, half = true, showWhen = isTransfer))
    list.add(Field("notes", "Notes (optional)", FType.Multiline))
    return list
}

private fun txPayload(v: Map<String, String>, vararg extra: Pair<String, Any?>) = obj(
    "type" to (v["type"] ?: ""), "category" to (v["category"] ?: ""), "description" to (v["description"] ?: ""),
    "amount" to num(v["amount"]), "paymentMode" to (v["paymentMode"] ?: ""), "person" to (v["person"] ?: ""),
    "bank" to (v["bank"] ?: ""), "toBank" to (v["toBank"] ?: ""), "toPerson" to (v["toPerson"] ?: ""), "notes" to (v["notes"] ?: ""),
    *extra
)

private fun recurringForm(r: Recurring?, cfg: AppConfig, vm: AppViewModel, ui: UiState) {
    ui.form = FormSpec(
        title = if (r == null) "New recurring entry" else "Edit recurring entry",
        initial = if (r == null) mapOf("nextDate" to Dates.today(), "frequency" to "Monthly") else mapOf(
            "type" to r.type, "frequency" to r.frequency, "amount" to r.amount.toString().removeSuffix(".0"), "nextDate" to r.nextDate,
            "endDate" to r.endDate, "category" to r.category, "description" to r.description, "paymentMode" to r.paymentMode,
            "person" to r.person, "bank" to r.bank, "toBank" to r.toBank, "toPerson" to r.toPerson, "notes" to r.notes
        ),
        fields = txFields(cfg, true),
        onSave = { v ->
            vm.run(
                "saveRecurring",
                arr(txPayload(v, "id" to (r?.id ?: ""), "frequency" to (v["frequency"] ?: ""), "nextDate" to (v["nextDate"] ?: ""),
                    "endDate" to (v["endDate"] ?: ""), "active" to (r?.active ?: true))),
                "Recurring ${v["description"]}"
            )
        }
    )
}

@Composable
private fun RecurringPanel(snap: Snapshot, vm: AppViewModel, ui: UiState) {
    val scope = rememberCoroutineScope()
    val due = snap.recurring.count { it.due }
    PageList {
        if (due > 0) {
            item {
                Button(onClick = { vm.postRecurringNow() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Post $due due ${if (due == 1) "entry" else "entries"} now", fontWeight = FontWeight.ExtraBold)
                }
            }
        }
        item {
            AppCard {
                if (snap.recurring.isEmpty()) EmptyState("Nothing recurring yet", "Add salary, SIPs, EMIs or rent once and they post automatically.")
                for (r in snap.recurring) {
                    PanelRow(
                        r.description.ifBlank { r.category } + (if (r.active) "" else " (paused)"),
                        r.type + " · " + r.frequency + " · next " + Dates.label(r.nextDate) + (if (r.endDate.isNotBlank()) " · ends " + Dates.label(r.endDate) else "") +
                            " · " + (if (r.type == "Transfer") r.bank + " → " + r.toBank else r.bank) + (if (r.due) " · DUE" else ""),
                        (if (r.type == "Income") "+" else "") + Fmt.money(r.amount)
                    ) {
                        OutlinedButton(onClick = {
                            scope.launch {
                                val err = vm.run("toggleRecurring", arr(r.id, !r.active), "Recurring ${r.description}")
                                if (err != null) vm.emit(err, "err")
                            }
                        }) { Text(if (r.active) "Pause" else "Resume") }
                        EditDelete({ recurringForm(r, snap.config, vm, ui) }, { confirmDelete(ui, vm, "deleteRecurring", r.id, r.description.ifBlank { r.category }) })
                    }
                }
            }
        }
        item { AddButton("Add recurring entry") { recurringForm(null, snap.config, vm, ui) } }
        item { Help("Posted every morning by the server's daily job, and any time you tap “Post now”. Missed days are caught up.") }
    }
}

private fun templateForm(t: Template?, cfg: AppConfig, vm: AppViewModel, ui: UiState) {
    ui.form = FormSpec(
        title = if (t == null) "New template" else "Edit template",
        initial = if (t == null) emptyMap() else mapOf(
            "name" to t.name, "type" to t.type, "amount" to t.amount.toString().removeSuffix(".0"), "category" to t.category,
            "description" to t.description, "paymentMode" to t.paymentMode, "person" to t.person, "bank" to t.bank,
            "toBank" to t.toBank, "toPerson" to t.toPerson, "notes" to t.notes
        ),
        fields = listOf(Field("name", "Template name", FType.Text, required = true, hint = "e.g. Milk")) + txFields(cfg, false),
        onSave = { v -> vm.run("saveTemplate", arr(txPayload(v, "id" to (t?.id ?: ""), "name" to (v["name"] ?: ""))), "Template ${v["name"]}") }
    )
}

@Composable
private fun TemplatesPanel(snap: Snapshot, vm: AppViewModel, ui: UiState) {
    PageList {
        item {
            AppCard {
                if (snap.templates.isEmpty()) EmptyState("No templates yet", "Tick “Save as quick-add template” when adding a transaction.")
                for (t in snap.templates) {
                    PanelRow(t.name, t.type + " · " + t.description.ifBlank { t.category }, Fmt.money(t.amount)) {
                        EditDelete({ templateForm(t, snap.config, vm, ui) }, { confirmDelete(ui, vm, "deleteTemplate", t.id, t.name) })
                    }
                }
            }
        }
        item { AddButton("New template") { templateForm(null, snap.config, vm, ui) } }
        item { Help("Templates appear on Home. One tap saves the entry dated today, with Undo.") }
    }
}

/* ===================================================================================== */
/*  Credit cards                                                                          */
/* ===================================================================================== */

@Composable
private fun CardsPanel(snap: Snapshot, vm: AppViewModel, ui: UiState) {
    fun form(c: com.familymoney.app.data.Card?) {
        ui.form = FormSpec(
            title = if (c == null) "Add credit card" else "Edit credit card",
            initial = if (c == null) emptyMap() else mapOf(
                "name" to c.name, "holder" to c.holder, "limit" to c.limit.toString().removeSuffix(".0"),
                "openingOutstanding" to c.openingOutstanding.toString().removeSuffix(".0"),
                "billingDay" to (if (c.billingDay > 0) c.billingDay.toString() else ""), "dueDay" to (if (c.dueDay > 0) c.dueDay.toString() else "")
            ),
            fields = listOf(
                Field("name", "Card name", FType.Text, required = true, hint = "e.g. HDFC Regalia"),
                Field("holder", "Holder", FType.Select, snap.config.persons, half = true),
                Field("limit", "Credit limit (₹)", FType.Number, half = true),
                Field("openingOutstanding", "Outstanding today (₹)", FType.Number, half = true),
                Field("billingDay", "Statement day (1-31)", FType.Number, half = true),
                Field("dueDay", "Payment due day (1-31)", FType.Number)
            ),
            onSave = { v ->
                vm.run(
                    "saveCard",
                    arr(obj("id" to (c?.cardId ?: ""), "name" to (v["name"] ?: ""), "holder" to (v["holder"] ?: ""), "limit" to num(v["limit"]),
                        "openingOutstanding" to num(v["openingOutstanding"]), "billingDay" to num(v["billingDay"]).toInt(), "dueDay" to num(v["dueDay"]).toInt())),
                    "Card ${v["name"]}"
                )
            }
        )
    }
    PageList {
        item {
            AppCard {
                if (snap.cards.isEmpty()) EmptyState("No credit cards", "Add a card to track dues separately from bank balances.")
                for (c in snap.cards) {
                    PanelRow(
                        c.name,
                        listOf(c.holder, if (c.limit > 0) "Limit " + Fmt.money(c.limit) else "", if (c.dueDay > 0) "Due day ${c.dueDay}" else "").filter { it.isNotBlank() }.joinToString(" · "),
                        Fmt.money(c.outstanding), typeColor("Expense")
                    ) { EditDelete({ form(c) }, { confirmDelete(ui, vm, "deleteCard", c.cardId, c.name) }) }
                }
            }
        }
        item { AddButton("Add credit card") { form(null) } }
        item {
            Help("How it works: choose payment mode “Credit Card” and pick the card when adding an expense, so your bank balance stays untouched. " +
                "When you pay the card bill, add a Transfer from your bank to the card.")
        }
    }
}

/* ===================================================================================== */
/*  Budgets editor, lists                                                                 */
/* ===================================================================================== */

@Composable
private fun BudgetsPanel(snap: Snapshot, vm: AppViewModel) {
    val month by vm.month.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val existing = snap.budgets.filter { it.month == month }
    val categories = (snap.config.expenseCategories + existing.map { it.category }).distinctBy { it.lowercase() }
    val amounts = remember(month, snap.budgets) {
        mutableStateMapOf<String, String>().apply {
            for (c in categories) {
                val e = existing.firstOrNull { it.category.equals(c, ignoreCase = true) }
                put(c, if (e != null && e.amount > 0) e.amount.toString().removeSuffix(".0") else "")
            }
        }
    }
    var saving by remember { mutableStateOf(false) }

    PageList {
        item {
            Text("Monthly budget for " + Dates.monthLabel(month) + " (change the month on the Dashboard). Leave blank for no budget.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            AppCard {
                for (c in categories) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(c, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        AppField(amounts[c] ?: "", { amounts[c] = it }, "₹", Modifier.width(140.dp), number = true)
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = {
                    scope.launch {
                        val err = vm.run("copyBudgets", arr(Dates.shiftMonth(month, -1), month), "Copy budgets")
                        if (err != null) vm.emit(err, "err")
                    }
                }, modifier = Modifier.weight(1f)) { Text("Copy from " + Dates.shortMonth(Dates.shiftMonth(month, -1))) }
                Button(enabled = !saving, onClick = {
                    saving = true
                    scope.launch {
                        val items = categories.map { obj("category" to it, "amount" to num(amounts[it])) }
                        val err = vm.run("saveBudgets", arr(month, items), "Save budgets")
                        saving = false
                        if (err != null) vm.emit(err, "err")
                    }
                }, modifier = Modifier.weight(1f)) { Text(if (saving) "Saving…" else "Save budgets", fontWeight = FontWeight.ExtraBold) }
            }
        }
    }
}

@Composable
private fun ListsPanel(snap: Snapshot, vm: AppViewModel) {
    val scope = rememberCoroutineScope()
    val cfg = snap.config
    val lists = listOf(
        "Expense Category" to cfg.expenseCategories, "Income Category" to cfg.incomeCategories,
        "Investment Category" to cfg.investmentCategories, "Bill Category" to cfg.billCategories, "Person" to cfg.persons
    )
    PageList {
        for ((listName, values) in lists) {
            item(key = listName) {
                var newValue by remember { mutableStateOf("") }
                AppCard {
                    Text(if (listName == "Person") "People (family members)" else listName.replace("Category", "categories"), fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(8.dp))
                    for (v in values) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(v, modifier = Modifier.weight(1f))
                            IconButton(onClick = {
                                scope.launch {
                                    val err = vm.run("removeListItem", arr(listName, v), "Remove $v", silent = true)
                                    if (err != null) vm.emit(err, "err")
                                }
                            }) { Icon(Icons.Filled.Close, contentDescription = "Remove $v") }
                        }
                    }
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppField(newValue, { newValue = it }, "Add new…", Modifier.weight(1f))
                        Button(onClick = {
                            val value = newValue.trim()
                            if (value.isNotEmpty()) {
                                scope.launch {
                                    val err = vm.run("addListItem", arr(listName, value), "Add $value", silent = true)
                                    if (err != null) vm.emit(err, "err") else newValue = ""
                                }
                            }
                        }) { Text("Add") }
                    }
                }
            }
        }
        item { Help("Removing an item only hides it from the dropdowns; old transactions keep their category.") }
    }
}

/* ===================================================================================== */
/*  Export, trash, queue                                                                  */
/* ===================================================================================== */

@Composable
private fun ExportPanel(snap: Snapshot, vm: AppViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val month by vm.month.collectAsStateWithLifecycle()
    var chosen by remember { mutableStateOf(month) }
    var csvText by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val monthLabels = Calc.monthsAvailable(snap.transactions, Dates.today(), chosen).associateBy { Dates.monthLabel(it) }

    val saveCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(csvText.toByteArray(Charsets.UTF_8)) }
                vm.emit("File saved.", "ok")
            } catch (e: Exception) {
                vm.emit("Could not save the file.", "err")
            }
        }
    }

    PageList {
        item { DropdownField("Month", Dates.monthLabel(chosen), monthLabels.keys.toList(), { chosen = monthLabels[it] ?: chosen }) }
        item {
            Button(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
                busy = true
                scope.launch {
                    try {
                        val (name, bytes) = vm.fetchReport(chosen)
                        val dir = File(ctx.cacheDir, "shared").apply { mkdirs() }
                        val file = File(dir, name)
                        file.writeBytes(bytes)
                        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "application/pdf"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        ctx.startActivity(Intent.createChooser(send, "Share report").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (e: Exception) {
                        vm.emit(e.message ?: "Could not build the report. You need an internet connection.", "err")
                    }
                    busy = false
                }
            }) { Text(if (busy) "Building…" else "Monthly PDF report (share)", fontWeight = FontWeight.ExtraBold) }
        }
        item {
            OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = {
                csvText = Calc.csv(Calc.inMonth(snap.transactions, chosen))
                saveCsv.launch("family-money-$chosen.csv")
            }) { Text("Month as CSV") }
        }
        item {
            OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = {
                csvText = Calc.csv(snap.transactions)
                saveCsv.launch("family-money-all.csv")
            }) { Text("All transactions as CSV") }
        }
        item { Help("CSV files open in Excel or Google Sheets. The PDF has the month's totals, categories, budget and every transaction.") }
    }
}

@Composable
private fun TrashPanel(vm: AppViewModel) {
    var items by remember { mutableStateOf<List<TrashItem>?>(null) }
    var error by remember { mutableStateOf("") }
    var reload by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(reload) {
        try {
            items = vm.fetchTrash()
            error = ""
        } catch (e: Exception) {
            error = e.message ?: "Could not load the trash. You need an internet connection."
        }
    }

    PageList {
        item {
            AppCard {
                val list = items
                when {
                    error.isNotBlank() -> EmptyState("Could not load", error)
                    list == null -> EmptyState("Loading…")
                    list.isEmpty() -> EmptyState("Trash is empty", "Deleted items stay here for 30 days.")
                    else -> for (t in list) {
                        PanelRow(t.label.ifBlank { t.kind }, t.kind + " · deleted " + t.deletedAt) {
                            OutlinedButton(onClick = {
                                scope.launch {
                                    val err = vm.run("restoreFromTrash", arr(t.kind, t.id), "Restore ${t.label}", okMsg = "Restored.")
                                    if (err != null) vm.emit(err, "err") else reload++
                                }
                            }) { Text("Restore") }
                        }
                    }
                }
            }
        }
        item { Help("Items are removed permanently after 30 days.") }
    }
}

@Composable
private fun QueuePanel(vm: AppViewModel) {
    val outbox by vm.outbox.collectAsStateWithLifecycle()
    PageList {
        item {
            AppCard {
                if (outbox.isEmpty()) EmptyState("Everything is synced", "Changes made offline wait here until they reach the server.")
                for (o in outbox) {
                    val status = when (o.status) {
                        "pending" -> "Waiting to sync"
                        "conflict" -> "Possible duplicate"
                        else -> "Rejected"
                    }
                    PanelRow(o.label, status + (if (o.error.isNotBlank()) " · " + o.error else "") + " · " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(o.createdAt))) {
                        if (o.status == "conflict") OutlinedButton(onClick = { vm.keepDuplicate(o.rid) }) { Text("Save anyway") }
                        if (o.status == "failed") OutlinedButton(onClick = { vm.retryItem(o.rid) }) { Text("Retry") }
                        OutlinedButton(onClick = { vm.discardItem(o.rid) }, colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Discard") }
                    }
                }
            }
        }
        item { Button(onClick = { vm.refresh(true) }, modifier = Modifier.fillMaxWidth()) { Text("Sync now", fontWeight = FontWeight.ExtraBold) } }
    }
}

/* ===================================================================================== */
/*  App settings                                                                          */
/* ===================================================================================== */

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SettingsPanel(snap: Snapshot, vm: AppViewModel, ui: UiState, host: MainActivity) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val syncing by vm.syncing.collectAsStateWithLifecycle()
    val outbox by vm.outbox.collectAsStateWithLifecycle()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) vm.emit("Notifications are blocked. Turn them on in Android settings to get reminders.", "warn")
    }

    PageList {
        item {
            AppCard {
                SectionHeader("Security")
                SwitchRow("App lock", "Ask for fingerprint, face or screen lock when opening the app", settings.lockEnabled) { on ->
                    if (on && !host.canUseAppLock()) {
                        vm.emit("Set a screen lock (PIN, pattern or fingerprint) on this phone first.", "warn")
                    } else {
                        vm.updateSettings { it.copy(lockEnabled = on) }
                    }
                }
                SwitchRow("Hide from screenshots", "Blocks screenshots and hides the app in the recent-apps list", settings.secureScreen) { on ->
                    vm.updateSettings { it.copy(secureScreen = on) }
                }
            }
        }
        item {
            AppCard {
                SectionHeader("Reminders")
                SwitchRow("Bill & budget notifications", "A daily summary of overdue bills, card due dates and budgets near the limit", settings.notifications) { on ->
                    if (on && Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    vm.updateSettings { it.copy(notifications = on) }
                }
                if (settings.notifications) {
                    val hours = (6..22).map { String.format(java.util.Locale.US, "%02d:00", it) }
                    DropdownField("Notify me at", String.format(java.util.Locale.US, "%02d:00", settings.notifyHour), hours, { h ->
                        vm.updateSettings { it.copy(notifyHour = h.substringBefore(":").toInt()) }
                    })
                }
            }
        }
        item {
            AppCard {
                SectionHeader("Appearance & widget")
                ChipRow(listOf("system", "light", "dark"), settings.themeMode, { m -> vm.updateSettings { it.copy(themeMode = m) } },
                    { it.replaceFirstChar { c -> c.uppercase() } })
                SwitchRow("Hide amounts on the widget", "The home-screen widget shows dots instead of your balance", settings.widgetHideAmounts) { on ->
                    vm.updateSettings { it.copy(widgetHideAmounts = on) }
                }
            }
        }
        item {
            AppCard {
                SectionHeader("Sync")
                val saved = vm.snapshot.value?.savedAt ?: 0L
                Text(
                    if (saved > 0) "Last updated " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(saved)) else "Not synced yet",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp
                )
                if (outbox.isNotEmpty()) Text("${outbox.size} change(s) waiting in the queue", fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !syncing, onClick = { vm.refresh(true) }) { Text(if (syncing) "Syncing…" else "Sync now") }
                    OutlinedButton(onClick = { ui.panel = "queue" }) { Text("View queue") }
                }
                if (snap.errors.isNotEmpty()) {
                    Text(
                        "Some data could not be loaded on the server: " + snap.errors.entries.joinToString("; ") { it.key + " (" + it.value + ")" },
                        fontSize = 12.sp, color = warnColor(), modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
        item {
            AppCard {
                SectionHeader("This phone")
                Text("Paired as “" + vm.repo.store.deviceName.ifBlank { "this phone" } + "”", fontWeight = FontWeight.Bold)
                Text(
                    "Revoke a lost phone from the web app: More > Android app.",
                    fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp)
                )
                OutlinedButton(
                    onClick = {
                        ui.confirm = ConfirmSpec(
                            "Unpair this phone?",
                            "Saved data and unsent changes on this phone will be removed. You can pair again with a new link.",
                            "Unpair", true
                        ) { ui.panel = null; vm.unpair() }
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Unpair this phone") }
            }
        }
        item {
            Text("Family Money for Android " + BuildConfig.VERSION_NAME, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

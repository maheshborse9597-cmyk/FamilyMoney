package com.familymoney.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familymoney.app.data.OutItem
import com.familymoney.app.data.Snapshot
import com.familymoney.app.logic.Calc
import com.familymoney.app.logic.Dates
import com.familymoney.app.logic.Fmt
import kotlin.math.roundToInt

private class AlertItem(val text: String, val color: Color, val actionLabel: String?, val action: (() -> Unit)?)

/* ===================================================================================== */
/*  Home                                                                                  */
/* ===================================================================================== */

@Composable
fun HomeScreen(snap: Snapshot, outbox: List<OutItem>, vm: AppViewModel, ui: UiState) {
    val today = Dates.today()
    val month = today.take(7)
    val agg = Calc.agg(Calc.inMonth(snap.transactions, month))
    val total = snap.banks.sumOf { it.balance }
    val cardDue = snap.cards.sumOf { it.outstanding }
    val urgent = Calc.urgentBills(snap.bills, today)
    val upcoming = snap.bills.filter { Calc.alertOf(it, today) != "Paid" }.sortedBy { Calc.daysUntil(it, today) }
    val billList = (if (urgent.isNotEmpty()) urgent else upcoming).take(4)
    val alertPct = snap.settings.budgetAlertPercent
    val waiting = outbox.count { it.status == "pending" }
    val stuck = outbox.count { it.status != "pending" }

    val dueRecurring = snap.recurring.count { it.due }
    val warn = warnColor()
    val bad = badColor()
    val alerts = ArrayList<AlertItem>()
    if (dueRecurring > 0) {
        alerts.add(
            AlertItem(
                "$dueRecurring recurring ${if (dueRecurring == 1) "entry is" else "entries are"} due to post.",
                MaterialTheme.colorScheme.primary, "Post now"
            ) { vm.postRecurringNow() }
        )
    }
    Calc.budgetRows(snap.transactions, snap.budgets, month).filter { it.budget > 0 && it.pct >= alertPct }
        .sortedByDescending { it.pct }.take(3).forEach { r ->
            alerts.add(
                AlertItem(
                    "${r.category} budget ${r.pct.roundToInt()}% used (${Fmt.money(r.actual)} of ${Fmt.money(r.budget)})",
                    if (r.pct > 100) bad else warn, "Details"
                ) { ui.tab = "dashboard" }
            )
        }
    for (c in snap.cards) {
        val d = Calc.cardDaysToDue(c, today)
        if (c.outstanding > 0 && d != null && d <= 5) {
            alerts.add(
                AlertItem(
                    c.name + " card bill " + Fmt.money(c.outstanding) + " due " + (if (d <= 0) "now" else "in $d day${if (d == 1) "" else "s"}"),
                    warn, "Pay"
                ) { ui.addTx("Transfer", mapOf("toBank" to c.name, "amount" to c.outstanding.toString().removeSuffix(".0"))) }
            )
        }
    }

    PageList {
        item {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(listOf(Pal.Brand, Pal.Brand2))).padding(20.dp)
            ) {
                Column {
                    Text("Total bank balance", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(Fmt.money(total), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        snap.banks.size.toString() + " account" + (if (snap.banks.size == 1) "" else "s") + " · " + Dates.monthLabel(month) +
                            (if (snap.cards.isNotEmpty()) " · Card dues " + Fmt.money(cardDue) else ""),
                        color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        HeroStat("Income", Fmt.compact(agg.income), Modifier.weight(1f))
                        HeroStat("Spent", Fmt.compact(agg.expense), Modifier.weight(1f))
                        HeroStat("Invested", Fmt.compact(agg.investment), Modifier.weight(1f))
                    }
                }
            }
        }

        if (waiting + stuck > 0) {
            item {
                AppCard {
                    Text(
                        if (stuck > 0) "$stuck change${if (stuck == 1) "" else "s"} need your attention" +
                            (if (waiting > 0) " · $waiting waiting to sync" else "")
                        else "$waiting change${if (waiting == 1) "" else "s"} saved on this phone, waiting to sync",
                        fontWeight = FontWeight.Bold
                    )
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { vm.refresh(true) }) { Text("Sync now") }
                        if (stuck > 0) OutlinedButton(onClick = { ui.panel = "queue" }) { Text("Review") }
                    }
                }
            }
        }

        if (alerts.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (a in alerts.take(5)) {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                .background(a.color.copy(alpha = 0.12f)).padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Warning, contentDescription = null, tint = a.color, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(a.text, fontSize = 13.5.sp, modifier = Modifier.weight(1f))
                            if (a.actionLabel != null && a.action != null) {
                                TextButton(onClick = { a.action.invoke() }) { Text(a.actionLabel, fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (t in listOf("Expense", "Income", "Investment", "Transfer")) {
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable { ui.addTx(t) }.padding(vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TypeChip(t, 44.dp)
                        Text(if (t == "Investment") "Invest" else t, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        if (snap.templates.isNotEmpty()) {
            item {
                SectionHeader("Quick add", "Manage") { ui.panel = "templates" }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(snap.templates.size) { i ->
                        val t = snap.templates[i]
                        AssistChip(
                            onClick = { vm.useTemplate(t) },
                            label = { Text(t.name + " · " + Fmt.compact(t.amount), fontWeight = FontWeight.Bold) },
                            leadingIcon = { Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                    }
                }
            }
        }

        item {
            SectionHeader("Accounts", "Dashboard") { ui.tab = "dashboard" }
            BankCards(snap.banks, emptyList(), null)
        }

        item {
            AppCard {
                SectionHeader(if (urgent.isNotEmpty()) "Needs attention" else "Upcoming bills", "View all") { ui.tab = "bills" }
                if (billList.isEmpty()) EmptyState("All clear", "No bills waiting. Add one to get reminders.")
                for (b in billList) {
                    val a = Calc.alertOf(b, today)
                    val c = billColor(a)
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(5.dp).height(38.dp).clip(RoundedCornerShape(4.dp)).background(c))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(b.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(dueText(b, today) + " · " + Dates.label(b.dueDate), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(Fmt.money(b.amount), fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                        IconButton(onClick = { ui.payBill = b }) {
                            Icon(Icons.Filled.Check, contentDescription = "Mark ${b.name} as paid", tint = okColor())
                        }
                    }
                }
            }
        }

        item {
            AppCard {
                SectionHeader("Recent transactions", "View all") { ui.tab = "history" }
                if (snap.transactions.isEmpty()) EmptyState("No transactions yet", "Tap + to add your first one.")
                for (t in snap.transactions.take(6)) {
                    TxRow(t) { openTransaction(t, vm, ui) }
                }
            }
        }
    }
}

@Composable
private fun HeroStat(label: String, value: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.16f)).padding(10.dp)) {
        Text(label, color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp)
        Text(value, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
    }
}

@Composable
fun billColor(alert: String): Color = when (alert) {
    "Overdue" -> badColor()
    "Due Today", "Due Soon" -> warnColor()
    "Paid" -> MaterialTheme.colorScheme.onSurfaceVariant
    else -> okColor()
}

fun dueText(b: com.familymoney.app.data.Bill, today: String): String {
    if (b.status.equals("Paid", ignoreCase = true)) return "Paid"
    val d = Calc.daysUntil(b, today)
    return when {
        d < 0 -> "${-d} day${if (d == -1) "" else "s"} overdue"
        d == 0 -> "Due today"
        d == 1 -> "Due tomorrow"
        else -> "Due in $d days"
    }
}

/* ===================================================================================== */
/*  History                                                                               */
/* ===================================================================================== */

@Composable
fun HistoryScreen(snap: Snapshot, vm: AppViewModel, ui: UiState) {
    val ctx = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf("All") }
    var month by rememberSaveable { mutableStateOf("All") }
    var limit by rememberSaveable { mutableIntStateOf(40) }

    val list = remember(snap.transactions, query, type, month) { Calc.filter(snap.transactions, query, type, month) }
    val agg = Calc.agg(list)
    val shown = list.take(limit)
    val months = listOf("All") + Calc.monthsAvailable(snap.transactions, Dates.today(), "")
    val monthLabels: Map<String, String> = months.associateBy { if (it == "All") "All months" else Dates.monthLabel(it) }
    val today = Dates.today()

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(Calc.csv(list).toByteArray(Charsets.UTF_8)) }
                vm.emit("Exported ${list.size} transactions.", "ok")
            } catch (e: Exception) {
                vm.emit("Could not save the file.", "err")
            }
        }
    }

    PageList {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it; limit = 40 },
                    label = { Text("Search") }, singleLine = true, modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { export.launch("family-money-transactions.csv") }) {
                    Icon(Icons.Filled.Download, contentDescription = "Export as CSV")
                }
            }
        }
        item {
            ChipRow(listOf("All", "Expense", "Income", "Investment", "Transfer"), type, { type = it; limit = 40 })
        }
        item {
            DropdownField(
                "Month", if (month == "All") "All months" else Dates.monthLabel(month),
                monthLabels.keys.toList(),
                { month = monthLabels[it] ?: "All"; limit = 40 }
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("${list.size} transactions", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                Text("In " + Fmt.money(agg.income), fontSize = 12.5.sp, color = typeColor("Income"))
                Text("Spent " + Fmt.money(agg.expense), fontSize = 12.5.sp, color = typeColor("Expense"))
            }
        }
        if (list.isEmpty()) {
            item { AppCard { EmptyState("Nothing found", "Try a different search or filter.") } }
        }
        val groups = shown.groupBy { it.date }
        for ((date, rows) in groups) {
            item(key = "day-$date") {
                AppCard {
                    val label = when {
                        date.isBlank() -> "No date"
                        date == today -> "Today"
                        date == Dates.addDays(today, -1) -> "Yesterday"
                        else -> Dates.label(date, true)
                    }
                    Text(label.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    for (t in rows) TxRow(t) { openTransaction(t, vm, ui) }
                }
            }
        }
        if (list.size > shown.size) {
            item {
                OutlinedButton(onClick = { limit += 40 }, modifier = Modifier.fillMaxWidth()) {
                    Text("Show more (${list.size - shown.size} left)")
                }
            }
        }
    }
}

/* ===================================================================================== */
/*  Bills                                                                                 */
/* ===================================================================================== */

@Composable
fun BillsScreen(snap: Snapshot, vm: AppViewModel, ui: UiState) {
    val today = Dates.today()
    var tab by rememberSaveable { mutableStateOf("Active") }
    val active = snap.bills.filter { Calc.alertOf(it, today) != "Paid" }.sortedBy { Calc.daysUntil(it, today) }
    val paid = snap.bills.filter { Calc.alertOf(it, today) == "Paid" }
    val overdue = active.count { Calc.alertOf(it, today) == "Overdue" }
    val soon = active.count { val a = Calc.alertOf(it, today); a == "Due Today" || a == "Due Soon" }
    val next30 = active.filter { Calc.daysUntil(it, today) <= 30 }.sumOf { it.amount }
    val list = if (tab == "Paid") paid else active

    PageList {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                ChipRow(listOf("Active", "Paid"), tab, { tab = it }, { if (it == "Active") "Active (${active.size})" else "Paid (${paid.size})" })
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Overdue", overdue.toString(), Modifier.weight(1f), badColor())
                StatTile("Due soon", soon.toString(), Modifier.weight(1f), warnColor())
                StatTile("Next 30 days", Fmt.compact(next30), Modifier.weight(1f))
            }
        }
        item {
            Button(onClick = { ui.billSheet = BillSheetSpec(null) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add bill or recharge", fontWeight = FontWeight.ExtraBold)
            }
        }
        if (list.isEmpty()) {
            item {
                AppCard {
                    EmptyState(
                        if (tab == "Paid") "No paid one-time bills" else "No bills yet",
                        if (tab == "Paid") "Recurring bills move to their next due date after payment."
                        else "Track electricity, recharges, rent and more."
                    )
                }
            }
        }
        for (b in list) {
            item(key = "bill-" + b.billId) {
                val alert = Calc.alertOf(b, today)
                val c = billColor(alert)
                AppCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(b.name, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                            Text(
                                listOf(b.category, if (b.frequency == "Recharge") "Recharge · ${b.validityDays} days" else b.frequency)
                                    .filter { it.isNotBlank() }.joinToString(" · "),
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(Fmt.money(b.amount), fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                    }
                    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Pill(dueText(b, today), c)
                        Text(
                            if (alert == "Paid") "Paid on " + Dates.label(b.lastPaidDate) else "Due " + Dates.label(b.dueDate),
                            fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (b.lastPaidDate.isNotBlank() && alert != "Paid") {
                        Text("Last paid " + Dates.label(b.lastPaidDate), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    }
                    if (b.notes.isNotBlank()) {
                        Text(
                            b.notes, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp).clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp).fillMaxWidth()
                        )
                    }
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (alert != "Paid") {
                            Button(onClick = { ui.payBill = b }) {
                                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Mark as paid")
                            }
                        }
                        OutlinedButton(onClick = { ui.billSheet = BillSheetSpec(b) }) {
                            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Edit")
                        }
                    }
                }
            }
        }
    }
}

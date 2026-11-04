package com.familymoney.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familymoney.app.data.Bank
import com.familymoney.app.data.Recon
import com.familymoney.app.data.Snapshot
import com.familymoney.app.data.arr
import com.familymoney.app.data.obj
import com.familymoney.app.logic.Calc
import com.familymoney.app.logic.Dates
import com.familymoney.app.logic.Fmt
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** Account cards (Home shows them plainly, the Dashboard adds a "Check balance" button). */
@Composable
fun BankCards(banks: List<Bank>, recons: List<Recon>, onCheck: ((Bank) -> Unit)?) {
    if (banks.isEmpty()) {
        AppCard { EmptyState("No active bank accounts", "Add accounts in the “Bank Accounts” sheet and set Active = Yes.") }
        return
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(banks.size) { i ->
            val b = banks[i]
            val rc = recons.firstOrNull { it.bank == b.accountName }
            AppCard(Modifier.width(236.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconChip(Icons.Filled.AccountBalance, MaterialTheme.colorScheme.primary, 38.dp)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(b.accountName, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                        Text(b.accountHolder, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(
                    Fmt.money(b.balance), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
                    color = if (b.balance < 0) badColor() else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 10.dp)
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("In " + Fmt.compact(b.income + b.transfersIn), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Out " + Fmt.compact(b.expenses + b.investments + b.transfersOut), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (onCheck != null) {
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            if (rc == null) "Never checked"
                            else "Checked " + Dates.label(rc.date) + (if (abs(rc.difference) >= 0.01) " · off by " + Fmt.money(rc.difference) else " · matched ✓"),
                            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(onClick = { onCheck(b) }) { Text("Check", fontSize = 12.sp) }
                    }
                }
            }
        }
    }
}

fun openRecon(bank: Bank, snap: Snapshot, vm: AppViewModel, ui: UiState) {
    ui.form = FormSpec(
        title = "Check balance · " + bank.accountName,
        saveText = "Save check",
        intro = "The app calculates ${Fmt.money(bank.balance)}. Enter the balance your bank app or passbook shows right now.",
        initial = mapOf("date" to Dates.today()),
        fields = listOf(
            Field("actual", "Real balance (₹)", FType.Number, required = true, half = true),
            Field("date", "As of", FType.Date, half = true),
            Field("note", "Note (optional)", FType.Text),
            Field("adjust", "Fix the difference by adding an adjustment entry", FType.Check),
            Field("person", "Person for the adjustment", FType.Select, snap.config.persons, required = true,
                showWhen = { it["adjust"] == "true" })
        ),
        live = { v ->
            val actual = v["actual"]?.toDoubleOrNull()
            if (actual == null) "" else {
                val d = actual - bank.balance
                if (abs(d) < 0.01) "✓ Matches the app"
                else "Difference: " + Fmt.money(d) + (if (d > 0) " (bank has more)" else " (bank has less)")
            }
        },
        onSave = { v ->
            vm.run(
                "saveReconciliation",
                arr(
                    obj(
                        "bank" to bank.accountName, "actual" to num(v["actual"]), "date" to (v["date"] ?: Dates.today()),
                        "note" to (v["note"] ?: ""), "adjust" to (v["adjust"] == "true"), "person" to (v["person"] ?: "")
                    )
                ),
                "Check ${bank.accountName}"
            )
        }
    )
}

@Composable
fun CardTiles(snap: Snapshot, ui: UiState) {
    val today = Dates.today()
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(snap.cards.size) { i ->
            val c = snap.cards[i]
            val d = Calc.cardDaysToDue(c, today)
            val color = if (c.utilization > 80) badColor() else if (c.utilization > 50) warnColor() else okColor()
            AppCard(Modifier.width(236.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconChip(Icons.Filled.CreditCard, typeColor("Expense"), 38.dp)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(c.name, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                        Text(c.holder, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(
                    Fmt.money(c.outstanding), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
                    color = if (c.outstanding > 0) typeColor("Expense") else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 10.dp)
                )
                Text("Outstanding" + (if (c.limit > 0) " · limit " + Fmt.compact(c.limit) else ""), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (c.limit > 0) {
                    AppProgress((c.utilization / 100.0).toFloat(), color, Modifier.padding(top = 8.dp))
                    Text("${c.utilization.roundToInt()}% used · available " + Fmt.compact(c.available), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        if (d == null) "" else "Due " + Dates.label(c.nextDueDate) + (if (d <= 5) " · " + (if (d <= 0) "now" else "in ${d}d") else ""),
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f)
                    )
                    if (c.outstanding > 0) {
                        OutlinedButton(onClick = {
                            ui.addTx("Transfer", mapOf("toBank" to c.name, "amount" to c.outstanding.toString().removeSuffix(".0")))
                        }) { Text("Pay", fontSize = 12.sp) }
                    }
                }
            }
        }
    }
}

@Composable
fun Donut(values: List<Pair<String, Double>>, centerText: String, modifier: Modifier = Modifier.size(170.dp)) {
    val total = values.sumOf { it.second }
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 26.dp.toPx()
            val side = size.minDimension - stroke
            val topLeft = Offset((size.width - side) / 2f, (size.height - side) / 2f)
            val arcSize = Size(side, side)
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            var start = -90f
            values.forEachIndexed { i, v ->
                val sweep = if (total > 0) (v.second / total * 360.0).toFloat() else 0f
                drawArc(Pal.Chart[i % Pal.Chart.size], start, sweep, false, topLeft, arcSize, style = Stroke(stroke))
                start += sweep
            }
        }
        Text(centerText, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
    }
}

@Composable
private fun TrendBars(snap: Snapshot, month: String) {
    val keys = (5 downTo 0).map { Dates.shiftMonth(month, -it) }
    val data = keys.map { k -> val a = Calc.agg(Calc.inMonth(snap.transactions, k)); Pair(a.income, a.expense) }
    val maxV = max(1.0, data.maxOf { max(it.first, it.second) })
    val incomeColor = typeColor("Income")
    val expenseColor = typeColor("Expense")

    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(bottom = 6.dp)) {
        Legend(incomeColor, "Income")
        Legend(expenseColor, "Expenses")
    }
    Canvas(Modifier.fillMaxWidth().height(140.dp)) {
        val slot = size.width / keys.size
        val bar = slot * 0.26f
        data.forEachIndexed { i, d ->
            val cx = slot * i + slot / 2f
            val hIncome = (d.first / maxV * size.height).toFloat().coerceAtLeast(3f)
            val hExpense = (d.second / maxV * size.height).toFloat().coerceAtLeast(3f)
            val radius = CornerRadius(5.dp.toPx(), 5.dp.toPx())
            drawRoundRect(incomeColor, Offset(cx - bar - 2f, size.height - hIncome), Size(bar, hIncome), radius)
            drawRoundRect(expenseColor, Offset(cx + 2f, size.height - hExpense), Size(bar, hExpense), radius)
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        for (k in keys) {
            Text(
                Dates.shortMonth(k), modifier = Modifier.weight(1f), fontSize = 11.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Kpi(label: String, value: String, color: Color, modifier: Modifier, note: String = "") {
    AppCard(modifier) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
        Text(value, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        if (note.isNotBlank()) Text(note, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun DashboardScreen(snap: Snapshot, vm: AppViewModel, ui: UiState) {
    val month by vm.month.collectAsStateWithLifecycle()
    val today = Dates.today()
    var catScope by rememberSaveable { mutableStateOf("Month") }
    var budgetScope by rememberSaveable { mutableStateOf("Month") }
    val alertPct = snap.settings.budgetAlertPercent

    val monthTx = Calc.inMonth(snap.transactions, month)
    val a = Calc.agg(monthTx)
    val saved = a.income - a.expense
    val rate = if (a.income > 0) (saved / a.income * 100).roundToInt() else null
    val prev = Calc.agg(Calc.inMonth(snap.transactions, Dates.shiftMonth(month, -1)))
    val monthLabels = Calc.monthsAvailable(snap.transactions, today, month).associateBy { Dates.monthLabel(it) }

    PageList {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconButton(onClick = { vm.month.value = Dates.shiftMonth(month, -1) }) {
                    Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
                }
                DropdownField("Month", Dates.monthLabel(month), monthLabels.keys.toList(), { vm.month.value = monthLabels[it] ?: month }, Modifier.weight(1f))
                IconButton(onClick = { vm.month.value = Dates.shiftMonth(month, 1) }) {
                    Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "Next month")
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Kpi("Expenses", Fmt.money(a.expense), typeColor("Expense"), Modifier.weight(1f))
                    Kpi("Income", Fmt.money(a.income), typeColor("Income"), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Kpi("Investments", Fmt.money(a.investment), typeColor("Investment"), Modifier.weight(1f))
                    Kpi("Net savings", Fmt.money(saved), if (saved < 0) badColor() else MaterialTheme.colorScheme.primary, Modifier.weight(1f),
                        if (rate == null) "" else "$rate% of income")
                }
                Kpi("Transfers", Fmt.money(a.transfer), typeColor("Transfer"), Modifier.fillMaxWidth(), "not counted as income or expense")
            }
        }

        item {
            AppCard {
                SectionHeader("Compared with last month")
                if (a.n == 0 && prev.n == 0) {
                    EmptyState("No data yet", "Add some transactions to see trends.")
                } else {
                    ChangeRow("Spending", a.expense, prev.expense, badWhenUp = true)
                    ChangeRow("Income", a.income, prev.income, badWhenUp = false)
                    val deltas = Calc.categoryDeltas(snap.transactions, month)
                    for (d in deltas.filter { it.diff > 0 }.sortedByDescending { it.diff }.take(3)) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(d.category)
                            Text(
                                (if (d.previous > 0) "▲ ${(d.diff / d.previous * 100).roundToInt()}%" else "new") + " (+" + Fmt.money(d.diff) + ")",
                                color = badColor(), fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                    for (d in deltas.filter { it.diff < 0 }.sortedBy { it.diff }.take(2)) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(d.category)
                            Text("▼ ${(-d.diff / d.previous * 100).roundToInt()}% (" + Fmt.money(d.diff) + ")", color = okColor(), fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
        }

        item {
            AppCard {
                SectionHeader("Net worth", "Investments") { ui.panel = "investments" }
                val banks = snap.banks.sumOf { it.balance }
                val inv = snap.holdings.sumOf { it.current }
                val due = snap.cards.sumOf { it.outstanding }
                NetRow("Bank balances", Fmt.money(banks))
                NetRow("Investments (current value)", Fmt.money(inv))
                NetRow("Credit card dues", (if (due > 0) "-" else "") + Fmt.money(due), typeColor("Expense"))
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Net worth", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                    Text(Fmt.money(banks + inv - due), fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                }
            }
        }

        item {
            SectionHeader("Bank balances")
            BankCards(snap.banks, snap.reconciliations) { openRecon(it, snap, vm, ui) }
        }

        if (snap.cards.isNotEmpty()) {
            item {
                SectionHeader("Credit cards", "Manage") { ui.panel = "cards" }
                CardTiles(snap, ui)
            }
        }

        item {
            AppCard {
                SectionHeader("Budget")
                ChipRow(listOf("Month", "Year"), budgetScope, { budgetScope = it })
                Spacer(Modifier.height(10.dp))
                if (budgetScope == "Year") BudgetYear(snap, month, alertPct) else BudgetMonth(snap, month, alertPct)
                OutlinedButton(onClick = { ui.panel = "budgets" }, modifier = Modifier.padding(top = 12.dp)) {
                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Edit budgets")
                }
            }
        }

        item {
            AppCard {
                SectionHeader("Spending by category")
                ChipRow(listOf("Month", "All time"), catScope, { catScope = it })
                Spacer(Modifier.height(10.dp))
                val expenses = snap.transactions.filter { it.type == "Expense" && (catScope == "All time" || it.date.startsWith(month)) }
                val cats = Calc.groupSum(expenses) { it.category }
                val total = cats.sumOf { it.second }
                if (cats.isEmpty()) {
                    EmptyState("No expenses", "Nothing recorded for this period.")
                } else {
                    val top = cats.take(7).toMutableList()
                    val rest = cats.drop(7).sumOf { it.second }
                    if (rest > 0) top.add("Others" to rest)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        Donut(top, Fmt.compact(total))
                    }
                    Spacer(Modifier.height(8.dp))
                    top.forEachIndexed { i, c ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(12.dp).clip(RoundedCornerShape(4.dp)).background(Pal.Chart[i % Pal.Chart.size]))
                            Spacer(Modifier.width(10.dp))
                            Text(c.first + "  " + (c.second / total * 100).roundToInt() + "%", modifier = Modifier.weight(1f), fontSize = 13.5.sp)
                            Text(Fmt.money(c.second), fontWeight = FontWeight.ExtraBold, fontSize = 13.5.sp)
                        }
                    }
                }
            }
        }

        item {
            AppCard {
                SectionHeader("Spending by person")
                val expenses = snap.transactions.filter { it.type == "Expense" && (catScope == "All time" || it.date.startsWith(month)) }
                val people = Calc.groupSum(expenses) { it.person }
                val top = people.firstOrNull()?.second ?: 0.0
                if (people.isEmpty()) EmptyState("No spending", "Nothing recorded for this period.")
                people.forEachIndexed { i, p ->
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(p.first, fontWeight = FontWeight.Bold)
                            Text(Fmt.money(p.second), fontWeight = FontWeight.Bold)
                        }
                        AppProgress(if (top > 0) (p.second / top).toFloat() else 0f, Pal.Chart[i % Pal.Chart.size], Modifier.padding(top = 6.dp))
                    }
                }
            }
        }

        item {
            AppCard {
                SectionHeader("Last 6 months")
                TrendBars(snap, month)
            }
        }

        if (snap.goals.isNotEmpty()) {
            item {
                AppCard {
                    SectionHeader("Savings goals", "Manage") { ui.panel = "goals" }
                    for (g in snap.goals.take(4)) {
                        val p = if (g.target > 0) (g.saved / g.target).toFloat() else 0f
                        Column(Modifier.padding(vertical = 6.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(g.name, fontWeight = FontWeight.Bold)
                                Text(Fmt.money(g.saved) + " / " + Fmt.money(g.target), fontSize = 13.sp)
                            }
                            AppProgress(p, if (p >= 1f) okColor() else MaterialTheme.colorScheme.primary, Modifier.padding(top = 6.dp), 10.dp)
                            val need = Calc.goalNeed(g, today)
                            Text(
                                "${(p * 100).roundToInt()}%" + if (need.isNotBlank()) " · $need" else "",
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChangeRow(label: String, cur: Double, prev: Double, badWhenUp: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Row {
            Text(Fmt.money(cur), fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.width(8.dp))
            if (prev > 0) {
                val pc = (cur - prev) / prev * 100
                val up = pc >= 0
                val bad = if (badWhenUp) up else !up
                Text(
                    (if (up) "▲ " else "▼ ") + abs(pc).roundToInt() + "%",
                    color = if (abs(pc) < 1) MaterialTheme.colorScheme.onSurfaceVariant else if (bad) badColor() else okColor(),
                    fontWeight = FontWeight.ExtraBold
                )
            } else {
                Text("no data last month", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun NetRow(label: String, value: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 14.sp)
        Text(value, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun BudgetMonth(snap: Snapshot, month: String, alertPct: Int) {
    val rows = Calc.budgetRows(snap.transactions, snap.budgets, month)
    if (rows.isEmpty()) {
        EmptyState("No budget for " + Dates.monthLabel(month), "Set one, or copy last month's, with “Edit budgets”.")
        return
    }
    val totalBudget = rows.sumOf { it.budget }
    val totalActual = rows.sumOf { it.actual }
    val overall = if (totalBudget > 0) totalActual / totalBudget * 100 else 0.0
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatTile("Budget", Fmt.money(totalBudget), Modifier.weight(1f))
        StatTile("Spent", Fmt.money(totalActual), Modifier.weight(1f))
        StatTile("Remaining", Fmt.money(totalBudget - totalActual), Modifier.weight(1f), if (totalBudget - totalActual < 0) badColor() else okColor())
    }
    AppProgress(((overall) / 100).toFloat(), budgetColor(overall, alertPct), Modifier.padding(vertical = 10.dp))
    for (r in rows.sortedByDescending { it.pct }) {
        val c = budgetColor(r.pct, alertPct)
        Column(Modifier.padding(vertical = 7.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(r.category, fontWeight = FontWeight.Bold)
                Text(Fmt.money(r.actual) + " / " + Fmt.money(r.budget), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            AppProgress((r.pct / 100).toFloat(), c, Modifier.padding(vertical = 5.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(budgetLabel(r.pct, alertPct) + " · " + r.pct.roundToInt() + "%", color = c, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                Text("Remaining " + Fmt.money(r.remaining), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    val budgeted = rows.map { it.category.lowercase() }.toSet()
    val unbudgeted = Calc.inMonth(snap.transactions, month)
        .filter { it.type == "Expense" && !budgeted.contains(it.category.lowercase()) }.sumOf { it.amount }
    if (unbudgeted > 0) {
        Text(Fmt.money(unbudgeted) + " spent in categories with no budget this month.", fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun BudgetYear(snap: Snapshot, month: String, alertPct: Int) {
    val year = month.take(4)
    var totalBudget = 0.0
    var totalActual = 0.0
    val monthsWithBudget = ArrayList<Triple<String, Double, Double>>()
    for (i in 1..12) {
        val key = year + "-" + i.toString().padStart(2, '0')
        val rows = Calc.budgetRows(snap.transactions, snap.budgets, key)
        if (rows.isEmpty()) continue
        val b = rows.sumOf { it.budget }
        val ac = rows.sumOf { it.actual }
        totalBudget += b
        totalActual += ac
        monthsWithBudget.add(Triple(Dates.monthLabel(key).substringBefore(" "), b, ac))
    }
    if (monthsWithBudget.isEmpty()) {
        EmptyState("No budgets in $year", "Use “Edit budgets” to set them.")
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatTile("Budget $year", Fmt.money(totalBudget), Modifier.weight(1f))
        StatTile("Spent", Fmt.money(totalActual), Modifier.weight(1f))
        StatTile("Left", Fmt.money(totalBudget - totalActual), Modifier.weight(1f), if (totalBudget - totalActual < 0) badColor() else okColor())
    }
    for (m in monthsWithBudget) {
        val p = if (m.second > 0) m.third / m.second * 100 else 0.0
        Column(Modifier.padding(vertical = 7.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(m.first, fontWeight = FontWeight.Bold)
                Text(Fmt.money(m.third) + " / " + Fmt.money(m.second), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            AppProgress((p / 100).toFloat(), budgetColor(p, alertPct), Modifier.padding(top = 5.dp))
        }
    }
}

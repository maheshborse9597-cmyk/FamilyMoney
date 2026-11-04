package com.familymoney.app.logic

import com.familymoney.app.data.Bill
import com.familymoney.app.data.BudgetLine
import com.familymoney.app.data.Card
import com.familymoney.app.data.Goal
import com.familymoney.app.data.OutItem
import com.familymoney.app.data.Snapshot
import com.familymoney.app.data.Tx
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

object Fmt {
    private val inLocale: Locale = Locale.Builder().setLanguage("en").setRegion("IN").build()

    /** 1234567.5 -> ₹12,34,567.5 (Indian digit grouping) */
    fun money(n: Double): String {
        val f = NumberFormat.getNumberInstance(inLocale)
        f.maximumFractionDigits = 2
        f.minimumFractionDigits = 0
        return (if (n < 0) "-" else "") + "₹" + f.format(abs(n))
    }

    fun compact(n: Double): String {
        val a = abs(n)
        val s = if (n < 0) "-" else ""
        return when {
            a >= 1e7 -> s + "₹" + trim1(a / 1e7) + "Cr"
            a >= 1e5 -> s + "₹" + trim1(a / 1e5) + "L"
            a >= 1e3 -> s + "₹" + trim1(a / 1e3) + "K"
            else -> s + "₹" + a.roundToInt()
        }
    }

    private fun trim1(x: Double): String {
        val t = String.format(Locale.US, "%.1f", x)
        return if (t.endsWith(".0")) t.dropLast(2) else t
    }

    fun percent(x: Double): String = "${x.roundToInt()}%"
}

object Dates {
    private val MON = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    private val MONFULL = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )
    private val DOW = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

    fun today(): String = LocalDate.now().toString()

    fun parse(key: String): LocalDate? = try { LocalDate.parse(key) } catch (e: Exception) { null }

    fun label(key: String, weekday: Boolean = false): String {
        val d = parse(key) ?: return "No date"
        val base = "${d.dayOfMonth} ${MON[d.monthValue - 1]} ${d.year}"
        return if (weekday) DOW[d.dayOfWeek.value % 7] + ", " + base else base
    }

    fun monthLabel(key: String): String {
        val p = key.split("-")
        if (p.size < 2) return key
        val m = p[1].toIntOrNull() ?: return key
        return MONFULL[(m - 1).coerceIn(0, 11)] + " " + p[0]
    }

    fun shortMonth(key: String): String {
        val m = key.split("-").getOrNull(1)?.toIntOrNull() ?: return key
        return MON[(m - 1).coerceIn(0, 11)]
    }

    fun shiftMonth(key: String, n: Int): String = YearMonth.parse(key).plusMonths(n.toLong()).toString()

    fun addDays(key: String, n: Int): String = LocalDate.parse(key).plusDays(n.toLong()).toString()

    fun daysBetween(from: String, to: String): Int {
        val a = parse(from) ?: return 0
        val b = parse(to) ?: return 0
        return ChronoUnit.DAYS.between(a, b).toInt()
    }
}

data class Agg(
    val income: Double = 0.0,
    val expense: Double = 0.0,
    val investment: Double = 0.0,
    val transfer: Double = 0.0,
    val n: Int = 0
)

data class BudgetRow(val category: String, val budget: Double, val actual: Double) {
    val remaining: Double get() = budget - actual
    val pct: Double get() = if (budget > 0) actual / budget * 100.0 else if (actual > 0) 999.0 else 0.0
}

data class CatDelta(val category: String, val current: Double, val previous: Double) {
    val diff: Double get() = current - previous
}

object Calc {

    fun agg(list: List<Tx>): Agg {
        var income = 0.0; var expense = 0.0; var investment = 0.0; var transfer = 0.0; var n = 0
        for (t in list) {
            when (t.type) {
                "Income" -> { income += t.amount; n++ }
                "Expense" -> { expense += t.amount; n++ }
                "Investment" -> { investment += t.amount; n++ }
                "Transfer" -> { transfer += t.amount; n++ }
            }
        }
        return Agg(income, expense, investment, transfer, n)
    }

    fun inMonth(list: List<Tx>, month: String): List<Tx> = list.filter { it.date.startsWith(month) }

    fun groupSum(list: List<Tx>, key: (Tx) -> String): List<Pair<String, Double>> {
        val m = LinkedHashMap<String, Double>()
        for (t in list) {
            val k = key(t).ifBlank { "Other" }
            m[k] = (m[k] ?: 0.0) + t.amount
        }
        return m.entries.sortedByDescending { it.value }.map { it.key to it.value }
    }

    fun budgetRows(tx: List<Tx>, budgets: List<BudgetLine>, month: String): List<BudgetRow> {
        val actual = HashMap<String, Double>()
        for (t in tx) {
            if (t.type == "Expense" && t.date.startsWith(month)) {
                val k = t.category.lowercase()
                actual[k] = (actual[k] ?: 0.0) + t.amount
            }
        }
        return budgets.filter { it.month == month }
            .map { BudgetRow(it.category, it.amount, actual[it.category.lowercase()] ?: 0.0) }
    }

    /** Category changes versus the previous month (only changes of at least ₹100). */
    fun categoryDeltas(tx: List<Tx>, month: String): List<CatDelta> {
        val cur = groupSum(inMonth(tx, month).filter { it.type == "Expense" }) { it.category }.toMap()
        val prev = groupSum(inMonth(tx, Dates.shiftMonth(month, -1)).filter { it.type == "Expense" }) { it.category }.toMap()
        val keys = LinkedHashSet<String>()
        keys.addAll(cur.keys)
        keys.addAll(prev.keys)
        return keys.map { CatDelta(it, cur[it] ?: 0.0, prev[it] ?: 0.0) }.filter { abs(it.diff) >= 100.0 }
    }

    fun goalNeed(g: Goal, today: String): String {
        if (g.targetDate.isBlank() || g.saved >= g.target) return ""
        val days = Dates.daysBetween(today, g.targetDate)
        val months = max(1, ceil(days / 30.44).toInt())
        return "Save " + Fmt.money((g.target - g.saved) / months) + "/month to reach it by " + Dates.label(g.targetDate)
    }

    fun filter(tx: List<Tx>, query: String, type: String, month: String): List<Tx> {
        val q = query.trim().lowercase()
        return tx.filter { t ->
            if (type != "All" && t.type != type) return@filter false
            if (month != "All" && !t.date.startsWith(month)) return@filter false
            if (q.isNotEmpty()) {
                val hay = listOf(
                    t.description, t.category, t.person, t.bankAccount, t.transferToBank,
                    t.paymentMode, t.notes, t.type, t.amount.toString()
                ).joinToString(" ").lowercase()
                if (!hay.contains(q)) return@filter false
            }
            true
        }
    }

    fun monthsAvailable(tx: List<Tx>, today: String, selected: String): List<String> {
        val set = sortedSetOf<String>()
        for (t in tx) if (t.date.length >= 7) set.add(t.date.substring(0, 7))
        if (today.length >= 7) set.add(today.substring(0, 7))
        if (selected.isNotBlank()) set.add(selected)
        return set.toList().reversed()
    }

    /* ---- bills: always computed against the phone's own date, so cached data never shows stale "due in 3 days" ---- */

    fun daysUntil(b: Bill, today: String): Int = Dates.daysBetween(today, b.dueDate)

    fun alertOf(b: Bill, today: String): String {
        if (b.status.equals("Paid", ignoreCase = true)) return "Paid"
        val d = daysUntil(b, today)
        return when {
            d < 0 -> "Overdue"
            d == 0 -> "Due Today"
            d <= b.reminderDaysBefore -> "Due Soon"
            else -> "Upcoming"
        }
    }

    fun urgentBills(bills: List<Bill>, today: String): List<Bill> =
        bills.filter { val a = alertOf(it, today); a == "Overdue" || a == "Due Today" || a == "Due Soon" }
            .sortedBy { daysUntil(it, today) }

    fun cardDaysToDue(c: Card, today: String): Int? =
        if (c.nextDueDate.isBlank()) null else Dates.daysBetween(today, c.nextDueDate)

    /** Short lines for notifications: overdue bills, card dues in the next 5 days, budgets above the alert level. */
    fun reminderLines(s: Snapshot, today: String, alertPct: Int): List<String> {
        val lines = ArrayList<String>()
        for (b in urgentBills(s.bills, today)) {
            val d = daysUntil(b, today)
            val whenText = when {
                d < 0 -> "${-d} day(s) overdue"
                d == 0 -> "due today"
                else -> "due in $d day(s)"
            }
            lines.add("${b.name} ${Fmt.money(b.amount)} $whenText")
        }
        for (c in s.cards) {
            val d = cardDaysToDue(c, today)
            if (c.outstanding > 0 && d != null && d <= 5) {
                lines.add("${c.name} card bill ${Fmt.money(c.outstanding)} " + (if (d <= 0) "due now" else "due in $d day(s)"))
            }
        }
        for (r in budgetRows(s.transactions, s.budgets, today.take(7))) {
            if (r.budget > 0 && r.pct >= alertPct) {
                lines.add("${r.category} budget ${r.pct.roundToInt()}% used")
            }
        }
        return lines
    }

    /* ---- offline changes ---- */

    private fun bump(m: MutableMap<String, Double>, key: String, delta: Double) {
        if (key.isBlank()) return
        m[key] = (m[key] ?: 0.0) + delta
    }

    /**
     * Shows changes that were made offline straight away: they appear in the lists (marked pending)
     * and already move the bank balances / card dues, so the screen matches what the person just did.
     */
    fun applyPending(s: Snapshot, outbox: List<OutItem>): Snapshot {
        val pend = outbox.filter { it.tx != null && it.status != "failed" }.mapNotNull { it.tx }
        if (pend.isEmpty()) return s

        val bankDelta = HashMap<String, Double>()
        val cardDelta = HashMap<String, Double>()
        for (t in pend) {
            when (t.type) {
                "Income" -> { bump(bankDelta, t.bankAccount, t.amount); bump(cardDelta, t.bankAccount, -t.amount) }
                "Expense", "Investment" -> { bump(bankDelta, t.bankAccount, -t.amount); bump(cardDelta, t.bankAccount, t.amount) }
                "Transfer" -> {
                    bump(bankDelta, t.bankAccount, -t.amount); bump(cardDelta, t.bankAccount, t.amount)
                    bump(bankDelta, t.transferToBank, t.amount); bump(cardDelta, t.transferToBank, -t.amount)
                }
            }
        }
        val banks = s.banks.map { b ->
            val d = bankDelta[b.accountName] ?: 0.0
            if (d == 0.0) b else b.copy(balance = b.balance + d)
        }
        val cards = s.cards.map { c ->
            val d = cardDelta[c.name] ?: 0.0
            if (d == 0.0) c else {
                val out = c.outstanding + d
                c.copy(
                    outstanding = out,
                    available = if (c.limit > 0) c.limit - out else 0.0,
                    utilization = if (c.limit > 0) out / c.limit * 100.0 else 0.0
                )
            }
        }
        return s.copy(banks = banks, cards = cards, transactions = (pend + s.transactions).sortedByDescending { it.date })
    }

    /* ---- export ---- */

    fun csv(list: List<Tx>): String {
        fun cell(v: Any?): String {
            var s = (v ?: "").toString()
            if (s.isNotEmpty() && s[0] in "=+-@") s = "'$s"
            return if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
        }
        val header = listOf("Date", "Type", "Category", "Description", "Amount", "Payment mode", "Person", "Account", "To account", "Notes", "Receipt")
        val sb = StringBuilder("\uFEFF")
        sb.append(header.joinToString(",")).append("\r\n")
        for (t in list) {
            sb.append(
                listOf(t.date, t.type, t.category, t.description, t.amount, t.paymentMode, t.person, t.bankAccount, t.transferToBank, t.notes, t.receiptUrl)
                    .joinToString(",") { cell(it) }
            ).append("\r\n")
        }
        return sb.toString()
    }
}

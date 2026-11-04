package com.familymoney.app

import com.familymoney.app.data.Bank
import com.familymoney.app.data.Bill
import com.familymoney.app.data.BudgetLine
import com.familymoney.app.data.Card
import com.familymoney.app.data.Goal
import com.familymoney.app.data.OutItem
import com.familymoney.app.data.Snapshot
import com.familymoney.app.data.Tx
import com.familymoney.app.logic.Calc
import com.familymoney.app.logic.Dates
import com.familymoney.app.logic.Fmt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalcTest {

    private fun expense(cat: String, amount: Double, date: String = "2026-10-05", bank: String = "HDFC") =
        Tx(date = date, type = "Expense", category = cat, description = cat, amount = amount, bankAccount = bank)

    @Test fun indianGroupingAndCompactMoney() {
        assertEquals("₹12,34,567.5", Fmt.money(1234567.5))
        assertEquals("-₹500", Fmt.money(-500.0))
        assertEquals("₹1.5L", Fmt.compact(150000.0))
        assertEquals("₹2.5K", Fmt.compact(2500.0))
        assertEquals("₹999", Fmt.compact(999.0))
        assertEquals("₹2.5Cr", Fmt.compact(25000000.0))
    }

    @Test fun dateHelpers() {
        assertEquals("2025-12", Dates.shiftMonth("2026-01", -1))
        assertEquals("2027-01", Dates.shiftMonth("2026-12", 1))
        assertEquals("1 Nov 2026", Dates.label("2026-11-01"))
        assertEquals("Sun, 1 Nov 2026", Dates.label("2026-11-01", true))
        assertEquals(3, Dates.daysBetween("2026-10-31", "2026-11-03"))
        assertEquals("October 2026", Dates.monthLabel("2026-10"))
        assertEquals("2026-11-01", Dates.addDays("2026-10-31", 1))
    }

    @Test fun aggregationIgnoresTransfersInSpending() {
        val list = listOf(
            expense("Grocery", 100.0),
            Tx(date = "2026-10-06", type = "Income", amount = 1000.0),
            Tx(date = "2026-10-07", type = "Transfer", amount = 500.0),
            Tx(date = "2026-10-08", type = "Investment", amount = 200.0)
        )
        val a = Calc.agg(list)
        assertEquals(100.0, a.expense, 0.001)
        assertEquals(1000.0, a.income, 0.001)
        assertEquals(500.0, a.transfer, 0.001)
        assertEquals(200.0, a.investment, 0.001)
        assertEquals(4, a.n)
    }

    @Test fun budgetRowsCompareExpensesCaseInsensitively() {
        val tx = listOf(expense("grocery", 3450.0), expense("Fuel", 500.0, date = "2026-09-10"))
        val rows = Calc.budgetRows(tx, listOf(BudgetLine("2026-10", "Grocery", 3800.0), BudgetLine("2026-09", "Fuel", 1000.0)), "2026-10")
        assertEquals(1, rows.size)
        assertEquals(3450.0, rows[0].actual, 0.001)
        assertEquals(350.0, rows[0].remaining, 0.001)
        assertTrue(rows[0].pct > 90.0 && rows[0].pct < 91.0)
    }

    @Test fun billAlertsUseThePhonesOwnDate() {
        val bill = Bill(billId = "b1", name = "Electricity", dueDate = "2026-11-03", reminderDaysBefore = 3)
        assertEquals("Upcoming", Calc.alertOf(bill, "2026-10-29"))
        assertEquals("Due Soon", Calc.alertOf(bill, "2026-10-31"))
        assertEquals("Due Today", Calc.alertOf(bill, "2026-11-03"))
        assertEquals("Overdue", Calc.alertOf(bill, "2026-11-04"))
        assertEquals("Paid", Calc.alertOf(bill.copy(status = "Paid"), "2026-11-04"))
    }

    @Test fun offlineEntriesMoveBalancesImmediately() {
        val snap = Snapshot(
            banks = listOf(Bank(accountName = "HDFC", balance = 1000.0), Bank(accountName = "ICICI", balance = 500.0)),
            cards = listOf(Card(name = "Visa", limit = 10000.0, outstanding = 2000.0)),
            transactions = listOf(expense("Old", 1.0, date = "2026-10-01"))
        )
        val queued = listOf(
            OutItem("r1", "saveTransaction", "[]", "x", 1L, tx = expense("Food", 200.0, date = "2026-10-09")),
            OutItem("r2", "saveTransaction", "[]", "x", 2L, tx = Tx(date = "2026-10-10", type = "Transfer", amount = 300.0, bankAccount = "HDFC", transferToBank = "Visa")),
            OutItem("r3", "saveTransaction", "[]", "x", 3L, status = "failed", tx = expense("Ignored", 999.0))
        )
        val result = Calc.applyPending(snap, queued)
        assertEquals(1000.0 - 200.0 - 300.0, result.banks[0].balance, 0.001)   // expense + transfer out
        assertEquals(500.0, result.banks[1].balance, 0.001)
        assertEquals(1700.0, result.cards[0].outstanding, 0.001)                // card bill partly paid
        assertEquals(3, result.transactions.size)                               // failed item is not shown
        assertTrue(result.transactions.first().pending)
    }

    @Test fun csvQuotesAndNeutralisesFormulas() {
        val csv = Calc.csv(listOf(Tx(date = "2026-10-05", type = "Expense", description = "=SUM(A1)", notes = "a, \"b\"", amount = 5.0)))
        assertTrue(csv.contains("'=SUM(A1)"))
        assertTrue(csv.contains("\"a, \"\"b\"\"\""))
        assertTrue(csv.startsWith("\uFEFFDate,Type"))
    }

    @Test fun categoryDeltasIgnoreSmallChanges() {
        val tx = listOf(
            expense("Fuel", 1800.0, "2026-10-20"), expense("Fuel", 900.0, "2026-09-15"),
            expense("Tea", 50.0, "2026-10-02"), expense("Tea", 20.0, "2026-09-02")
        )
        val d = Calc.categoryDeltas(tx, "2026-10")
        assertEquals(1, d.size)
        assertEquals("Fuel", d[0].category)
        assertEquals(900.0, d[0].diff, 0.001)
    }

    @Test fun goalMonthlyNeed() {
        val goal = Goal(name = "Trip", target = 12000.0, saved = 0.0, targetDate = "2027-01-01")
        assertEquals("Save ₹3,000/month to reach it by 1 Jan 2027", Calc.goalNeed(goal, "2026-10-01"))
        assertEquals("", Calc.goalNeed(goal.copy(saved = 12000.0), "2026-10-01"))
    }

    @Test fun reminderLinesSummariseWhatNeedsAttention() {
        val snap = Snapshot(
            bills = listOf(Bill(name = "Rent", amount = 15000.0, dueDate = "2026-10-28")),
            budgets = listOf(BudgetLine("2026-10", "Fuel", 1000.0)),
            transactions = listOf(expense("Fuel", 900.0, "2026-10-20")),
            cards = listOf(Card(name = "Visa", outstanding = 5000.0, nextDueDate = "2026-11-02"))
        )
        val lines = Calc.reminderLines(snap, "2026-10-31", 80)
        assertEquals(3, lines.size)
        assertTrue(lines[0].contains("Rent") && lines[0].contains("overdue"))
        assertTrue(lines[1].contains("Visa"))
        assertTrue(lines[2].contains("Fuel budget 90%"))
    }
}

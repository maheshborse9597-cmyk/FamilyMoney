package com.familymoney.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/** One JSON configuration used everywhere. Tolerant: unknown keys are ignored, nulls become defaults. */
val AppJson: Json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    isLenient = true
    encodeDefaults = true
}

/** An error reported by the server (validation, access denied ...). Never retried automatically. */
class ApiException(message: String) : Exception(message)

@Serializable
data class Tx(
    val date: String = "",
    val type: String = "",
    val category: String = "",
    val description: String = "",
    val amount: Double = 0.0,
    val paymentMode: String = "",
    val person: String = "",
    val toPerson: String = "",
    val notes: String = "",
    val entryId: String = "",
    val bankAccount: String = "",
    val transferToBank: String = "",
    val receiptUrl: String = "",
    val pending: Boolean = false
)

@Serializable
data class Bank(
    val bankId: String = "",
    val accountName: String = "",
    val accountHolder: String = "",
    val openingBalance: Double = 0.0,
    val income: Double = 0.0,
    val expenses: Double = 0.0,
    val investments: Double = 0.0,
    val transfersIn: Double = 0.0,
    val transfersOut: Double = 0.0,
    val balance: Double = 0.0
)

@Serializable
data class Card(
    val cardId: String = "",
    val name: String = "",
    val holder: String = "",
    val limit: Double = 0.0,
    val openingOutstanding: Double = 0.0,
    val billingDay: Int = 0,
    val dueDay: Int = 0,
    val spent: Double = 0.0,
    val payments: Double = 0.0,
    val outstanding: Double = 0.0,
    val available: Double = 0.0,
    val utilization: Double = 0.0,
    val nextDueDate: String = "",
    val daysToDue: Int? = null
)

@Serializable
data class Bill(
    val billId: String = "",
    val name: String = "",
    val category: String = "",
    val amount: Double = 0.0,
    val dueDate: String = "",
    val frequency: String = "Monthly",
    val validityDays: Int = 0,
    val reminderDaysBefore: Int = 3,
    val status: String = "Pending",
    val lastPaidDate: String = "",
    val notes: String = "",
    val daysUntilDue: Int = 0,
    val alert: String = "Upcoming"
)

@Serializable
data class BudgetLine(val month: String = "", val category: String = "", val amount: Double = 0.0)

@Serializable
data class Holding(
    val id: String = "",
    val name: String = "",
    val type: String = "",
    val person: String = "",
    val invested: Double = 0.0,
    val current: Double = 0.0,
    val updated: String = "",
    val notes: String = ""
)

@Serializable
data class Goal(
    val id: String = "",
    val name: String = "",
    val target: Double = 0.0,
    val saved: Double = 0.0,
    val targetDate: String = "",
    val notes: String = ""
)

@Serializable
data class Recurring(
    val id: String = "",
    val type: String = "",
    val category: String = "",
    val description: String = "",
    val amount: Double = 0.0,
    val paymentMode: String = "",
    val person: String = "",
    val bank: String = "",
    val toBank: String = "",
    val toPerson: String = "",
    val frequency: String = "Monthly",
    val nextDate: String = "",
    val endDate: String = "",
    val active: Boolean = true,
    val notes: String = "",
    val lastRun: String = "",
    val due: Boolean = false
)

@Serializable
data class Template(
    val id: String = "",
    val name: String = "",
    val type: String = "",
    val category: String = "",
    val description: String = "",
    val amount: Double = 0.0,
    val paymentMode: String = "",
    val person: String = "",
    val bank: String = "",
    val toBank: String = "",
    val toPerson: String = "",
    val notes: String = ""
)

@Serializable
data class Recon(
    val bank: String = "",
    val date: String = "",
    val actual: Double = 0.0,
    val calculated: Double = 0.0,
    val difference: Double = 0.0,
    val note: String = "",
    val adjusted: Boolean = false
)

@Serializable
data class AppConfig(
    val persons: List<String> = emptyList(),
    val expenseCategories: List<String> = emptyList(),
    val incomeCategories: List<String> = emptyList(),
    val investmentCategories: List<String> = emptyList(),
    val billCategories: List<String> = emptyList(),
    val expenseModes: List<String> = emptyList(),
    val otherModes: List<String> = emptyList(),
    val billFrequencies: List<String> = emptyList(),
    val recurringFrequencies: List<String> = emptyList(),
    val defaultReminderDays: Int = 3,
    val banks: List<String> = emptyList(),
    val cards: List<String> = emptyList(),
    val creditCardMode: String = "Credit Card"
)

@Serializable
data class ServerAutomation(val daily: Boolean = false, val backup: Boolean = false)

@Serializable
data class ServerSettings(
    val reminderEmails: String = "",
    val budgetAlertPercent: Int = 80,
    val accessRestricted: Boolean = false,
    val telegram: Boolean = false,
    val automation: ServerAutomation = ServerAutomation()
)

/** Everything returned by the server's getAppData call, plus bookkeeping for the offline cache. */
@Serializable
data class Snapshot(
    val today: String = "",
    val month: String = "",
    val config: AppConfig = AppConfig(),
    val transactions: List<Tx> = emptyList(),
    val banks: List<Bank> = emptyList(),
    val cards: List<Card> = emptyList(),
    val bills: List<Bill> = emptyList(),
    val budgets: List<BudgetLine> = emptyList(),
    val holdings: List<Holding> = emptyList(),
    val goals: List<Goal> = emptyList(),
    val recurring: List<Recurring> = emptyList(),
    val templates: List<Template> = emptyList(),
    val reconciliations: List<Recon> = emptyList(),
    val trashCount: Int = 0,
    val settings: ServerSettings = ServerSettings(),
    val errors: Map<String, String> = emptyMap(),
    val savedAt: Long = 0L,
    val syncToken: String = ""
)

/** A change made on the phone that has not reached the server yet (offline queue). */
@Serializable
data class OutItem(
    val rid: String,
    val op: String,
    val args: String,
    val label: String,
    val createdAt: Long,
    /** pending = waiting to be sent, conflict = server thinks it is a duplicate, failed = server rejected it */
    val status: String = "pending",
    val error: String = "",
    val tx: Tx? = null
)

@Serializable
data class AppSettings(
    val lockEnabled: Boolean = false,
    val secureScreen: Boolean = true,
    val notifications: Boolean = true,
    val notifyHour: Int = 8,
    val themeMode: String = "system",
    val widgetHideAmounts: Boolean = false
)

sealed class WriteResult {
    data class Ok(val data: JsonElement?) : WriteResult()
    object Queued : WriteResult()
    data class Failed(val message: String) : WriteResult()
}

/* ---------- small JSON helpers ---------- */

fun toJson(v: Any?): JsonElement = when (v) {
    null -> JsonNull
    is JsonElement -> v
    is String -> JsonPrimitive(v)
    is Boolean -> JsonPrimitive(v)
    is Number -> JsonPrimitive(v)
    is Collection<*> -> JsonArray(v.map { toJson(it) })
    is Map<*, *> -> JsonObject(v.entries.associate { it.key.toString() to toJson(it.value) })
    else -> JsonPrimitive(v.toString())
}

fun obj(vararg pairs: Pair<String, Any?>): JsonObject = JsonObject(pairs.associate { it.first to toJson(it.second) })

fun arr(vararg values: Any?): JsonArray = JsonArray(values.map { toJson(it) })

fun JsonElement?.asObj(): JsonObject? = this as? JsonObject

fun JsonElement?.str(): String? {
    val p = this as? JsonPrimitive ?: return null
    return if (p is JsonNull) null else p.content
}

fun JsonElement?.bool(): Boolean? = (this as? JsonPrimitive)?.booleanOrNull

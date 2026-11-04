package com.familymoney.app.ui

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.familymoney.app.data.Bank
import com.familymoney.app.data.Bill
import com.familymoney.app.data.Tx

/** What the add / edit transaction sheet should open with. */
data class TxSheetSpec(
    val entry: Tx? = null,
    val type: String = "Expense",
    val prefill: Map<String, String> = emptyMap(),
    val photo: Uri? = null
)

data class BillSheetSpec(val bill: Bill? = null)

data class ConfirmSpec(
    val title: String,
    val message: String,
    val confirmText: String = "Confirm",
    val danger: Boolean = false,
    val onConfirm: () -> Unit
)

enum class FType { Text, Number, Date, Select, Multiline, Check }

data class Field(
    val key: String,
    val label: String,
    val type: FType = FType.Text,
    val options: List<String> = emptyList(),
    val required: Boolean = false,
    val half: Boolean = false,
    val hint: String = "",
    val clearable: Boolean = false,
    val showWhen: ((Map<String, String>) -> Boolean)? = null
)

/**
 * A simple form description. Values are kept as text (checkboxes use "true"/"").
 * onSave returns null when saved, or a message to show in the form.
 */
data class FormSpec(
    val title: String,
    val fields: List<Field>,
    val initial: Map<String, String> = emptyMap(),
    val intro: String = "",
    val saveText: String = "Save",
    val live: ((Map<String, String>) -> String)? = null,
    val onSave: suspend (Map<String, String>) -> String?
)

/** Holds which sheet, dialog or screen is open. One instance lives for the whole session. */
class UiState {
    var tab by mutableStateOf("home")
    var txSheet by mutableStateOf<TxSheetSpec?>(null)
    var billSheet by mutableStateOf<BillSheetSpec?>(null)
    var payBill by mutableStateOf<Bill?>(null)
    var form by mutableStateOf<FormSpec?>(null)
    var panel by mutableStateOf<String?>(null)
    var confirm by mutableStateOf<ConfirmSpec?>(null)
    var recon by mutableStateOf<Bank?>(null)
    var receiptUrl by mutableStateOf<String?>(null)

    fun addTx(type: String = "Expense", prefill: Map<String, String> = emptyMap(), photo: Uri? = null) {
        txSheet = TxSheetSpec(entry = null, type = type, prefill = prefill, photo = photo)
    }

    fun editTx(t: Tx) {
        txSheet = TxSheetSpec(entry = t, type = t.type)
    }
}

fun num(s: String?): Double = s?.trim()?.toDoubleOrNull() ?: 0.0

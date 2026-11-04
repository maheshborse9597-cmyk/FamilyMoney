package com.familymoney.app.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.familymoney.app.data.AppConfig
import com.familymoney.app.data.Bill
import com.familymoney.app.data.Snapshot
import com.familymoney.app.data.Tx
import com.familymoney.app.data.arr
import com.familymoney.app.data.obj
import com.familymoney.app.logic.CompressedImage
import com.familymoney.app.logic.Dates
import com.familymoney.app.logic.Fmt
import com.familymoney.app.logic.ImageUtil
import kotlinx.coroutines.launch
import java.io.File

/** Remembers the last person / account / pay-bill choice for the session so repeat entries are faster. */
object LastUsed {
    var person: String = ""
    var bank: String = ""
    var recordBillExpense: Boolean = true
}

fun bankOptions(cfg: AppConfig, mode: String): List<String> =
    if (mode == cfg.creditCardMode && cfg.cards.isNotEmpty()) cfg.cards else cfg.banks

private fun trimNum(d: Double): String {
    val s = d.toString()
    return if (s.endsWith(".0")) s.dropLast(2) else s
}

/** Tap on a transaction: edit it, or (if it has not synced yet) offer to discard it. */
fun openTransaction(t: Tx, vm: AppViewModel, ui: UiState) {
    if (t.pending) {
        val rid = t.entryId.removePrefix("pending:")
        ui.confirm = ConfirmSpec(
            title = "Not synced yet",
            message = "This entry was saved on your phone and will be sent when you are online. Discard it instead?",
            confirmText = "Discard it",
            danger = true
        ) { vm.discardItem(rid) }
    } else if (t.entryId.isNotBlank()) {
        ui.editTx(t)
    }
}

/* ===================================================================================== */
/*  Add / edit transaction                                                                */
/* ===================================================================================== */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TxSheet(spec: TxSheetSpec, snap: Snapshot, vm: AppViewModel, ui: UiState, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cfg = snap.config
    val entry = spec.entry
    val editing = entry != null
    val today = Dates.today()

    var type by remember { mutableStateOf(spec.type) }
    var amount by remember { mutableStateOf(entry?.amount?.let { trimNum(it) } ?: spec.prefill["amount"] ?: "") }
    var date by remember { mutableStateOf(entry?.date?.ifBlank { today } ?: today) }
    var mode by remember { mutableStateOf(entry?.paymentMode ?: "") }
    var category by remember { mutableStateOf(entry?.category ?: "") }
    var person by remember { mutableStateOf(entry?.person ?: LastUsed.person) }
    var bank by remember { mutableStateOf(entry?.bankAccount ?: LastUsed.bank) }
    var desc by remember { mutableStateOf(if (entry != null && entry.type != "Transfer") entry.description else "") }
    var notes by remember { mutableStateOf(entry?.notes ?: "") }
    var fromPerson by remember { mutableStateOf(entry?.person ?: LastUsed.person) }
    var fromBank by remember { mutableStateOf(entry?.bankAccount ?: spec.prefill["fromBank"] ?: "") }
    var toPerson by remember { mutableStateOf(entry?.toPerson ?: "") }
    var toBank by remember { mutableStateOf(entry?.transferToBank ?: spec.prefill["toBank"] ?: "") }
    var saveTemplate by remember { mutableStateOf(false) }
    var removeReceipt by remember { mutableStateOf(false) }
    var photo by remember { mutableStateOf<CompressedImage?>(null) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    var error by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    fun loadPhoto(uri: Uri) {
        scope.launch {
            try {
                photo = ImageUtil.compress(ctx, uri)
            } catch (e: Exception) {
                error = e.message ?: "Could not use that photo."
            }
        }
    }

    LaunchedEffect(spec.photo) { spec.photo?.let { loadPhoto(it) } }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) cameraUri?.let { loadPhoto(it) }
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) loadPhoto(uri)
    }

    fun startCamera() {
        val dir = File(ctx.cacheDir, "receipts").apply { mkdirs() }
        val file = File(dir, "receipt_" + System.currentTimeMillis() + ".jpg")
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)
        cameraUri = uri
        takePicture.launch(uri)
    }

    val categories = when (type) {
        "Income" -> cfg.incomeCategories
        "Investment" -> cfg.investmentCategories
        else -> cfg.expenseCategories
    }
    val modes = if (type == "Expense") cfg.expenseModes else cfg.otherModes
    val bankLabel = when {
        type == "Income" -> "Received in (account)"
        type == "Investment" -> "Invested from (account)"
        mode == cfg.creditCardMode && cfg.cards.isNotEmpty() -> "Charged to (credit card)"
        else -> "Paid from (account)"
    }

    fun submit(allowDuplicate: Boolean) {
        val amt = amount.toDoubleOrNull() ?: 0.0
        var err = ""
        if (amt <= 0) err = "Enter an amount greater than zero."
        else if (date.isBlank()) err = "Please choose a date."
        else if (mode.isBlank()) err = "Please select a payment mode."
        else if (type == "Transfer") {
            if (fromPerson.isBlank() || toPerson.isBlank()) err = "Select both people."
            else if (fromBank.isBlank() || toBank.isBlank()) err = "Select both accounts."
            else if (fromBank == toBank) err = "Source and destination must be different."
            else if (fromPerson == toPerson) err = "From person and To person cannot be the same."
        } else {
            if (category.isBlank()) err = "Please select a category."
            else if (desc.isBlank()) err = "Please add a short description."
            else if (bank.isBlank()) err = "Please select an account."
            else if (person.isBlank()) err = "Please select a person."
        }
        if (err.isNotEmpty()) { error = err; return }
        error = ""

        val pairs = mutableListOf<Pair<String, Any?>>(
            "action" to (if (editing) "update" else "new"),
            "entryId" to (entry?.entryId ?: ""),
            "type" to type,
            "date" to date,
            "amount" to amt,
            "paymentMode" to mode,
            "notes" to notes.trim()
        )
        val optimistic: Tx
        if (type == "Transfer") {
            pairs.add("fromPerson" to fromPerson); pairs.add("toPerson" to toPerson)
            pairs.add("fromBank" to fromBank); pairs.add("transferToBank" to toBank)
            optimistic = Tx(
                date = date, type = type, category = "Family Transfer", description = "Transfer: $fromPerson → $toPerson",
                amount = amt, paymentMode = mode, person = fromPerson, toPerson = toPerson,
                bankAccount = fromBank, transferToBank = toBank, notes = notes.trim()
            )
        } else {
            pairs.add("category" to category); pairs.add("description" to desc.trim())
            pairs.add("person" to person); pairs.add("bankAccount" to bank)
            optimistic = Tx(
                date = date, type = type, category = category, description = desc.trim(), amount = amt,
                paymentMode = mode, person = person, bankAccount = bank, notes = notes.trim()
            )
        }
        val shot = photo
        if (shot != null) pairs.add("receipt" to obj("mime" to "image/jpeg", "data" to shot.base64))
        if (editing && removeReceipt) pairs.add("removeReceipt" to true)
        if (!editing && saveTemplate) {
            pairs.add("saveAsTemplate" to true)
            pairs.add("templateName" to (if (type == "Transfer") "Transfer" else desc.trim()).take(40))
        }
        if (allowDuplicate) pairs.add("allowDuplicate" to true)

        saving = true
        scope.launch {
            val outcome = vm.saveTx(obj(*pairs.toTypedArray()), optimistic, editing, "$type ${Fmt.money(amt)}")
            saving = false
            when (outcome) {
                is TxOutcome.Saved, is TxOutcome.Queued -> {
                    if (type != "Transfer") { LastUsed.person = person; LastUsed.bank = bank }
                    onClose()
                }
                is TxOutcome.Duplicate -> ui.confirm = ConfirmSpec(
                    title = "Possible duplicate",
                    message = outcome.message + " Save it anyway?",
                    confirmText = "Save anyway"
                ) { submit(true) }
                is TxOutcome.Failed -> error = outcome.message
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                if (editing) "Edit ${type.lowercase()}" else if (type == "Transfer") "New transfer" else "Add ${type.lowercase()}",
                fontSize = 20.sp, fontWeight = FontWeight.ExtraBold
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (t in listOf("Expense", "Income", "Investment", "Transfer")) {
                    FilterChip(
                        selected = type == t,
                        enabled = !editing,
                        onClick = {
                            type = t
                            category = ""
                            mode = ""
                        },
                        label = { Text(if (t == "Investment") "Invest" else t, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    )
                }
            }

            OutlinedTextField(
                value = amount,
                onValueChange = { v -> amount = v.filter { it.isDigit() || it == '.' } },
                label = { Text("Amount") },
                prefix = { Text("₹ ") },
                textStyle = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.ExtraBold),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DateField("Date", date, { date = it }, Modifier.weight(1f))
                DropdownField("Payment mode", mode, modes, { m ->
                    mode = m
                    if (type != "Transfer" && !bankOptions(cfg, m).contains(bank)) bank = ""
                }, Modifier.weight(1f))
            }

            if (type != "Transfer") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DropdownField("Category", category, categories, { category = it }, Modifier.weight(1f))
                    DropdownField("Person", person, cfg.persons, { person = it }, Modifier.weight(1f))
                }
                AppField(desc, { desc = it }, "Description", hint = if (type == "Income") "e.g. Monthly salary" else "e.g. Monthly groceries")
                DropdownField(bankLabel, bank, bankOptions(cfg, mode), { bank = it })
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DropdownField("From person", fromPerson, cfg.persons, { fromPerson = it }, Modifier.weight(1f))
                    DropdownField("From account", fromBank, cfg.banks, { fromBank = it }, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DropdownField("To person", toPerson, cfg.persons, { toPerson = it }, Modifier.weight(1f))
                    DropdownField("To account / card", toBank, cfg.banks + cfg.cards, { toBank = it }, Modifier.weight(1f))
                }
            }

            AppField(notes, { notes = it }, "Notes (optional)", multiline = true)

            // ---- receipt photo ----
            Text("Receipt photo (optional)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { startCamera() }) {
                    Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.padding(start = 6.dp))
                    Text("Camera")
                }
                OutlinedButton(onClick = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                    Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.padding(start = 6.dp))
                    Text("Gallery")
                }
            }
            val shown = photo
            if (shown != null) {
                Image(
                    bitmap = shown.preview.asImageBitmap(), contentDescription = "Receipt preview",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(140.dp)
                )
                TextButton(onClick = { photo = null }) { Text("Remove photo") }
            }
            if (entry != null && entry.receiptUrl.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { ui.receiptUrl = entry.receiptUrl }) { Text("View current receipt") }
                    Checkbox(checked = removeReceipt, onCheckedChange = { removeReceipt = it })
                    Text("Remove it")
                }
            }

            if (!editing) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = saveTemplate, onCheckedChange = { saveTemplate = it })
                    Text("Save as quick-add template")
                }
            }

            if (error.isNotBlank()) {
                Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onClose, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(onClick = { submit(false) }, enabled = !saving, modifier = Modifier.weight(2f)) {
                    Text(if (saving) "Saving…" else if (editing) "Update" else "Save", fontWeight = FontWeight.ExtraBold)
                }
            }
            if (entry != null) {
                OutlinedButton(
                    onClick = {
                        ui.confirm = ConfirmSpec(
                            title = "Delete this transaction?",
                            message = (entry.description.ifBlank { entry.category }) + " · " + Fmt.money(entry.amount) +
                                ". You can undo this for 30 days from More > Trash.",
                            confirmText = "Delete", danger = true
                        ) {
                            vm.deleteItem("deleteTransaction", entry.entryId, "Delete entry")
                            onClose()
                        }
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Delete this transaction") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/* ===================================================================================== */
/*  Bills                                                                                 */
/* ===================================================================================== */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillSheet(spec: BillSheetSpec, snap: Snapshot, vm: AppViewModel, ui: UiState, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val cfg = snap.config
    val bill = spec.bill
    val editing = bill != null

    var name by remember { mutableStateOf(bill?.name ?: "") }
    var amount by remember { mutableStateOf(bill?.amount?.let { trimNum(it) } ?: "") }
    var due by remember { mutableStateOf(bill?.dueDate ?: "") }
    var category by remember { mutableStateOf(bill?.category ?: cfg.billCategories.firstOrNull() ?: "") }
    var frequency by remember { mutableStateOf(bill?.frequency ?: "Monthly") }
    var validity by remember { mutableStateOf(if (bill != null && bill.validityDays > 0) bill.validityDays.toString() else "") }
    var reminder by remember { mutableStateOf((bill?.reminderDaysBefore ?: cfg.defaultReminderDays).toString()) }
    var notes by remember { mutableStateOf(bill?.notes ?: "") }
    var error by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    fun submit() {
        val amt = amount.toDoubleOrNull() ?: 0.0
        val err = when {
            name.isBlank() -> "Please enter a name."
            amt <= 0 -> "Enter an amount greater than zero."
            due.isBlank() -> "Please choose the next due date."
            frequency == "Recharge" && (validity.toIntOrNull() ?: 0) <= 0 -> "Enter the recharge validity in days."
            else -> ""
        }
        if (err.isNotEmpty()) { error = err; return }
        val pairs = mutableListOf<Pair<String, Any?>>(
            "name" to name.trim(), "category" to category, "amount" to amt, "dueDate" to due,
            "frequency" to frequency, "validityDays" to (validity.toIntOrNull() ?: 0),
            "reminderDaysBefore" to (reminder.toIntOrNull() ?: cfg.defaultReminderDays), "notes" to notes.trim()
        )
        if (bill != null) pairs.add("billId" to bill.billId)
        saving = true
        scope.launch {
            val result = vm.run(if (editing) "updateBill" else "addBill", arr(obj(*pairs.toTypedArray())), "Bill ${name.trim()}", okMsg = "Bill saved.")
            saving = false
            if (result == null) onClose() else error = result
        }
    }

    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(if (editing) "Edit bill" else "Add bill or recharge", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            AppField(name, { name = it }, "Bill / service name", hint = "Electricity, Mobile recharge…")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppField(amount, { amount = it }, "Amount (₹)", Modifier.weight(1f), number = true)
                DateField("Next due date", due, { due = it }, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DropdownField("Category", category, cfg.billCategories, { category = it }, Modifier.weight(1f))
                DropdownField("Frequency", frequency, cfg.billFrequencies, { frequency = it }, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (frequency == "Recharge") {
                    AppField(validity, { validity = it.filter { c -> c.isDigit() } }, "Validity (days)", Modifier.weight(1f), number = true)
                }
                AppField(reminder, { reminder = it.filter { c -> c.isDigit() } }, "Remind (days before)", Modifier.weight(1f), number = true)
            }
            AppField(notes, { notes = it }, "Notes (optional)", multiline = true)
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onClose, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(onClick = { submit() }, enabled = !saving, modifier = Modifier.weight(2f)) {
                    Text(if (saving) "Saving…" else if (editing) "Update bill" else "Save bill", fontWeight = FontWeight.ExtraBold)
                }
            }
            if (bill != null) {
                OutlinedButton(
                    onClick = {
                        ui.confirm = ConfirmSpec(
                            "Delete this bill?", bill.name + " will move to Trash (restorable for 30 days).", "Delete", true
                        ) {
                            vm.deleteItem("deleteBill", bill.billId, "Delete bill")
                            onClose()
                        }
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Delete this bill") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** "Mark as paid", optionally also recording the expense so bank balances stay right. */
@Composable
fun PayBillDialog(bill: Bill, snap: Snapshot, vm: AppViewModel, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val cfg = snap.config
    var date by remember { mutableStateOf(Dates.today()) }
    var record by remember { mutableStateOf(LastUsed.recordBillExpense) }
    var amount by remember { mutableStateOf(trimNum(bill.amount)) }
    var mode by remember { mutableStateOf("UPI") }
    var category by remember {
        mutableStateOf(
            if (cfg.expenseCategories.contains(bill.category)) bill.category
            else if (cfg.expenseCategories.contains("Other")) "Other" else ""
        )
    }
    var person by remember { mutableStateOf(LastUsed.person) }
    var bank by remember { mutableStateOf(LastUsed.bank) }
    var error by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Mark as paid?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    bill.name + " · " + Fmt.money(bill.amount) +
                        (if (bill.frequency == "One-time") "" else " — the next due date is set automatically."),
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                DateField("Paid on", date, { date = it })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = record, onCheckedChange = { record = it })
                    Text("Also record as an expense", fontSize = 14.sp)
                }
                if (record) {
                    AppField(amount, { amount = it }, "Amount", number = true)
                    DropdownField("Payment mode", mode, cfg.expenseModes, {
                        mode = it
                        if (!bankOptions(cfg, it).contains(bank)) bank = ""
                    })
                    DropdownField("Category", category, cfg.expenseCategories, { category = it })
                    DropdownField("Person", person, cfg.persons, { person = it })
                    DropdownField(
                        if (mode == cfg.creditCardMode) "Charged to card" else "Paid from",
                        bank, bankOptions(cfg, mode), { bank = it }
                    )
                }
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving,
                onClick = {
                    val amt = amount.toDoubleOrNull() ?: 0.0
                    if (record && (amt <= 0 || mode.isBlank() || category.isBlank() || person.isBlank() || bank.isBlank())) {
                        error = "Please fill every expense field, or untick the box."
                        return@TextButton
                    }
                    LastUsed.recordBillExpense = record
                    if (record) { LastUsed.person = person; LastUsed.bank = bank }
                    val expense: Any? = if (record) {
                        obj("record" to true, "amount" to amt, "paymentMode" to mode, "category" to category, "person" to person, "bank" to bank)
                    } else null
                    saving = true
                    scope.launch {
                        val result = vm.run("markBillPaid", arr(bill.billId, date, expense), "Pay ${bill.name}")
                        saving = false
                        if (result == null) onClose() else error = result
                    }
                }
            ) { Text(if (saving) "Saving…" else "Mark paid", fontWeight = FontWeight.ExtraBold) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } }
    )
}

/* ===================================================================================== */
/*  Generic form                                                                          */
/* ===================================================================================== */

@Composable
private fun FieldInput(f: Field, value: String, onChange: (String) -> Unit, modifier: Modifier) {
    when (f.type) {
        FType.Select -> DropdownField(f.label + if (f.required) " *" else "", value, f.options, onChange, modifier)
        FType.Date -> DateField(f.label + if (f.required) " *" else "", value, onChange, modifier, clearable = f.clearable)
        FType.Number -> AppField(value, onChange, f.label + if (f.required) " *" else "", modifier, number = true, hint = f.hint)
        FType.Multiline -> AppField(value, onChange, f.label, modifier, multiline = true, hint = f.hint)
        FType.Check -> Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = value == "true", onCheckedChange = { onChange(if (it) "true" else "") })
            Text(f.label, fontSize = 14.sp)
        }
        else -> AppField(value, onChange, f.label + if (f.required) " *" else "", modifier, hint = f.hint)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormSheet(spec: FormSpec, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var values by remember { mutableStateOf(spec.initial) }
    var error by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    val visible = spec.fields.filter { f -> f.showWhen?.invoke(values) ?: true }

    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(spec.title, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            if (spec.intro.isNotBlank()) {
                Text(spec.intro, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            var i = 0
            while (i < visible.size) {
                val f = visible[i]
                val next = visible.getOrNull(i + 1)
                if (f.half && next != null && next.half) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FieldInput(f, values[f.key] ?: "", { v -> values = values + (f.key to v) }, Modifier.weight(1f))
                        FieldInput(next, values[next.key] ?: "", { v -> values = values + (next.key to v) }, Modifier.weight(1f))
                    }
                    i += 2
                } else {
                    FieldInput(f, values[f.key] ?: "", { v -> values = values + (f.key to v) }, Modifier.fillMaxWidth())
                    i += 1
                }
            }

            val live = spec.live?.invoke(values)
            if (!live.isNullOrBlank()) Text(live, fontWeight = FontWeight.ExtraBold)
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onClose, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(
                    enabled = !saving,
                    modifier = Modifier.weight(2f),
                    onClick = {
                        val missing = visible.firstOrNull { it.required && (values[it.key] ?: "").isBlank() }
                        if (missing != null) {
                            error = missing.label + " is required."
                        } else {
                            error = ""
                            saving = true
                            scope.launch {
                                val result = spec.onSave(values)
                                saving = false
                                if (result == null) onClose() else error = result
                            }
                        }
                    }
                ) { Text(if (saving) "Saving…" else spec.saveText, fontWeight = FontWeight.ExtraBold) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/* ===================================================================================== */
/*  Dialog host                                                                           */
/* ===================================================================================== */

@Composable
fun ReceiptDialog(url: String, vm: AppViewModel, onClose: () -> Unit) {
    var bitmap by remember(url) { mutableStateOf<Bitmap?>(null) }
    var error by remember(url) { mutableStateOf("") }
    var loading by remember(url) { mutableStateOf(true) }

    LaunchedEffect(url) {
        try {
            bitmap = vm.fetchReceipt(url)
            if (bitmap == null) error = "Could not open this receipt."
        } catch (e: Exception) {
            error = e.message ?: "Could not open this receipt."
        }
        loading = false
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Receipt") },
        text = {
            val b = bitmap
            when {
                loading -> CircularProgressIndicator()
                b != null -> Image(bitmap = b.asImageBitmap(), contentDescription = "Receipt", modifier = Modifier.fillMaxWidth())
                else -> Text(error)
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Close") } }
    )
}

/** Shows whichever sheet or dialog is currently open. Placed once at the top of the app. */
@Composable
fun Hosts(snap: Snapshot, vm: AppViewModel, ui: UiState) {
    ui.txSheet?.let { spec -> TxSheet(spec, snap, vm, ui) { ui.txSheet = null } }
    ui.billSheet?.let { spec -> BillSheet(spec, snap, vm, ui) { ui.billSheet = null } }
    ui.form?.let { spec -> FormSheet(spec) { ui.form = null } }
    ui.payBill?.let { bill -> PayBillDialog(bill, snap, vm) { ui.payBill = null } }
    ui.receiptUrl?.let { url -> ReceiptDialog(url, vm) { ui.receiptUrl = null } }
    ui.confirm?.let { c ->
        AlertDialog(
            onDismissRequest = { ui.confirm = null },
            title = { Text(c.title) },
            text = { Text(c.message) },
            confirmButton = {
                TextButton(onClick = { ui.confirm = null; c.onConfirm() }) {
                    Text(c.confirmText, fontWeight = FontWeight.ExtraBold, color = if (c.danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = { TextButton(onClick = { ui.confirm = null }) { Text("Cancel") } }
        )
    }
}

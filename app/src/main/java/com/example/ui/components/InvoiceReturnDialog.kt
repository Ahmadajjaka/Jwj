package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.local.CashAccountEntity
import com.example.data.local.InvoiceWithItems
import com.example.ui.theme.LocalMamoColors
import com.example.utils.ArabicFormatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceReturnDialog(
    title: String,
    invoiceWithItems: InvoiceWithItems,
    cashAccounts: List<CashAccountEntity>,
    currencySymbol: String,
    isSaleReturn: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (
        productId: Long,
        unitName: String,
        conversionFactor: Double,
        returnedQty: Double,
        refundCashAmount: Double,
        reason: String,
        accountId: Long
    ) -> Unit
) {
    val mamoColors = LocalMamoColors.current
    val items = invoiceWithItems.items
    if (items.isEmpty()) return

    var selectedItem by remember { mutableStateOf(items.first()) }
    var itemExpanded by remember { mutableStateOf(false) }
    var qtyText by remember { mutableStateOf("1") }
    val parsedQty = (ArabicFormatters.parseDoubleOrNull(qtyText) ?: 0.0).coerceIn(0.0, selectedItem.quantity)
    val suggestedTotal = parsedQty * selectedItem.unitPrice
    var refundCashText by remember(selectedItem, qtyText) {
        mutableStateOf(
            if (invoiceWithItems.invoice.paidAmount > 0.0) ArabicFormatters.formatNumber(suggestedTotal).replace(",", "")
            else "0"
        )
    }
    var reason by remember { mutableStateOf(if (isSaleReturn) "مرتجع زبون" else "مرتجع للمورد") }
    var selectedAccountId by remember { mutableStateOf(cashAccounts.firstOrNull()?.id ?: 1L) }
    var accExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$title — ${invoiceWithItems.invoice.invoiceNumber}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExposedDropdownMenuBox(
                    expanded = itemExpanded,
                    onExpandedChange = { itemExpanded = !itemExpanded }
                ) {
                    OutlinedTextField(
                        value = "${selectedItem.productName} (الكمية: ${ArabicFormatters.formatNumber(selectedItem.quantity)} ${selectedItem.unitName})",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("الصنف المرتجع") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = itemExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = itemExpanded, onDismissRequest = { itemExpanded = false }) {
                        items.forEach { itLine ->
                            DropdownMenuItem(
                                text = { Text("${itLine.productName} — ${ArabicFormatters.formatNumber(itLine.quantity)} ${itLine.unitName}") },
                                onClick = {
                                    selectedItem = itLine
                                    itemExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = qtyText,
                    onValueChange = { qtyText = it },
                    label = { Text("الكمية المرتجعة (${selectedItem.unitName}، الحد الأقصى ${ArabicFormatters.formatNumber(selectedItem.quantity)})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "قيمة المرتجع المحسوبة: ${ArabicFormatters.formatMoney(suggestedTotal, currencySymbol)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = mamoColors.gold
                )

                OutlinedTextField(
                    value = refundCashText,
                    onValueChange = { refundCashText = it },
                    label = { Text(if (isSaleReturn) "المبلغ المردود نقداً للزبون" else "المبلغ المسترد نقداً من المورد") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (cashAccounts.isNotEmpty()) {
                    val chosenAcc = cashAccounts.firstOrNull { it.id == selectedAccountId } ?: cashAccounts.first()
                    ExposedDropdownMenuBox(expanded = accExpanded, onExpandedChange = { accExpanded = !accExpanded }) {
                        OutlinedTextField(
                            value = chosenAcc.name,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("الحساب المالي") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accExpanded) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = accExpanded, onDismissRequest = { accExpanded = false }) {
                            cashAccounts.forEach { acc ->
                                DropdownMenuItem(
                                    text = { Text(acc.name) },
                                    onClick = { selectedAccountId = acc.id; accExpanded = false }
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("سبب الإرجاع") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            val q = ArabicFormatters.parseDoubleOrNull(qtyText) ?: 0.0
            val refund = (ArabicFormatters.parseDoubleOrNull(refundCashText) ?: 0.0).coerceAtLeast(0.0)
            Button(
                onClick = {
                    onConfirm(
                        selectedItem.productId,
                        selectedItem.unitName,
                        selectedItem.conversionFactor.coerceAtLeast(0.001),
                        q,
                        refund,
                        reason.ifBlank { "مرتجع فاتورة" },
                        selectedAccountId
                    )
                },
                enabled = q > 0.0 && q <= selectedItem.quantity + 0.0001 && reason.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = mamoColors.loss),
                modifier = Modifier.testTag("confirm_return_btn")
            ) {
                Text("اعتماد المرتجع")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

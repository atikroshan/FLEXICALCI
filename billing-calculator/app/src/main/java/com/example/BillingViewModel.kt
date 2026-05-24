package com.example

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

data class BillingRow(
    val id: String = UUID.randomUUID().toString(),
    val label: String = "",
    val height: String = "",
    val width: String = "",
    val qty: String = "",
    val price: String = "",
    val isEditing: Boolean = true, // New rows start in edit mode
) {
    val heightVal: Double get() = height.toDoubleOrNull() ?: 0.0
    val widthVal: Double get() = width.toDoubleOrNull() ?: 0.0
    val qtyVal: Double get() = qty.toDoubleOrNull() ?: 1.0 // default to 1 matching placeholder
    val priceVal: Double get() = price.toDoubleOrNull() ?: 0.0

    // Match HTML: const sqft = Math.round(h * w);
    val sqft: Long get() = Math.round(heightVal * widthVal)

    // Match HTML: const result = Math.round(sqft * qty);
    val result: Long get() = Math.round(sqft * qtyVal)

    // Match HTML: const total = Math.round(result * price);
    val total: Long get() = Math.round(result * priceVal)

    // Text representation helper matching HTML placeholders
    val sqftStr: String get() = if (height.isEmpty() || width.isEmpty()) "-" else sqft.toString()
    val resultStr: String get() = if (sqft == 0L || qty.isEmpty()) "-" else result.toString()
    val totalStr: String get() = if (result == 0L || price.isEmpty()) "-" else total.toString()
}

class BillingViewModel : ViewModel() {
    private val _rows = MutableStateFlow<List<BillingRow>>(listOf(BillingRow()))
    val rows: StateFlow<List<BillingRow>> = _rows.asStateFlow()

    fun addRow() {
        _rows.update { current ->
            // Ensure any existing rows are finished editing to keep UI focused on the new entry
            current.map { it.copy(isEditing = false) } + BillingRow(isEditing = true)
        }
    }

    fun removeRow(id: String) {
        _rows.update { current ->
            val list = current.filter { it.id != id }
            if (list.isEmpty()) listOf(BillingRow(isEditing = true)) else list
        }
    }

    fun toggleEdit(id: String, isEditing: Boolean) {
        _rows.update { currentList ->
            currentList.map { row ->
                if (row.id == id) {
                    row.copy(isEditing = isEditing)
                } else {
                    row
                }
            }
        }
    }

    fun updateRow(
         id: String,
         label: String? = null,
         height: String? = null,
         width: String? = null,
         qty: String? = null,
         price: String? = null
     ) {
         _rows.update { currentList ->
             currentList.map { row ->
                 if (row.id == id) {
                     row.copy(
                         label = label ?: row.label,
                         height = height ?: row.height,
                         width = width ?: row.width,
                         qty = qty ?: row.qty,
                         price = price ?: row.price
                     )
                 } else {
                     row
                 }
             }
         }
     }

    fun clearAll() {
        _rows.value = listOf(BillingRow())
    }

    val grandTotal: Long
        get() = _rows.value.sumOf { it.total }
}

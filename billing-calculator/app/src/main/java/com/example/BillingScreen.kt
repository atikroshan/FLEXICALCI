package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillingScreen(
    modifier: Modifier = Modifier,
    viewModel: BillingViewModel = viewModel()
) {
    val rows by viewModel.rows.collectAsState()
    val grandTotal = remember(rows) { rows.sumOf { it.total } }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val formatRupee = remember {
        { amount: Long ->
            "₹ " + java.text.DecimalFormat("#,##,##,##0").format(amount)
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWide = maxWidth >= 600.dp

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Billing Calculator",
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Flexible Dimensions Invoice Generator",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                viewModel.clearAll()
                                focusManager.clearFocus()
                                Toast.makeText(context, "Calculations Reset", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.testTag("reset_all_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reset All Fields",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            bottomBar = {
                // Persistent bottom receipt panel on Compact screens
                if (!isWide) {
                    ReceiptPanel(
                        grandTotal = grandTotal,
                        rows = rows,
                        onClearAll = {
                            viewModel.clearAll()
                            focusManager.clearFocus()
                        },
                        onShareText = {
                            scope.launch {
                                val report = withContext(Dispatchers.Default) {
                                    val sb = StringBuilder()
                                    sb.append("===============================\n")
                                    sb.append("       BILLING ESTIMATE        \n")
                                    sb.append("===============================\n\n")

                                    rows.forEachIndexed { idx, r ->
                                        if (r.total > 0) {
                                            val labelStr = if (r.label.isNotEmpty()) " (${r.label})" else ""
                                            sb.append("Item #${idx + 1}$labelStr:\n")
                                            sb.append("  Dimensions: ${r.height}h x ${r.width}w\n")
                                            sb.append("  Area: ${r.sqftStr} sqft\n")
                                            sb.append("  Quantity: ${r.qty}\n")
                                            sb.append("  Result Yield: ${r.resultStr}\n")
                                            sb.append("  Unit Price: IDR/INR ${r.price}\n")
                                            sb.append("  Total price: ${formatRupee(r.total)}\n")
                                            sb.append("-------------------------------\n")
                                        }
                                    }

                                    sb.append("\nGrand Total: ${formatRupee(grandTotal)}\n")
                                    sb.append("===============================\n")
                                    sb.append("Generated via Billing Calculator app\n")
                                    sb.toString()
                                }
                                
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Billing Invoice", report)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Receipt formatted & copied to clipboard!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onSharePdf = {
                            shareInvoicePdf(context, rows, grandTotal, formatRupee)
                        },
                        formatRupee = formatRupee,
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                    )
                }
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isWide) {
                // Large screen / Landscape / Tablet multi-pane layout
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    // Calculator Fields Pane
                    Column(
                        modifier = Modifier
                            .weight(1.3f)
                            .fillMaxHeight()
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        ) {
                            Text(
                                text = "Estimate Line Items",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )

                            Button(
                                onClick = { viewModel.addRow() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.testTag("add_row_wide")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Line Item", fontWeight = FontWeight.Bold)
                            }
                        }

                        // Wide Spreadsheet Table
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            tonalElevation = 1.dp,
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                // Table Header
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TableHeaderCell("Label", Modifier.weight(1.7f))
                                    TableHeaderCell("Height", Modifier.weight(1.1f))
                                    TableHeaderCell("Width", Modifier.weight(1.1f))
                                    TableHeaderCell("Sqft Area", Modifier.weight(1.1f))
                                    TableHeaderCell("Quantity", Modifier.weight(1.0f))
                                    TableHeaderCell("Result", Modifier.weight(1.1f))
                                    TableHeaderCell("Price (₹)", Modifier.weight(1.1f))
                                    TableHeaderCell("Total (₹)", Modifier.weight(1.3f))
                                    TableHeaderCell("Actions", Modifier.weight(0.9f))
                                }

                                LazyColumn(
                                    contentPadding = PaddingValues(bottom = 80.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    itemsIndexed(rows, key = { _, item -> item.id }) { index, row ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                                // Inputs & calculations
                                                if (row.isEditing) {
                                                    TableTextInputField(
                                                        value = row.label,
                                                        onValueChange = { viewModel.updateRow(row.id, label = it) },
                                                        placeholderText = "Glass, plywood...",
                                                        modifier = Modifier.weight(1.7f)
                                                    )
                                                } else {
                                                    TableBadgeField(
                                                        text = row.label.ifEmpty { "Line #${index + 1}" },
                                                        modifier = Modifier.weight(1.7f)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(6.dp))

                                                if (row.isEditing) {
                                                    TableInputField(
                                                        value = row.height,
                                                        onValueChange = { viewModel.updateRow(row.id, height = it) },
                                                        placeholderText = "0",
                                                        modifier = Modifier.weight(1.1f)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    TableInputField(
                                                        value = row.width,
                                                        onValueChange = { viewModel.updateRow(row.id, width = it) },
                                                        placeholderText = "0",
                                                        modifier = Modifier.weight(1.1f)
                                                    )
                                                } else {
                                                    TableBadgeField(
                                                        text = row.height,
                                                        modifier = Modifier.weight(1.1f)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    TableBadgeField(
                                                        text = row.width,
                                                        modifier = Modifier.weight(1.1f)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(6.dp))
                                                TableBadgeField(
                                                    text = row.sqftStr,
                                                    modifier = Modifier.weight(1.1f)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                if (row.isEditing) {
                                                    TableInputField(
                                                        value = row.qty,
                                                        onValueChange = { viewModel.updateRow(row.id, qty = it) },
                                                        placeholderText = "1",
                                                        modifier = Modifier.weight(1.0f)
                                                    )
                                                } else {
                                                    TableBadgeField(
                                                        text = row.qty.ifEmpty { "1" },
                                                        modifier = Modifier.weight(1.0f)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(6.dp))
                                                TableBadgeField(
                                                    text = row.resultStr,
                                                    modifier = Modifier.weight(1.1f)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                if (row.isEditing) {
                                                    TableInputField(
                                                        value = row.price,
                                                        onValueChange = { viewModel.updateRow(row.id, price = it) },
                                                        placeholderText = "0",
                                                        modifier = Modifier.weight(1.1f)
                                                    )
                                                } else {
                                                    TableBadgeField(
                                                        text = row.price.ifEmpty { "0" },
                                                        modifier = Modifier.weight(1.1f)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(6.dp))
                                                TableBadgeField(
                                                    text = if (row.totalStr == "-") "-" else formatRupee(row.total),
                                                    modifier = Modifier.weight(1.3f),
                                                    isHighlighted = !row.isEditing && row.total > 0
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))

                                                Row(
                                                    modifier = Modifier.weight(0.9f),
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    if (row.isEditing) {
                                                        IconButton(
                                                            onClick = { viewModel.toggleEdit(row.id, false) },
                                                            modifier = Modifier
                                                                .size(28.dp)
                                                                .testTag("save_row_${index}")
                                                        ) {
                                                            Icon(
                                                                Icons.Default.Check,
                                                                contentDescription = "Save line item",
                                                                tint = MaterialTheme.colorScheme.primary
                                                            )
                                                        }
                                                    } else {
                                                        IconButton(
                                                            onClick = { viewModel.toggleEdit(row.id, true) },
                                                            modifier = Modifier
                                                                .size(28.dp)
                                                                .testTag("edit_row_${index}")
                                                        ) {
                                                            Icon(
                                                                Icons.Default.Edit,
                                                                contentDescription = "Edit line item",
                                                                tint = MaterialTheme.colorScheme.secondary
                                                            )
                                                        }
                                                    }

                                                    IconButton(
                                                        onClick = {
                                                            viewModel.removeRow(row.id)
                                                        },
                                                        modifier = Modifier
                                                            .size(28.dp)
                                                            .testTag("remove_row_${index}")
                                                    ) {
                                                        Icon(
                                                            Icons.Default.Delete,
                                                            contentDescription = "Remove line item",
                                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Receipt & summary sidebar pane for wide screens
                    Column(
                        modifier = Modifier
                            .weight(0.8f)
                            .fillMaxHeight()
                    ) {
                        ReceiptPanel(
                            grandTotal = grandTotal,
                            rows = rows,
                            onClearAll = {
                                viewModel.clearAll()
                                focusManager.clearFocus()
                            },
                            onShareText = {
                            scope.launch {
                                val report = withContext(Dispatchers.Default) {
                                    val sb = StringBuilder()
                                    sb.append("===============================\n")
                                    sb.append("       BILLING ESTIMATE        \n")
                                    sb.append("===============================\n\n")

                                    rows.forEachIndexed { idx, r ->
                                        if (r.total > 0) {
                                            val labelStr = if (r.label.isNotEmpty()) " (${r.label})" else ""
                                            sb.append("Item #${idx + 1}$labelStr:\n")
                                            sb.append("  Dimensions: ${r.height}h x ${r.width}w\n")
                                            sb.append("  Area: ${r.sqftStr} sqft\n")
                                            sb.append("  Quantity: ${r.qty}\n")
                                            sb.append("  Result Yield: ${r.resultStr}\n")
                                            sb.append("  Unit Price: IDR/INR ${r.price}\n")
                                            sb.append("  Total price: ${formatRupee(r.total)}\n")
                                            sb.append("-------------------------------\n")
                                        }
                                    }

                                    sb.append("\nGrand Total: ${formatRupee(grandTotal)}\n")
                                    sb.append("===============================\n")
                                    sb.append("Generated via Billing Calculator app\n")
                                    sb.toString()
                                }
                                
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Billing Invoice", report)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Receipt formatted & copied to clipboard!", Toast.LENGTH_SHORT).show()
                            }
                        },
                            onSharePdf = {
                                shareInvoicePdf(context, rows, grandTotal, formatRupee)
                            },
                            formatRupee = formatRupee,
                            modifier = Modifier
                                .fillMaxSize()
                                .statusBarsPadding()
                        )
                    }
                }
            } else {
                // Compact Screen Layout (Scrolling list of interactive Cards)
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "Estimate Line Items",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        FilledTonalButton(
                            onClick = { viewModel.addRow() },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("add_row_compact")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Row", fontWeight = FontWeight.Bold)
                        }
                    }

                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        itemsIndexed(rows, key = { _, item -> item.id }) { index, row ->
                            CardItem(
                                index = index,
                                row = row,
                                onUpdateLabel = { viewModel.updateRow(row.id, label = it) },
                                onUpdateHeight = { viewModel.updateRow(row.id, height = it) },
                                onUpdateWidth = { viewModel.updateRow(row.id, width = it) },
                                onUpdateQty = { viewModel.updateRow(row.id, qty = it) },
                                onUpdatePrice = { viewModel.updateRow(row.id, price = it) },
                                onToggleEdit = { viewModel.toggleEdit(row.id, it) },
                                onDelete = { viewModel.removeRow(row.id) },
                                formatRupee = formatRupee
                            )
                        }
                    }
                }
            }
        }
    }
}
}

@Composable
fun CardItem(
    index: Int,
    row: BillingRow,
    onUpdateLabel: (String) -> Unit,
    onUpdateHeight: (String) -> Unit,
    onUpdateWidth: (String) -> Unit,
    onUpdateQty: (String) -> Unit,
    onUpdatePrice: (String) -> Unit,
    onToggleEdit: (Boolean) -> Unit,
    onDelete: () -> Unit,
    formatRupee: (Long) -> String
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (row.isEditing) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
            }
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (row.isEditing) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (row.isEditing) 4.dp else 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Card Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = if (row.isEditing) 12.dp else 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (row.isEditing) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                else MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Line #${index + 1}",
                            fontWeight = FontWeight.Bold,
                            color = if (row.isEditing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                            fontSize = 12.sp
                        )
                    }

                    if (!row.isEditing) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Finished",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!row.isEditing) {
                        TextButton(
                            onClick = { onToggleEdit(true) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("edit_row_button_${index}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit entry",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Edit", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("delete_row_button_${index}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Remove item",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (row.isEditing) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    InputLabel("Item Label / Description")
                    CardTextInputField(
                        value = row.label,
                        onValueChange = onUpdateLabel,
                        placeholderText = "e.g. Glass, Plywood, Window frame",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))

                // Inputs Layout Grid
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        InputLabel("Height")
                        CardInputField(
                            value = row.height,
                            onValueChange = onUpdateHeight,
                            placeholderText = "0",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        InputLabel("Width")
                        CardInputField(
                            value = row.width,
                            onValueChange = onUpdateWidth,
                            placeholderText = "0",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        InputLabel("Quantity")
                        CardInputField(
                            value = row.qty,
                            onValueChange = onUpdateQty,
                            placeholderText = "1",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        InputLabel("Unit Price (₹)")
                        CardInputField(
                            value = row.price,
                            onValueChange = onUpdatePrice,
                            placeholderText = "0",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Bottom calculations section with nice subtle border separator
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    thickness = 1.dp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(2.5f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CalcOutputBadge("Sqft Area", row.sqftStr, Modifier.weight(1f))
                        CalcOutputBadge("Result Yield", row.resultStr, Modifier.weight(1f))
                        CalcOutputBadge(
                            label = "Row Total",
                            value = if (row.totalStr == "-") "-" else formatRupee(row.total),
                            modifier = Modifier.weight(1.2f),
                            isHighlighted = row.total > 0
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onToggleEdit(false) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .weight(1.5f)
                            .height(38.dp)
                            .testTag("finish_row_button_${index}")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Finish", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            } else {
                // Static completed row layout! Neat vertical or grid arrangement of computed details
                if (row.label.isNotEmpty()) {
                    Text(
                        text = row.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1.2f)) {
                        Text(
                            text = "Dimensions",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        Text(
                            text = "${row.height} h × ${row.width} w",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Qty: ${row.qty.ifEmpty { "1" }}  |  Price: ₹${row.price.ifEmpty { "0" }}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Result Yield",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        Text(
                            text = "${row.resultStr} sqft",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1.2f),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = "Total Price",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        )
                        Text(
                            text = if (row.totalStr == "-") "-" else formatRupee(row.total),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun InputLabel(text: String) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        modifier = Modifier.padding(start = 2.dp, bottom = 4.dp)
    )
}

@Composable
fun CardInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholderText: String,
    modifier: Modifier = Modifier
) {
    val cleanValueChange = { inputStr: String ->
        // Accept empty string, digits, and a single decimal point
        if (inputStr.isEmpty() || inputStr.matches(Regex("^\\d*\\.?\\d*$"))) {
            onValueChange(inputStr)
        }
    }

    OutlinedTextField(
        value = value,
        onValueChange = cleanValueChange,
        placeholder = {
            Text(
                text = placeholderText,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Medium
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        ),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
            imeAction = ImeAction.Done
        ),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    )
}

@Composable
fun CardTextInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholderText: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            Text(
                text = placeholderText,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )
        },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            textAlign = TextAlign.Start,
            fontWeight = FontWeight.Medium
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        ),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Next
        ),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    )
}

@Composable
fun CalcOutputBadge(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    isHighlighted: Boolean = false
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isHighlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
            .padding(vertical = 6.dp, horizontal = 4.dp)
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            color = if (isHighlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (isHighlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// Wide Screen Table Components
@Composable
fun TableHeaderCell(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
        modifier = modifier
    )
}

@Composable
fun TableInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholderText: String,
    modifier: Modifier = Modifier
) {
    val cleanValueChange = { inputStr: String ->
        if (inputStr.isEmpty() || inputStr.matches(Regex("^\\d*\\.?\\d*$"))) {
            onValueChange(inputStr)
        }
    }

    OutlinedTextField(
        value = value,
        onValueChange = cleanValueChange,
        placeholder = {
            Text(
                placeholderText,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Medium
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        ),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
            imeAction = ImeAction.Next
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    )
}

@Composable
fun TableTextInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholderText: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            Text(
                placeholderText,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )
        },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            textAlign = TextAlign.Start,
            fontWeight = FontWeight.Medium
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        ),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Next
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    )
}

@Composable
fun TableBadgeField(
    text: String,
    modifier: Modifier = Modifier,
    isHighlighted: Boolean = false
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isHighlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (isHighlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

// Receipt Panel Design
@Composable
fun ReceiptPanel(
    grandTotal: Long,
    rows: List<BillingRow>,
    onClearAll: () -> Unit,
    onShareText: () -> Unit,
    onSharePdf: () -> Unit,
    formatRupee: (Long) -> String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        tonalElevation = 6.dp,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Elegant thermal receipt jagged header simulation
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Receipt Overview",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = "${rows.size} Line Items",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }

            // Beautiful dashed invoice line
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                Color.Transparent
                            )
                        )
                    )
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Billing Totals Layout
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Grand Total",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Rounded Estimate",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    text = formatRupee(grandTotal),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("grand_total_text")
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Dynamic interactive bottom action bar
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Primary Option: Share PDF
                Button(
                    onClick = onSharePdf,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("share_pdf_button")
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Share PDF Invoice", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                }

                // Secondary Options
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onClearAll,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .testTag("clear_button")
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Clear All", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = onShareText,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .weight(1.2f)
                            .height(40.dp)
                            .testTag("share_text_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy Text", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// Copy invoice content neatly formatted
private fun shareInvoiceText(
    context: Context,
    rows: List<BillingRow>,
    grandTotal: Long,
    formatRupee: (Long) -> String
) {
    if (rows.isEmpty() || rows.all { it.total == 0L }) {
        Toast.makeText(context, "No computations to copy yet", Toast.LENGTH_SHORT).show()
        return
    }

    val report = StringBuilder()
    report.append("===============================\n")
    report.append("       BILLING ESTIMATE        \n")
    report.append("===============================\n\n")

    rows.forEachIndexed { idx, r ->
        if (r.total > 0) {
            val labelStr = if (r.label.isNotEmpty()) " (${r.label})" else ""
            report.append("Item #${idx + 1}$labelStr:\n")
            report.append("  Dimensions: ${r.height}h x ${r.width}w\n")
            report.append("  Area: ${r.sqftStr} sqft\n")
            report.append("  Quantity: ${r.qty}\n")
            report.append("  Result Yield: ${r.resultStr}\n")
            report.append("  Unit Price: IDR/INR ${r.price}\n")
            report.append("  Total price: ${formatRupee(r.total)}\n")
            report.append("-------------------------------\n")
        }
    }

    report.append("\nGrand Total: ${formatRupee(grandTotal)}\n")
    report.append("===============================\n")
    report.append("Generated via Billing Calculator app\n")

    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Billing Invoice", report.toString())
    clipboard.setPrimaryClip(clip)

    Toast.makeText(context, "Receipt formatted & copied to clipboard!", Toast.LENGTH_SHORT).show()
}

private fun shareInvoicePdf(
    context: Context,
    rows: List<BillingRow>,
    grandTotal: Long,
    formatRupee: (Long) -> String
) {
    if (rows.isEmpty() || rows.all { it.total == 0L }) {
        Toast.makeText(context, "No computations to share yet", Toast.LENGTH_SHORT).show()
        return
    }

    CoroutineScope(Dispatchers.IO).launch {
        try {
            val pdfDocument = PdfDocument()

            // Page setup: A4 size is 595 x 842 points
            val pageWidth = 595
            val pageHeight = 842
            var pageNumber = 1

            var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            var page = pdfDocument.startPage(pageInfo)
            var canvas = page.canvas

            // Paints
            val primaryPaint = Paint().apply {
                color = android.graphics.Color.rgb(24, 43, 73) // Deep Navy
                isAntiAlias = true
            }
            val accentPaint = Paint().apply {
                color = android.graphics.Color.rgb(65, 105, 225) // Royal Blue
                isAntiAlias = true
            }
            val textDarkPaint = Paint().apply {
                color = android.graphics.Color.rgb(33, 33, 33) // Off-black
                textSize = 10f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                isAntiAlias = true
            }
            val textMutedPaint = Paint().apply {
                color = android.graphics.Color.rgb(100, 110, 120) // Slate Grey
                textSize = 9f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                isAntiAlias = true
            }
            val textHeaderPaint = Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = 16f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }
            val linePaint = Paint().apply {
                color = android.graphics.Color.rgb(218, 220, 224) // Light Divider Grey
                strokeWidth = 1f
                style = Paint.Style.STROKE
            }
            val headerBgPaint = Paint().apply {
                color = android.graphics.Color.rgb(245, 246, 248) // Very light grey for table header
                style = Paint.Style.FILL
            }
            val zebraBgPaint = Paint().apply {
                color = android.graphics.Color.rgb(250, 250, 251) // Subtly tinted row bg
                style = Paint.Style.FILL
            }

            // Draw Header Function
            fun drawPageHeader(canvas: Canvas) {
                // Draw a stylish blue accent block at the left margin top
                canvas.drawRect(40f, 40f, 45f, 90f, accentPaint)

                // Invoice Title
                val titlePaint = Paint().apply {
                    color = android.graphics.Color.rgb(24, 43, 73)
                    textSize = 22f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    isAntiAlias = true
                }
                canvas.drawText("BILLING ESTIMATE", 55f, 65f, titlePaint)

                // Subtitle
                val subtitlePaint = Paint().apply {
                    color = android.graphics.Color.rgb(100, 110, 120)
                    textSize = 9f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    isAntiAlias = true
                }
                canvas.drawText("Flexible Dimensions Invoice Generator", 55f, 82f, subtitlePaint)

                // Date / Metadata on Right Side
                val sdf = SimpleDateFormat("dd-MMM-yyyy hh:mm a", Locale.getDefault())
                val dateStr = sdf.format(Date())
                val metaPaint = Paint().apply {
                    color = android.graphics.Color.rgb(80, 80, 80)
                    textSize = 9f
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
                    isAntiAlias = true
                    textAlign = Paint.Align.RIGHT
                }
                canvas.drawText("Date: $dateStr", 555f, 62f, metaPaint)
                canvas.drawText("Page: $pageNumber", 555f, 77f, metaPaint)

                // Header line divider
                canvas.drawLine(40f, 105f, 555f, 105f, linePaint)
            }

            // Draw Table Header Block
            fun drawTableHeader(canvas: Canvas, yPos: Float) {
                // Draw a soft grey bar
                canvas.drawRect(40f, yPos, 555f, yPos + 22f, headerBgPaint)
                canvas.drawRect(40f, yPos, 555f, yPos + 22f, linePaint) // border

                val fontHeader = Paint().apply {
                    color = android.graphics.Color.rgb(24, 43, 73)
                    textSize = 9f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    isAntiAlias = true
                }

                // Columns positions:
                // Label (X=45) | Dimensions (X=185) | Sqft (X=275) | Qty (X=335) | Yield (X=370) | Unit Price (X=430) | Total (X=490)
                canvas.drawText("Label / Description", 45f, yPos + 15f, fontHeader)
                canvas.drawText("Dimensions", 185f, yPos + 15f, fontHeader)
                canvas.drawText("Sqft", 275f, yPos + 15f, fontHeader)
                canvas.drawText("Qty", 335f, yPos + 15f, fontHeader)
                canvas.drawText("Yield", 370f, yPos + 15f, fontHeader)
                canvas.drawText("Price", 430f, yPos + 15f, fontHeader)

                val textHeaderRight = Paint(fontHeader).apply {
                    textAlign = Paint.Align.RIGHT
                }
                canvas.drawText("Total", 550f, yPos + 15f, textHeaderRight)
            }

            // Initialize first page header
            drawPageHeader(canvas)
            var currentY = 125f

            // Draw Table Header
            drawTableHeader(canvas, currentY)
            currentY += 22f

            // Dynamic Rows drawing
            val rightAlignedNumberPaint = Paint().apply {
                color = android.graphics.Color.rgb(33, 33, 33)
                textSize = 9.5f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
                isAntiAlias = true
                textAlign = Paint.Align.RIGHT
            }

            val rowTextPaint = Paint().apply {
                color = android.graphics.Color.rgb(33, 33, 33)
                textSize = 9.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                isAntiAlias = true
            }

            val rowLabelPaint = Paint(rowTextPaint).apply {
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }

            rows.forEachIndexed { index, row ->
                // Only draw rows expressing real computes or that contain values
                if (row.total > 0 || row.height.isNotEmpty() || row.width.isNotEmpty() || row.label.isNotEmpty()) {
                    // Check if we need to paginate to a new page
                    // We need at least 28px height for a row
                    if (currentY > 750f) {
                        pdfDocument.finishPage(page)
                        pageNumber++
                        pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                        page = pdfDocument.startPage(pageInfo)
                        canvas = page.canvas

                        // Draw headers of new page
                        drawPageHeader(canvas)
                        currentY = 125f
                        drawTableHeader(canvas, currentY)
                        currentY += 22f
                    }

                    // Stripe alternating colors
                    if (index % 2 == 1) {
                        canvas.drawRect(40f, currentY, 555f, currentY + 28f, zebraBgPaint)
                    }
                    // Underline row
                    canvas.drawLine(40f, currentY + 28f, 555f, currentY + 28f, linePaint)

                    // Fill Row details
                    val labelText = row.label.ifEmpty { "Item #${index + 1}" }
                    // Truncate label text if too long
                    var displayLabel = labelText
                    if (rowLabelPaint.measureText(displayLabel) > 130f) {
                        while (rowLabelPaint.measureText("$displayLabel...") > 130f && displayLabel.isNotEmpty()) {
                            displayLabel = displayLabel.dropLast(1)
                        }
                        displayLabel = "$displayLabel..."
                    }

                    // Draw Label
                    canvas.drawText(displayLabel, 45f, currentY + 17f, rowLabelPaint)

                    // Dimensions (height x width)
                    val dimStr = "${row.height} h × ${row.width} w"
                    canvas.drawText(dimStr, 185f, currentY + 17f, rowTextPaint)

                    // Sqft
                    canvas.drawText(row.sqftStr, 275f, currentY + 17f, rowTextPaint)

                    // Qty
                    canvas.drawText(row.qty.ifEmpty { "1" }, 335f, currentY + 17f, rowTextPaint)

                    // Yield
                    val yieldStr = "${row.resultStr} sqft"
                    canvas.drawText(yieldStr, 370f, currentY + 17f, rowTextPaint)

                    // Price
                    val priceStr = row.price.ifEmpty { "0" }
                    canvas.drawText(priceStr, 430f, currentY + 17f, rowTextPaint)

                    // Row Total
                    val totalStrFormatted = if (row.totalStr == "-") "-" else formatRupee(row.total)
                    canvas.drawText(totalStrFormatted, 550f, currentY + 17f, rightAlignedNumberPaint)

                    currentY += 28f
                }
            }

            // Summary details (check space)
            if (currentY > 700f) {
                // Paginate before drawing Grand summary to guarantee it does not overflow
                pdfDocument.finishPage(page)
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas

                drawPageHeader(canvas)
                currentY = 125f
            }

            // Draw Summary Box
            currentY += 15f
            val summaryBoxTop = currentY
            val summaryBoxBottom = currentY + 65f

            // Draw neat border / card for grand total
            val summaryPaint = Paint().apply {
                color = android.graphics.Color.rgb(240, 244, 250)
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            val summaryBorderPaint = Paint().apply {
                color = android.graphics.Color.rgb(24, 43, 73)
                strokeWidth = 1.5f
                style = Paint.Style.STROKE
                isAntiAlias = true
            }

            // Left alignment summary card of A4
            canvas.drawRect(280f, summaryBoxTop, 555f, summaryBoxBottom, summaryPaint)
            canvas.drawRect(280f, summaryBoxTop, 555f, summaryBoxBottom, summaryBorderPaint)

            // Labels inside summary
            val summaryTextPaint = Paint().apply {
                color = android.graphics.Color.rgb(24, 43, 73)
                textSize = 11f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }
            canvas.drawText("GRAND ESTIMATED TOTAL:", 295f, summaryBoxTop + 25f, summaryTextPaint)

            val totalAmountPaint = Paint().apply {
                color = android.graphics.Color.rgb(198, 40, 40) // Beautiful accent warning red for final amount
                textSize = 20f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
                textAlign = Paint.Align.RIGHT
            }
            canvas.drawText(formatRupee(grandTotal), 540f, summaryBoxTop + 50f, totalAmountPaint)

            // Footer Thank You Note & App Stamp
            val footerPaint = Paint().apply {
                color = android.graphics.Color.rgb(140, 150, 160)
                textSize = 9f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("Thank you for your business!", 297.5f, 790f, footerPaint)

            val appStampPaint = Paint().apply {
                color = android.graphics.Color.rgb(180, 185, 195)
                textSize = 7.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("Invoice generated dynamically via Billing Calculator Application on Android.", 297.5f, 805f, appStampPaint)

            pdfDocument.finishPage(page)

            // Save file to cache
            val pdfFile = File(context.cacheDir, "billing_invoice.pdf")
            val fileOutputStream = FileOutputStream(pdfFile)
            pdfDocument.writeTo(fileOutputStream)
            fileOutputStream.close()
            pdfDocument.close()

            // Share Intent
            val fileUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdfFile)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, fileUri)
                putExtra(Intent.EXTRA_SUBJECT, "Billing Invoice - ${formatRupee(grandTotal)}")
                putExtra(Intent.EXTRA_TEXT, "Here is your billing statement estimate: ${formatRupee(grandTotal)}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            withContext(Dispatchers.Main) {
                val activity = context.findActivity()
                if (activity != null) {
                    val chooser = Intent.createChooser(shareIntent, "Share PDF Invoice").apply {
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    activity.startActivity(chooser)
                } else {
                    shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    val chooser = Intent.createChooser(shareIntent, "Share PDF Invoice").apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(chooser)
                }
            }

        } catch (t: Throwable) {
            t.printStackTrace()
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Error sharing PDF: ${t.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }
}

private fun Context.findActivity(): android.app.Activity? {
    var ctx = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is android.app.Activity) {
            return ctx
        }
        ctx = ctx.baseContext
    }
    return null
}

@Preview(showBackground = true)
@Composable
fun TablePreview() {
    MyApplicationTheme {
        BillingScreen()
    }
}

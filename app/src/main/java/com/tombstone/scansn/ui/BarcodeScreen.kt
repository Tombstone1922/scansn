package com.tombstone.scansn.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tombstone.scansn.barcode.BarcodeMatrix
import com.tombstone.scansn.barcode.Code128Generator
import com.tombstone.scansn.data.ProductEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarcodeScreen(product: ProductEntity, onBack: () -> Unit) {
    val codes = buildList {
        if (product.sn.isNotBlank()) add(BarcodeValue("SN", product.sn))
        if (product.imei.isNotBlank()) add(BarcodeValue("IMEI", product.imei))
        if (product.orderNumber.isNotBlank()) add(BarcodeValue("Номер заказа", product.orderNumber))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Штрихкоды") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "${product.brand} ${product.model}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Коды созданы на устройстве в формате Code 128. Увеличьте яркость экрана для надёжного сканирования.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
            codes.forEach { code -> BarcodeCard(code) }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun BarcodeCard(code: BarcodeValue) {
    val matrix = remember(code.value) { runCatching { Code128Generator.encode(code.value) }.getOrNull() }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(code.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (matrix != null) {
                BarcodeCanvas(matrix, "Штрихкод ${code.label}: ${code.value}")
            } else {
                Text(
                    "Нельзя создать Code 128: используйте цифры, латинские буквы и стандартные символы.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(code.value, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun BarcodeCanvas(matrix: BarcodeMatrix, description: String) {
    Canvas(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .width(420.dp)
            .height(116.dp)
            .background(Color.White)
            .semantics { contentDescription = description },
    ) {
        val scaleX = size.width / matrix.width
        val scaleY = size.height / matrix.height
        for (y in 0 until matrix.height) {
            var x = 0
            while (x < matrix.width) {
                if (!matrix[x, y]) {
                    x++
                    continue
                }
                val start = x
                while (x < matrix.width && matrix[x, y]) x++
                drawRect(
                    color = Color.Black,
                    topLeft = Offset(start * scaleX, y * scaleY),
                    size = Size((x - start) * scaleX + 0.25f, scaleY + 0.25f),
                )
            }
        }
    }
}

private data class BarcodeValue(val label: String, val value: String)

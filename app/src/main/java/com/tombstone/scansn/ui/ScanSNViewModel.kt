package com.tombstone.scansn.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tombstone.scansn.data.ProductCategory
import com.tombstone.scansn.data.ProductEntity
import com.tombstone.scansn.data.ProductRepository
import com.tombstone.scansn.export.TableExport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ScanSNViewModel(private val repository: ProductRepository) : ViewModel() {
    val products: StateFlow<List<ProductEntity>> = repository.products.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    private val _draft = MutableStateFlow(ProductEntity())
    val draft: StateFlow<ProductEntity> = _draft.asStateFlow()
    private val _formError = MutableStateFlow<String?>(null)
    val formError: StateFlow<String?> = _formError.asStateFlow()
    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun newProduct() {
        _draft.value = ProductEntity()
        _formError.value = null
    }

    fun editProduct(id: Long) {
        val product = products.value.firstOrNull { it.id == id } ?: return
        _draft.value = product
        _formError.value = null
    }

    fun updateDraft(change: (ProductEntity) -> ProductEntity) {
        _draft.value = change(_draft.value)
        _formError.value = null
    }

    fun applyScan(target: ScanTarget, rawValue: String) {
        val value = rawValue.trim()
        if (value.isEmpty()) return
        updateDraft {
            when (target) {
                ScanTarget.SN -> it.copy(sn = value)
                ScanTarget.IMEI -> it.copy(imei = value)
                ScanTarget.ORDER -> it.copy(orderNumber = value)
            }
        }
        _message.value = "Код записан: $value"
    }

    fun save(onSaved: () -> Unit) {
        val current = _draft.value.normalized()
        val error = validate(current)
        if (error != null) {
            _formError.value = error
            return
        }
        if (_saving.value) return
        viewModelScope.launch {
            _saving.value = true
            _formError.value = null
            try {
                if (current.sn.isNotEmpty() && repository.findSnDuplicate(current.sn, current.id) != null) {
                    _formError.value = "Товар с таким SN уже есть в базе"
                    return@launch
                }
                if (current.imei.isNotEmpty() && repository.findImeiDuplicate(current.imei, current.id) != null) {
                    _formError.value = "Товар с таким IMEI уже есть в базе"
                    return@launch
                }
                repository.save(current.copy(updatedAt = System.currentTimeMillis()))
                _message.value = "Товар сохранён"
                onSaved()
            } catch (error: Exception) {
                _formError.value = "Не удалось сохранить: ${error.localizedMessage ?: "ошибка базы"}"
            } finally {
                _saving.value = false
            }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            repository.delete(id)
            _message.value = "Товар удалён"
        }
    }

    fun consumeMessage() { _message.value = null }

    fun export(uri: Uri, format: ExportFormat, context: Context) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                        when (format) {
                            ExportFormat.CSV -> TableExport.writeCsv(products.value, output)
                            ExportFormat.XLSX -> TableExport.writeXlsx(products.value, output)
                        }
                    } ?: error("файл недоступен")
                }
                _message.value = "Таблица ${format.extension.uppercase()} сохранена"
            } catch (error: Exception) {
                _message.value = "Ошибка экспорта: ${error.localizedMessage ?: "повторите попытку"}"
            }
        }
    }

    private fun validate(product: ProductEntity): String? = when {
        product.category == ProductCategory.PC_COMPONENT && product.componentType.isBlank() -> "Выберите тип комплектующего"
        product.brand.isBlank() -> "Укажите бренд"
        product.model.isBlank() -> "Укажите модель"
        product.sn.isBlank() -> "Отсканируйте или введите SN"
        product.category.requiresImei && product.imei.isBlank() -> "Для телефона необходим IMEI"
        product.imei.isNotBlank() && !product.imei.matches(Regex("\\d{15}")) -> "IMEI должен содержать ровно 15 цифр"
        product.orderNumber.isBlank() -> "Отсканируйте или введите номер заказа"
        product.price.isNotBlank() && !product.price.matches(Regex("\\d+([.,]\\d{1,2})?")) -> "Цена должна быть числом, например 16934 или 16934,50"
        else -> null
    }

    private fun ProductEntity.normalized(): ProductEntity = copy(
        componentType = if (category == ProductCategory.PC_COMPONENT) componentType.trim() else "",
        brand = brand.trim(),
        model = model.trim(),
        color = color.trim(),
        account = account.trim(),
        price = price.trim().replace(',', '.'),
        customsDuty = customsDuty.trim().replace(',', '.'),
        sn = sn.trim(),
        imei = if (category == ProductCategory.PC_COMPONENT) "" else imei.trim(),
        orderNumber = orderNumber.trim(),
        notes = notes.trim(),
    )

    class Factory(private val repository: ProductRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ScanSNViewModel(repository) as T
    }
}

enum class ScanTarget(val fieldTitle: String) {
    SN("SN"),
    IMEI("IMEI"),
    ORDER("номер заказа"),
}

enum class ExportFormat(val extension: String, val mimeType: String) {
    CSV("csv", "text/csv"),
    XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
}

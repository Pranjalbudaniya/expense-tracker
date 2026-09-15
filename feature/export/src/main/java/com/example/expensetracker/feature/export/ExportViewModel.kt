package com.example.expensetracker.feature.export

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

@HiltViewModel
class ExportViewModel @Inject constructor(
    private val exportService: TransactionExportService
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExportUiState())
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    fun setExportFilter(filter: ExportFilter) {
        _uiState.update { it.copy(exportFilter = filter) }
    }

    fun setImportMode(mode: ImportMode) {
        _uiState.update { it.copy(importMode = mode) }
    }

    fun toggleShowInvalidRowsDetails() {
        _uiState.update { it.copy(showInvalidRowsDetails = !it.showInvalidRowsDetails) }
    }

    fun dismissExportMessage() {
        _uiState.update { it.copy(exportSuccessMessage = null, exportError = null) }
    }

    fun dismissImportMessage() {
        _uiState.update { it.copy(importSuccessMessage = null, importError = null) }
    }

    fun clearImportAnalysis() {
        _uiState.update {
            it.copy(
                importAnalysis = null,
                importError = null,
                showInvalidRowsDetails = false
            )
        }
    }

    fun exportToUri(contentResolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, exportSuccessMessage = null, exportError = null) }
            try {
                val outputStream = contentResolver.openOutputStream(uri)
                if (outputStream == null) {
                    _uiState.update {
                        it.copy(
                            isExporting = false,
                            exportError = "Unable to open file for writing"
                        )
                    }
                    return@launch
                }
                outputStream.use { stream ->
                    val result = exportService.exportTransactions(_uiState.value.exportFilter, stream)
                    result.fold(
                        onSuccess = { count ->
                            _uiState.update {
                                it.copy(
                                    isExporting = false,
                                    exportSuccessMessage = "Successfully exported $count transactions to CSV."
                                )
                            }
                        },
                        onFailure = { error ->
                            _uiState.update {
                                it.copy(
                                    isExporting = false,
                                    exportError = error.message ?: "Failed to export transactions"
                                )
                            }
                        }
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        exportError = e.message ?: "An unexpected error occurred during export"
                    )
                }
            }
        }
    }

    fun exportToStream(outputStream: OutputStream) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, exportSuccessMessage = null, exportError = null) }
            val result = exportService.exportTransactions(_uiState.value.exportFilter, outputStream)
            result.fold(
                onSuccess = { count ->
                    _uiState.update {
                        it.copy(
                            isExporting = false,
                            exportSuccessMessage = "Successfully exported $count transactions to CSV."
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isExporting = false,
                            exportError = error.message ?: "Failed to export transactions"
                        )
                    }
                }
            )
        }
    }

    fun analyzeCsvUri(contentResolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isAnalyzingImport = true,
                    importAnalysis = null,
                    importSuccessMessage = null,
                    importError = null
                )
            }
            try {
                val inputStream = contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    _uiState.update {
                        it.copy(
                            isAnalyzingImport = false,
                            importError = "Unable to open selected file for reading"
                        )
                    }
                    return@launch
                }
                inputStream.use { stream ->
                    val analysis = exportService.analyzeCsvForImport(stream)
                    _uiState.update {
                        it.copy(
                            isAnalyzingImport = false,
                            importAnalysis = analysis,
                            importError = analysis.globalError
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isAnalyzingImport = false,
                        importError = e.message ?: "An unexpected error occurred while reading CSV"
                    )
                }
            }
        }
    }

    fun analyzeCsvStream(inputStream: InputStream) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isAnalyzingImport = true,
                    importAnalysis = null,
                    importSuccessMessage = null,
                    importError = null
                )
            }
            try {
                val analysis = exportService.analyzeCsvForImport(inputStream)
                _uiState.update {
                    it.copy(
                        isAnalyzingImport = false,
                        importAnalysis = analysis,
                        importError = analysis.globalError
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isAnalyzingImport = false,
                        importError = e.message ?: "An unexpected error occurred while reading CSV"
                    )
                }
            }
        }
    }

    fun requestExecuteImport() {
        val analysis = _uiState.value.importAnalysis ?: return
        if (analysis.validTransactions.isEmpty()) return

        if (_uiState.value.importMode == ImportMode.REPLACE) {
            _uiState.update { it.copy(showReplaceConfirmDialog = true) }
        } else {
            confirmAndExecuteImport()
        }
    }

    fun dismissReplaceConfirmDialog() {
        _uiState.update { it.copy(showReplaceConfirmDialog = false) }
    }

    fun confirmAndExecuteImport() {
        val analysis = _uiState.value.importAnalysis ?: return
        val mode = _uiState.value.importMode
        val transactions = analysis.validTransactions

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    showReplaceConfirmDialog = false,
                    isExecutingImport = true,
                    importSuccessMessage = null,
                    importError = null
                )
            }

            val result = exportService.executeImport(transactions, mode)
            result.fold(
                onSuccess = { res ->
                    val modeText = if (res.mode == ImportMode.REPLACE) {
                        "Replaced ${res.replacedCount} old records with ${res.insertedCount} new transactions."
                    } else {
                        "Merged ${res.insertedCount} transactions successfully."
                    }
                    _uiState.update {
                        it.copy(
                            isExecutingImport = false,
                            importSuccessMessage = modeText,
                            importAnalysis = null
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isExecutingImport = false,
                            importError = err.message ?: "Failed to execute import."
                        )
                    }
                }
            )
        }
    }
}

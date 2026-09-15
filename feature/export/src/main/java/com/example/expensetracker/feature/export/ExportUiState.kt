package com.example.expensetracker.feature.export

data class ExportUiState(
    val exportFilter: ExportFilter = ExportFilter.ALL,
    val isExporting: Boolean = false,
    val exportSuccessMessage: String? = null,
    val exportError: String? = null,

    val isAnalyzingImport: Boolean = false,
    val importAnalysis: CsvImportAnalysis? = null,
    val importMode: ImportMode = ImportMode.MERGE,
    val showReplaceConfirmDialog: Boolean = false,
    val isExecutingImport: Boolean = false,
    val importSuccessMessage: String? = null,
    val importError: String? = null,
    val showInvalidRowsDetails: Boolean = false
)

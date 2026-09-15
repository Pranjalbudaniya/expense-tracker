package com.example.expensetracker.feature.backup

sealed interface BackupOperationState {
    data object Idle : BackupOperationState
    data object Exporting : BackupOperationState
    data object Validating : BackupOperationState
    data class ReadyForConfirmation(
        val summary: BackupSummary,
        val backup: AppBackup,
        val mode: RestoreMode
    ) : BackupOperationState
    data object Restoring : BackupOperationState
    data class Success(val message: String, val summary: BackupSummary?) : BackupOperationState
    data class Error(val message: String, val errorDetails: List<String> = emptyList()) : BackupOperationState
}

data class BackupUiState(
    val operationState: BackupOperationState = BackupOperationState.Idle,
    val selectedRestoreMode: RestoreMode = RestoreMode.REPLACE
)

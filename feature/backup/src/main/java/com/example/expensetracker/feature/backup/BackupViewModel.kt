package com.example.expensetracker.feature.backup

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupService: BackupService
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    fun setRestoreMode(mode: RestoreMode) {
        _uiState.update { it.copy(selectedRestoreMode = mode) }
    }

    fun exportBackup(context: Context, destinationUri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(operationState = BackupOperationState.Exporting) }

            val result = withContext(Dispatchers.IO) {
                try {
                    val outputStream = context.contentResolver.openOutputStream(destinationUri)
                        ?: return@withContext Result.failure(Exception("Unable to open output stream for selected destination"))
                    outputStream.use { stream ->
                        backupService.exportBackup(stream)
                    }
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }

            result.fold(
                onSuccess = { summary ->
                    _uiState.update {
                        it.copy(
                            operationState = BackupOperationState.Success(
                                message = "Backup exported successfully",
                                summary = summary
                            )
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            operationState = BackupOperationState.Error(
                                message = error.message ?: "Failed to export backup file"
                            )
                        )
                    }
                }
            )
        }
    }

    fun validateBackupFile(context: Context, sourceUri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(operationState = BackupOperationState.Validating) }

            val validationResult = withContext(Dispatchers.IO) {
                try {
                    val inputStream = context.contentResolver.openInputStream(sourceUri)
                        ?: return@withContext BackupValidationResult.Invalid(
                            listOf("Unable to open input stream for selected backup file")
                        )
                    inputStream.use { stream ->
                        backupService.validateBackup(stream)
                    }
                } catch (e: Exception) {
                    BackupValidationResult.Invalid(listOf(e.message ?: "Error reading backup file"))
                }
            }

            when (validationResult) {
                is BackupValidationResult.Valid -> {
                    _uiState.update {
                        it.copy(
                            operationState = BackupOperationState.ReadyForConfirmation(
                                summary = validationResult.summary,
                                backup = validationResult.backup,
                                mode = it.selectedRestoreMode
                            )
                        )
                    }
                }
                is BackupValidationResult.Invalid -> {
                    _uiState.update {
                        it.copy(
                            operationState = BackupOperationState.Error(
                                message = validationResult.errors.firstOrNull() ?: "Invalid backup file",
                                errorDetails = validationResult.errors
                            )
                        )
                    }
                }
            }
        }
    }

    fun confirmRestore() {
        val currentState = _uiState.value.operationState
        if (currentState !is BackupOperationState.ReadyForConfirmation) return

        viewModelScope.launch {
            _uiState.update { it.copy(operationState = BackupOperationState.Restoring) }

            val result = backupService.restoreBackup(
                backup = currentState.backup,
                mode = currentState.mode
            )

            result.fold(
                onSuccess = { summary ->
                    _uiState.update {
                        it.copy(
                            operationState = BackupOperationState.Success(
                                message = if (currentState.mode == RestoreMode.REPLACE) {
                                    "Application data completely replaced with backup"
                                } else {
                                    "Backup data successfully merged into application"
                                },
                                summary = summary
                            )
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            operationState = BackupOperationState.Error(
                                message = error.message ?: "Restore failed. Live data remains untouched."
                            )
                        )
                    }
                }
            )
        }
    }

    fun cancelConfirmation() {
        _uiState.update { it.copy(operationState = BackupOperationState.Idle) }
    }

    fun dismissOperationState() {
        _uiState.update { it.copy(operationState = BackupOperationState.Idle) }
    }
}

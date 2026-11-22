package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.unwrapFirestoreThrowable
import com.example.data.model.BatchEnrollment
import com.example.data.model.BatchMember
import com.example.data.model.StudyBatch
import com.example.data.model.StudyWallet
import com.example.data.model.UiState
import com.example.data.repository.StudyRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class BatchDetailViewModel(
    private val repository: StudyRepository,
    private val currentUserId: String,
    private val batchId: String,
) : ViewModel() {

    private val _events = MutableSharedFlow<String>()
    val events = _events.asSharedFlow()

    private val retryTrigger = MutableStateFlow(0)

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    fun retry() {
        retryTrigger.value += 1
    }

    val batch: StateFlow<UiState<StudyBatch?>> =
        retryTrigger.flatMapLatest {
            repository.observeBatch(batchId)
                .map<StudyBatch?, UiState<StudyBatch?>> { UiState.Success(it) }
                .onStart { emit(UiState.Loading) }
                .catch { error ->
                    val root = unwrapFirestoreThrowable(error)
                    Log.w(TAG, "Error observing batch", root)
                    emit(UiState.Error(root.message ?: "Failed to load batch"))
                }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val wallet: StateFlow<UiState<StudyWallet?>> =
        retryTrigger.flatMapLatest {
            repository.observeWallet(currentUserId)
                .map<StudyWallet?, UiState<StudyWallet?>> { UiState.Success(it) }
                .onStart { emit(UiState.Loading) }
                .catch { error ->
                    val root = unwrapFirestoreThrowable(error)
                    Log.w(TAG, "Error observing wallet", root)
                    emit(UiState.Error(root.message ?: "Failed to load wallet"))
                }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val myEnrollment: StateFlow<BatchEnrollment?> =
        retryTrigger.flatMapLatest {
            repository.observeBatchEnrollments(currentUserId)
                .map { list -> list.firstOrNull { it.batchId == batchId } }
                .catch { error ->
                    val root = unwrapFirestoreThrowable(error)
                    Log.w(TAG, "Error observing batch enrollment", root)
                    emit(null)
                }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val members: StateFlow<UiState<List<BatchMember>>> =
        batch.flatMapLatest { state ->
            when (state) {
                is UiState.Success -> {
                    val b = state.data
                    if (b == null || b.teacherId != currentUserId) {
                        flowOf(UiState.Success(emptyList()))
                    } else {
                        repository.observeBatchMembers(batchId, b.teacherId)
                            .map<List<BatchMember>, UiState<List<BatchMember>>> { UiState.Success(it) }
                            .onStart { emit(UiState.Loading) }
                            .catch { error ->
                                val root = unwrapFirestoreThrowable(error)
                                Log.w(TAG, "Error observing batch members", root)
                                emit(UiState.Error(root.message ?: "Failed to load students"))
                            }
                    }
                }
                is UiState.Error -> flowOf(UiState.Error(state.message))
                UiState.Loading -> flowOf(UiState.Loading)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    fun topUpWallet(amountInr: Long, paymentMethod: String, onDone: () -> Unit = {}) {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            try {
                repository.topUpWallet(amountInr, paymentMethod)
                    .onSuccess {
                        _events.emit("₹$amountInr added to your Study Wallet!")
                        onDone()
                    }
                    .onFailure { e -> _events.emit(e.message ?: "Could not add money.") }
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun payAndEnroll(targetBatch: StudyBatch, paymentMethod: String) {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            try {
                repository.payAndEnrollBatch(targetBatch, paymentMethod)
                    .onSuccess {
                        val msg = if (targetBatch.priceInr > 0L) {
                            "Payment of ₹${targetBatch.priceInr} successful! Enrolled in \"${targetBatch.title}\"."
                        } else {
                            "Enrolled in \"${targetBatch.title}\"!"
                        }
                        _events.emit(msg)
                    }
                    .onFailure { e -> _events.emit(e.message ?: "Enrollment failed.") }
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun deleteBatch(onDeleted: () -> Unit) {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            try {
                repository.deleteBatch(batchId)
                    .onSuccess {
                        _events.emit("Batch deleted.")
                        onDeleted()
                    }
                    .onFailure { e -> _events.emit(e.message ?: "Could not delete batch.") }
            } finally {
                _isBusy.value = false
            }
        }
    }

    private companion object {
        const val TAG = "BatchDetailVM"
        const val STOP_TIMEOUT_MS = 5000L
    }
}

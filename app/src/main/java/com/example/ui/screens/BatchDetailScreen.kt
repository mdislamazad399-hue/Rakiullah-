package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.UiState
import com.example.ui.components.AddMoneyDialog
import com.example.ui.components.BatchCoverBanner
import com.example.ui.components.JoinCodePill
import com.example.ui.components.PayBatchDialog
import com.example.ui.viewmodel.BatchDetailViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchDetailScreen(
    viewModel: BatchDetailViewModel,
    currentUserId: String,
    onBack: () -> Unit,
    onEditBatch: (String) -> Unit,
) {
    val batchState by viewModel.batch.collectAsStateWithLifecycle()
    val walletState by viewModel.wallet.collectAsStateWithLifecycle()
    val myEnrollment by viewModel.myEnrollment.collectAsStateWithLifecycle()
    val membersState by viewModel.members.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current

    var showPayDialog by remember { mutableStateOf(false) }
    var showAddMoneyDialog by remember { mutableStateOf(false) }
    var suggestedTopUp by remember { mutableStateOf(0L) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val currentBatch = (batchState as? UiState.Success)?.data
    val isTeacher = currentBatch?.teacherId == currentUserId
    val balanceInr = (walletState as? UiState.Success)?.data?.balanceInr ?: 0L

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = currentBatch?.title ?: "Batch Details",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("batch_detail_back_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    if (isTeacher && currentBatch != null) {
                        IconButton(
                            onClick = { onEditBatch(currentBatch.id) },
                            modifier = Modifier.testTag("edit_batch_button"),
                        ) {
                            Icon(Icons.Rounded.Edit, contentDescription = "Edit batch")
                        }
                        IconButton(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier.testTag("delete_batch_button"),
                        ) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete batch")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            when (batchState) {
                UiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is UiState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = (batchState as UiState.Error).message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { viewModel.retry() },
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("batch_detail_retry_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.action_retry))
                        }
                    }
                }
                is UiState.Success -> {
                    if (currentBatch == null) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Batch not found.")
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .widthIn(max = 760.dp)
                                .fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = MaterialTheme.shapes.large,
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        BatchCoverBanner(
                                            coverImageData = currentBatch.coverImageData,
                                            subject = currentBatch.subject,
                                            accentKey = currentBatch.accentColorKey,
                                            priceInr = currentBatch.priceInr,
                                            isEnrolled = myEnrollment != null,
                                            height = 200.dp,
                                        )
                                        Column(
                                            modifier = Modifier.padding(18.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            Text(
                                                text = currentBatch.title,
                                                style = MaterialTheme.typography.headlineSmall,
                                                fontWeight = FontWeight.Bold,
                                            )
                                            Text(
                                                text = "Instructor: ${currentBatch.teacherName}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            if (currentBatch.description.isNotBlank()) {
                                                Text(
                                                    text = currentBatch.description,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                )
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                                    if (currentBatch.scheduleText.isNotBlank()) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                        ) {
                                                            Icon(
                                                                Icons.Rounded.Schedule,
                                                                contentDescription = null,
                                                                modifier = Modifier.size(16.dp),
                                                                tint = MaterialTheme.colorScheme.primary,
                                                            )
                                                            Text(
                                                                text = currentBatch.scheduleText,
                                                                style = MaterialTheme.typography.labelLarge,
                                                            )
                                                        }
                                                    }
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                    ) {
                                                        Icon(
                                                            Icons.Rounded.Group,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(16.dp),
                                                            tint = MaterialTheme.colorScheme.secondary,
                                                        )
                                                        Text(
                                                            text = "${currentBatch.enrolledCount} enrolled",
                                                            style = MaterialTheme.typography.labelLarge,
                                                        )
                                                    }
                                                }
                                                JoinCodePill(
                                                    joinCode = currentBatch.batchCode,
                                                    onCopyClick = {
                                                        clipboard.setText(AnnotatedString(currentBatch.batchCode))
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Enrollment / Payment Action Card
                            item {
                                if (myEnrollment != null) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.tertiaryContainer,
                                        shape = MaterialTheme.shapes.large,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.CheckCircle,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                                modifier = Modifier.size(28.dp),
                                            )
                                            Column {
                                                Text(
                                                    text = "You are enrolled in this batch",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                    fontWeight = FontWeight.Bold,
                                                )
                                                Text(
                                                    text = if (myEnrollment!!.amountPaidInr > 0L) {
                                                        "Paid ₹${myEnrollment!!.amountPaidInr} via ${myEnrollment!!.paymentMethod}"
                                                    } else {
                                                        "Free enrollment active"
                                                    },
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f),
                                                )
                                            }
                                        }
                                    }
                                } else if (!isTeacher) {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = MaterialTheme.shapes.large,
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                ) {
                                                    Icon(
                                                        Icons.Rounded.AccountBalanceWallet,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                    )
                                                    Text(
                                                        text = when (walletState) {
                                                            UiState.Loading -> "Study Wallet: Loading..."
                                                            is UiState.Error -> "Study Wallet: Unavailable"
                                                            is UiState.Success -> "Study Wallet: ₹$balanceInr"
                                                        },
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.SemiBold,
                                                    )
                                                }
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    if (walletState is UiState.Error) {
                                                        TextButton(
                                                            onClick = { viewModel.retry() },
                                                            modifier = Modifier.testTag("batch_detail_wallet_retry"),
                                                        ) {
                                                            Text(stringResource(R.string.action_retry))
                                                        }
                                                    }
                                                    TextButton(
                                                        onClick = {
                                                            suggestedTopUp = (currentBatch.priceInr - balanceInr).coerceAtLeast(0L)
                                                            showAddMoneyDialog = true
                                                        },
                                                    ) {
                                                        Text("Add Money")
                                                    }
                                                }
                                            }

                                            Button(
                                                onClick = { showPayDialog = true },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(50.dp)
                                                    .testTag("detail_pay_join_batch_button"),
                                                shape = MaterialTheme.shapes.medium,
                                            ) {
                                                Icon(Icons.Rounded.Payments, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = if (currentBatch.priceInr > 0L) {
                                                        "Pay ₹${currentBatch.priceInr} & Join Batch"
                                                    } else {
                                                        "Join Batch Free"
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Teacher Roster Section
                            if (isTeacher) {
                                item {
                                    Text(
                                        text = "Enrolled Students",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                when (membersState) {
                                    UiState.Loading -> {
                                        item { CircularProgressIndicator() }
                                    }
                                    is UiState.Error -> {
                                        item {
                                            Text(
                                                text = (membersState as UiState.Error).message,
                                                color = MaterialTheme.colorScheme.error,
                                            )
                                        }
                                    }
                                    is UiState.Success -> {
                                        val members = (membersState as UiState.Success).data
                                        if (members.isEmpty()) {
                                            item {
                                                Text(
                                                    text = "No students have joined this batch yet. Share code ${currentBatch.batchCode}!",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        } else {
                                            items(members, key = { it.studentId }) { member ->
                                                Surface(
                                                    color = MaterialTheme.colorScheme.surface,
                                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                                                    shape = MaterialTheme.shapes.medium,
                                                    modifier = Modifier.fillMaxWidth(),
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(14.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically,
                                                    ) {
                                                        Column {
                                                            Text(
                                                                text = member.studentName,
                                                                style = MaterialTheme.typography.titleMedium,
                                                            )
                                                            Text(
                                                                text = "Method: ${member.paymentMethod}",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            )
                                                        }
                                                        Text(
                                                            text = if (member.amountPaidInr > 0L) "Paid ₹${member.amountPaidInr}" else "Free",
                                                            style = MaterialTheme.typography.titleSmall,
                                                            color = MaterialTheme.colorScheme.tertiary,
                                                            fontWeight = FontWeight.Bold,
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            item { Spacer(modifier = Modifier.height(16.dp)) }
                        }
                    }
                }
            }
        }
    }

    if (showAddMoneyDialog) {
        AddMoneyDialog(
            currentBalanceInr = balanceInr,
            suggestedMinAmount = suggestedTopUp,
            isProcessing = isBusy,
            onDismiss = { showAddMoneyDialog = false },
            onAddMoney = { amount, method ->
                viewModel.topUpWallet(amount, method) {
                    showAddMoneyDialog = false
                }
            },
        )
    }

    if (showPayDialog && currentBatch != null) {
        PayBatchDialog(
            batch = currentBatch,
            walletBalanceInr = balanceInr,
            isProcessing = isBusy,
            onDismiss = { showPayDialog = false },
            onOpenAddMoney = { shortfall ->
                showPayDialog = false
                suggestedTopUp = shortfall
                showAddMoneyDialog = true
            },
            onConfirmPay = { method ->
                viewModel.payAndEnroll(currentBatch, method)
                showPayDialog = false
            },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete this batch?") },
            text = { Text("This permanently removes the batch from search and deletes its roster.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteBatch(onDeleted = onBack)
                    },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

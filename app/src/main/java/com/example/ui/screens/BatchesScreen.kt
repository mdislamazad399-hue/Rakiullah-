package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.BatchEnrollment
import com.example.data.model.StudyBatch
import com.example.data.model.StudyWallet
import com.example.data.model.UiState
import com.example.data.model.WalletTransaction
import com.example.ui.components.AddMoneyDialog
import com.example.ui.components.BatchCoverBanner
import com.example.ui.components.EmptyStateIllustration
import com.example.ui.components.JoinCodePill
import com.example.ui.components.PayBatchDialog
import com.example.ui.components.WalletHistoryDialog
import com.example.ui.viewmodel.HomeViewModel

private enum class BatchFilter(val label: String) {
    ALL("All"),
    ENROLLED("Enrolled"),
    MY_BATCHES("Created by Me"),
    FREE("Free"),
    PAID("Paid"),
}

@Composable
fun BatchesScreen(
    homeViewModel: HomeViewModel,
    currentUserId: String,
    onAddBatchClick: () -> Unit,
    onEditBatchClick: (String) -> Unit,
    onOpenBatch: (String) -> Unit,
    onCopiedCode: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val walletState by homeViewModel.wallet.collectAsStateWithLifecycle()
    val transactionsState by homeViewModel.transactions.collectAsStateWithLifecycle()
    val teacherBatchesState by homeViewModel.teacherBatches.collectAsStateWithLifecycle()
    val publishedBatchesState by homeViewModel.publishedBatches.collectAsStateWithLifecycle()
    val enrollmentsState by homeViewModel.batchEnrollments.collectAsStateWithLifecycle()
    val isProcessingPayment by homeViewModel.isProcessingPayment.collectAsStateWithLifecycle()

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var activeFilter by rememberSaveable { mutableStateOf(BatchFilter.ALL) }
    var selectedSubject by rememberSaveable { mutableStateOf<String?>(null) }

    var showAddMoneyDialog by remember { mutableStateOf(false) }
    var suggestedTopUp by remember { mutableStateOf(0L) }
    var showWalletHistory by remember { mutableStateOf(false) }
    var batchPendingCheckout by remember { mutableStateOf<StudyBatch?>(null) }
    var batchPendingDelete by remember { mutableStateOf<StudyBatch?>(null) }

    val clipboard = LocalClipboardManager.current
    val wallet: StudyWallet? = (walletState as? UiState.Success)?.data
    val balanceInr = wallet?.balanceInr ?: 0L
    val transactions: List<WalletTransaction> = (transactionsState as? UiState.Success)?.data.orEmpty()
    val enrollments: List<BatchEnrollment> = (enrollmentsState as? UiState.Success)?.data.orEmpty()
    val enrolledBatchIds = remember(enrollments) { enrollments.map { it.batchId }.toSet() }

    val teacherBatches = (teacherBatchesState as? UiState.Success)?.data.orEmpty()
    val publishedBatches = (publishedBatchesState as? UiState.Success)?.data.orEmpty()

    val allBatches = remember(teacherBatches, publishedBatches) {
        val map = LinkedHashMap<String, StudyBatch>()
        teacherBatches.forEach { map[it.id] = it }
        publishedBatches.forEach { if (!map.containsKey(it.id)) map[it.id] = it }
        map.values.sortedByDescending { it.updatedAt ?: it.createdAt }
    }

    val availableSubjects = remember(allBatches) {
        allBatches.map { it.subject.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .take(8)
    }

    val filteredBatches = remember(allBatches, searchQuery, activeFilter, selectedSubject, enrolledBatchIds, currentUserId) {
        val q = searchQuery.trim().lowercase()
        allBatches.filter { batch ->
            val matchesQuery = q.isEmpty() ||
                batch.title.lowercase().contains(q) ||
                batch.subject.lowercase().contains(q) ||
                batch.teacherName.lowercase().contains(q) ||
                batch.description.lowercase().contains(q) ||
                batch.batchCode.lowercase().contains(q)

            val matchesFilter = when (activeFilter) {
                BatchFilter.ALL -> true
                BatchFilter.ENROLLED -> batch.id in enrolledBatchIds
                BatchFilter.MY_BATCHES -> batch.teacherId == currentUserId
                BatchFilter.FREE -> batch.priceInr == 0L
                BatchFilter.PAID -> batch.priceInr > 0L
            }

            val matchesSubject = selectedSubject == null ||
                batch.subject.equals(selectedSubject, ignoreCase = true)

            matchesQuery && matchesFilter && matchesSubject
        }
    }

    val isLoading = teacherBatchesState is UiState.Loading ||
        (publishedBatchesState is UiState.Loading && allBatches.isEmpty())

    val streamErrorMessage = listOfNotNull(
        (walletState as? UiState.Error)?.message,
        (transactionsState as? UiState.Error)?.message,
        (enrollmentsState as? UiState.Error)?.message,
        (teacherBatchesState as? UiState.Error)?.message,
        (publishedBatchesState as? UiState.Error)?.message,
    ).firstOrNull()
    val batchesFailed = teacherBatchesState is UiState.Error || publishedBatchesState is UiState.Error

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Study Wallet + Add Money + Add Batch Banner
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.75f),
                                    )
                                )
                            )
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.size(42.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Rounded.AccountBalanceWallet,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                        )
                                    }
                                }
                                Column {
                                    Text(
                                        text = stringResource(R.string.study_wallet_title),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                                    )
                                    Text(
                                        text = when (walletState) {
                                            UiState.Loading -> "₹—"
                                            is UiState.Error -> stringResource(R.string.wallet_balance_unavailable)
                                            is UiState.Success -> "₹$balanceInr"
                                        },
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.testTag("wallet_balance_text"),
                                    )
                                }
                            }
                            IconButton(
                                onClick = { showWalletHistory = true },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("batches_wallet_history_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.History,
                                    contentDescription = "Wallet and payment history",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    suggestedTopUp = 0L
                                    showAddMoneyDialog = true
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("batches_add_money_button"),
                                shape = MaterialTheme.shapes.medium,
                            ) {
                                Icon(Icons.Rounded.Payments, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.action_add_money))
                            }
                            Button(
                                onClick = onAddBatchClick,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("batches_add_batch_button"),
                                shape = MaterialTheme.shapes.medium,
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.action_add_batch))
                            }
                        }
                    }
                }
            }

            if (streamErrorMessage != null) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("batches_error_banner"),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.batches_sync_error_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                                Text(
                                    text = streamErrorMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            FilledTonalButton(
                                onClick = { homeViewModel.retryAll() },
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("batches_retry_button"),
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
                }
            }

            // Batch Search Bar
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.batches_search_hint),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = "Search batches",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.testTag("batch_search_clear"),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Clear search",
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("batch_search_input"),
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        BatchFilter.entries.forEach { filter ->
                            FilterChip(
                                selected = activeFilter == filter && selectedSubject == null,
                                onClick = {
                                    activeFilter = filter
                                    selectedSubject = null
                                },
                                label = { Text(filter.label) },
                                modifier = Modifier.testTag("batch_filter_${filter.name.lowercase()}"),
                            )
                        }
                        availableSubjects.forEach { subj ->
                            FilterChip(
                                selected = selectedSubject.equals(subj, ignoreCase = true),
                                onClick = {
                                    selectedSubject = if (selectedSubject.equals(subj, ignoreCase = true)) {
                                        null
                                    } else {
                                        activeFilter = BatchFilter.ALL
                                        subj
                                    }
                                },
                                label = { Text(subj) },
                            )
                        }
                    }
                }
            }

            // Section Title
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.batches_title),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        text = "${filteredBatches.size} found",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (filteredBatches.isEmpty() && !batchesFailed) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(26.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            EmptyStateIllustration(accentColor = MaterialTheme.colorScheme.primary)
                            Text(
                                text = if (searchQuery.isNotBlank()) "No Matching Batches" else "No Batches Yet",
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                text = if (searchQuery.isNotBlank()) {
                                    "Try a different batch name, subject, instructor, or clear your filter."
                                } else {
                                    "Create your first coaching batch with a cover image, schedule, and optional fee."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (searchQuery.isNotBlank() || activeFilter != BatchFilter.ALL || selectedSubject != null) {
                                OutlinedButton(
                                    onClick = {
                                        searchQuery = ""
                                        activeFilter = BatchFilter.ALL
                                        selectedSubject = null
                                    },
                                ) {
                                    Text("Reset Search & Filters")
                                }
                            } else {
                                Button(
                                    onClick = onAddBatchClick,
                                    modifier = Modifier.testTag("empty_add_batch_button"),
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Add First Batch")
                                }
                            }
                        }
                    }
                }
            } else {
                items(filteredBatches, key = { it.id }) { batch ->
                    val isOwner = batch.teacherId == currentUserId
                    val isEnrolled = batch.id in enrolledBatchIds
                    BatchListCard(
                        batch = batch,
                        isOwner = isOwner,
                        isEnrolled = isEnrolled,
                        onOpen = { onOpenBatch(batch.id) },
                        onPayOrJoin = { batchPendingCheckout = batch },
                        onEdit = { onEditBatchClick(batch.id) },
                        onDeleteRequest = { batchPendingDelete = batch },
                        onCopyCode = {
                            clipboard.setText(AnnotatedString(batch.batchCode))
                            onCopiedCode(batch.batchCode)
                        },
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    if (showAddMoneyDialog) {
        AddMoneyDialog(
            currentBalanceInr = balanceInr,
            suggestedMinAmount = suggestedTopUp,
            isProcessing = isProcessingPayment,
            onDismiss = { showAddMoneyDialog = false },
            onAddMoney = { amount, method ->
                homeViewModel.topUpWallet(amount, method) {
                    showAddMoneyDialog = false
                }
            },
        )
    }

    if (showWalletHistory) {
        WalletHistoryDialog(
            balanceInr = balanceInr,
            totalAddedInr = wallet?.totalAddedInr ?: 0L,
            totalSpentInr = wallet?.totalSpentInr ?: 0L,
            transactions = transactions,
            onDismiss = { showWalletHistory = false },
            onAddMoneyClick = {
                showWalletHistory = false
                suggestedTopUp = 0L
                showAddMoneyDialog = true
            },
        )
    }

    batchPendingCheckout?.let { targetBatch ->
        PayBatchDialog(
            batch = targetBatch,
            walletBalanceInr = balanceInr,
            isProcessing = isProcessingPayment,
            onDismiss = { batchPendingCheckout = null },
            onOpenAddMoney = { shortfall ->
                batchPendingCheckout = null
                suggestedTopUp = shortfall
                showAddMoneyDialog = true
            },
            onConfirmPay = { method ->
                homeViewModel.payAndEnrollBatch(targetBatch, method) {
                    batchPendingCheckout = null
                    onOpenBatch(targetBatch.id)
                }
            },
        )
    }

    batchPendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { batchPendingDelete = null },
            title = { Text("Delete \"${target.title}\"?") },
            text = { Text("This deletes the batch and its student roster permanently.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        homeViewModel.deleteBatch(target.id)
                        batchPendingDelete = null
                    },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { batchPendingDelete = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun BatchListCard(
    batch: StudyBatch,
    isOwner: Boolean,
    isEnrolled: Boolean,
    onOpen: () -> Unit,
    onPayOrJoin: () -> Unit,
    onEdit: () -> Unit,
    onDeleteRequest: () -> Unit,
    onCopyCode: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onOpen)
            .testTag("batch_card_${batch.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            BatchCoverBanner(
                coverImageData = batch.coverImageData,
                subject = batch.subject,
                accentKey = batch.accentColorKey,
                priceInr = batch.priceInr,
                isEnrolled = isEnrolled,
            )

            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = batch.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "Instructor: ${batch.teacherName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (isOwner) {
                        Row {
                            IconButton(
                                onClick = onEdit,
                                modifier = Modifier.size(44.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Edit,
                                    contentDescription = "Edit batch",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                            IconButton(
                                onClick = onDeleteRequest,
                                modifier = Modifier.size(44.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = "Delete batch",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }

                if (batch.description.isNotBlank()) {
                    Text(
                        text = batch.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (batch.scheduleText.isNotBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Schedule,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = batch.scheduleText,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Group,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.secondary,
                            )
                            Text(
                                text = "${batch.enrolledCount} joined",
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                    JoinCodePill(joinCode = batch.batchCode, onCopyClick = onCopyCode)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = onOpen,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Text("View Details")
                    }
                    if (!isEnrolled && !isOwner) {
                        Button(
                            onClick = onPayOrJoin,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("pay_batch_button_${batch.id}"),
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Text(
                                text = if (batch.priceInr > 0L) "Pay ₹${batch.priceInr}" else "Join Free",
                            )
                        }
                    }
                }
            }
        }
    }
}

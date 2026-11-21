package com.linnbri13.spllibrary.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.linnbri13.spllibrary.data.HoldItem

@Composable
fun HoldsScreen(
    state: SplUiState,
    vm: SplViewModel,
    onGoToAccount: () -> Unit
) {
    var confirmCancelHold by remember { mutableStateOf<HoldItem?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(tonalElevation = 2.dp) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "My Holds",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (state.isLoggedIn) {
                            val quotaText = if (state.physicalHoldsUsed != null && state.physicalHoldsLimit != null) {
                                "${state.holds.size} total • ${state.physicalHoldsUsed}/${state.physicalHoldsLimit} physical"
                            } else {
                                "${state.holds.size} active holds"
                            }
                            Text(
                                text = quotaText,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (state.isLoggedIn) {
                        IconButton(onClick = { vm.refreshHolds() }, enabled = !state.isLoadingHolds) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh holds")
                        }
                    }
                }

                if (state.isLoggedIn) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HoldFilter.entries.forEach { f ->
                            FilterChip(
                                selected = state.holdsFilter == f,
                                onClick = { vm.setHoldsFilter(f) },
                                label = { Text(f.label) }
                            )
                        }
                    }
                }
            }
        }

        if (!state.isLoggedIn) {
            SignInPrompt(
                title = "Sign in to view your holds",
                subtitle = "See queue positions, ready-for-pickup notices, and place 1-touch holds.",
                onGoToAccount = onGoToAccount
            )
            return
        }

        val filtered = remember(state.holds, state.holdsFilter) {
            when (state.holdsFilter) {
                HoldFilter.ALL -> state.holds
                HoldFilter.PHYSICAL -> state.holds.filter { it.isPhysical }
                HoldFilter.DIGITAL -> state.holds.filter { !it.isPhysical }
                HoldFilter.READY -> state.holds.filter { it.isReady }
            }
        }

        when {
            state.isLoadingHolds && state.holds.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            state.holdsError != null && state.holds.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.holdsError, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { vm.refreshHolds() }) { Text("Retry") }
                    }
                }
            }

            filtered.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.Bookmark,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = if (state.holds.isEmpty()) "No active holds." else "No holds in this filter.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filtered, key = { it.holdId }) { hold ->
                        HoldCard(
                            hold = hold,
                            isCanceling = hold.holdId in state.cancelingHoldIds,
                            onCancelClick = { confirmCancelHold = hold }
                        )
                    }
                }
            }
        }
    }

    val pendingCancel = confirmCancelHold
    if (pendingCancel != null) {
        AlertDialog(
            onDismissRequest = { confirmCancelHold = null },
            title = { Text("Cancel hold?") },
            text = {
                val posNote = pendingCancel.position?.let { " You are currently #$it in line." } ?: ""
                Text("Cancel your hold on \"${pendingCancel.title}\"?$posNote This cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.cancelHold(pendingCancel)
                        confirmCancelHold = null
                    }
                ) {
                    Text("Cancel Hold", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmCancelHold = null }) {
                    Text("Keep Hold")
                }
            }
        )
    }
}

@Composable
private fun HoldCard(
    hold: HoldItem,
    isCanceling: Boolean,
    onCancelClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BookCover(url = hold.jacketUrl, modifier = Modifier.size(width = 62.dp, height = 92.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = hold.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (isCanceling) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(
                            onClick = onCancelClick,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Cancel hold",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (!hold.author.isNullOrBlank()) {
                    Text(
                        text = hold.author,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(6.dp))

                // Prominent status / queue position badge
                val (badgeText, bgColor, fgColor) = when {
                    hold.isReady -> Triple("READY FOR PICKUP", Color(0xFF1B7A43), Color.White)
                    hold.isInTransit -> Triple("IN TRANSIT", Color(0xFF1565C0), Color.White)
                    hold.position != null -> Triple("#${hold.position} in line", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                    else -> Triple(hold.status.replace('_', ' '), MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PillBadge(text = badgeText, containerColor = bgColor, contentColor = fgColor)
                    PillBadge(
                        text = hold.formatLabel,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(6.dp))

                if (!hold.pickupBranchName.isNullOrBlank()) {
                    Text(
                        text = "Pickup: ${hold.pickupBranchName}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (hold.isReady && !hold.expiryDate.isNullOrBlank()) {
                    Text(
                        text = "Pick up by ${hold.expiryDate}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1B7A43)
                    )
                }
            }
        }
    }
}

@Composable
fun SignInPrompt(title: String, subtitle: String, onGoToAccount: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Button(onClick = onGoToAccount) {
                Text("Go to Account")
            }
        }
    }
}

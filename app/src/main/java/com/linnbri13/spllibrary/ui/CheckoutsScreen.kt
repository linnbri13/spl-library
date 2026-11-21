package com.linnbri13.spllibrary.ui

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.linnbri13.spllibrary.data.CheckoutItem
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable
fun CheckoutsScreen(
    state: SplUiState,
    vm: SplViewModel,
    onGoToAccount: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Surface(tonalElevation = 2.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Checkouts",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (state.isLoggedIn) {
                        Text(
                            text = "${state.checkouts.size} checked out",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (state.isLoggedIn) {
                    IconButton(onClick = { vm.refreshCheckouts() }, enabled = !state.isLoadingCheckouts) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh checkouts")
                    }
                }
            }
        }

        if (!state.isLoggedIn) {
            SignInPrompt(
                title = "Sign in to view checkouts",
                subtitle = "See due dates at a glance and renew eligible items in one tap.",
                onGoToAccount = onGoToAccount
            )
            return
        }

        when {
            state.isLoadingCheckouts && state.checkouts.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            state.checkoutsError != null && state.checkouts.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.checkoutsError, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { vm.refreshCheckouts() }) { Text("Retry") }
                    }
                }
            }

            state.checkouts.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.Book,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "No items currently checked out.",
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
                    items(state.checkouts, key = { it.checkoutId }) { checkout ->
                        CheckoutCard(
                            checkout = checkout,
                            isRenewing = checkout.checkoutId in state.renewingCheckoutIds,
                            onRenew = { vm.renewCheckout(checkout) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckoutCard(
    checkout: CheckoutItem,
    isRenewing: Boolean,
    onRenew: () -> Unit
) {
    val dueInfo = parseDueInfo(checkout.dueDate)

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
            BookCover(url = checkout.jacketUrl, modifier = Modifier.size(width = 62.dp, height = 92.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = checkout.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (!checkout.author.isNullOrBlank()) {
                    Text(
                        text = checkout.author,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(6.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PillBadge(
                        text = dueInfo.label,
                        containerColor = dueInfo.bgColor,
                        contentColor = dueInfo.fgColor
                    )
                    PillBadge(
                        text = checkout.formatLabel,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (checkout.timesRenewed > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Renewed ${checkout.timesRenewed}x",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (checkout.canRenew) {
                    Spacer(Modifier.height(8.dp))
                    FilledTonalButton(
                        onClick = onRenew,
                        enabled = !isRenewing,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        if (isRenewing) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                            Text("Renewing...", style = MaterialTheme.typography.labelMedium)
                        } else {
                            Icon(Icons.Filled.Autorenew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Renew", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

private data class DueDisplay(val label: String, val bgColor: Color, val fgColor: Color)

private fun parseDueInfo(dueDate: String?): DueDisplay {
    if (dueDate.isNullOrBlank()) {
        return DueDisplay("Due date unavailable", Color(0xFFE0E0E0), Color(0xFF333333))
    }
    val datePart = dueDate.take(10)
    return try {
        val parsed = LocalDate.parse(datePart)
        val days = ChronoUnit.DAYS.between(LocalDate.now(), parsed)
        when {
            days < 0 -> DueDisplay("Overdue (${-days}d) • $datePart", Color(0xFFC62828), Color.White)
            days == 0L -> DueDisplay("Due TODAY • $datePart", Color(0xFFEF6C00), Color.White)
            days <= 3L -> DueDisplay("Due in ${days}d • $datePart", Color(0xFFFFF3E0), Color(0xFFE65100))
            else -> DueDisplay("Due $datePart (${days}d)", Color(0xFFE0F2F1), Color(0xFF004D40))
        }
    } catch (_: Exception) {
        DueDisplay("Due $datePart", Color(0xFFE0F2F1), Color(0xFF004D40))
    }
}

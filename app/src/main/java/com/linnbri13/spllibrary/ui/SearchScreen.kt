package com.linnbri13.spllibrary.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.linnbri13.spllibrary.data.BookResult
import com.linnbri13.spllibrary.data.FormatFilter
import com.linnbri13.spllibrary.data.NewlyAcquiredFilter
import com.linnbri13.spllibrary.data.SortOption

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(state: SplUiState, vm: SplViewModel) {
    val keyboard = LocalSoftwareKeyboardController.current

    Column(modifier = Modifier.fillMaxSize()) {
        // Header & Search Bar
        Surface(tonalElevation = 2.dp) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "SPL Catalog",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (!state.defaultBranchName.isNullOrBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "1-Touch: ${state.defaultBranchName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = state.query,
                    onValueChange = { vm.updateQuery(it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search title, author, ISBN...") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = { vm.updateQuery("") }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            keyboard?.hide()
                            vm.runSearch(page = 1)
                        }
                    )
                )

                Spacer(Modifier.height(8.dp))

                // Format filter row (Physical Books default ON)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FormatFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = state.formatFilter == filter,
                            onClick = { vm.updateFormatFilter(filter) },
                            label = { Text(filter.label) }
                        )
                    }
                }

                // Newly Acquired & Sort row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NewlyAcquiredFilter.entries.forEach { na ->
                        FilterChip(
                            selected = state.newlyAcquired == na,
                            onClick = { vm.updateNewlyAcquired(na) },
                            label = { Text(na.label) }
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    SortOption.entries.forEach { sort ->
                        FilterChip(
                            selected = state.sortOption == sort,
                            onClick = { vm.updateSortOption(sort) },
                            label = { Text("Sort: ${sort.label}") }
                        )
                    }
                }
            }
        }

        // Body
        when {
            state.isSearching -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            state.searchError != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = state.searchError,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { vm.runSearch(page = 1) }) {
                            Text("Retry")
                        }
                    }
                }
            }

            !state.hasSearched -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Book,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Search Seattle Public Library",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Physical Books filter is on by default. Tap 1-Touch Hold on any book to reserve it immediately.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FilledTonalButton(onClick = { vm.browseNewPhysicalBooks() }) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Browse New Physical Books (30d)")
                        }
                    }
                }
            }

            state.searchResults.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No matching items found. Try loosening your filters.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            text = "${state.searchTotalCount} results • Showing ${state.searchResults.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    items(state.searchResults, key = { it.metadataId }) { book ->
                        BookSearchCard(
                            book = book,
                            isPlacingHold = book.metadataId in state.placingHoldBibIds,
                            isAlreadyHeld = book.metadataId in state.heldBibIds,
                            onCardClick = { vm.openBookDetail(book) },
                            onOneTouchHold = { vm.placeOneTouchHold(book) }
                        )
                    }

                    if (state.searchPage < state.searchTotalPages) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (state.isLoadingMore) {
                                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                                } else {
                                    OutlinedButton(onClick = { vm.runSearch(page = state.searchPage + 1) }) {
                                        Text("Load more (Page ${state.searchPage + 1} of ${state.searchTotalPages})")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Book Detail Modal Sheet
    val selected = state.selectedBook
    if (selected != null) {
        ModalBottomSheet(onDismissRequest = { vm.closeBookDetail() }) {
            BookDetailSheet(
                book = selected,
                state = state,
                onOneTouchHold = { vm.placeOneTouchHold(selected) }
            )
        }
    }
}

@Composable
private fun BookSearchCard(
    book: BookResult,
    isPlacingHold: Boolean,
    isAlreadyHeld: Boolean,
    onCardClick: () -> Unit,
    onOneTouchHold: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCardClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BookCover(url = book.jacketUrl, modifier = Modifier.size(width = 68.dp, height = 100.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = book.authorLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(4.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PillBadge(
                        text = book.formatLabel,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!book.publicationDate.isNullOrBlank()) {
                        Text(
                            text = book.publicationDate,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))

                // Availability summary
                val availText = buildAvailabilitySummary(book)
                val isAvail = book.availableCopies > 0
                Text(
                    text = availText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (isAvail) Color(0xFF1B7A43) else MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(8.dp))

                // One-Touch Hold button
                if (book.holdable) {
                    if (isAlreadyHeld) {
                        FilledTonalButton(
                            onClick = {},
                            enabled = false,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("On Hold", style = MaterialTheme.typography.labelMedium)
                        }
                    } else {
                        Button(
                            onClick = onOneTouchHold,
                            enabled = !isPlacingHold,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            if (isPlacingHold) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Placing...", style = MaterialTheme.typography.labelMedium)
                            } else {
                                Icon(Icons.Filled.AddCircleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("1-Touch Hold", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BookDetailSheet(
    book: BookResult,
    state: SplUiState,
    onOneTouchHold: () -> Unit
) {
    val isPlacing = book.metadataId in state.placingHoldBibIds
    val isHeld = book.metadataId in state.heldBibIds

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            BookCover(url = book.jacketUrl, modifier = Modifier.size(width = 84.dp, height = 124.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(book.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (!book.subtitle.isNullOrBlank()) {
                    Text(book.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(4.dp))
                Text(book.authorLine, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = listOfNotNull(book.formatLabel, book.publicationDate, book.callNumber).joinToString(" • "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        if (book.holdable) {
            Button(
                onClick = onOneTouchHold,
                enabled = !isPlacing && !isHeld,
                modifier = Modifier.fillMaxWidth()
            ) {
                val label = when {
                    isHeld -> "Already On Hold"
                    isPlacing -> "Placing Hold..."
                    !state.defaultBranchName.isNullOrBlank() -> "1-Touch Hold • Pickup at ${state.defaultBranchName}"
                    else -> "Place Hold (Choose Pickup Branch)"
                }
                Text(label)
            }
            Spacer(Modifier.height(14.dp))
        }

        if (!book.description.isNullOrBlank()) {
            Text("Summary", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(book.description, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(14.dp))
        }

        if (book.isPhysical) {
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Text("Copy Availability", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            if (state.isLoadingAvailability) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else if (state.copyAvailability.isEmpty()) {
                Text(
                    text = buildAvailabilitySummary(book),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                state.copyAvailability.take(20).forEach { copy ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(copy.branchName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            if (!copy.callNumber.isNullOrBlank()) {
                                Text(copy.callNumber, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        val avail = copy.status.equals("AVAILABLE", ignoreCase = true)
                        Text(
                            text = copy.status.replace('_', ' '),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (avail) Color(0xFF1B7A43) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(28.dp))
    }
}

private fun buildAvailabilitySummary(book: BookResult): String {
    if (!book.isPhysical) {
        return if (book.status.equals("AVAILABLE", ignoreCase = true)) {
            "Digital • Available"
        } else {
            val h = if (book.heldCopies > 0) " • ${book.heldCopies} holds" else ""
            "Digital • Waitlist$h"
        }
    }
    return when {
        book.availableCopies > 0 ->
            "Available • ${book.availableCopies} of ${book.totalCopies} copies on shelf" +
                if (book.heldCopies > 0) " (${book.heldCopies} holds)" else ""
        book.onOrderCopies > 0 && book.totalCopies == 0 ->
            "On order (${book.onOrderCopies}) • ${book.heldCopies} holds"
        else ->
            "Waitlist • ${book.heldCopies} holds on ${book.totalCopies} copies"
    }
}

@Composable
fun BookCover(url: String?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Book,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(28.dp)
        )
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun PillBadge(text: String, containerColor: Color, contentColor: Color) {
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

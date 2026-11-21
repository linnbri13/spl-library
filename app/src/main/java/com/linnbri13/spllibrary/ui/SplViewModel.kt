package com.linnbri13.spllibrary.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.linnbri13.spllibrary.api.SplApiClient
import com.linnbri13.spllibrary.data.BookResult
import com.linnbri13.spllibrary.data.Branch
import com.linnbri13.spllibrary.data.CheckoutItem
import com.linnbri13.spllibrary.data.CopyAvailability
import com.linnbri13.spllibrary.data.FormatFilter
import com.linnbri13.spllibrary.data.HoldItem
import com.linnbri13.spllibrary.data.NewlyAcquiredFilter
import com.linnbri13.spllibrary.data.SessionStore
import com.linnbri13.spllibrary.data.SortOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class HoldFilter(val label: String) {
    ALL("All"),
    PHYSICAL("Physical"),
    DIGITAL("Digital"),
    READY("Ready")
}

data class SplUiState(
    // Auth & Settings
    val isLoggedIn: Boolean = false,
    val savedUsername: String = "",
    val isLoggingIn: Boolean = false,
    val loginError: String? = null,
    val branches: List<Branch> = emptyList(),
    val defaultBranchCode: String? = null,
    val defaultBranchName: String? = null,

    // Search
    val query: String = "",
    val formatFilter: FormatFilter = FormatFilter.PHYSICAL_BOOKS,
    val newlyAcquired: NewlyAcquiredFilter = NewlyAcquiredFilter.ANY,
    val sortOption: SortOption = SortOption.RELEVANCY,
    val searchResults: List<BookResult> = emptyList(),
    val searchTotalCount: Int = 0,
    val searchPage: Int = 1,
    val searchTotalPages: Int = 1,
    val hasSearched: Boolean = false,
    val isSearching: Boolean = false,
    val isLoadingMore: Boolean = false,
    val searchError: String? = null,

    // Book detail & availability
    val selectedBook: BookResult? = null,
    val copyAvailability: List<CopyAvailability> = emptyList(),
    val isLoadingAvailability: Boolean = false,

    // One-touch holds
    val placingHoldBibIds: Set<String> = emptySet(),
    val heldBibIds: Set<String> = emptySet(),
    val pendingHoldForBranchPick: BookResult? = null,

    // Holds
    val holds: List<HoldItem> = emptyList(),
    val holdsFilter: HoldFilter = HoldFilter.ALL,
    val physicalHoldsUsed: Int? = null,
    val physicalHoldsLimit: Int? = null,
    val isLoadingHolds: Boolean = false,
    val holdsError: String? = null,
    val cancelingHoldIds: Set<String> = emptySet(),

    // Checkouts
    val checkouts: List<CheckoutItem> = emptyList(),
    val isLoadingCheckouts: Boolean = false,
    val checkoutsError: String? = null,
    val renewingCheckoutIds: Set<String> = emptySet(),

    // Feedback
    val snackbarMessage: String? = null
)

class SplViewModel(application: Application) : AndroidViewModel(application) {
    private val sessionStore = SessionStore(application.applicationContext)
    private val api = SplApiClient(sessionStore)

    private val _uiState = MutableStateFlow(
        SplUiState(
            isLoggedIn = sessionStore.isLoggedIn || sessionStore.hasCredentials,
            savedUsername = sessionStore.username,
            defaultBranchCode = sessionStore.defaultBranchCode,
            defaultBranchName = sessionStore.defaultBranchName
        )
    )
    val uiState: StateFlow<SplUiState> = _uiState.asStateFlow()

    init {
        loadBranches()
        if (sessionStore.isLoggedIn || sessionStore.hasCredentials) {
            refreshHolds(silent = true)
            refreshCheckouts(silent = true)
        }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun loadBranches() {
        viewModelScope.launch {
            try {
                val branches = api.getBranches()
                _uiState.update { it.copy(branches = branches) }
            } catch (_: Exception) {
                // Non-fatal; will retry if needed
            }
        }
    }

    fun login(username: String, pin: String) {
        if (username.isBlank() || pin.isBlank()) {
            _uiState.update { it.copy(loginError = "Enter both your SPL card / username and PIN.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoggingIn = true, loginError = null) }
            try {
                api.login(username, pin)
                _uiState.update {
                    it.copy(
                        isLoggedIn = true,
                        savedUsername = username.trim(),
                        isLoggingIn = false,
                        loginError = null,
                        snackbarMessage = "Signed in to Seattle Public Library"
                    )
                }
                refreshHolds(silent = true)
                refreshCheckouts(silent = true)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoggingIn = false,
                        loginError = e.message ?: "Sign in failed"
                    )
                }
            }
        }
    }

    fun logout() {
        sessionStore.clearSession()
        _uiState.update {
            it.copy(
                isLoggedIn = false,
                savedUsername = "",
                holds = emptyList(),
                checkouts = emptyList(),
                heldBibIds = emptySet(),
                holdsError = null,
                checkoutsError = null,
                snackbarMessage = "Signed out"
            )
        }
    }

    fun selectDefaultBranch(branch: Branch) {
        sessionStore.saveDefaultBranch(branch.code, branch.name)
        val pendingBook = _uiState.value.pendingHoldForBranchPick
        _uiState.update {
            it.copy(
                defaultBranchCode = branch.code,
                defaultBranchName = branch.name,
                pendingHoldForBranchPick = null,
                snackbarMessage = "Default pickup branch: ${branch.name}"
            )
        }
        if (pendingBook != null) {
            placeOneTouchHold(pendingBook)
        }
    }

    fun dismissBranchPicker() {
        _uiState.update { it.copy(pendingHoldForBranchPick = null) }
    }

    fun updateQuery(q: String) {
        _uiState.update { it.copy(query = q) }
    }

    fun updateFormatFilter(filter: FormatFilter) {
        _uiState.update { it.copy(formatFilter = filter) }
        if (_uiState.value.hasSearched) {
            runSearch(page = 1)
        }
    }

    fun updateNewlyAcquired(filter: NewlyAcquiredFilter) {
        _uiState.update {
            it.copy(
                newlyAcquired = filter,
                sortOption = if (filter != NewlyAcquiredFilter.ANY && it.query.isBlank()) {
                    SortOption.NEWLY_ACQUIRED
                } else {
                    it.sortOption
                }
            )
        }
        if (_uiState.value.hasSearched || filter != NewlyAcquiredFilter.ANY) {
            runSearch(page = 1)
        }
    }

    fun updateSortOption(sort: SortOption) {
        _uiState.update { it.copy(sortOption = sort) }
        if (_uiState.value.hasSearched) {
            runSearch(page = 1)
        }
    }

    fun browseNewPhysicalBooks() {
        _uiState.update {
            it.copy(
                query = "",
                formatFilter = FormatFilter.PHYSICAL_BOOKS,
                newlyAcquired = NewlyAcquiredFilter.PAST_30_DAYS,
                sortOption = SortOption.NEWLY_ACQUIRED
            )
        }
        runSearch(page = 1)
    }

    fun runSearch(page: Int = 1) {
        val state = _uiState.value
        if (state.query.isBlank() && state.newlyAcquired == NewlyAcquiredFilter.ANY) {
            _uiState.update { it.copy(searchError = "Enter a book title/author or pick a New Books filter.") }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                if (page == 1) {
                    it.copy(isSearching = true, searchError = null, hasSearched = true)
                } else {
                    it.copy(isLoadingMore = true, searchError = null)
                }
            }
            try {
                val result = api.search(
                    query = state.query,
                    formatFilter = state.formatFilter,
                    newlyAcquired = state.newlyAcquired,
                    sortOption = state.sortOption,
                    page = page
                )
                _uiState.update { cur ->
                    val merged = if (page == 1) result.items else cur.searchResults + result.items
                    cur.copy(
                        searchResults = merged,
                        searchTotalCount = result.totalCount,
                        searchPage = result.page,
                        searchTotalPages = result.totalPages,
                        isSearching = false,
                        isLoadingMore = false,
                        searchError = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSearching = false,
                        isLoadingMore = false,
                        searchError = e.message ?: "Search failed"
                    )
                }
            }
        }
    }

    fun openBookDetail(book: BookResult) {
        _uiState.update {
            it.copy(
                selectedBook = book,
                copyAvailability = emptyList(),
                isLoadingAvailability = book.isPhysical
            )
        }
        if (book.isPhysical) {
            viewModelScope.launch {
                try {
                    val copies = api.getAvailability(book.metadataId)
                    _uiState.update { it.copy(copyAvailability = copies, isLoadingAvailability = false) }
                } catch (_: Exception) {
                    _uiState.update { it.copy(isLoadingAvailability = false) }
                }
            }
        }
    }

    fun closeBookDetail() {
        _uiState.update { it.copy(selectedBook = null, copyAvailability = emptyList()) }
    }

    fun placeOneTouchHold(book: BookResult) {
        val state = _uiState.value
        if (!state.isLoggedIn) {
            _uiState.update { it.copy(snackbarMessage = "Sign in on the Account tab to place holds.") }
            return
        }
        if (!book.holdable) {
            _uiState.update { it.copy(snackbarMessage = "One-touch holds are for circulating physical items.") }
            return
        }
        val branchCode = state.defaultBranchCode
        if (branchCode.isNullOrBlank()) {
            if (state.branches.isEmpty()) loadBranches()
            _uiState.update { it.copy(pendingHoldForBranchPick = book) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(placingHoldBibIds = it.placingHoldBibIds + book.metadataId) }
            try {
                val outcome = api.placePhysicalHold(book.metadataId, branchCode)
                val branchLabel = outcome.pickupBranchName ?: state.defaultBranchName ?: "your branch"
                val posPart = outcome.position?.let { " (#$it in line)" } ?: ""
                _uiState.update {
                    it.copy(
                        placingHoldBibIds = it.placingHoldBibIds - book.metadataId,
                        heldBibIds = it.heldBibIds + book.metadataId,
                        snackbarMessage = "Hold placed$posPart • Pickup: $branchLabel"
                    )
                }
                refreshHolds(silent = true)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        placingHoldBibIds = it.placingHoldBibIds - book.metadataId,
                        snackbarMessage = e.message ?: "Could not place hold"
                    )
                }
            }
        }
    }

    fun setHoldsFilter(filter: HoldFilter) {
        _uiState.update { it.copy(holdsFilter = filter) }
    }

    fun refreshHolds(silent: Boolean = false) {
        if (!_uiState.value.isLoggedIn) return
        viewModelScope.launch {
            if (!silent) {
                _uiState.update { it.copy(isLoadingHolds = true, holdsError = null) }
            }
            try {
                val result = api.listHolds()
                // Auto-detect default pickup branch from existing physical holds if user hasn't picked one yet
                if (sessionStore.defaultBranchCode.isNullOrBlank()) {
                    val inferred = result.holds.firstOrNull {
                        !it.pickupBranchCode.isNullOrBlank() && !it.pickupBranchName.isNullOrBlank()
                    }
                    if (inferred != null) {
                        sessionStore.saveDefaultBranch(inferred.pickupBranchCode!!, inferred.pickupBranchName!!)
                    }
                }
                _uiState.update {
                    it.copy(
                        holds = result.holds,
                        heldBibIds = result.holds.map { h -> h.metadataId }.toSet(),
                        physicalHoldsUsed = result.physicalUsed,
                        physicalHoldsLimit = result.physicalLimit,
                        defaultBranchCode = sessionStore.defaultBranchCode,
                        defaultBranchName = sessionStore.defaultBranchName,
                        isLoadingHolds = false,
                        holdsError = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoadingHolds = false,
                        holdsError = if (silent) it.holdsError else (e.message ?: "Could not load holds")
                    )
                }
            }
        }
    }

    fun cancelHold(hold: HoldItem) {
        viewModelScope.launch {
            _uiState.update { it.copy(cancelingHoldIds = it.cancelingHoldIds + hold.holdId) }
            try {
                api.cancelHold(hold.holdId, hold.metadataId)
                _uiState.update { cur ->
                    val remaining = cur.holds.filterNot { it.holdId == hold.holdId }
                    cur.copy(
                        holds = remaining,
                        heldBibIds = remaining.map { it.metadataId }.toSet(),
                        cancelingHoldIds = cur.cancelingHoldIds - hold.holdId,
                        snackbarMessage = "Canceled hold for \"${hold.title}\""
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        cancelingHoldIds = it.cancelingHoldIds - hold.holdId,
                        snackbarMessage = e.message ?: "Could not cancel hold"
                    )
                }
            }
        }
    }

    fun refreshCheckouts(silent: Boolean = false) {
        if (!_uiState.value.isLoggedIn) return
        viewModelScope.launch {
            if (!silent) {
                _uiState.update { it.copy(isLoadingCheckouts = true, checkoutsError = null) }
            }
            try {
                val list = api.listCheckouts()
                _uiState.update {
                    it.copy(
                        checkouts = list,
                        isLoadingCheckouts = false,
                        checkoutsError = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoadingCheckouts = false,
                        checkoutsError = if (silent) it.checkoutsError else (e.message ?: "Could not load checkouts")
                    )
                }
            }
        }
    }

    fun renewCheckout(checkout: CheckoutItem) {
        viewModelScope.launch {
            _uiState.update { it.copy(renewingCheckoutIds = it.renewingCheckoutIds + checkout.checkoutId) }
            try {
                val newDue = api.renewCheckout(checkout.checkoutId)
                val msg = if (!newDue.isNullOrBlank()) {
                    "Renewed \"${checkout.title}\" • New due date: $newDue"
                } else {
                    "Renewed \"${checkout.title}\""
                }
                _uiState.update {
                    it.copy(
                        renewingCheckoutIds = it.renewingCheckoutIds - checkout.checkoutId,
                        snackbarMessage = msg
                    )
                }
                refreshCheckouts(silent = true)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        renewingCheckoutIds = it.renewingCheckoutIds - checkout.checkoutId,
                        snackbarMessage = e.message ?: "Could not renew item"
                    )
                }
            }
        }
    }
}

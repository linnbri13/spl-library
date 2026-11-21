package com.linnbri13.spllibrary

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.linnbri13.spllibrary.ui.AccountScreen
import com.linnbri13.spllibrary.ui.BranchPickerDialog
import com.linnbri13.spllibrary.ui.CheckoutsScreen
import com.linnbri13.spllibrary.ui.HoldsScreen
import com.linnbri13.spllibrary.ui.SearchScreen
import com.linnbri13.spllibrary.ui.SplLibraryTheme
import com.linnbri13.spllibrary.ui.SplViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SplLibraryTheme {
                SplApp()
            }
        }
    }
}

@Composable
private fun SplApp(vm: SplViewModel = viewModel()) {
    val state by vm.uiState.collectAsState()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.snackbarMessage) {
        val msg = state.snackbarMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            vm.clearSnackbar()
        }
    }

    val readyCount = state.holds.count { it.isReady }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Filled.Search, contentDescription = "Search") },
                    label = { Text("Search") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = {
                        selectedTab = 1
                        if (state.isLoggedIn && state.holds.isEmpty()) vm.refreshHolds()
                    },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (readyCount > 0) {
                                    Badge { Text(readyCount.toString()) }
                                }
                            }
                        ) {
                            Icon(Icons.Filled.Bookmark, contentDescription = "Holds")
                        }
                    },
                    label = { Text("Holds") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = {
                        selectedTab = 2
                        if (state.isLoggedIn && state.checkouts.isEmpty()) vm.refreshCheckouts()
                    },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (state.checkouts.isNotEmpty()) {
                                    Badge { Text(state.checkouts.size.toString()) }
                                }
                            }
                        ) {
                            Icon(Icons.Filled.Book, contentDescription = "Checkouts")
                        }
                    },
                    label = { Text("Checkouts") }
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Filled.AccountCircle, contentDescription = "Account") },
                    label = { Text("Account") }
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (selectedTab) {
                0 -> SearchScreen(state = state, vm = vm)
                1 -> HoldsScreen(
                    state = state,
                    vm = vm,
                    onGoToAccount = { selectedTab = 3 }
                )
                2 -> CheckoutsScreen(
                    state = state,
                    vm = vm,
                    onGoToAccount = { selectedTab = 3 }
                )
                3 -> AccountScreen(state = state, vm = vm)
            }
        }
    }

    if (state.pendingHoldForBranchPick != null) {
        BranchPickerDialog(
            title = "Pick your 1-Touch Hold branch",
            subtitle = "Saved for future 1-touch holds. You can change it anytime on the Account tab.",
            branches = state.branches,
            selectedCode = state.defaultBranchCode,
            onSelect = { vm.selectDefaultBranch(it) },
            onDismiss = { vm.dismissBranchPicker() }
        )
    }
}

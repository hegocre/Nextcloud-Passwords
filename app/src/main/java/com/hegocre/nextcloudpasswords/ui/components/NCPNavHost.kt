package com.hegocre.nextcloudpasswords.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.hegocre.nextcloudpasswords.api.FoldersApi
import com.hegocre.nextcloudpasswords.data.folder.Folder
import com.hegocre.nextcloudpasswords.data.password.Password
import com.hegocre.nextcloudpasswords.data.viewmodels.PasswordsViewModel
import com.hegocre.nextcloudpasswords.ui.NCPScreen
import com.hegocre.nextcloudpasswords.utils.PreferencesManager
import com.hegocre.nextcloudpasswords.utils.decryptFolders
import com.hegocre.nextcloudpasswords.utils.decryptPasswords
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@ExperimentalMaterial3Api
@Composable
fun NCPNavHost(
    navController: NavHostController,
    passwordsViewModel: PasswordsViewModel,
    modifier: Modifier = Modifier,
    searchQuery: String = "",
    drawerState: DrawerState? = null,
    searchVisibility: Boolean? = null,
    closeSearch: (() -> Unit)? = null,
    onPasswordClick: ((Password) -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(all = 0.dp)
) {
    val context = LocalContext.current

    val passwords by passwordsViewModel.passwords.observeAsState()
    val folders by passwordsViewModel.folders.observeAsState()
    val keychain by passwordsViewModel.csEv1Keychain.observeAsState()
    val isRefreshing by passwordsViewModel.isRefreshing.collectAsState()

    val passwordsDecryptionState by produceState(
        initialValue = ListDecryptionState(isLoading = true),
        key1 = passwords, key2 = keychain
    ) {
        value = ListDecryptionState(decryptedList = passwords?.let { passwordList ->
            passwordList.decryptPasswords(context, keychain).sortedBy { it.label.lowercase() }
        } ?: emptyList())
    }

    val foldersDecryptionState by produceState(
        initialValue = ListDecryptionState(isLoading = true),
        key1 = folders, key2 = keychain
    ) {
        value = ListDecryptionState(decryptedList = folders?.let { folderList ->
            folderList.decryptFolders(keychain).sortedBy { it.label.lowercase() }
        } ?: emptyList())
    }

    val onFolderClick: (Folder) -> Unit = { folder ->
        navController.navigate("${NCPScreen.Folders.name}/${folder.id}")
    }

    val startDestination by PreferencesManager.getInstance(context).getStartScreen()
        .collectAsState(NCPScreen.Passwords.name, context = Dispatchers.IO)

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(NCPScreen.Passwords.name) {
            NCPNavHostComposable(
                drawerState = drawerState,
                searchVisibility = searchVisibility,
                closeSearch = closeSearch
            ) {
                when {
                    passwordsDecryptionState.isLoading -> {
                        Box(modifier = Modifier.fillMaxSize()) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        }
                    }
                    passwordsDecryptionState.decryptedList != null -> {
                        RefreshListBody(
                            isRefreshing = isRefreshing,
                            onRefresh = { passwordsViewModel.sync() },
                            indicatorPadding = contentPadding
                        ) {
                            MixedLazyColumn(
                                passwords = passwordsDecryptionState.decryptedList
                                    ?.filter {
                                        it.label.lowercase().contains(searchQuery.lowercase()) ||
                                                it.url.lowercase().contains(searchQuery.lowercase())
                                    },
                                onPasswordClick = onPasswordClick,
                                contentPadding = contentPadding
                            )
                        }
                    }
                }
            }
        }

        composable(NCPScreen.Favorites.name) {
            NCPNavHostComposable(
                drawerState = drawerState,
                searchVisibility = searchVisibility,
                closeSearch = closeSearch
            ) {
                when {
                    passwordsDecryptionState.isLoading -> {
                        Box(modifier = Modifier.fillMaxSize()) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        }
                    }
                    passwordsDecryptionState.decryptedList != null -> {
                        RefreshListBody(
                            isRefreshing = isRefreshing,
                            onRefresh = { passwordsViewModel.sync() },
                            indicatorPadding = contentPadding
                        ) {
                            MixedLazyColumn(
                                passwords = passwordsDecryptionState.decryptedList
                                    ?.filter {
                                        it.favorite
                                                && (it.label.lowercase()
                                            .contains(searchQuery.lowercase()) ||
                                                it.url.lowercase()
                                                    .contains(searchQuery.lowercase()))
                                    },
                                onPasswordClick = onPasswordClick,
                                contentPadding = contentPadding
                            )
                        }
                    }
                }
            }
        }

        composable(NCPScreen.Folders.name) {
            NCPNavHostComposable(
                drawerState = drawerState,
                searchVisibility = searchVisibility,
                closeSearch = closeSearch
            ) {
                SideEffect {
                    passwordsViewModel.setVisibleFolder(foldersDecryptionState.decryptedList
                        ?.find { it.id == FoldersApi.DEFAULT_FOLDER_UUID })
                }
                when {
                    foldersDecryptionState.isLoading || passwordsDecryptionState.isLoading -> {
                        Box(modifier = Modifier.fillMaxSize()) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        }
                    }
                    foldersDecryptionState.decryptedList != null
                            && passwordsDecryptionState.decryptedList != null -> {
                        RefreshListBody(
                            isRefreshing = isRefreshing,
                            onRefresh = { passwordsViewModel.sync() },
                            indicatorPadding = contentPadding
                        ) {
                            MixedLazyColumn(
                                passwords = passwordsDecryptionState.decryptedList
                                    ?.filter {
                                        it.folder == FoldersApi.DEFAULT_FOLDER_UUID
                                                && (it.label.lowercase()
                                            .contains(searchQuery.lowercase()) ||
                                                it.url.lowercase()
                                                    .contains(searchQuery.lowercase()))
                                    },
                                folders = foldersDecryptionState.decryptedList
                                    ?.filter {
                                        it.parent == FoldersApi.DEFAULT_FOLDER_UUID
                                                && it.label.lowercase()
                                            .contains(searchQuery.lowercase())
                                    },
                                onPasswordClick = onPasswordClick,
                                onFolderClick = onFolderClick,
                                contentPadding = contentPadding
                            )
                        }
                    }
                }
            }
        }

        composable(
            route = "${NCPScreen.Folders.name}/{folder_uuid}",
            arguments = listOf(
                navArgument("folder_uuid") {
                    type = NavType.StringType
                }
            )
        ) { entry ->
            val folderUuid =
                entry.arguments?.getString("folder_uuid") ?: FoldersApi.DEFAULT_FOLDER_UUID
            NCPNavHostComposable(
                drawerState = drawerState,
                searchVisibility = searchVisibility,
                closeSearch = closeSearch
            ) {
                when {
                    passwordsDecryptionState.isLoading -> {
                        Box(modifier = Modifier.fillMaxSize()) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        }
                    }
                    passwordsDecryptionState.decryptedList != null -> {
                        LaunchedEffect(folderUuid, passwordsDecryptionState) {
                            if (foldersDecryptionState.decryptedList?.isEmpty() == false) {
                                passwordsViewModel.setVisibleFolder(foldersDecryptionState.decryptedList
                                    ?.find { it.id == folderUuid })
                            }
                        }

                        RefreshListBody(
                            isRefreshing = isRefreshing,
                            onRefresh = { passwordsViewModel.sync() },
                            indicatorPadding = contentPadding
                        ) {
                            MixedLazyColumn(
                                passwords = passwordsDecryptionState.decryptedList
                                    ?.filter {
                                        it.folder == folderUuid
                                                && (it.label.lowercase()
                                            .contains(searchQuery.lowercase()) ||
                                                it.url.lowercase()
                                                    .contains(searchQuery.lowercase()))
                                    },
                                folders = foldersDecryptionState.decryptedList
                                    ?.filter {
                                        it.parent == folderUuid
                                                && it.label.lowercase()
                                            .contains(searchQuery.lowercase())
                                    },
                                onPasswordClick = onPasswordClick,
                                onFolderClick = onFolderClick,
                                contentPadding = contentPadding
                            )
                        }
                    }
                }
            }
        }
    }
}

@ExperimentalMaterial3Api
@Composable
fun NCPNavHostComposable(
    modifier: Modifier = Modifier,
    drawerState: DrawerState? = null,
    searchVisibility: Boolean? = null,
    closeSearch: (() -> Unit)? = null,
    content: @Composable () -> Unit = { }
) {
    BackHandler(enabled = searchVisibility == true) {
        closeSearch?.invoke()
    }
    val scope = rememberCoroutineScope()
    BackHandler(enabled = drawerState?.isOpen ?: false) {
        scope.launch {
            drawerState?.close()
        }
    }
    Box(modifier = modifier) {
        content()
    }
}
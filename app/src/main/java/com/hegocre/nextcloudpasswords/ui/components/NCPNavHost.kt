package com.hegocre.nextcloudpasswords.ui.components

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.hegocre.nextcloudpasswords.R
import com.hegocre.nextcloudpasswords.api.FoldersApi
import com.hegocre.nextcloudpasswords.data.folder.DeletedFolder
import com.hegocre.nextcloudpasswords.data.folder.Folder
import com.hegocre.nextcloudpasswords.data.folder.NewFolder
import com.hegocre.nextcloudpasswords.data.folder.UpdatedFolder
import com.hegocre.nextcloudpasswords.data.password.DeletedPassword
import com.hegocre.nextcloudpasswords.data.password.NewPassword
import com.hegocre.nextcloudpasswords.data.password.Password
import com.hegocre.nextcloudpasswords.data.password.UpdatedPassword
import com.hegocre.nextcloudpasswords.data.serversettings.ServerSettings
import com.hegocre.nextcloudpasswords.ui.NCPScreen
import com.hegocre.nextcloudpasswords.ui.viewmodels.PasswordsViewModel
import com.hegocre.nextcloudpasswords.utils.PreferencesManager
import com.hegocre.nextcloudpasswords.utils.decryptFolders
import com.hegocre.nextcloudpasswords.utils.decryptPasswords
import com.hegocre.nextcloudpasswords.utils.encryptValue
import com.hegocre.nextcloudpasswords.utils.sha1Hash
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@ExperimentalMaterial3Api
@Composable
fun NCPNavHost(
    navController: NavHostController,
    passwordsViewModel: PasswordsViewModel,
    modifier: Modifier = Modifier,
    searchQuery: String = "",
    isAutofillRequest: Boolean,
    openPasswordDetails: (Password, List<String>) -> Unit,
    replyAutofill: ((String, String, String) -> Unit)? = null,
    modalSheetState: SheetState? = null,
    searchVisibility: Boolean? = null,
    closeSearch: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val passwords by passwordsViewModel.passwords.observeAsState()
    val folders by passwordsViewModel.folders.observeAsState()
    val shares by passwordsViewModel.shares.observeAsState()
    val keychain by passwordsViewModel.csEv1Keychain.observeAsState()
    val isRefreshing by passwordsViewModel.isRefreshing.collectAsState()
    val isUpdating by passwordsViewModel.isUpdating.collectAsState()
    val serverSettings by passwordsViewModel.serverSettings.observeAsState(initial = ServerSettings())
    val sessionOpen by passwordsViewModel.sessionOpen.collectAsState()

    val passwordsDecryptionState by produceState(
        initialValue = ListDecryptionState(isLoading = true),
        key1 = passwords, key2 = keychain
    ) {
        value = ListDecryptionState(decryptedList = passwords?.let { passwordList ->
            passwordList.decryptPasswords(keychain).sortedBy { it.label.lowercase() }
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

    val baseFolderName = stringResource(R.string.top_level_folder_name)
    val onPasswordClick: (Password) -> Unit = { password ->
        if (isAutofillRequest && replyAutofill != null) {
            replyAutofill(password.label, password.username, password.password)
        } else {
            val folderPath = mutableListOf<String>()
            var nextFolderUuid = password.folder
            while (nextFolderUuid != FoldersApi.DEFAULT_FOLDER_UUID) {
                val nextFolder =
                    foldersDecryptionState.decryptedList?.find { it.id == nextFolderUuid }
                nextFolder?.label?.let {
                    folderPath.add(it)
                }
                nextFolderUuid = nextFolder?.parent ?: FoldersApi.DEFAULT_FOLDER_UUID
            }
            folderPath.add(baseFolderName)
            openPasswordDetails(password, folderPath.toList())
        }
    }

    val onFolderClick: (Folder) -> Unit = { folder ->
        navController.navigate("${NCPScreen.Folders.name}/${folder.id}")
    }

    val userStartDestination by PreferencesManager.getInstance(context).getStartScreen()
        .collectAsState(NCPScreen.Passwords.name, context = Dispatchers.IO)

    val startDestination = remember(isAutofillRequest, userStartDestination) {
        if (isAutofillRequest) NCPScreen.Passwords.name else userStartDestination
    }

    val searchByUsername by PreferencesManager.getInstance(context).getSearchByUsername()
        .collectAsState(true, context = Dispatchers.IO)
    val strictUrlMatching by PreferencesManager.getInstance(context).getUseStrictUrlMatching()
        .collectAsState(true, context = Dispatchers.IO)

    val filteredPasswordList = remember(passwordsDecryptionState.decryptedList, searchQuery) {
        passwordsDecryptionState.decryptedList?.filter {
            !it.hidden && !it.trashed && (it.matches(searchQuery, strictUrlMatching)
                    || (searchByUsername && it.username.contains(searchQuery)))
        }
    }
    val filteredFolderList = remember(foldersDecryptionState.decryptedList, searchQuery) {
        foldersDecryptionState.decryptedList?.filter {
            !it.hidden && !it.trashed && it.label.lowercase().contains(searchQuery.lowercase())
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = { fadeIn(animationSpec = tween(300)) },
        exitTransition = { fadeOut(animationSpec = tween(300)) },
    ) {
        composable(NCPScreen.Passwords.name) {
            NCPNavHostComposable(
                modalSheetState = modalSheetState,
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
                        PullToRefreshBody(
                            isRefreshing = isRefreshing,
                            onRefresh = { passwordsViewModel.sync() },
                        ) {
                            if (filteredPasswordList?.isEmpty() == true) {
                                if (searchQuery.isBlank()) NoContentText() else NoResultsText()
                            } else {
                                MixedLazyColumn(
                                    passwords = filteredPasswordList,
                                    onPasswordClick = onPasswordClick,
                                    onPasswordLongClick = {
                                        if (sessionOpen && !isAutofillRequest && it.editable)
                                            navController.navigate("${NCPScreen.PasswordEdit.name}/${it.id}")
                                    },
                                    getPainterForUrl = { passwordsViewModel.getPainterForUrl(url = it) }
                                )
                            }
                        }
                    }
                }
            }
        }

        composable(NCPScreen.Favorites.name) {
            val filteredFavoritePasswords = remember(filteredPasswordList) {
                filteredPasswordList?.filter { it.favorite }
            }
            NCPNavHostComposable(
                modalSheetState = modalSheetState,
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
                        PullToRefreshBody(
                            isRefreshing = isRefreshing,
                            onRefresh = { passwordsViewModel.sync() },
                        ) {
                            if (filteredFavoritePasswords?.isEmpty() == true) {
                                if (searchQuery.isBlank())
                                    NoContentText()
                                else
                                    NoResultsText { navController.navigate(NCPScreen.Passwords.name) }
                            } else {
                                MixedLazyColumn(
                                    passwords = filteredFavoritePasswords,
                                    onPasswordClick = onPasswordClick,
                                    onPasswordLongClick = {
                                        if (sessionOpen && !isAutofillRequest && it.editable)
                                            navController.navigate("${NCPScreen.PasswordEdit.name}/${it.id}")
                                    },
                                    getPainterForUrl = { passwordsViewModel.getPainterForUrl(url = it) }
                                )
                            }
                        }
                    }
                }
            }
        }

        composable(NCPScreen.Folders.name) {
            NCPNavHostComposable(
                modalSheetState = modalSheetState,
                searchVisibility = searchVisibility,
                closeSearch = closeSearch
            ) {
                val filteredPasswordsParentFolder = remember(filteredPasswordList) {
                    filteredPasswordList?.filter {
                        it.folder == FoldersApi.DEFAULT_FOLDER_UUID
                    }
                }
                val filteredFoldersParentFolder = remember(filteredFolderList) {
                    filteredFolderList?.filter {
                        it.parent == FoldersApi.DEFAULT_FOLDER_UUID
                    }
                }
                when {
                    foldersDecryptionState.isLoading || passwordsDecryptionState.isLoading -> {
                        Box(modifier = Modifier.fillMaxSize()) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        }
                    }
                    foldersDecryptionState.decryptedList != null
                            && passwordsDecryptionState.decryptedList != null -> {

                        LaunchedEffect(Unit) {
                            passwordsViewModel.setVisibleFolder(null)
                        }

                        PullToRefreshBody(
                            isRefreshing = isRefreshing,
                            onRefresh = { passwordsViewModel.sync() },
                        ) {
                            if (filteredFoldersParentFolder?.isEmpty() == true
                                && filteredPasswordsParentFolder?.isEmpty() == true
                            ) {
                                if (searchQuery.isBlank())
                                    NoContentText()
                                else
                                    NoResultsText { navController.navigate(NCPScreen.Passwords.name) }
                            } else {
                                MixedLazyColumn(
                                    passwords = filteredPasswordsParentFolder,
                                    folders = filteredFoldersParentFolder,
                                    onPasswordClick = onPasswordClick,
                                    onPasswordLongClick = {
                                        if (sessionOpen && !isAutofillRequest && it.editable)
                                            navController.navigate("${NCPScreen.PasswordEdit.name}/${it.id}")
                                    },
                                    onFolderClick = onFolderClick,
                                    onFolderLongClick = {
                                        if (sessionOpen && !isAutofillRequest)
                                            navController.navigate("${NCPScreen.FolderEdit.name}/${it.id}")
                                    },
                                    getPainterForUrl = { passwordsViewModel.getPainterForUrl(url = it) }
                                )
                            }
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
            val filteredPasswordsSelectedFolder = remember(filteredPasswordList) {
                filteredPasswordList?.filter {
                    it.folder == folderUuid
                }
            }
            val filteredFoldersSelectedFolder = remember(filteredFolderList) {
                filteredFolderList?.filter {
                    it.parent == folderUuid
                }
            }
            NCPNavHostComposable(
                modalSheetState = modalSheetState,
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
                        DisposableEffect(folderUuid) {
                            if (foldersDecryptionState.decryptedList?.isEmpty() == false) {
                                passwordsViewModel.setVisibleFolder(foldersDecryptionState.decryptedList
                                    ?.firstOrNull { it.id == folderUuid })
                            }
                            onDispose {
                                if (passwordsViewModel.visibleFolder.value?.id == folderUuid) {
                                    passwordsViewModel.setVisibleFolder(null)
                                }
                            }
                        }

                        PullToRefreshBody(
                            isRefreshing = isRefreshing,
                            onRefresh = { passwordsViewModel.sync() },
                        ) {
                            if (filteredFoldersSelectedFolder?.isEmpty() == true
                                && filteredPasswordsSelectedFolder?.isEmpty() == true
                            ) {
                                if (searchQuery.isBlank())
                                    NoContentText()
                                else
                                    NoResultsText { navController.navigate(NCPScreen.Passwords.name) }
                            } else {
                                MixedLazyColumn(
                                    passwords = filteredPasswordsSelectedFolder,
                                    folders = filteredFoldersSelectedFolder,
                                    onPasswordClick = onPasswordClick,
                                    onPasswordLongClick = {
                                        if (sessionOpen && !isAutofillRequest && it.editable)
                                            navController.navigate("${NCPScreen.PasswordEdit.name}/${it.id}")
                                    },
                                    onFolderClick = onFolderClick,
                                    onFolderLongClick = {
                                        if (sessionOpen && !isAutofillRequest)
                                            navController.navigate("${NCPScreen.FolderEdit.name}/${it.id}")
                                    },
                                    getPainterForUrl = { passwordsViewModel.getPainterForUrl(url = it) }
                                )
                            }
                        }
                    }
                }
            }
        }

        composable(
            route = "${NCPScreen.PasswordEdit.name}/{password_uuid}",
            arguments = listOf(
                navArgument("password_uuid") {
                    type = NavType.StringType
                }
            )
        ) { entry ->
            BackHandler(enabled = isUpdating) {
                // Block back gesture when updating to avoid data loss
                return@BackHandler
            }

            val passwordUuid = entry.arguments?.getString("password_uuid")
            val selectedPassword = remember(passwordsDecryptionState.decryptedList, passwordUuid) {
                if (passwordUuid == "none") {
                    null
                } else {
                    passwordsDecryptionState.decryptedList?.firstOrNull {
                        it.id == passwordUuid
                    }
                }
            }
            NCPNavHostComposable(
                modalSheetState = modalSheetState,
                searchVisibility = searchVisibility,
                closeSearch = closeSearch
            ) {
                when {
                    passwordsDecryptionState.isLoading || foldersDecryptionState.isLoading -> {
                        Box(modifier = Modifier.fillMaxSize()) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        }
                    }

                    passwordsDecryptionState.decryptedList != null && foldersDecryptionState.decryptedList != null -> {
                        val editablePasswordState =
                            rememberEditablePasswordState(selectedPassword).apply {
                                if (selectedPassword == null) {
                                    folder = passwordsViewModel.visibleFolder.value?.id ?: folder
                                }
                            }

                        EditablePasswordView(
                            editablePasswordState = editablePasswordState,
                            folders = foldersDecryptionState.decryptedList ?: listOf(),
                            onSavePassword = {
                                val currentKeychain = keychain

                                val customFields =
                                    Json.encodeToString(editablePasswordState.customFields.toList())

                                if (selectedPassword == null) {
                                    // New password
                                    val newPassword =
                                        if (currentKeychain != null && serverSettings.encryptionCse != 0) {
                                            NewPassword(
                                                password = editablePasswordState.password.encryptValue(
                                                    currentKeychain.current,
                                                    currentKeychain
                                                ),
                                                label = editablePasswordState.label.encryptValue(
                                                    currentKeychain.current,
                                                    currentKeychain
                                                ),
                                                username = editablePasswordState.username.encryptValue(
                                                    currentKeychain.current,
                                                    currentKeychain
                                                ),
                                                url = editablePasswordState.url.encryptValue(
                                                    currentKeychain.current,
                                                    currentKeychain
                                                ),
                                                notes = editablePasswordState.notes.encryptValue(
                                                    currentKeychain.current,
                                                    currentKeychain
                                                ),
                                                customFields = customFields.encryptValue(
                                                    currentKeychain.current,
                                                    currentKeychain
                                                ),
                                                hash = editablePasswordState.password.sha1Hash()
                                                    .take(serverSettings.passwordSecurityHash),
                                                cseType = "CSEv1r1",
                                                cseKey = currentKeychain.current,
                                                folder = editablePasswordState.folder,
                                                edited = 0,
                                                hidden = false,
                                                favorite = editablePasswordState.favorite
                                            )
                                        } else {
                                            NewPassword(
                                                password = editablePasswordState.password,
                                                label = editablePasswordState.label,
                                                username = editablePasswordState.username,
                                                url = editablePasswordState.url,
                                                notes = editablePasswordState.notes,
                                                customFields = customFields,
                                                hash = editablePasswordState.password.sha1Hash()
                                                    .take(serverSettings.passwordSecurityHash),
                                                cseType = "none",
                                                cseKey = "",
                                                folder = editablePasswordState.folder,
                                                edited = 0,
                                                hidden = false,
                                                favorite = editablePasswordState.favorite
                                            )
                                        }
                                    coroutineScope.launch {
                                        if (passwordsViewModel.createPassword(newPassword)
                                                .await()
                                        ) {
                                            if (editablePasswordState.replyAutofill && replyAutofill != null) {
                                                replyAutofill(
                                                    editablePasswordState.label,
                                                    editablePasswordState.username,
                                                    editablePasswordState.password
                                                )
                                            } else {
                                                navController.navigateUp()
                                            }
                                        } else {
                                            Toast.makeText(
                                                context,
                                                R.string.error_password_saving_failed,
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                } else {
                                    val updatedPassword =
                                        if (currentKeychain != null && selectedPassword.cseType == "CSEv1r1") {
                                            UpdatedPassword(
                                                id = selectedPassword.id,
                                                revision = selectedPassword.revision,
                                                password = editablePasswordState.password.encryptValue(
                                                    currentKeychain.current,
                                                    currentKeychain
                                                ),
                                                label = editablePasswordState.label.encryptValue(
                                                    currentKeychain.current,
                                                    currentKeychain
                                                ),
                                                username = editablePasswordState.username.encryptValue(
                                                    currentKeychain.current,
                                                    currentKeychain
                                                ),
                                                url = editablePasswordState.url.encryptValue(
                                                    currentKeychain.current,
                                                    currentKeychain
                                                ),
                                                notes = editablePasswordState.notes.encryptValue(
                                                    currentKeychain.current,
                                                    currentKeychain
                                                ),
                                                customFields = customFields.encryptValue(
                                                    currentKeychain.current,
                                                    currentKeychain
                                                ),
                                                hash = editablePasswordState.password.sha1Hash()
                                                    .take(serverSettings.passwordSecurityHash),
                                                cseType = "CSEv1r1",
                                                cseKey = currentKeychain.current,
                                                folder = editablePasswordState.folder,
                                                edited = if (editablePasswordState.password == selectedPassword.password) selectedPassword.edited else 0,
                                                hidden = selectedPassword.hidden,
                                                favorite = editablePasswordState.favorite
                                            )
                                        } else {
                                            UpdatedPassword(
                                                id = selectedPassword.id,
                                                revision = selectedPassword.revision,
                                                password = editablePasswordState.password,
                                                label = editablePasswordState.label,
                                                username = editablePasswordState.username,
                                                url = editablePasswordState.url,
                                                notes = editablePasswordState.notes,
                                                customFields = customFields,
                                                hash = editablePasswordState.password.sha1Hash()
                                                    .take(serverSettings.passwordSecurityHash),
                                                cseType = "none",
                                                cseKey = "",
                                                folder = editablePasswordState.folder,
                                                edited = if (editablePasswordState.password == selectedPassword.password) selectedPassword.edited else 0,
                                                hidden = selectedPassword.hidden,
                                                favorite = editablePasswordState.favorite
                                            )
                                        }
                                    coroutineScope.launch {
                                        if (passwordsViewModel.updatePassword(updatedPassword)
                                                .await()
                                        ) {
                                            if (editablePasswordState.replyAutofill && replyAutofill != null) {
                                                replyAutofill(
                                                    editablePasswordState.label,
                                                    editablePasswordState.username,
                                                    editablePasswordState.password
                                                )
                                            } else {
                                                navController.navigateUp()
                                            }
                                        } else {
                                            Toast.makeText(
                                                context,
                                                R.string.error_password_saving_failed,
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                }
                            },
                            onDeletePassword = if (selectedPassword == null) null
                            else {
                                {
                                    val deletedPassword = DeletedPassword(
                                        id = selectedPassword.id,
                                        revision = selectedPassword.revision
                                    )
                                    coroutineScope.launch {
                                        if (passwordsViewModel.deletePassword(deletedPassword)
                                                .await()
                                        ) {
                                            navController.navigateUp()
                                        } else {
                                            Toast.makeText(
                                                context,
                                                R.string.error_password_deleting_failed,
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                }
                            },
                            isUpdating = isUpdating,
                            isAutofillRequest = isAutofillRequest,
                            onGeneratePassword = passwordsViewModel::generatePassword
                        )
                    }
                }
            }
        }

        composable(
            route = "${NCPScreen.FolderEdit.name}/{folder_uuid}",
            arguments = listOf(
                navArgument("folder_uuid") {
                    type = NavType.StringType
                }
            )
        ) { entry ->
            BackHandler(enabled = isUpdating) {
                // Block back gesture when updating to avoid data loss
                return@BackHandler
            }

            val folderUuid = entry.arguments?.getString("folder_uuid")
            val selectedFolder = remember(foldersDecryptionState.decryptedList, folderUuid) {
                if (folderUuid == "none") {
                    null
                } else {
                    foldersDecryptionState.decryptedList?.firstOrNull {
                        it.id == folderUuid
                    }
                }
            }
            NCPNavHostComposable(
                modalSheetState = modalSheetState,
                searchVisibility = searchVisibility,
                closeSearch = closeSearch
            ) {
                when {
                    foldersDecryptionState.isLoading -> {
                        Box(modifier = Modifier.fillMaxSize()) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        }
                    }

                    foldersDecryptionState.decryptedList != null -> {
                        val editableFolderState =
                            rememberEditableFolderState(selectedFolder).apply {
                                if (selectedFolder == null) {
                                    parent = passwordsViewModel.visibleFolder.value?.id ?: parent
                                }
                            }

                        EditableFolderView(
                            editableFolderState = editableFolderState,
                            folders = foldersDecryptionState.decryptedList ?: listOf(),
                            onSaveFolder = {
                                if (selectedFolder == null) {
                                    val newFolder = keychain?.let {
                                        NewFolder(
                                            label = editableFolderState.label.encryptValue(
                                                it.current,
                                                it
                                            ),
                                            cseType = "CSEv1r1",
                                            cseKey = it.current,
                                            parent = editableFolderState.parent,
                                            edited = 0,
                                            hidden = false,
                                            favorite = editableFolderState.favorite
                                        )
                                    } ?: NewFolder(
                                        label = editableFolderState.label,
                                        cseType = "none",
                                        cseKey = "",
                                        parent = editableFolderState.parent,
                                        edited = 0,
                                        hidden = false,
                                        favorite = editableFolderState.favorite
                                    )
                                    coroutineScope.launch {
                                        if (passwordsViewModel.createFolder(newFolder)
                                                .await()
                                        ) {
                                            navController.navigateUp()
                                        } else {
                                            Toast.makeText(
                                                context,
                                                R.string.error_folder_saving_failed,
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                } else {
                                    val updatedFolder = keychain?.let {
                                        UpdatedFolder(
                                            id = selectedFolder.id,
                                            revision = selectedFolder.revision,
                                            label = editableFolderState.label.encryptValue(
                                                it.current,
                                                it
                                            ),
                                            cseType = "CSEv1r1",
                                            cseKey = it.current,
                                            parent = editableFolderState.parent,
                                            edited = if (editableFolderState.label == selectedFolder.label) selectedFolder.edited else 0,
                                            hidden = selectedFolder.hidden,
                                            favorite = editableFolderState.favorite
                                        )
                                    } ?: UpdatedFolder(
                                        id = selectedFolder.id,
                                        revision = selectedFolder.revision,
                                        label = editableFolderState.label,
                                        cseType = "none",
                                        cseKey = "",
                                        parent = editableFolderState.parent,
                                        edited = if (editableFolderState.label == selectedFolder.label) selectedFolder.edited else 0,
                                        hidden = selectedFolder.hidden,
                                        favorite = editableFolderState.favorite
                                    )
                                    coroutineScope.launch {
                                        if (passwordsViewModel.updateFolder(updatedFolder)
                                                .await()
                                        ) {
                                            navController.navigateUp()
                                        } else {
                                            Toast.makeText(
                                                context,
                                                R.string.error_folder_saving_failed,
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                }
                            },
                            onDeleteFolder = if (selectedFolder == null) null
                            else {
                                {
                                    val deletedFolder = DeletedFolder(
                                        id = selectedFolder.id,
                                        revision = selectedFolder.revision
                                    )
                                    coroutineScope.launch {
                                        if (passwordsViewModel.deleteFolder(deletedFolder)
                                                .await()
                                        ) {
                                            navController.navigateUp()
                                        } else {
                                            Toast.makeText(
                                                context,
                                                R.string.error_folder_deleting_failed,
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                }
                            },
                            isUpdating = isUpdating,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NoContentText() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp), contentAlignment = Alignment.Center
    ) {
        Text(text = stringResource(id = R.string.empty_list_no_content_here))
    }
}

@Composable
fun NoResultsText(
    onButtonPress: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp), contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = stringResource(id = R.string.empty_list_no_results_found))
            if (onButtonPress != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onButtonPress) {
                    Text(text = stringResource(id = R.string.action_search_everywhere))
                }
            }
        }
    }
}

@ExperimentalMaterial3Api
@Composable
fun NCPNavHostComposable(
    modifier: Modifier = Modifier,
    modalSheetState: SheetState? = null,
    searchVisibility: Boolean? = null,
    closeSearch: (() -> Unit)? = null,
    content: @Composable () -> Unit = { }
) {
    BackHandler(enabled = searchVisibility == true) {
        closeSearch?.invoke()
    }
    val scope = rememberCoroutineScope()
    BackHandler(enabled = modalSheetState?.isVisible ?: false) {
        scope.launch {
            modalSheetState?.hide()
        }
    }
    Box(modifier = modifier) {
        content()
    }
}
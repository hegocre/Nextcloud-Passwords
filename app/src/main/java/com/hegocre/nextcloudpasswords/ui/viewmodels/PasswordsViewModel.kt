package com.hegocre.nextcloudpasswords.ui.viewmodels

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.hegocre.nextcloudpasswords.R
import com.hegocre.nextcloudpasswords.api.ApiController
import com.hegocre.nextcloudpasswords.api.encryption.CSEv1Keychain
import com.hegocre.nextcloudpasswords.api.exceptions.ClientDeauthorizedException
import com.hegocre.nextcloudpasswords.api.exceptions.PWDv1ChallengeMasterKeyInvalidException
import com.hegocre.nextcloudpasswords.api.exceptions.PWDv1ChallengeMasterKeyNeededException
import com.hegocre.nextcloudpasswords.api.exceptions.PWDv1ChallengePasswordException
import com.hegocre.nextcloudpasswords.data.folder.DeletedFolder
import com.hegocre.nextcloudpasswords.data.folder.Folder
import com.hegocre.nextcloudpasswords.data.folder.FolderController
import com.hegocre.nextcloudpasswords.data.folder.NewFolder
import com.hegocre.nextcloudpasswords.data.folder.UpdatedFolder
import com.hegocre.nextcloudpasswords.data.password.DeletedPassword
import com.hegocre.nextcloudpasswords.data.password.NewPassword
import com.hegocre.nextcloudpasswords.data.password.Password
import com.hegocre.nextcloudpasswords.data.password.PasswordController
import com.hegocre.nextcloudpasswords.data.password.UpdatedPassword
import com.hegocre.nextcloudpasswords.data.serversettings.ServerSettings
import com.hegocre.nextcloudpasswords.data.share.Share
import com.hegocre.nextcloudpasswords.data.share.ShareController
import com.hegocre.nextcloudpasswords.data.user.UserController
import com.hegocre.nextcloudpasswords.utils.AppLockHelper
import com.hegocre.nextcloudpasswords.utils.PreferencesManager
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.Credentials
import java.net.MalformedURLException
import java.net.URL

class PasswordsViewModel(application: Application) : AndroidViewModel(application) {
    private val preferencesManager = PreferencesManager.getInstance(application)

    private var masterPassword: MutableLiveData<String?> = MutableLiveData<String?>(null).also {
        it.value = preferencesManager.getMasterPassword()
    }

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean>
        get() = _isRefreshing.asStateFlow()


    private val _isUpdating = MutableStateFlow(false)
    val isUpdating: StateFlow<Boolean>
        get() = _isUpdating.asStateFlow()

    private val _needsMasterPassword = MutableStateFlow(false)
    val needsMasterPassword: StateFlow<Boolean>
        get() = _needsMasterPassword.asStateFlow()

    private val _masterPasswordInvalid = MutableStateFlow(false)
    val masterPasswordInvalid: StateFlow<Boolean>
        get() = _masterPasswordInvalid.asStateFlow()

    private val _clientDeauthorized = MutableLiveData(false)
    val clientDeauthorized: LiveData<Boolean>
        get() = _clientDeauthorized

    private val apiController = ApiController.getInstance(application)

    val sessionOpen
        get() = apiController.sessionOpen

    private val _showSessionOpenError = MutableStateFlow(false)
    val showSessionOpenError: StateFlow<Boolean>
        get() = _showSessionOpenError.asStateFlow()

    val csEv1Keychain: LiveData<CSEv1Keychain?>
        get() = apiController.csEv1Keychain

    val serverSettings: LiveData<ServerSettings>
        get() = apiController.serverSettings

    val server
        get() = UserController.getInstance(getApplication()).getServer()

    val passwords: LiveData<List<Password>>
        get() = PasswordController.getInstance(getApplication()).getPasswords()
    val folders: LiveData<List<Folder>>
        get() = FolderController.getInstance(getApplication()).getFolders()
    val shares: LiveData<List<Share>>
        get() = ShareController.getInstance(getApplication()).getShares()

    var visiblePassword = mutableStateOf<Pair<Password, List<String>>?>(null)
        private set
    var visibleFolder = mutableStateOf<Folder?>(null)
        private set

    init {
        val screenLockFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        val screenOffReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (context != null && intent != null) {
                    val action = intent.action
                    if (screenLockFilter.matchAction(action)) {
                        AppLockHelper.getInstance(context).enableLock()
                    }
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            application.registerReceiver(
                screenOffReceiver,
                screenLockFilter,
                Context.RECEIVER_EXPORTED
            )
        } else {
            application.registerReceiver(screenOffReceiver, screenLockFilter)
        }

        if (!sessionOpen.value)
            openSession(masterPassword.value)
    }

    private fun openSession(password: String?) {
        viewModelScope.launch {
            _isRefreshing.emit(true)
            try {
                if (apiController.openSession(password)) {
                    sync()
                    _showSessionOpenError.emit(true)
                    return@launch
                }
                _showSessionOpenError.emit(true)
            } catch (ex: PWDv1ChallengeMasterKeyNeededException) {
                _needsMasterPassword.emit(true)
            } catch (ex: ClientDeauthorizedException) {
                _clientDeauthorized.postValue(true)
            } catch (ex: Exception) {
                when (ex) {
                    is PWDv1ChallengeMasterKeyInvalidException, is PWDv1ChallengePasswordException -> {
                        _needsMasterPassword.emit(true)
                        _masterPasswordInvalid.emit(true)
                        masterPassword.postValue(null)
                        preferencesManager.setMasterPassword(null)
                    }
                    else -> {
                        _showSessionOpenError.emit(true)
                        ex.printStackTrace()
                    }
                }
            }
            _isRefreshing.emit(false)
        }
    }

    fun setMasterPassword(password: String, save: Boolean = false) {
        openSession(password)
        masterPassword.postValue(password)
        viewModelScope.launch {
            _needsMasterPassword.emit(false)
            _masterPasswordInvalid.emit(false)
        }
        if (save)
            preferencesManager.setMasterPassword(password)
    }

    fun sync() {
        if (sessionOpen.value) {
            viewModelScope.launch {
                _isRefreshing.emit(true)
                PasswordController.getInstance(getApplication()).syncPasswords()
                FolderController.getInstance(getApplication()).syncFolders()
                ShareController.getInstance(getApplication()).syncShares()
                _isRefreshing.emit(false)
            }
        } else {
            openSession(masterPassword.value)
        }
    }

    fun setVisiblePassword(password: Password, folderPath: List<String>) {
        visiblePassword.value = Pair(password, folderPath)
    }

    fun setVisibleFolder(folder: Folder?) {
        visibleFolder.value = folder
    }

    fun createPassword(newPassword: NewPassword): Deferred<Boolean> {
        return viewModelScope.async {
            _isUpdating.value = true
            if (!apiController.createPassword(newPassword)) {
                _isUpdating.value = false
                return@async false
            }
            sync()
            _isUpdating.value = false
            true
        }
    }

    fun updatePassword(updatedPassword: UpdatedPassword): Deferred<Boolean> {
        return viewModelScope.async {
            _isUpdating.value = true
            if (!apiController.updatePassword(updatedPassword)) {
                _isUpdating.value = false
                return@async false
            }
            sync()
            _isUpdating.value = false
            true
        }
    }

    fun deletePassword(deletedPassword: DeletedPassword): Deferred<Boolean> {
        return viewModelScope.async {
            _isUpdating.value = true
            if (!apiController.deletePassword(deletedPassword)) {
                _isUpdating.value = false
                return@async false
            }
            sync()
            _isUpdating.value = false
            true
        }
    }

    fun generatePassword(
        strength: Int, includeDigits: Boolean, includeSymbols: Boolean
    ): Deferred<String?> {
        return viewModelScope.async {
            return@async apiController.generatePassword(strength, includeDigits, includeSymbols)
        }
    }

    fun createFolder(newFolder: NewFolder): Deferred<Boolean> {
        return viewModelScope.async {
            _isUpdating.value = true
            if (!apiController.createFolder(newFolder)) {
                _isUpdating.value = false
                return@async false
            }
            sync()
            _isUpdating.value = false
            true
        }
    }

    fun updateFolder(updatedFolder: UpdatedFolder): Deferred<Boolean> {
        return viewModelScope.async {
            _isUpdating.value = true
            if (!apiController.updateFolder(updatedFolder)) {
                _isUpdating.value = false
                return@async false
            }
            sync()
            _isUpdating.value = false
            true
        }
    }

    fun deleteFolder(deletedFolder: DeletedFolder): Deferred<Boolean> {
        return viewModelScope.async {
            _isUpdating.value = true
            if (!apiController.deleteFolder(deletedFolder)) {
                _isUpdating.value = false
                return@async false
            }
            sync()
            _isUpdating.value = false
            true
        }
    }

    @Composable
    fun getPainterForUrl(url: String): Painter {
        val context = LocalContext.current
        val domain = try {
            URL(url).host
        } catch (e: MalformedURLException) {
            url
        }
        val (requestUrl, server) = apiController.getFaviconServiceRequest(domain)
        return rememberAsyncImagePainter(
            ImageRequest.Builder(context).apply {
                data(requestUrl)
                addHeader("OCS-APIRequest", "true")
                addHeader("Authorization", Credentials.basic(server.username, server.password))
                crossfade(true)
                val lockDrawable = context.getDrawable(R.drawable.ic_lock)?.apply {
                    setTintList(
                        ColorStateList.valueOf(
                            MaterialTheme.colorScheme.primary.toArgb()
                        )
                    )
                }
                placeholder(lockDrawable)
                fallback(lockDrawable)
                error(lockDrawable)
            }.build()
        )
    }

    @Composable
    fun getPainterForAvatar(): Painter {
        val context = LocalContext.current

        val (requestUrl, server) = apiController.getAvatarServiceRequest()
        return rememberAsyncImagePainter(
            model = ImageRequest.Builder(context).apply {
                data(requestUrl)
                addHeader("OCS-APIRequest", "true")
                addHeader("Authorization", Credentials.basic(server.username, server.password))
                crossfade(true)
                val accountDrawable = context.getDrawable(R.drawable.ic_account_circle)?.apply {
                    setTintList(
                        ColorStateList.valueOf(
                            MaterialTheme.colorScheme.primary.toArgb()
                        )
                    )
                }
                placeholder(accountDrawable)
                fallback(accountDrawable)
                error(accountDrawable)
            }.build()
        )
    }

    override fun onCleared() {
        apiController
        super.onCleared()
    }
}
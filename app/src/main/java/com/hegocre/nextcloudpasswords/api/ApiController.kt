package com.hegocre.nextcloudpasswords.api

import android.content.Context
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.work.WorkManager
import com.hegocre.nextcloudpasswords.api.encryption.CSEv1Keychain
import com.hegocre.nextcloudpasswords.api.exceptions.PWDv1ChallengeMasterKeyInvalidException
import com.hegocre.nextcloudpasswords.api.exceptions.PWDv1ChallengeMasterKeyNeededException
import com.hegocre.nextcloudpasswords.data.folder.DeletedFolder
import com.hegocre.nextcloudpasswords.data.folder.Folder
import com.hegocre.nextcloudpasswords.data.folder.NewFolder
import com.hegocre.nextcloudpasswords.data.folder.UpdatedFolder
import com.hegocre.nextcloudpasswords.data.password.DeletedPassword
import com.hegocre.nextcloudpasswords.data.password.NewPassword
import com.hegocre.nextcloudpasswords.data.password.Password
import com.hegocre.nextcloudpasswords.data.password.UpdatedPassword
import com.hegocre.nextcloudpasswords.data.share.Share
import com.hegocre.nextcloudpasswords.data.user.UserController
import com.hegocre.nextcloudpasswords.services.keepalive.KeepAliveWorker
import com.hegocre.nextcloudpasswords.utils.Error
import com.hegocre.nextcloudpasswords.utils.OkHttpRequest
import com.hegocre.nextcloudpasswords.utils.PreferencesManager
import com.hegocre.nextcloudpasswords.utils.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Class with methods used to interact with [the API](https://git.mdns.eu/nextcloud/passwords/-/wikis/Developers/Api)
 * classes. This is a Singleton class and will have only one instance.
 *
 * @param context Context of the application.
 */
class ApiController private constructor(context: Context) {
    private val server = UserController.getInstance(context).getServer()

    private val preferencesManager = PreferencesManager.getInstance(context)

    private val passwordsApi = PasswordsApi.getInstance(server)
    private val foldersApi = FoldersApi.getInstance(server)
    private val sessionApi = SessionApi.getInstance(server)
    private val serviceApi = ServiceApi.getInstance(server)
    private val settingsApi = SettingsApi.getInstance(server)
    private val shareApi = ShareApi.getInstance(server)

    private var sessionCode: String? = null

    val csEv1Keychain = MutableLiveData<CSEv1Keychain?>(null)

    val serverSettings = MutableLiveData(
        preferencesManager.getServerSettings()
    )

    private val _sessionOpen = MutableStateFlow(false)
    val sessionOpen: StateFlow<Boolean>
        get() = _sessionOpen.asStateFlow()

    private val workManager = WorkManager.getInstance(context)

    init {
        decryptCSEv1Keychain(
            preferencesManager.getCSEv1Keychain(),
            preferencesManager.getMasterPassword()
        )?.let {
            csEv1Keychain.postValue(it)
        }

        CoroutineScope(Dispatchers.IO).launch {
            var result = settingsApi.get()
            while (result !is Result.Success) {
                Log.e("ServerSettings", "Error getting server settings")
                delay(5000L)
                result = settingsApi.get()
            }
            Log.i("ServerSettings", "Got server settings")
            val settings = result.data
            serverSettings.postValue(settings)
            preferencesManager.setServerSettings(settings)
            preferencesManager.setInstanceColor(settings.themeColorPrimary)
        }
        OkHttpRequest.getInstance().allowInsecureRequests =
            preferencesManager.getSkipCertificateValidation()
    }

    private fun decryptCSEv1Keychain(
        encryptedData: String?,
        masterPassword: String?
    ): CSEv1Keychain? = try {
        encryptedData?.let { encryptedCSEv1Keychain ->
            masterPassword?.let { masterPassword ->
                val decryptedCsEv1KeychainJson = CSEv1Keychain.decryptJson(
                    encryptedCSEv1Keychain,
                    masterPassword
                )
                CSEv1Keychain.fromJson(decryptedCsEv1KeychainJson)
            }
        }
    } catch (e: Exception) {
        null
    }

    /**
     * Requests and opens a session via the [SessionApi] class.
     *
     * @param masterPassword Master password to request the session, if provided, and not needed
     * if no CSE used.
     * @return A boolean indicating if the session was successfully opened.
     * @throws PWDv1ChallengeMasterKeyNeededException If there is no master key provided, but one is
     * needed.
     * @throws PWDv1ChallengeMasterKeyInvalidException If a master key was provided, but is not valid.
     */
    @Throws(
        PWDv1ChallengeMasterKeyNeededException::class,
        PWDv1ChallengeMasterKeyInvalidException::class
    )
    suspend fun openSession(masterPassword: String?): Boolean = withContext(Dispatchers.Default) {
        decryptCSEv1Keychain(
            preferencesManager.getCSEv1Keychain(),
            masterPassword
        )?.let {
            csEv1Keychain.postValue(it)
        }

        val requestResult = sessionApi.requestSession()

        val secretResult = if (requestResult is Result.Success) {
            requestResult.data.solve(masterPassword)
        } else {
            // Error opening session
            if (requestResult is Result.Error) {
                // Could not open session, try to use cached keychain
                preferencesManager.getCSEv1Keychain()?.let { cachedKeychain ->
                    if (masterPassword == null) {
                        throw PWDv1ChallengeMasterKeyNeededException()
                    } else {
                        decryptCSEv1Keychain(cachedKeychain, masterPassword)?.let {
                            csEv1Keychain.postValue(it)
                        } ?: throw PWDv1ChallengeMasterKeyInvalidException() // Could not decrypt
                    }
                }
                // If we get here, keychain was decrypted from cache, but session is still not open
                when (requestResult.code) {
                    Error.API_TIMEOUT -> Log.e(
                        "API Controller",
                        "Timeout requesting session, user ${server.username}"
                    )

                    Error.API_BAD_RESPONSE -> Log.e(
                        "API Controller",
                        "Bad response on session request, user ${server.username}"
                    )
                }
            }
            return@withContext false
        }

        val secret = if (secretResult is Result.Success) {
            secretResult.data
        } else {
            return@withContext if (secretResult is Result.Error && secretResult.code == Error.API_NO_CSE) {
                // No encryption, we need no session
                // Clear old keychain, if CSE was disabled
                preferencesManager.setCSEv1Keychain(null)
                _sessionOpen.emit(true)
                true
            } else {
                // Error opening session
                false
            }
        }

        val openedSessionRequest = sessionApi.openSession(secret)

        val (newSessionCode, encryptedKeychainJson) = if (openedSessionRequest is Result.Success) {
            openedSessionRequest.data
        } else {
            if (openedSessionRequest is Result.Error) {
                when (openedSessionRequest.code) {
                    Error.API_TIMEOUT -> Log.e(
                        "API Controller",
                        "Timeout opening session, user ${server.username}"
                    )

                    Error.API_BAD_RESPONSE -> Log.e(
                        "API Controller",
                        "Bad response on session open, user ${server.username}"
                    )
                }
            }
            return@withContext false
        }

        preferencesManager.setCSEv1Keychain(encryptedKeychainJson)

        encryptedKeychainJson.let {
            masterPassword?.let { masterPassword ->
                val keysJson = CSEv1Keychain.decryptJson(encryptedKeychainJson, masterPassword)
                csEv1Keychain.postValue(CSEv1Keychain.fromJson(keysJson))
            }
        }
        sessionCode = newSessionCode
        serverSettings.value?.let { settings ->
            val keepAliveDelay = (settings.sessionLifetime * 3 / 4 * 1000).toLong()
            workManager.cancelAllWorkByTag(KeepAliveWorker.TAG)
            workManager.enqueue(KeepAliveWorker.getRequest(keepAliveDelay, newSessionCode))
        }

        _sessionOpen.emit(true)
        return@withContext true
    }

    /**
     * Closes the current session and deletes the saved keychain from the app storage.
     *
     * @return A boolean indicating if the session was successfully closed.
     */
    suspend fun closeSession(): Boolean {
        return if (sessionCode == null || sessionCode?.let { code -> sessionApi.closeSession(code) } == true) {
            _sessionOpen.emit(false)
            preferencesManager.setCSEv1Keychain(null)
            true
        } else {
            // Session was not closed, some error happened
            false
        }
    }

    suspend fun clearSession() {
        _sessionOpen.emit(false)
        sessionCode = null
    }

    /**
     * Gets a list of the user passwords via the [PasswordsApi] class. This can only be called when a
     * session is open, otherwise an error is thrown.
     *
     * @return A result with the list of passwords if success, or an error code otherwise.
     */
    suspend fun listPasswords(): Result<List<Password>> {
        if (!sessionOpen.value) return Result.Error(Error.API_NO_SESSION)
        return passwordsApi.list(sessionCode)
    }

    /**
     * Gets a list of the user folders via the [FoldersApi] class. This can only be called when a session
     * is open, otherwise an error is thrown.
     *
     * @return A result with the list of folders if success, or an error code otherwise.
     */
    suspend fun listFolders(): Result<List<Folder>> {
        if (!sessionOpen.value) return Result.Error(Error.API_NO_SESSION)
        return foldersApi.list(sessionCode)
    }

    suspend fun listShares(): Result<List<Share>> {
        if (!sessionOpen.value) return Result.Error(Error.API_NO_SESSION)
        return shareApi.list(sessionCode)
    }

    /**
     * Creates a new password via the [PasswordsApi] class. This can only be called when a
     * session is open, otherwise an error is thrown.
     *
     * @param newPassword [NewPassword] object to be created.
     * @return A boolean stating whether the password was successfully created.
     */
    suspend fun createPassword(newPassword: NewPassword): Boolean {
        if (!sessionOpen.value) return false
        val result = passwordsApi.create(newPassword, sessionCode)
        return result is Result.Success
    }

    /**
     * Updates an existing password via the [PasswordsApi] class. This can only be called when a
     * session is open, otherwise an error is thrown.
     *
     * @param updatedPassword [UpdatedPassword] object to be updated.
     * @return A boolean stating whether the password was successfully updated.
     */
    suspend fun updatePassword(updatedPassword: UpdatedPassword): Boolean {
        if (!sessionOpen.value) return false
        val result = passwordsApi.update(updatedPassword, sessionCode)
        return result is Result.Success
    }

    /**
     * Deletes an existing password via the [PasswordsApi] class. This can only be called when a
     * session is open, otherwise an error is thrown.
     *
     * @param deletedPassword [DeletedPassword] object to be deleted.
     * @return A boolean stating whether the password was successfully deleted.
     */
    suspend fun deletePassword(deletedPassword: DeletedPassword): Boolean {
        if (!sessionOpen.value) return false
        val result = passwordsApi.delete(deletedPassword, sessionCode)
        return result is Result.Success
    }

    /**
     * Generates a random password using user's settings. This can only be called when a
     * session is open, otherwise an error is thrown.
     *
     * @return A string with the generated password, or null if there was an error.
     */
    suspend fun generatePassword(
        strength: Int,
        includeDigits: Boolean,
        includeSymbols: Boolean
    ): String? {
        if (!sessionOpen.value) return null
        val result = serviceApi.password(strength, includeDigits, includeSymbols, sessionCode)
        return if (result is Result.Success) result.data else null
    }

    /**
     * Creates a new folder via the [FoldersApi] class. This can only be called when a
     * session is open, otherwise an error is thrown.
     *
     * @param newFolder [NewFolder] object to be created.
     * @return A boolean stating whether the folder was successfully created.
     */
    suspend fun createFolder(newFolder: NewFolder): Boolean {
        if (!sessionOpen.value) return false
        val result = foldersApi.create(newFolder, sessionCode)
        return result is Result.Success
    }

    /**
     * Updates an existing folder via the [FoldersApi] class. This can only be called when a
     * session is open, otherwise an error is thrown.
     *
     * @param updatedFolder [UpdatedFolder] object to be updated.
     * @return A boolean stating whether the folder was successfully updated.
     */
    suspend fun updateFolder(updatedFolder: UpdatedFolder): Boolean {
        if (!sessionOpen.value) return false
        val result = foldersApi.update(updatedFolder, sessionCode)
        return result is Result.Success
    }

    /**
     * Deletes an existing folder via the [FoldersApi] class. This can only be called when a
     * session is open, otherwise an error is thrown.
     *
     * @param deletedFolder [DeletedFolder] object to be deleted.
     * @return A boolean stating whether the folder was successfully deleted.
     */
    suspend fun deleteFolder(deletedFolder: DeletedFolder): Boolean {
        if (!sessionOpen.value) return false
        val result = foldersApi.delete(deletedFolder, sessionCode)
        return result is Result.Success
    }

    fun getFaviconServiceRequest(url: String): Pair<String, Server> =
        Pair(serviceApi.getFaviconUrl(url), server)

    fun getAvatarServiceRequest(): Pair<String, Server> =
        Pair(serviceApi.getAvatarUrl(), server)

    companion object {
        private var instance: ApiController? = null

        /**
         * Get the instance of the [ApiController], and create it if null.
         *
         * @param context Context of the application.
         * @return The instance of the controller.
         */
        fun getInstance(context: Context): ApiController {
            synchronized(this) {
                var tempInstance = instance

                if (tempInstance == null) {
                    tempInstance = ApiController(context)
                    instance = tempInstance
                }

                return tempInstance
            }
        }
    }

}


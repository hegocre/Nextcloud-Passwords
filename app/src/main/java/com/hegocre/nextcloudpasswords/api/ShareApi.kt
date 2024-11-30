package com.hegocre.nextcloudpasswords.api

import com.hegocre.nextcloudpasswords.BuildConfig
import com.hegocre.nextcloudpasswords.data.share.Share
import com.hegocre.nextcloudpasswords.utils.Error
import com.hegocre.nextcloudpasswords.utils.OkHttpRequest
import com.hegocre.nextcloudpasswords.utils.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.SocketTimeoutException
import javax.net.ssl.SSLHandshakeException

class ShareApi private constructor(private var server: Server) {

    suspend fun list(
        sessionCode: String? = null,
    ): Result<List<Share>> {
        return try {
            val apiResponse = withContext(Dispatchers.IO) {
                OkHttpRequest.getInstance().get(
                    sUrl = server.url + LIST_URL,
                    sessionCode = sessionCode,
                    username = server.username,
                    password = server.password
                )
            }

            val code = apiResponse.code
            val body = withContext(Dispatchers.IO) { apiResponse.body?.string() }
            withContext(Dispatchers.IO) {
                apiResponse.close()
            }

            if (code != 200 || body == null) {
                return Result.Error(Error.API_BAD_RESPONSE)
            }

            withContext(Dispatchers.Default) {
                Result.Success(Json.decodeFromString(body))
            }
        } catch (e: SocketTimeoutException) {
            if (BuildConfig.DEBUG) {
                e.printStackTrace()
            }
            Result.Error(Error.API_TIMEOUT)
        } catch (e: SSLHandshakeException) {
            if (BuildConfig.DEBUG) {
                e.printStackTrace()
            }
            Result.Error(Error.SSL_HANDSHAKE_EXCEPTION)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                e.printStackTrace()
            }
            Result.Error(Error.UNKNOWN)
        }
    }

    companion object {
        private const val LIST_URL = "/index.php/apps/passwords/api/1.0/share/list"

        private var instance: ShareApi? = null

        fun getInstance(server: Server): ShareApi {
            synchronized(this) {
                var tempInstance = instance

                if (tempInstance == null) {
                    tempInstance = ShareApi(server)
                    instance = tempInstance
                }

                return tempInstance
            }
        }
    }
}
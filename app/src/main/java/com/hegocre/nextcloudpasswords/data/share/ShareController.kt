package com.hegocre.nextcloudpasswords.data.share

import android.content.Context
import androidx.lifecycle.LiveData
import com.hegocre.nextcloudpasswords.api.ApiController
import com.hegocre.nextcloudpasswords.databases.AppDatabase
import com.hegocre.nextcloudpasswords.utils.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ShareController private constructor(context: Context) {
    private val passwordDatabase = AppDatabase.getInstance(context)
    private val apiController = ApiController.getInstance(context)

    suspend fun syncShares() {
        withContext(Dispatchers.IO) {
            val result = apiController.listShares()
            if (result is Result.Success) {
                val savedSharesSet = passwordDatabase.shareDao.fetchAllSharesId().toHashSet()
                for (share in result.data) {
                    val oldUpdated = passwordDatabase.shareDao.getShareUpdated(share.id)
                    if (oldUpdated == null || oldUpdated != share.updated) {
                        passwordDatabase.shareDao.insertShare(share)
                    }
                    savedSharesSet.remove(share.id)
                }
                for (id in savedSharesSet) {
                    passwordDatabase.shareDao.deleteShare(id)
                }
            }
        }
    }

    fun getShares(): LiveData<List<Share>> =
        passwordDatabase.shareDao.fetchAllShares()

    companion object {
        private var instance: ShareController? = null

        fun getInstance(context: Context): ShareController {
            synchronized(this) {
                var tempInstance = instance

                if (tempInstance == null) {
                    tempInstance = ShareController(context)
                    instance = tempInstance
                }

                return tempInstance
            }
        }
    }
}
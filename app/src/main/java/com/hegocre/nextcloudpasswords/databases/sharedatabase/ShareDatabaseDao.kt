package com.hegocre.nextcloudpasswords.databases.sharedatabase

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hegocre.nextcloudpasswords.data.share.Share

@Dao
interface ShareDatabaseDao {
    @Query("SELECT * FROM shares")
    fun fetchAllShares(): LiveData<List<Share>>

    @Query("SELECT id FROM shares")
    suspend fun fetchAllSharesId(): List<String>

    @Query("SELECT updated FROM shares WHERE id = :id")
    suspend fun getShareUpdated(id: String): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShare(share: Share)

    @Delete
    suspend fun deleteShare(share: Share)

    @Query("DELETE FROM shares WHERE id = :id")
    suspend fun deleteShare(id: String)
}
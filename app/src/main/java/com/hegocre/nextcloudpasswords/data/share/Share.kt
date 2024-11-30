package com.hegocre.nextcloudpasswords.data.share

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "shares", indices = [Index(value = ["id"], unique = true)])
data class Share(
    @PrimaryKey
    val id: String,
    val created: Int,
    val updated: Int,
    val expires: Int?,
    val editable: Boolean,
    val shareable: Boolean,
    val updatePending: Boolean,
    val password: String,
    val owner: ShareUser,
    val receiver: ShareUser,
    val client: String
)

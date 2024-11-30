package com.hegocre.nextcloudpasswords.databases

import androidx.room.TypeConverter
import com.hegocre.nextcloudpasswords.data.share.ShareUser
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {
    @TypeConverter
    fun shareUserToString(shareUser: ShareUser): String = Json.encodeToString(shareUser)

    @TypeConverter
    fun shareUserFromString(shareUser: String): ShareUser = Json.decodeFromString(shareUser)
}
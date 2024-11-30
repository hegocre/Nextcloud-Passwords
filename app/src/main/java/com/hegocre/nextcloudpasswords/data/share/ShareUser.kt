package com.hegocre.nextcloudpasswords.data.share

import kotlinx.serialization.Serializable

@Serializable
data class ShareUser(
    val id: String,
    val name: String
)


package com.anistream.domain.model

data class Episode(
    val id: Long,
    val number: Int,
    val title: String? = null,
    val aired: String? = null,
    val filler: Boolean = false
)

package com.anistream.domain.model

data class Genre(
    val id: Int,
    val name: String,
    val count: Int = 0,
    // hianime path segment, e.g. "action" for /genres/action
    val slug: String = ""
)

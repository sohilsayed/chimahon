package com.canopus.chimareader.data

import java.util.UUID

data class BookShelf(
    val name: String,
    val bookIds: List<UUID> = emptyList(),
)

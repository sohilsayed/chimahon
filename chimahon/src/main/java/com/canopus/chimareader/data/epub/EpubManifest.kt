package com.canopus.chimareader.data.epub

data class EpubManifest(
    val id: String? = null,
    val items: Map<String, ManifestItem> = emptyMap(),
)

package chimahon.ocr

data class ImageFileInfo(
    val name: String,
    val relativePath: String,
    val basename: String,
)

fun resolveMokuroPage(
    mokuro: MokuroVolume,
    chapterImageFiles: List<ImageFileInfo>,
    pageIndex: Int,
): MokuroPage? {
    if (pageIndex !in chapterImageFiles.indices) return null
    val imageFile = chapterImageFiles[pageIndex]

    mokuro.pages.forEach { mokuroPage ->
        val mokuroPath = mokuroPage.imgPath
        val mokuroBasename = mokuroPath.substringAfterLast('/').substringBeforeLast('.')

        if (mokuroPath.endsWith(imageFile.name, ignoreCase = true)) {
            return mokuroPage
        }

        if (mokuroBasename.equals(imageFile.basename, ignoreCase = true)) {
            return mokuroPage
        }
    }

    if (pageIndex < mokuro.pages.size) {
        return mokuro.pages[pageIndex]
    }

    return null
}

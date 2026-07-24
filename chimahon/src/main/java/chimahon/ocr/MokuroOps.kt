package chimahon.ocr

object Mokuro {
    fun parseMokuro(content: String): MokuroVolume? = chimahon.ocr.parseMokuro(content)
    fun resolveMokuroPage(
        mokuro: MokuroVolume,
        chapterImageFiles: List<ImageFileInfo>,
        pageIndex: Int,
    ): MokuroPage? = chimahon.ocr.resolveMokuroPage(mokuro, chapterImageFiles, pageIndex)
    fun convertMokuroBlocks(page: MokuroPage): List<OcrTextBlock> = chimahon.ocr.convertMokuroBlocks(page)
}

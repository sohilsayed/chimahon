package chimahon.ocr

fun convertMokuroBlocks(page: MokuroPage): List<OcrTextBlock> {
    val imgWidth = page.imgWidth.toFloat()
    val imgHeight = page.imgHeight.toFloat()

    if (imgWidth <= 0f || imgHeight <= 0f) return emptyList()

    return page.blocks.mapNotNull { block ->
        toOcrTextBlock(block, imgWidth, imgHeight)
    }
}

private fun toOcrTextBlock(block: MokuroBlock, imgWidth: Float, imgHeight: Float): OcrTextBlock? {
    if (block.box.size < 4) return null

    val x1 = block.box[0]
    val y1 = block.box[1]
    val x2 = block.box[2]
    val y2 = block.box[3]

    if (x2 <= x1 || y2 <= y1) return null

    val xmin = (x1 / imgWidth).coerceIn(0f, 1f)
    val ymin = (y1 / imgHeight).coerceIn(0f, 1f)
    val xmax = (x2 / imgWidth).coerceIn(0f, 1f)
    val ymax = (y2 / imgHeight).coerceIn(0f, 1f)

    val lineGeometries = block.linesCoords.takeIf { it.isNotEmpty() }?.mapNotNull { coords ->
        toLineGeometry(coords, imgWidth, imgHeight)
    }

    val filteredLines = block.lines.filter { it.isNotBlank() }
    if (filteredLines.isEmpty()) return null

    return OcrTextBlock(
        xmin = xmin,
        ymin = ymin,
        xmax = xmax,
        ymax = ymax,
        lines = filteredLines,
        vertical = block.vertical,
        lineGeometries = lineGeometries?.takeIf { it.isNotEmpty() },
    )
}

private fun toLineGeometry(polygon: List<List<Float>>, imgWidth: Float, imgHeight: Float): OcrLineGeometry? {
    if (polygon.isEmpty()) return null

    var minX = Float.POSITIVE_INFINITY
    var minY = Float.POSITIVE_INFINITY
    var maxX = Float.NEGATIVE_INFINITY
    var maxY = Float.NEGATIVE_INFINITY

    for (point in polygon) {
        if (point.size >= 2) {
            val x = point[0]
            val y = point[1]
            if (x < minX) minX = x
            if (y < minY) minY = y
            if (x > maxX) maxX = x
            if (y > maxY) maxY = y
        }
    }

    if (maxX <= minX || maxY <= minY) return null

    return OcrLineGeometry(
        xmin = (minX / imgWidth).coerceIn(0f, 1f),
        ymin = (minY / imgHeight).coerceIn(0f, 1f),
        xmax = (maxX / imgWidth).coerceIn(0f, 1f),
        ymax = (maxY / imgHeight).coerceIn(0f, 1f),
    )
}

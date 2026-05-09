package chimahon.dictionary

import java.util.concurrent.ConcurrentHashMap

private val transformerCache = ConcurrentHashMap<String, LanguageTransformer>()

fun deinflectRecursive(
    text: String,
    descriptor: TransformDescriptor,
): List<DeinflectionResult> {
    val transformer = transformerCache.getOrPut(descriptor.language) {
        LanguageTransformer().also { it.addDescriptor(descriptor) }
    }
    val transformed = transformer.transform(text)
    return transformed.map { DeinflectionResult(it.text, it.conditions) }
}
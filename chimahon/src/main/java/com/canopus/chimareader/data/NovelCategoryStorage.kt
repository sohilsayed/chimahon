package com.canopus.chimareader.data

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class NovelCategoryStorage(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val categoriesFile: File
        get() = File(context.filesDir, "novel_categories.json")

    fun loadAllCategories(): List<NovelCategory> {
        if (!categoriesFile.exists()) return listOf(createDefaultCategory())
        return try {
            json.decodeFromString<List<NovelCategory>>(categoriesFile.readText())
        } catch (e: Exception) {
            listOf(createDefaultCategory())
        }
    }

    private fun createDefaultCategory() = NovelCategory(id = "default", name = "Default", order = 0)

    fun saveCategories(categories: List<NovelCategory>) {
        categoriesFile.writeText(json.encodeToString(categories))
    }

    fun createCategory(name: String): NovelCategory {
        val categories = loadAllCategories().toMutableList()
        val newCategory = NovelCategory(name = name, order = categories.size)
        categories.add(newCategory)
        saveCategories(categories)
        return newCategory
    }

    fun deleteCategory(categoryId: String) {
        val categories = loadAllCategories().toMutableList()
        categories.removeAll { it.id == categoryId }
        saveCategories(categories)
    }

    fun updateCategory(category: NovelCategory) {
        val categories = loadAllCategories().toMutableList()
        val index = categories.indexOfFirst { it.id == category.id }
        if (index != -1) {
            categories[index] = category
            saveCategories(categories)
        }
    }
}

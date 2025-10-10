package com.example.ejemplo2.data.repository

import android.content.Context
import com.example.ejemplo2.data.database.AppDatabase
import com.example.ejemplo2.data.entity.Recipe
import com.example.ejemplo2.utils.ValidationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class RecipeRepository(context: Context) {
    
    private val recipeDao = AppDatabase.getDatabase(context).recipeDao()
    
    /**
     * Crear una nueva receta
     */
    suspend fun createRecipe(
        title: String,
        ingredients: String,
        steps: String,
        authorId: Long,
        authorName: String,
        tags: String? = null,
        cookingTime: Int = 0,
        servings: Int = 1,
        imagePath: String? = null,
        isPublished: Boolean = false
    ): ValidationResult = withContext(Dispatchers.IO) {
        
        // Validar campos obligatorios
        if (title.isBlank()) {
            return@withContext ValidationResult(false, "El título es obligatorio")
        }
        
        if (ingredients.isBlank()) {
            return@withContext ValidationResult(false, "Los ingredientes son obligatorios")
        }
        
        if (steps.isBlank()) {
            return@withContext ValidationResult(false, "Los pasos son obligatorios")
        }
        
        if (authorId <= 0) {
            return@withContext ValidationResult(false, "ID de autor inválido")
        }
        
        if (authorName.isBlank()) {
            return@withContext ValidationResult(false, "Nombre de autor es obligatorio")
        }
        
        try {
            val recipe = Recipe(
                title = title.trim(),
                ingredients = ingredients.trim(),
                steps = steps.trim(),
                authorId = authorId,
                authorName = authorName.trim(),
                tags = tags?.trim(),
                cookingTime = cookingTime,
                servings = servings,
                imagePath = imagePath,
                isPublished = isPublished
            )
            
            val recipeId = recipeDao.insertRecipe(recipe)
            
            if (recipeId > 0) {
                ValidationResult(true, "Receta ${if (isPublished) "publicada" else "guardada como borrador"} exitosamente")
            } else {
                ValidationResult(false, "Error al guardar la receta")
            }
        } catch (e: Exception) {
            ValidationResult(false, "Error al crear receta: ${e.message}")
        }
    }
    
    /**
     * Obtener todas las recetas
     */
    fun getAllRecipes(): Flow<List<Recipe>> = recipeDao.getAllRecipes()
    
    /**
     * Obtener recetas por autor
     */
    fun getRecipesByAuthor(authorId: Long): Flow<List<Recipe>> = recipeDao.getRecipesByAuthor(authorId)
    
    /**
     * Obtener una receta por ID
     */
    suspend fun getRecipeById(recipeId: Long): Recipe? = withContext(Dispatchers.IO) {
        recipeDao.getRecipeById(recipeId)
    }
    
    /**
     * Obtener recetas publicadas
     */
    fun getPublishedRecipes(): Flow<List<Recipe>> = recipeDao.getPublishedRecipes()
    
    /**
     * Obtener borradores de un autor
     */
    fun getDraftsByAuthor(authorId: Long): Flow<List<Recipe>> = recipeDao.getDraftsByAuthor(authorId)
    
    /**
     * Buscar recetas
     */
    fun searchRecipes(query: String): Flow<List<Recipe>> = recipeDao.searchRecipes("%$query%")
    
    /**
     * Obtener recetas por etiqueta
     */
    fun getRecipesByTag(tag: String): Flow<List<Recipe>> = recipeDao.getRecipesByTag("%$tag%")
    
    /**
     * Actualizar una receta
     */
    suspend fun updateRecipe(recipe: Recipe): ValidationResult = withContext(Dispatchers.IO) {
        try {
            val updatedRecipe = recipe.copy(updatedAt = System.currentTimeMillis())
            recipeDao.updateRecipe(updatedRecipe)
            ValidationResult(true, "Receta actualizada exitosamente")
        } catch (e: Exception) {
            ValidationResult(false, "Error al actualizar receta: ${e.message}")
        }
    }
    
    /**
     * Eliminar una receta
     */
    suspend fun deleteRecipe(recipeId: Long): ValidationResult = withContext(Dispatchers.IO) {
        try {
            recipeDao.deleteRecipeById(recipeId)
            ValidationResult(true, "Receta eliminada exitosamente")
        } catch (e: Exception) {
            ValidationResult(false, "Error al eliminar receta: ${e.message}")
        }
    }
    
    /**
     * Actualizar calificación de una receta
     */
    suspend fun updateRecipeRating(recipeId: Long, rating: Float): ValidationResult = withContext(Dispatchers.IO) {
        try {
            recipeDao.updateRecipeRating(recipeId, rating, System.currentTimeMillis())
            ValidationResult(true, "Calificación actualizada")
        } catch (e: Exception) {
            ValidationResult(false, "Error al actualizar calificación: ${e.message}")
        }
    }
    
    /**
     * Publicar o despublicar una receta
     */
    suspend fun updateRecipeStatus(recipeId: Long, isPublished: Boolean): ValidationResult = withContext(Dispatchers.IO) {
        try {
            recipeDao.updateRecipeStatus(recipeId, isPublished, System.currentTimeMillis())
            val status = if (isPublished) "publicada" else "despublicada"
            ValidationResult(true, "Receta $status exitosamente")
        } catch (e: Exception) {
            ValidationResult(false, "Error al cambiar estado: ${e.message}")
        }
    }
}

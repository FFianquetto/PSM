package com.example.ejemplo2.data.repository

import android.content.Context
import android.graphics.Bitmap
import com.example.ejemplo2.data.database.AppDatabase
import com.example.ejemplo2.data.entity.Recipe
import com.example.ejemplo2.data.entity.RecipeImage
import com.example.ejemplo2.utils.ValidationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class RecipeRepository(context: Context) {
    
    private val recipeDao = AppDatabase.getDatabase(context).recipeDao()
    private val recipeImageDao = AppDatabase.getDatabase(context).recipeImageDao()
    
    /**
     * Crear una nueva receta
     */
    suspend fun createRecipe(
        title: String,
        description: String = "",
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
        
        if (description.isBlank()) {
            return@withContext ValidationResult(false, "La descripción es obligatoria")
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
                description = description.trim(),
                ingredients = ingredients.trim(),
                steps = steps.trim(),
                authorId = authorId,
                authorName = authorName.trim(),
                tags = tags?.trim(),
                cookingTime = cookingTime,
                servings = servings,
                imagePath = null, // Ya no usamos imagePath, las imágenes van en recipe_images
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
     * Buscar recetas por título, descripción o nombre del usuario creador
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
    
    /**
     * Clase para retornar resultado con ID de receta
     */
    data class RecipeCreationResult(
        val isValid: Boolean,
        val message: String,
        val recipeId: Long = 0
    )
    
    /**
     * Crear una receta con imágenes
     */
    suspend fun createRecipeWithImages(
        title: String,
        description: String = "",
        ingredients: String,
        steps: String,
        authorId: Long,
        authorName: String,
        tags: String? = null,
        cookingTime: Int = 0,
        servings: Int = 1,
        isPublished: Boolean = false,
        images: List<Pair<Bitmap, String>> // Lista de pares (Bitmap, descripción)
    ): RecipeCreationResult = withContext(Dispatchers.IO) {
        
        // Validar campos obligatorios
        if (title.isBlank()) {
            return@withContext RecipeCreationResult(false, "El título es obligatorio")
        }
        
        if (description.isBlank()) {
            return@withContext RecipeCreationResult(false, "La descripción es obligatoria")
        }
        
        if (ingredients.isBlank()) {
            return@withContext RecipeCreationResult(false, "Los ingredientes son obligatorios")
        }
        
        if (steps.isBlank()) {
            return@withContext RecipeCreationResult(false, "Los pasos son obligatorios")
        }
        
        if (authorId <= 0) {
            return@withContext RecipeCreationResult(false, "ID de autor inválido")
        }
        
        if (authorName.isBlank()) {
            return@withContext RecipeCreationResult(false, "Nombre de autor es obligatorio")
        }
        
        try {
            // Crear la receta
            val recipe = Recipe(
                title = title.trim(),
                description = description.trim(),
                ingredients = ingredients.trim(),
                steps = steps.trim(),
                authorId = authorId,
                authorName = authorName.trim(),
                tags = tags?.trim(),
                cookingTime = cookingTime,
                servings = servings,
                imagePath = null, // Ya no usamos imagePath
                isPublished = isPublished
            )
            
            val recipeId = recipeDao.insertRecipe(recipe)
            
            if (recipeId > 0) {
                // Guardar las imágenes si hay alguna
                if (images.isNotEmpty()) {
                    val recipeImages = images.map { (bitmap, description) ->
                        RecipeImage(
                            recipeId = recipeId,
                            imageData = bitmapToByteArray(bitmap),
                            description = description.ifBlank { null }
                        )
                    }
                    recipeImageDao.insertImages(recipeImages)
                }
                
                RecipeCreationResult(
                    true, 
                    "Receta ${if (isPublished) "publicada" else "guardada como borrador"} exitosamente",
                    recipeId
                )
            } else {
                RecipeCreationResult(false, "Error al guardar la receta")
            }
        } catch (e: Exception) {
            RecipeCreationResult(false, "Error al crear receta: ${e.message}")
        }
    }
    
    /**
     * Guardar imágenes de una receta existente
     */
    suspend fun saveRecipeImages(
        recipeId: Long,
        images: List<Pair<Bitmap, String>>
    ): ValidationResult = withContext(Dispatchers.IO) {
        try {
            val recipeImages = images.map { (bitmap, description) ->
                RecipeImage(
                    recipeId = recipeId,
                    imageData = bitmapToByteArray(bitmap),
                    description = description.ifBlank { null }
                )
            }
            recipeImageDao.insertImages(recipeImages)
            ValidationResult(true, "Imágenes guardadas exitosamente")
        } catch (e: Exception) {
            ValidationResult(false, "Error al guardar imágenes: ${e.message}")
        }
    }
    
    /**
     * Obtener imágenes de una receta
     */
    fun getRecipeImages(recipeId: Long): Flow<List<RecipeImage>> {
        return recipeImageDao.getImagesByRecipeId(recipeId)
    }
    
    /**
     * Eliminar una imagen
     */
    suspend fun deleteRecipeImage(imageId: Long): ValidationResult = withContext(Dispatchers.IO) {
        try {
            recipeImageDao.deleteImageById(imageId)
            ValidationResult(true, "Imagen eliminada exitosamente")
        } catch (e: Exception) {
            ValidationResult(false, "Error al eliminar imagen: ${e.message}")
        }
    }
    
    /**
     * Convertir Bitmap a ByteArray
     */
    private fun bitmapToByteArray(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
        return stream.toByteArray()
    }
}

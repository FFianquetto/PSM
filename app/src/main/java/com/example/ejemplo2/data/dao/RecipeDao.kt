package com.example.ejemplo2.data.dao

import androidx.room.*
import com.example.ejemplo2.data.entity.Recipe
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {
    
    @Query("SELECT * FROM recipes ORDER BY createdAt DESC")
    fun getAllRecipes(): Flow<List<Recipe>>
    
    @Query("SELECT * FROM recipes WHERE authorId = :authorId ORDER BY createdAt DESC")
    fun getRecipesByAuthor(authorId: Long): Flow<List<Recipe>>
    
    @Query("SELECT * FROM recipes WHERE id = :recipeId")
    suspend fun getRecipeById(recipeId: Long): Recipe?
    
    @Query("SELECT * FROM recipes WHERE isPublished = 1 ORDER BY createdAt DESC")
    fun getPublishedRecipes(): Flow<List<Recipe>>
    
    @Query("SELECT * FROM recipes WHERE isPublished = 0 AND authorId = :authorId ORDER BY createdAt DESC")
    fun getDraftsByAuthor(authorId: Long): Flow<List<Recipe>>
    
    @Query("SELECT * FROM recipes WHERE title LIKE :query OR description LIKE :query OR authorName LIKE :query ORDER BY createdAt DESC")
    fun searchRecipes(query: String): Flow<List<Recipe>>
    
    @Query("SELECT * FROM recipes WHERE tags LIKE :tag ORDER BY createdAt DESC")
    fun getRecipesByTag(tag: String): Flow<List<Recipe>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecipe(recipe: Recipe): Long
    
    @Update
    suspend fun updateRecipe(recipe: Recipe)
    
    @Delete
    suspend fun deleteRecipe(recipe: Recipe)
    
    @Query("DELETE FROM recipes WHERE id = :recipeId")
    suspend fun deleteRecipeById(recipeId: Long)
    
    @Query("UPDATE recipes SET rating = :rating, updatedAt = :updatedAt WHERE id = :recipeId")
    suspend fun updateRecipeRating(recipeId: Long, rating: Float, updatedAt: Long)
    
    @Query("UPDATE recipes SET isPublished = :isPublished, updatedAt = :updatedAt WHERE id = :recipeId")
    suspend fun updateRecipeStatus(recipeId: Long, isPublished: Boolean, updatedAt: Long)
    
    @Query("DELETE FROM recipes")
    suspend fun deleteAllRecipes()
}

package com.example.ejemplo2.data.dao

import androidx.room.*
import com.example.ejemplo2.data.entity.RecipeImage
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeImageDao {
    
    @Query("SELECT * FROM recipe_images WHERE recipeId = :recipeId ORDER BY createdAt ASC")
    fun getImagesByRecipeId(recipeId: Long): Flow<List<RecipeImage>>
    
    @Query("SELECT * FROM recipe_images WHERE id = :imageId")
    suspend fun getImageById(imageId: Long): RecipeImage?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImage(image: RecipeImage): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImages(images: List<RecipeImage>): List<Long>
    
    @Update
    suspend fun updateImage(image: RecipeImage)
    
    @Delete
    suspend fun deleteImage(image: RecipeImage)
    
    @Query("DELETE FROM recipe_images WHERE id = :imageId")
    suspend fun deleteImageById(imageId: Long)
    
    @Query("DELETE FROM recipe_images WHERE recipeId = :recipeId")
    suspend fun deleteImagesByRecipeId(recipeId: Long)
    
    @Query("SELECT COUNT(*) FROM recipe_images WHERE recipeId = :recipeId")
    suspend fun getImageCountByRecipeId(recipeId: Long): Int
}



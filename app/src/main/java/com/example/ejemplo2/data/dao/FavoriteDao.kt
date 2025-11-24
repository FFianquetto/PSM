package com.example.ejemplo2.data.dao

import androidx.room.*
import com.example.ejemplo2.data.entity.Favorite
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    
    @Query("SELECT * FROM favorites WHERE userId = :userId ORDER BY createdAt DESC")
    suspend fun getFavoritesByUserId(userId: Long): List<Favorite>
    
    @Query("SELECT * FROM favorites WHERE userId = :userId ORDER BY createdAt DESC")
    fun getFavoritesByUserIdFlow(userId: Long): Flow<List<Favorite>>
    
    @Query("SELECT * FROM favorites WHERE userId = :userId AND recipeId = :recipeId LIMIT 1")
    suspend fun getFavorite(userId: Long, recipeId: Long): Favorite?
    
    @Query("SELECT COUNT(*) FROM favorites WHERE userId = :userId AND recipeId = :recipeId")
    suspend fun isFavorite(userId: Long, recipeId: Long): Int
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favorite: Favorite): Long
    
    @Delete
    suspend fun deleteFavorite(favorite: Favorite)
    
    @Query("DELETE FROM favorites WHERE userId = :userId AND recipeId = :recipeId")
    suspend fun deleteFavoriteByRecipeId(userId: Long, recipeId: Long)
    
    @Query("DELETE FROM favorites WHERE userId = :userId")
    suspend fun deleteAllFavoritesByUserId(userId: Long)
    
    @Query("SELECT COUNT(*) FROM favorites WHERE userId = :userId")
    suspend fun getFavoriteCount(userId: Long): Int
    
    /**
     * Buscar favoritos por título, nombre del autor o fecha de publicación
     * La fecha se busca en formato legible (año, mes, día)
     */
    @Query("""
        SELECT * FROM favorites 
        WHERE userId = :userId 
        AND (
            title LIKE :query 
            OR authorName LIKE :query 
            OR strftime('%Y', datetime(createdAt/1000, 'unixepoch')) LIKE :query
            OR strftime('%m', datetime(createdAt/1000, 'unixepoch')) LIKE :query
            OR strftime('%d', datetime(createdAt/1000, 'unixepoch')) LIKE :query
            OR strftime('%Y-%m-%d', datetime(createdAt/1000, 'unixepoch')) LIKE :query
        )
        ORDER BY createdAt DESC
    """)
    suspend fun searchFavorites(userId: Long, query: String): List<Favorite>
}


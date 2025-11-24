package com.example.ejemplo2.data.dao

import androidx.room.*
import com.example.ejemplo2.data.entity.User
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    
    @Query("SELECT * FROM users WHERE email = :email")
    suspend fun getUserByEmail(email: String): User?
    
    @Query("SELECT * FROM users WHERE email = :email AND password = :password")
    suspend fun loginUser(email: String, password: String): User?
    
    @Query("SELECT * FROM users WHERE alias = :alias")
    suspend fun getUserByAlias(alias: String): User?
    
    @Query("SELECT COUNT(*) FROM users WHERE email = :email")
    suspend fun checkEmailExists(email: String): Int
    
    @Query("SELECT COUNT(*) FROM users WHERE alias = :alias")
    suspend fun checkAliasExists(alias: String): Int
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User): Long
    
    @Update
    suspend fun updateUser(user: User)
    
    @Delete
    suspend fun deleteUser(user: User)
    
    @Query("SELECT * FROM users")
    fun getAllUsers(): Flow<List<User>>
    
    @Query("SELECT * FROM users WHERE id = :userId")
    suspend fun getUserById(userId: Long): User?
    
    @Query("DELETE FROM users")
    suspend fun deleteAllUsers()
}

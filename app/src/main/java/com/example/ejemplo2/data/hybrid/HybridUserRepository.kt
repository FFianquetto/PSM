package com.example.ejemplo2.data.hybrid

import android.content.Context
import com.example.ejemplo2.data.entity.User
import com.example.ejemplo2.data.repository.UserRepository
import com.example.ejemplo2.data.repository.MigrationRepository
import com.example.ejemplo2.utils.ValidationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HybridUserRepository(context: Context) {
    
    private val sqliteRepository = UserRepository(context)
    private val migrationRepository = MigrationRepository()
    
    /**
     * Registra un usuario en SQLite
     */
    suspend fun registerUser(
        name: String,
        lastName: String,
        email: String,
        password: String,
        phone: String?,
        address: String?,
        alias: String,
        avatarPath: String? = null
    ): ValidationResult = withContext(Dispatchers.IO) {
        sqliteRepository.registerUser(
            name, lastName, email, password, phone, address, alias, avatarPath
        )
    }
    
    /**
     * Login del usuario desde MySQL usando API REST
     */
    suspend fun loginUser(email: String, password: String): ValidationResult = withContext(Dispatchers.IO) {
        migrationRepository.loginUser(email, password)
    }
    
    /**
     * Obtiene un usuario por email desde SQLite (para registro)
     */
    suspend fun getUserByEmail(email: String): User? = withContext(Dispatchers.IO) {
        sqliteRepository.getUserByEmail(email)
    }
    
    /**
     * Obtiene un usuario por email desde MySQL usando API REST (para login)
     */
    suspend fun getUserByEmailFromMySQL(email: String): User? = withContext(Dispatchers.IO) {
        val mysqlUser = migrationRepository.getUserByEmail(email)
        mysqlUser?.let { 
            User(
                id = it.id,
                name = it.name,
                lastName = it.lastName,
                email = it.email,
                password = it.password,
                phone = it.phone,
                address = it.address,
                alias = it.alias,
                avatarPath = it.avatarPath,
                createdAt = it.createdAt,
                updatedAt = it.updatedAt
            )
        }
    }
    
    /**
     * Obtiene un usuario por ID desde SQLite
     */
    suspend fun getUserById(userId: Long): User? = withContext(Dispatchers.IO) {
        sqliteRepository.getUserById(userId)
    }
    
    /**
     * Obtiene un usuario por ID desde MySQL usando API REST
     */
    suspend fun getUserByIdFromMySQL(userId: Long): User? = withContext(Dispatchers.IO) {
        try {
            val allUsers = migrationRepository.getAllUsers()
            allUsers.fold(
                onSuccess = { users ->
                    val mysqlUser = users.find { it.id == userId }
                    mysqlUser?.let { 
                        User(
                            id = it.id,
                            name = it.name,
                            lastName = it.lastName,
                            email = it.email,
                            password = it.password,
                            phone = it.phone,
                            address = it.address,
                            alias = it.alias,
                            avatarPath = it.avatarPath,
                            createdAt = it.createdAt,
                            updatedAt = it.updatedAt
                        )
                    }
                },
                onFailure = { null }
            )
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Obtiene todos los usuarios desde SQLite
     */
    suspend fun getAllUsers(): List<User> = withContext(Dispatchers.IO) {
        sqliteRepository.getAllUsersAsList()
    }
    
    /**
     * Verifica si un email existe en SQLite
     */
    suspend fun checkEmailExists(email: String): Boolean = withContext(Dispatchers.IO) {
        sqliteRepository.checkEmailExists(email)
    }
    
    /**
     * Verifica si un alias existe en SQLite
     */
    suspend fun checkAliasExists(alias: String): Boolean = withContext(Dispatchers.IO) {
        sqliteRepository.checkAliasExists(alias)
    }
    
    /**
     * Guarda el usuario activo en SQLite para la sesión
     */
    suspend fun saveActiveUser(user: User): ValidationResult = withContext(Dispatchers.IO) {
        try {
            // Verificar si ya existe un usuario con ese email en SQLite
            val existingUser = sqliteRepository.getUserByEmail(user.email)
            
            if (existingUser != null) {
                // Actualizar usuario existente
                val updatedUser = user.copy(id = existingUser.id) // Mantener el ID de SQLite
                sqliteRepository.updateUser(updatedUser)
            } else {
                // Insertar nuevo usuario
                sqliteRepository.registerUser(
                    name = user.name,
                    lastName = user.lastName,
                    email = user.email,
                    password = user.password,
                    phone = user.phone,
                    address = user.address,
                    alias = user.alias,
                    avatarPath = user.avatarPath
                )
            }
            
            ValidationResult(true, "Usuario activo guardado")
        } catch (e: Exception) {
            ValidationResult(false, "Error guardando usuario activo: ${e.message}")
        }
    }
    
    /**
     * Actualiza un usuario en SQLite
     */
    suspend fun updateUser(user: User): ValidationResult = withContext(Dispatchers.IO) {
        sqliteRepository.updateUser(user)
    }
}
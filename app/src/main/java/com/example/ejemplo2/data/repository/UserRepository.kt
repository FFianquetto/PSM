package com.example.ejemplo2.data.repository

import android.content.Context
import com.example.ejemplo2.data.database.AppDatabase
import com.example.ejemplo2.data.entity.User
import com.example.ejemplo2.utils.ValidationResult
import com.example.ejemplo2.utils.ValidationUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class UserRepository(context: Context) {
    
    private val userDao = AppDatabase.getDatabase(context).userDao()
    
    /**
     * Registra un nuevo usuario con todas las validaciones
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
        
        // Validar campos obligatorios
        val nameValidation = ValidationUtils.isValidName(name)
        if (!nameValidation.isValid) return@withContext nameValidation
        
        val lastNameValidation = ValidationUtils.isValidName(lastName)
        if (!lastNameValidation.isValid) return@withContext lastNameValidation
        
        val emailValidation = ValidationUtils.isValidEmail(email)
        if (!emailValidation.isValid) return@withContext emailValidation
        
        val passwordValidation = ValidationUtils.isValidPassword(password)
        if (!passwordValidation.isValid) return@withContext passwordValidation
        
        val aliasValidation = ValidationUtils.isValidAlias(alias)
        if (!aliasValidation.isValid) return@withContext aliasValidation
        
        val phoneValidation = ValidationUtils.isValidPhone(phone)
        if (!phoneValidation.isValid) return@withContext phoneValidation
        
        // Verificar que el email no exista
        val emailExists = userDao.checkEmailExists(email)
        if (emailExists > 0) {
            return@withContext ValidationResult(false, "Este correo electrónico ya está registrado")
        }
        
        // Verificar que el alias no exista
        val aliasExists = userDao.checkAliasExists(alias)
        if (aliasExists > 0) {
            return@withContext ValidationResult(false, "Este alias ya está en uso")
        }
        
        // Crear el usuario
        val user = User(
            name = name.trim(),
            lastName = lastName.trim(),
            email = email.trim().lowercase(),
            password = password, // En producción deberías hashear la contraseña
            phone = phone?.trim(),
            address = address?.trim(),
            alias = alias.trim(),
            avatarPath = avatarPath
        )
        
        try {
            userDao.insertUser(user)
            ValidationResult(true, "Usuario registrado exitosamente")
        } catch (e: Exception) {
            ValidationResult(false, "Error al registrar usuario: ${e.message}")
        }
    }
    
    /**
     * Autentica un usuario
     */
    suspend fun loginUser(email: String, password: String): ValidationResult = withContext(Dispatchers.IO) {
        
        val emailValidation = ValidationUtils.isValidEmail(email)
        if (!emailValidation.isValid) return@withContext emailValidation
        
        if (password.isBlank()) {
            return@withContext ValidationResult(false, "La contraseña es obligatoria")
        }
        
        try {
            val user = userDao.loginUser(email.trim().lowercase(), password)
            if (user != null) {
                ValidationResult(true, "Login exitoso")
            } else {
                ValidationResult(false, "Credenciales incorrectas")
            }
        } catch (e: Exception) {
            ValidationResult(false, "Error al iniciar sesión: ${e.message}")
        }
    }
    
    /**
     * Verifica si un email existe
     */
    suspend fun checkEmailExists(email: String): Boolean = withContext(Dispatchers.IO) {
        userDao.checkEmailExists(email) > 0
    }
    
    /**
     * Verifica si un alias existe
     */
    suspend fun checkAliasExists(alias: String): Boolean = withContext(Dispatchers.IO) {
        userDao.checkAliasExists(alias) > 0
    }
    
    /**
     * Obtiene un usuario por email
     */
    suspend fun getUserByEmail(email: String): User? = withContext(Dispatchers.IO) {
        userDao.getUserByEmail(email)
    }
    
    /**
     * Obtiene un usuario por ID
     */
    suspend fun getUserById(userId: Long): User? = withContext(Dispatchers.IO) {
        userDao.getUserById(userId)
    }
    
    /**
     * Obtiene todos los usuarios registrados
     */
    fun getAllUsers(): Flow<List<User>> {
        return userDao.getAllUsers()
    }
    
    /**
     * Obtiene todos los usuarios como List (para uso en repositorios híbridos)
     */
    suspend fun getAllUsersAsList(): List<User> = withContext(Dispatchers.IO) {
        var userList = emptyList<User>()
        userDao.getAllUsers().collect { users ->
            userList = users
        }
        userList
    }
    
    /**
     * Actualiza un usuario existente
     */
    suspend fun updateUser(user: User): ValidationResult = withContext(Dispatchers.IO) {
        try {
            // Validar campos obligatorios
            val nameValidation = ValidationUtils.isValidName(user.name)
            if (!nameValidation.isValid) return@withContext nameValidation
            
            val lastNameValidation = ValidationUtils.isValidName(user.lastName)
            if (!lastNameValidation.isValid) return@withContext lastNameValidation
            
            val aliasValidation = ValidationUtils.isValidAlias(user.alias)
            if (!aliasValidation.isValid) return@withContext aliasValidation
            
            val phoneValidation = ValidationUtils.isValidPhone(user.phone)
            if (!phoneValidation.isValid) return@withContext phoneValidation
            
            // Verificar que el alias no exista en otro usuario
            val existingUser = userDao.getUserByAlias(user.alias)
            if (existingUser != null && existingUser.id != user.id) {
                return@withContext ValidationResult(false, "Este alias ya está en uso")
            }
            
            userDao.updateUser(user)
            ValidationResult(true, "Usuario actualizado exitosamente")
        } catch (e: Exception) {
            ValidationResult(false, "Error al actualizar usuario: ${e.message}")
        }
    }
}

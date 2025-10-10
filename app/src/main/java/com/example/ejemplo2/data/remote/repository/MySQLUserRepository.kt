package com.example.ejemplo2.data.remote.repository

import android.content.Context
import com.example.ejemplo2.config.DatabaseConfig
import com.example.ejemplo2.data.remote.dao.MySQLUserDao
import com.example.ejemplo2.data.remote.entity.MySQLUser
import com.example.ejemplo2.utils.ValidationResult
import com.example.ejemplo2.utils.ValidationUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MySQLUserRepository(context: Context) {
    
    private val mysqlDao = MySQLUserDao()
    private var isConnected = false
    
    init {
        DatabaseConfig.initialize(context)
        connectToDatabase()
    }
    
    private fun connectToDatabase(): Boolean {
        val connectionUrl = DatabaseConfig.getConnectionUrl()
        val username = DatabaseConfig.getDatabaseUsername()
        val password = DatabaseConfig.getDatabasePassword()
        
        isConnected = mysqlDao.connect(connectionUrl, username, password)
        
        if (isConnected) {
            mysqlDao.createTable()
        }
        
        return isConnected
    }
    
    fun isConnected(): Boolean {
        return mysqlDao.isConnected()
    }
    
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
        
        if (!isConnected()) {
            return@withContext ValidationResult(false, "No hay conexión a MySQL")
        }
        
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
        if (mysqlDao.checkEmailExists(email)) {
            return@withContext ValidationResult(false, "Este correo electrónico ya está registrado en MySQL")
        }
        
        // Verificar que el alias no exista
        if (mysqlDao.checkAliasExists(alias)) {
            return@withContext ValidationResult(false, "Este alias ya está en uso en MySQL")
        }
        
        // Crear el usuario
        val user = MySQLUser(
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
            val userId = mysqlDao.insertUser(user)
            if (userId != null) {
                ValidationResult(true, "Usuario registrado exitosamente en MySQL")
            } else {
                ValidationResult(false, "Error al registrar usuario en MySQL")
            }
        } catch (e: Exception) {
            ValidationResult(false, "Error al registrar usuario en MySQL: ${e.message}")
        }
    }
    
    suspend fun loginUser(email: String, password: String): ValidationResult = withContext(Dispatchers.IO) {
        
        if (!isConnected()) {
            return@withContext ValidationResult(false, "No hay conexión a MySQL")
        }
        
        val emailValidation = ValidationUtils.isValidEmail(email)
        if (!emailValidation.isValid) return@withContext emailValidation
        
        if (password.isBlank()) {
            return@withContext ValidationResult(false, "La contraseña es obligatoria")
        }
        
        try {
            val user = mysqlDao.loginUser(email.trim().lowercase(), password)
            if (user != null) {
                ValidationResult(true, "Login exitoso en MySQL")
            } else {
                ValidationResult(false, "Credenciales incorrectas en MySQL")
            }
        } catch (e: Exception) {
            ValidationResult(false, "Error al iniciar sesión en MySQL: ${e.message}")
        }
    }
    
    suspend fun getUserByEmail(email: String): MySQLUser? = withContext(Dispatchers.IO) {
        if (!isConnected()) return@withContext null
        
        try {
            mysqlDao.getUserByEmail(email.trim().lowercase())
        } catch (e: Exception) {
            null
        }
    }
    
    suspend fun getAllUsers(): List<MySQLUser> = withContext(Dispatchers.IO) {
        if (!isConnected()) return@withContext emptyList()
        
        try {
            mysqlDao.getAllUsers()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    suspend fun checkEmailExists(email: String): Boolean = withContext(Dispatchers.IO) {
        if (!isConnected()) return@withContext false
        
        try {
            mysqlDao.checkEmailExists(email.trim().lowercase())
        } catch (e: Exception) {
            false
        }
    }
    
    suspend fun checkAliasExists(alias: String): Boolean = withContext(Dispatchers.IO) {
        if (!isConnected()) return@withContext false
        
        try {
            mysqlDao.checkAliasExists(alias.trim())
        } catch (e: Exception) {
            false
        }
    }
    
    suspend fun updateUser(user: MySQLUser): ValidationResult = withContext(Dispatchers.IO) {
        if (!isConnected()) {
            return@withContext ValidationResult(false, "No hay conexión a MySQL")
        }
        
        try {
            val success = mysqlDao.updateUser(user)
            if (success) {
                ValidationResult(true, "Usuario actualizado exitosamente en MySQL")
            } else {
                ValidationResult(false, "Error al actualizar usuario en MySQL")
            }
        } catch (e: Exception) {
            ValidationResult(false, "Error al actualizar usuario en MySQL: ${e.message}")
        }
    }
    
    fun disconnect() {
        mysqlDao.disconnect()
        isConnected = false
    }
}

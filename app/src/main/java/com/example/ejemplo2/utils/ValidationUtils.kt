package com.example.ejemplo2.utils

import android.util.Patterns

object ValidationUtils {
    
    /**
     * Valida que la contraseña cumpla con los requisitos:
     * - Mínimo 10 caracteres
     * - Al menos una mayúscula
     * - Al menos una minúscula
     * - Al menos un número
     */
    fun isValidPassword(password: String): ValidationResult {
        if (password.length < 10) {
            return ValidationResult(false, "La contraseña debe tener al menos 10 caracteres")
        }
        
        if (!password.any { it.isUpperCase() }) {
            return ValidationResult(false, "La contraseña debe contener al menos una letra mayúscula")
        }
        
        if (!password.any { it.isLowerCase() }) {
            return ValidationResult(false, "La contraseña debe contener al menos una letra minúscula")
        }
        
        if (!password.any { it.isDigit() }) {
            return ValidationResult(false, "La contraseña debe contener al menos un número")
        }
        
        return ValidationResult(true, "Contraseña válida")
    }
    
    /**
     * Valida que el email tenga formato correcto
     */
    fun isValidEmail(email: String): ValidationResult {
        if (email.isBlank()) {
            return ValidationResult(false, "El correo electrónico es obligatorio")
        }
        
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            return ValidationResult(false, "Formato de correo electrónico inválido")
        }
        
        return ValidationResult(true, "Email válido")
    }
    
    /**
     * Valida que el nombre no esté vacío
     */
    fun isValidName(name: String): ValidationResult {
        if (name.isBlank()) {
            return ValidationResult(false, "El nombre es obligatorio")
        }
        
        if (name.length < 2) {
            return ValidationResult(false, "El nombre debe tener al menos 2 caracteres")
        }
        
        return ValidationResult(true, "Nombre válido")
    }
    
    /**
     * Valida que el alias no esté vacío y sea único
     */
    fun isValidAlias(alias: String): ValidationResult {
        if (alias.isBlank()) {
            return ValidationResult(false, "El alias es obligatorio")
        }
        
        if (alias.length < 3) {
            return ValidationResult(false, "El alias debe tener al menos 3 caracteres")
        }
        
        if (!alias.matches(Regex("^[a-zA-Z0-9_]+$"))) {
            return ValidationResult(false, "El alias solo puede contener letras, números y guiones bajos")
        }
        
        return ValidationResult(true, "Alias válido")
    }
    
    /**
     * Valida formato de teléfono (opcional)
     */
    fun isValidPhone(phone: String?): ValidationResult {
        if (phone.isNullOrBlank()) {
            return ValidationResult(true, "Teléfono opcional")
        }
        
        if (phone.length < 10) {
            return ValidationResult(false, "El teléfono debe tener al menos 10 dígitos")
        }
        
        return ValidationResult(true, "Teléfono válido")
    }
}

data class ValidationResult(
    val isValid: Boolean,
    val message: String
)

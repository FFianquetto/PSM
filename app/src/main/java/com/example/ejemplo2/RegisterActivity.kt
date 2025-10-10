package com.example.ejemplo2

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.ejemplo2.data.hybrid.HybridUserRepository
import com.example.ejemplo2.service.MigrationService
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class RegisterActivity : AppCompatActivity() {
    
    private lateinit var hybridRepository: HybridUserRepository
    private lateinit var migrationService: MigrationService
    private var selectedAvatarUri: Uri? = null
    
    // Launcher para seleccionar imagen
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedAvatarUri = it
            val avatarImageView = findViewById<ImageView>(R.id.avatarImageView)
            avatarImageView.setImageURI(it)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        // Inicializar el repositorio híbrido
        hybridRepository = HybridUserRepository(this)
        migrationService = MigrationService(this)

        // Referencias a los elementos del layout
        val nameInput = findViewById<EditText>(R.id.editTextName)
        val lastNameInput = findViewById<EditText>(R.id.editTextLastName)
        val emailInput = findViewById<EditText>(R.id.editTextEmail)
        
        // Campo de contraseña con ojito
        val passwordField = findViewById<LinearLayout>(R.id.passwordField)
        val passwordInput = passwordField.findViewById<EditText>(R.id.passwordEditText)
        val passwordToggle = passwordField.findViewById<ImageButton>(R.id.togglePasswordButton)
        
        val phoneInput = findViewById<EditText>(R.id.editTextPhone)
        val aliasInput = findViewById<EditText>(R.id.editTextAlias)
        val addressInput = findViewById<EditText>(R.id.editTextAddress)
        val registerBtn = findViewById<Button>(R.id.buttonRegister)
        val loginLink = findViewById<TextView>(R.id.textViewLoginLink)
        val avatarImageView = findViewById<ImageView>(R.id.avatarImageView)
        val selectAvatarText = findViewById<TextView>(R.id.selectAvatarText)
        
        // Configurar hint específico para el campo de contraseña
        passwordInput.hint = "Mínimo 10 caracteres"
        
        // Configurar listener para mostrar/ocultar contraseña
        setupPasswordToggle(passwordInput, passwordToggle)
        
        // Configurar click listeners para seleccionar avatar
        avatarImageView.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }
        
        selectAvatarText.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }
        

        // Botón de registro
        registerBtn.setOnClickListener {
            registerUser(
                nameInput.text.toString(),
                lastNameInput.text.toString(),
                emailInput.text.toString(),
                passwordInput.text.toString(),
                phoneInput.text.toString(),
                addressInput.text.toString(),
                aliasInput.text.toString(),
                selectedAvatarUri?.toString()
            )
        }

        // Link para ir a login
        loginLink.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
    
    private fun registerUser(
        name: String,
        lastName: String,
        email: String,
        password: String,
        phone: String,
        address: String,
        alias: String,
        avatarPath: String?
    ) {
        // Validar que los campos obligatorios no estén vacíos
        if (name.isBlank() || lastName.isBlank() || email.isBlank() || 
            password.isBlank() || alias.isBlank()) {
            Toast.makeText(this, "Por favor completa todos los campos obligatorios", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Deshabilitar el botón para evitar múltiples registros
        val registerBtn = findViewById<Button>(R.id.buttonRegister)
        registerBtn.isEnabled = false
        registerBtn.text = "Registrando..."
        
        lifecycleScope.launch {
            try {
                // Copiar imagen a almacenamiento interno si existe
                val finalAvatarPath = if (selectedAvatarUri != null) {
                    copyImageToInternalStorage(selectedAvatarUri!!)
                } else {
                    avatarPath
                }
                
                val result = hybridRepository.registerUser(
                    name = name,
                    lastName = lastName,
                    email = email,
                    password = password,
                    phone = phone.ifBlank { null },
                    address = address.ifBlank { null },
                    alias = alias,
                    avatarPath = finalAvatarPath
                )
                
                if (result.isValid) {
                    Toast.makeText(this@RegisterActivity, result.message, Toast.LENGTH_LONG).show()
                    
                    // Obtener el ID del usuario recién registrado para sincronizar
                    val registeredUser = hybridRepository.getUserByEmail(email)
                    if (registeredUser != null) {
                        // Sincronizar automáticamente con MySQL
                        syncNewUserToMySQL(registeredUser.id)
                    }
                    
                    // Navegar al login después del registro exitoso
                    val intent = Intent(this@RegisterActivity, LoginActivity::class.java)
                    intent.putExtra("registered_email", email)
                    startActivity(intent)
                    finish()
                } else {
                    Toast.makeText(this@RegisterActivity, result.message, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@RegisterActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                // Rehabilitar el botón
                registerBtn.isEnabled = true
                registerBtn.text = "Registrar"
            }
        }
    }
    
    /**
     * Sincroniza el nuevo usuario con MySQL insertándolo directamente
     */
    private fun syncNewUserToMySQL(userId: Long) {
        lifecycleScope.launch {
            try {
                // Obtener el usuario desde SQLite
                val user = hybridRepository.getUserById(userId)
                if (user != null) {
                    // Insertar directamente en MySQL usando la API
                    val result = migrationService.createUserInMySQL(user)
                    result.fold(
                        onSuccess = { 
                            Toast.makeText(this@RegisterActivity, "✅ Usuario registrado y sincronizado con MySQL exitosamente", Toast.LENGTH_SHORT).show()
                        },
                        onFailure = { error ->
                            Toast.makeText(this@RegisterActivity, "⚠️ Usuario registrado localmente. Error al sincronizar con MySQL: ${error.message}", Toast.LENGTH_LONG).show()
                        }
                    )
                } else {
                    Toast.makeText(this@RegisterActivity, "⚠️ Error: No se pudo obtener el usuario registrado", Toast.LENGTH_LONG).show()
                }
                
            } catch (e: Exception) {
                Toast.makeText(this@RegisterActivity, "⚠️ Usuario registrado localmente. Error de sincronización: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    /**
     * Copia la imagen seleccionada a un directorio interno de la app
     */
    private fun copyImageToInternalStorage(uri: Uri): String? {
        return try {
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            val fileName = "avatar_${System.currentTimeMillis()}.jpg"
            val file = File(filesDir, fileName)
            val outputStream = FileOutputStream(file)
            
            inputStream?.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }
            
            file.absolutePath
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Configura el botón para mostrar/ocultar contraseña
     */
    private fun setupPasswordToggle(editText: EditText, toggleButton: ImageButton) {
        var isPasswordVisible = false
        
        toggleButton.setOnClickListener {
            if (isPasswordVisible) {
                // Ocultar contraseña
                editText.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                toggleButton.setImageResource(android.R.drawable.ic_menu_view)
            } else {
                // Mostrar contraseña
                editText.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                toggleButton.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            }
            
            // Mover cursor al final
            editText.setSelection(editText.text.length)
            isPasswordVisible = !isPasswordVisible
        }
    }
}

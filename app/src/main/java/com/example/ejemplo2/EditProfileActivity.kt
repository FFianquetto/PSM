package com.example.ejemplo2

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
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

class EditProfileActivity : AppCompatActivity() {
    
    private lateinit var hybridRepository: HybridUserRepository
    private lateinit var migrationService: MigrationService
    private var currentUserId: Long = -1
    private var currentUser: com.example.ejemplo2.data.entity.User? = null
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
        setContentView(R.layout.activity_edit_profile)

        // Inicializar el repositorio híbrido
        hybridRepository = HybridUserRepository(this)
        migrationService = MigrationService(this)

        // Obtener datos del usuario desde el intent
        currentUserId = intent.getLongExtra("user_id", -1)
        
        if (currentUserId == -1L) {
            Toast.makeText(this, "Error: No se encontró información del usuario", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // Referencias a los elementos del layout
        val nameInput = findViewById<EditText>(R.id.editTextName)
        val lastNameInput = findViewById<EditText>(R.id.editTextLastName)
        val emailInput = findViewById<EditText>(R.id.editTextEmail)
        val phoneInput = findViewById<EditText>(R.id.editTextPhone)
        val aliasInput = findViewById<EditText>(R.id.editTextAlias)
        val addressInput = findViewById<EditText>(R.id.editTextAddress)
        
        // Campos de contraseña con ojitos
        val currentPasswordField = findViewById<LinearLayout>(R.id.currentPasswordField)
        val newPasswordField = findViewById<LinearLayout>(R.id.newPasswordField)
        val confirmPasswordField = findViewById<LinearLayout>(R.id.confirmPasswordField)
        
        val currentPasswordInput = currentPasswordField.findViewById<EditText>(R.id.passwordEditText)
        val newPasswordInput = newPasswordField.findViewById<EditText>(R.id.passwordEditText)
        val confirmPasswordInput = confirmPasswordField.findViewById<EditText>(R.id.passwordEditText)
        
        val currentPasswordToggle = currentPasswordField.findViewById<ImageButton>(R.id.togglePasswordButton)
        val newPasswordToggle = newPasswordField.findViewById<ImageButton>(R.id.togglePasswordButton)
        val confirmPasswordToggle = confirmPasswordField.findViewById<ImageButton>(R.id.togglePasswordButton)
        
        val saveBtn = findViewById<Button>(R.id.buttonSaveChanges)
        val cancelBtn = findViewById<Button>(R.id.buttonCancel)
        val avatarImageView = findViewById<ImageView>(R.id.avatarImageView)
        val selectAvatarText = findViewById<TextView>(R.id.selectAvatarText)
        
        // Configurar click listeners para seleccionar avatar
        avatarImageView.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }
        
        selectAvatarText.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }
        
        // Configurar listeners para mostrar/ocultar contraseñas
        setupPasswordToggle(currentPasswordInput, currentPasswordToggle)
        setupPasswordToggle(newPasswordInput, newPasswordToggle)
        setupPasswordToggle(confirmPasswordInput, confirmPasswordToggle)
        
        // Configurar hints específicos para cada campo de contraseña
        currentPasswordInput.hint = "Contraseña actual"
        newPasswordInput.hint = "Mínimo 10 caracteres"
        confirmPasswordInput.hint = "Repetir nueva contraseña"

        // Cargar datos del usuario
        loadUserData()

        // Botón guardar cambios
        saveBtn.setOnClickListener {
            saveChanges()
        }

        // Botón cancelar
        cancelBtn.setOnClickListener {
            finish()
        }
        
        // Botón de prueba de conectividad (temporal)
        val testBtn = findViewById<Button>(R.id.buttonTestConnectivity)
        testBtn?.setOnClickListener {
            testConnectivity()
        }
        
        // Configurar navegación inferior
        setupBottomNavigation()
    }
    
    private fun loadUserData() {
        lifecycleScope.launch {
            try {
                val user = hybridRepository.getUserById(currentUserId)
                if (user != null) {
                    currentUser = user
                    updateUI(user)
                } else {
                    Toast.makeText(this@EditProfileActivity, "Usuario no encontrado", Toast.LENGTH_LONG).show()
                    finish()
                }
            } catch (e: Exception) {
                Toast.makeText(this@EditProfileActivity, "Error al cargar datos: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun updateUI(user: com.example.ejemplo2.data.entity.User) {
        val nameInput = findViewById<EditText>(R.id.editTextName)
        val lastNameInput = findViewById<EditText>(R.id.editTextLastName)
        val emailInput = findViewById<EditText>(R.id.editTextEmail)
        val phoneInput = findViewById<EditText>(R.id.editTextPhone)
        val aliasInput = findViewById<EditText>(R.id.editTextAlias)
        val addressInput = findViewById<EditText>(R.id.editTextAddress)
        val avatarImageView = findViewById<ImageView>(R.id.avatarImageView)

        // Cargar datos básicos
        nameInput.setText(user.name)
        lastNameInput.setText(user.lastName)
        emailInput.setText(user.email)
        phoneInput.setText(user.phone ?: "")
        aliasInput.setText(user.alias)
        addressInput.setText(user.address ?: "")

        // Cargar avatar si existe
        if (!user.avatarPath.isNullOrBlank()) {
            try {
                if (user.avatarPath.startsWith("/")) {
                    // Es una ruta de archivo local
                    val file = File(user.avatarPath)
                    if (file.exists()) {
                        val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                        avatarImageView.setImageBitmap(bitmap)
                    }
                } else {
                    // Es una URI
                    val uri = Uri.parse(user.avatarPath)
                    avatarImageView.setImageURI(uri)
                }
            } catch (e: Exception) {
                // Si hay error, mantener la imagen por defecto
            }
        }
    }
    
    private fun saveChanges() {
        val nameInput = findViewById<EditText>(R.id.editTextName)
        val lastNameInput = findViewById<EditText>(R.id.editTextLastName)
        val phoneInput = findViewById<EditText>(R.id.editTextPhone)
        val aliasInput = findViewById<EditText>(R.id.editTextAlias)
        val addressInput = findViewById<EditText>(R.id.editTextAddress)
        
        // Campos de contraseña con ojitos
        val currentPasswordField = findViewById<LinearLayout>(R.id.currentPasswordField)
        val newPasswordField = findViewById<LinearLayout>(R.id.newPasswordField)
        val confirmPasswordField = findViewById<LinearLayout>(R.id.confirmPasswordField)
        
        val currentPasswordInput = currentPasswordField.findViewById<EditText>(R.id.passwordEditText)
        val newPasswordInput = newPasswordField.findViewById<EditText>(R.id.passwordEditText)
        val confirmPasswordInput = confirmPasswordField.findViewById<EditText>(R.id.passwordEditText)
        
        val saveBtn = findViewById<Button>(R.id.buttonSaveChanges)

        val name = nameInput.text.toString().trim()
        val lastName = lastNameInput.text.toString().trim()
        val phone = phoneInput.text.toString().trim()
        val alias = aliasInput.text.toString().trim()
        val address = addressInput.text.toString().trim()
        val currentPassword = currentPasswordInput.text.toString()
        val newPassword = newPasswordInput.text.toString()
        val confirmPassword = confirmPasswordInput.text.toString()

        // Validar campos obligatorios
        if (name.isBlank() || lastName.isBlank() || alias.isBlank()) {
            Toast.makeText(this, "Por favor completa todos los campos obligatorios", Toast.LENGTH_SHORT).show()
            return
        }

        // Validar alias si cambió
        if (alias != currentUser?.alias) {
            lifecycleScope.launch {
                val aliasExists = hybridRepository.checkAliasExists(alias)
                if (aliasExists) {
                    Toast.makeText(this@EditProfileActivity, "Este alias ya está en uso", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                updateUserData(name, lastName, phone, alias, address, currentPassword, newPassword, confirmPassword)
            }
        } else {
            updateUserData(name, lastName, phone, alias, address, currentPassword, newPassword, confirmPassword)
        }
    }
    
    private fun updateUserData(
        name: String,
        lastName: String,
        phone: String,
        alias: String,
        address: String,
        currentPassword: String,
        newPassword: String,
        confirmPassword: String
    ) {
        val saveBtn = findViewById<Button>(R.id.buttonSaveChanges)
        saveBtn.isEnabled = false
        saveBtn.text = "Guardando..."

        lifecycleScope.launch {
            try {
                currentUser?.let { user ->
                    // Determinar la nueva contraseña
                    val finalPassword = if (newPassword.isNotBlank()) {
                        // Validar cambio de contraseña
                        if (currentPassword != user.password) {
                            Toast.makeText(this@EditProfileActivity, "La contraseña actual es incorrecta", Toast.LENGTH_SHORT).show()
                            return@let
                        }
                        
                        if (newPassword != confirmPassword) {
                            Toast.makeText(this@EditProfileActivity, "Las contraseñas nuevas no coinciden", Toast.LENGTH_SHORT).show()
                            return@let
                        }
                        
                        // Validar nueva contraseña
                        val passwordValidation = com.example.ejemplo2.utils.ValidationUtils.isValidPassword(newPassword)
                        if (!passwordValidation.isValid) {
                            Toast.makeText(this@EditProfileActivity, passwordValidation.message, Toast.LENGTH_SHORT).show()
                            return@let
                        }
                        
                        newPassword
                    } else {
                        user.password
                    }

                    // Copiar imagen si se seleccionó una nueva
                    val finalAvatarPath = if (selectedAvatarUri != null) {
                        copyImageToInternalStorage(selectedAvatarUri!!)
                    } else {
                        user.avatarPath
                    }

                    // Crear usuario actualizado
                    val updatedUser = user.copy(
                        name = name,
                        lastName = lastName,
                        phone = phone.ifBlank { null },
                        address = address.ifBlank { null },
                        alias = alias,
                        password = finalPassword,
                        avatarPath = finalAvatarPath,
                        updatedAt = System.currentTimeMillis()
                    )

                    // Actualizar en la base de datos local (SQLite)
                    val result = hybridRepository.updateUser(updatedUser)
                    
                    if (result.isValid) {
                        Toast.makeText(this@EditProfileActivity, "Perfil actualizado localmente", Toast.LENGTH_SHORT).show()
                        
                        // Sincronizar cambios con MySQL (API)
                        syncChangesToMySQL(user.id)
                        
                        // Regresar a ProfileActivity con datos actualizados
                        val intent = Intent(this@EditProfileActivity, ProfileActivity::class.java)
                        intent.putExtra("user_id", user.id)
                        intent.putExtra("user_name", updatedUser.name)
                        intent.putExtra("user_email", updatedUser.email)
                        intent.putExtra("user_alias", updatedUser.alias)
                        intent.putExtra("user_avatar", updatedUser.avatarPath)
                        startActivity(intent)
                        finish()
                    } else {
                        Toast.makeText(this@EditProfileActivity, result.message, Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this@EditProfileActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                saveBtn.isEnabled = true
                saveBtn.text = "Guardar Cambios"
            }
        }
    }
    
    /**
     * Método de prueba para verificar conectividad
     */
    private fun testConnectivity() {
        lifecycleScope.launch {
            try {
                val result = migrationService.testConnectivity()
                result.fold(
                    onSuccess = { message ->
                        Toast.makeText(this@EditProfileActivity, "✅ $message", Toast.LENGTH_SHORT).show()
                    },
                    onFailure = { error ->
                        Toast.makeText(this@EditProfileActivity, "❌ Error de conectividad: ${error.message}", Toast.LENGTH_LONG).show()
                    }
                )
            } catch (e: Exception) {
                Toast.makeText(this@EditProfileActivity, "❌ Excepción: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    /**
     * Sincroniza los cambios del perfil con MySQL usando UPDATE específico del usuario
     */
    private fun syncChangesToMySQL(userId: Long) {
        lifecycleScope.launch {
            try {
                // Usar UPDATE específico del usuario (no migración completa)
                val result = migrationService.updateUserInMySQL(userId)
                result.fold(
                    onSuccess = {
                        Toast.makeText(this@EditProfileActivity, "✅ Cambios sincronizados con la API (MySQL) exitosamente", Toast.LENGTH_SHORT).show()
                    },
                    onFailure = { error ->
                        Toast.makeText(this@EditProfileActivity, "⚠️ Cambios guardados localmente. Error sincronizando con API: ${error.message}", Toast.LENGTH_LONG).show()
                    }
                )
                
            } catch (e: Exception) {
                Toast.makeText(this@EditProfileActivity, "⚠️ Cambios guardados localmente. Error de sincronización: ${e.message}", Toast.LENGTH_LONG).show()
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
    
    /**
     * Configura la navegación inferior
     */
    private fun setupBottomNavigation() {
        // Botón Inicio (navegar a MainActivity)
        val homeButton = findViewById<View>(R.id.homeButton)
        homeButton.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("user_id", currentUserId)
            intent.putExtra("user_email", currentUser?.email ?: "")
            startActivity(intent)
        }

        // Botón Crear (navegar a CreateRecipeActivity)
        val createButton = findViewById<View>(R.id.createButton)
        createButton.setOnClickListener {
            if (currentUserId != -1L) {
                val intent = Intent(this, CreateRecipeActivity::class.java)
                intent.putExtra("user_id", currentUserId)
                intent.putExtra("user_name", "${currentUser?.name} ${currentUser?.lastName}")
                intent.putExtra("user_email", currentUser?.email ?: "")
                startActivity(intent)
            } else {
                Toast.makeText(this, "Debes iniciar sesión para crear recetas", Toast.LENGTH_SHORT).show()
            }
        }

        // Botón Perfil (navegar a ProfileActivity)
        val profileButton = findViewById<View>(R.id.profileButton)
        profileButton.setOnClickListener {
            if (currentUserId != -1L) {
                val intent = Intent(this, ProfileActivity::class.java)
                intent.putExtra("user_id", currentUserId)
                intent.putExtra("user_email", currentUser?.email ?: "")
                startActivity(intent)
            } else {
                Toast.makeText(this, "Debes iniciar sesión para ver tu perfil", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

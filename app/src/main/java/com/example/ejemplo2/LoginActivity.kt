package com.example.ejemplo2

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.ejemplo2.data.hybrid.HybridUserRepository
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {
    
    private lateinit var hybridRepository: HybridUserRepository
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Inicializar el repositorio híbrido
        hybridRepository = HybridUserRepository(this)

        // Referencias a los elementos del layout
        val emailInput = findViewById<EditText>(R.id.emailInput)
        
        // Campo de contraseña con ojito
        val passwordField = findViewById<LinearLayout>(R.id.passwordField)
        val passwordInput = passwordField.findViewById<EditText>(R.id.passwordEditText)
        val passwordToggle = passwordField.findViewById<ImageButton>(R.id.togglePasswordButton)
        
        val loginBtn = findViewById<Button>(R.id.loginBtn)
        val registerLink = findViewById<TextView>(R.id.textViewRegisterLink)
        val guestText = findViewById<TextView>(R.id.guestText)
        
        // Configurar hint específico para el campo de contraseña
        passwordInput.hint = "**********"
        
        // Configurar listener para mostrar/ocultar contraseña
        setupPasswordToggle(passwordInput, passwordToggle)

        // Verificar si hay un email pre-cargado desde el registro
        val registeredEmail = intent.getStringExtra("registered_email")
        if (!registeredEmail.isNullOrBlank()) {
            emailInput.setText(registeredEmail)
        }

        // Botón de login
        loginBtn.setOnClickListener {
            val email = emailInput.text.toString()
            val password = passwordInput.text.toString()
            
            if (email.isBlank() || password.isBlank()) {
                Toast.makeText(this, "Por favor completa todos los campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            loginUser(email, password)
        }

        // Link para ir a registro
        registerLink.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }

        // Continuar como invitado
        guestText.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
    
    private fun loginUser(email: String, password: String) {
        val loginBtn = findViewById<Button>(R.id.loginBtn)
        loginBtn.isEnabled = false
        loginBtn.text = "Iniciando sesión..."
        
        lifecycleScope.launch {
            try {
                val result = hybridRepository.loginUser(email, password)
                
                if (result.isValid) {
                    Toast.makeText(this@LoginActivity, "¡Bienvenido!", Toast.LENGTH_SHORT).show()
                    
                    // Obtener el usuario desde SQLite (información local)
                    val user = hybridRepository.getUserByEmail(email)
                    
                    user?.let { userData ->
                        // Navegar al dashboard (MainActivity)
                        val intent = Intent(this@LoginActivity, MainActivity::class.java)
                        intent.putExtra("user_id", userData.id)
                        intent.putExtra("user_name", userData.name)
                        intent.putExtra("user_email", userData.email)
                        intent.putExtra("user_alias", userData.alias)
                        intent.putExtra("user_avatar", userData.avatarPath)
                        startActivity(intent)
                        finish()
                    }
                } else {
                    Toast.makeText(this@LoginActivity, result.message, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@LoginActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                loginBtn.isEnabled = true
                loginBtn.text = "Iniciar Sesión"
            }
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

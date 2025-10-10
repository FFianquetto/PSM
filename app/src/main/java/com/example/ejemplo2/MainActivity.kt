package com.example.ejemplo2

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.ejemplo2.data.database.AppDatabase

class MainActivity : AppCompatActivity() {
    
    private var isUserLoggedIn = false
    private var currentUserId: Long = -1
    
    companion object {
        // Removido REQUEST_CODE_CREATE_PUBLICATION
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        Log.d("MainActivity", "onCreate iniciado")
        
        // Verificar si el usuario está logueado
        checkUserLogin()
        
        // Configurar RecyclerView
        val recyclerView = findViewById<RecyclerView>(R.id.recipesRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        
        
        // Configurar navegación inferior
        setupBottomNavigation()
    }
    
    private fun checkUserLogin() {
        // Verificar si hay datos de usuario en el intent
        val userId = intent.getLongExtra("user_id", -1)
        val userName = intent.getStringExtra("user_name")
        
        if (userId != -1L && !userName.isNullOrBlank()) {
            isUserLoggedIn = true
            currentUserId = userId
            Log.d("MainActivity", "Usuario logueado: $userName (ID: $userId)")
            Toast.makeText(this, "¡Bienvenido, $userName!", Toast.LENGTH_SHORT).show()
        } else {
            isUserLoggedIn = false
            Log.d("MainActivity", "Usuario no logueado - modo invitado")
        }
    }
    

    private fun setupBottomNavigation() {
        Log.d("MainActivity", "Configurando navegación inferior")
        
        // Botón Inicio (ya estamos en esta pantalla)
        val homeButton = findViewById<View>(R.id.homeButton)
        homeButton.setOnClickListener {
            Log.d("MainActivity", "Botón inicio clickeado - ya estamos en inicio")
            Toast.makeText(this, "Ya estás en la pantalla de inicio", Toast.LENGTH_SHORT).show()
            // Ya estamos en la pantalla de inicio, no hacer nada
            // O podríamos refrescar la pantalla si es necesario
        }

        // Botón Crear (navegar a nueva receta)
        val createButton = findViewById<View>(R.id.createButton)
        createButton.setOnClickListener {
            Log.d("MainActivity", "Botón crear clickeado - navegando a CreateRecipeActivity")
            
            if (isUserLoggedIn) {
                Toast.makeText(this, "Navegando a crear receta", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, CreateRecipeActivity::class.java)
                // Pasar datos del usuario para la receta
                intent.putExtra("user_id", currentUserId)
                intent.putExtra("user_name", "Usuario") // Temporal, se puede mejorar obteniendo de BD
                startActivity(intent)
            } else {
                Toast.makeText(this, "Debes iniciar sesión para crear recetas", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, LoginActivity::class.java)
                startActivity(intent)
            }
        }

        // Botón Perfil (navegar a perfil)
        val profileButton = findViewById<View>(R.id.profileButton)
        profileButton.setOnClickListener {
            Log.d("MainActivity", "Botón perfil clickeado")
            
            if (isUserLoggedIn) {
                Toast.makeText(this, "Navegando a perfil", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, ProfileActivity::class.java)
                // Pasar solo el ID del usuario, ProfileActivity cargará el resto desde la BD
                intent.putExtra("user_id", currentUserId)
                startActivity(intent)
            } else {
                Toast.makeText(this, "Debes iniciar sesión para ver tu perfil", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, LoginActivity::class.java)
                startActivity(intent)
            }
        }
        
        Log.d("MainActivity", "Navegación inferior configurada")
    }
    
    
}
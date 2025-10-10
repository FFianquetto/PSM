package com.example.ejemplo2

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.ejemplo2.adapter.UserRecipesAdapter
import com.example.ejemplo2.data.entity.Recipe
import com.example.ejemplo2.data.hybrid.HybridUserRepository
import com.example.ejemplo2.data.repository.RecipeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.*

class ProfileActivity : AppCompatActivity() {
    
    private lateinit var hybridRepository: HybridUserRepository
    private lateinit var recipeRepository: RecipeRepository
    private lateinit var userRecipesAdapter: UserRecipesAdapter
    private var currentUserId: Long = -1
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        // Inicializar repositorios
        hybridRepository = HybridUserRepository(this)
        recipeRepository = RecipeRepository(this)
        
        // Configurar RecyclerView para recetas del usuario
        setupUserRecipesRecyclerView()

        // Obtener ID del usuario desde el intent
        currentUserId = intent.getLongExtra("user_id", -1)

        // Referencias a los elementos del layout
        val profileAvatar = findViewById<ImageView>(R.id.profileAvatar)
        val textViewFullName = findViewById<TextView>(R.id.textViewFullName)
        val textViewEmail = findViewById<TextView>(R.id.textViewEmail)
        val textViewAlias = findViewById<TextView>(R.id.textViewAlias)
        val textViewPhone = findViewById<TextView>(R.id.textViewPhone)
        val textViewAddress = findViewById<TextView>(R.id.textViewAddress)
        val labelPhone = findViewById<TextView>(R.id.labelPhone)
        val labelAddress = findViewById<TextView>(R.id.labelAddress)
        val buttonEditProfile = findViewById<Button>(R.id.buttonEditProfile)
        val buttonLogout = findViewById<Button>(R.id.buttonLogout)

        // Mostrar indicador de carga
        showLoadingIndicator(true)
        
        // Cargar datos del usuario
        loadUserData()

        // Botón editar perfil
        buttonEditProfile.setOnClickListener {
            val intent = Intent(this, EditProfileActivity::class.java)
            intent.putExtra("user_id", currentUserId)
            startActivity(intent)
        }

        // Botón cerrar sesión
        buttonLogout.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }
    
    private fun loadUserData() {
        if (currentUserId == -1L) {
            Toast.makeText(this, "Error: No se encontró información del usuario", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        
        // Cargar datos directamente desde la base de datos
        
        lifecycleScope.launch {
            try {
                // Luego cargar datos completos desde la base de datos
                var user = hybridRepository.getUserById(currentUserId)
                
                // Si no se encuentra en SQLite, intentar desde MySQL
                if (user == null) {
                    user = hybridRepository.getUserByIdFromMySQL(currentUserId)
                }
                
                if (user != null) {
                    updateUI(user)
                    // Ocultar indicador de carga después de actualizar con datos completos
                    showLoadingIndicator(false)
                } else {
                    Log.w("ProfileActivity", "Usuario no encontrado en la base de datos")
                    Toast.makeText(this@ProfileActivity, "Usuario no encontrado en la base de datos", Toast.LENGTH_LONG).show()
                    finish()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ProfileActivity, "Error al cargar datos completos: ${e.message}", Toast.LENGTH_SHORT).show()
                showLoadingIndicator(false)
            }
        }
    }
    
    /**
     * Muestra u oculta el indicador de carga
     */
    private fun showLoadingIndicator(show: Boolean) {
        // Buscar el ProgressBar en el layout (si existe)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)
        if (progressBar != null) {
            progressBar.visibility = if (show) View.VISIBLE else View.GONE
        }
        
        // También podemos deshabilitar los botones mientras carga
        val buttonEditProfile = findViewById<Button>(R.id.buttonEditProfile)
        val buttonLogout = findViewById<Button>(R.id.buttonLogout)
        
        buttonEditProfile.isEnabled = !show
        buttonLogout.isEnabled = !show
    }
    
    /**
     * Carga el avatar del usuario desde una ruta de manera asíncrona
     */
    private fun loadAvatar(avatarPath: String) {
        val profileAvatar = findViewById<ImageView>(R.id.profileAvatar)
        
        Log.d("ProfileActivity", "Intentando cargar avatar desde: $avatarPath")
        
        if (avatarPath.isBlank()) {
            Log.d("ProfileActivity", "No hay ruta de avatar, usando ícono por defecto")
            profileAvatar.setImageResource(android.R.drawable.ic_menu_camera)
            return
        }
        
        // Cargar imagen de manera asíncrona
        lifecycleScope.launch {
            try {
                val bitmap = loadBitmapFromPath(avatarPath)
                
                if (bitmap != null) {
                    Log.d("ProfileActivity", "Avatar cargado exitosamente")
                    profileAvatar.setImageBitmap(bitmap)
                } else {
                    Log.w("ProfileActivity", "No se pudo cargar el avatar, usando ícono por defecto")
                    profileAvatar.setImageResource(android.R.drawable.ic_menu_myplaces)
                }
            } catch (e: Exception) {
                Log.e("ProfileActivity", "Error cargando avatar: ${e.message}", e)
                profileAvatar.setImageResource(android.R.drawable.ic_menu_myplaces)
            }
        }
    }
    
    /**
     * Carga un bitmap desde una ruta de archivo de manera asíncrona
     */
    private suspend fun loadBitmapFromPath(path: String): Bitmap? = withContext(Dispatchers.IO) {
        return@withContext try {
            if (path.startsWith("/")) {
                // Es una ruta de archivo local
                val file = File(path)
                Log.d("ProfileActivity", "Archivo local - Existe: ${file.exists()}, Ruta: ${file.absolutePath}")
                
                if (file.exists()) {
                    // Leer el archivo de manera más robusta
                    val inputStream = FileInputStream(file)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream.close()
                    
                    if (bitmap != null) {
                        Log.d("ProfileActivity", "Bitmap decodificado exitosamente")
                        bitmap
                    } else {
                        Log.w("ProfileActivity", "No se pudo decodificar el bitmap")
                        null
                    }
                } else {
                    Log.w("ProfileActivity", "Archivo no existe: ${file.absolutePath}")
                    null
                }
            } else if (path.startsWith("content://")) {
                // Es una URI de contenido
                Log.d("ProfileActivity", "Cargando desde URI de contenido: $path")
                val uri = Uri.parse(path)
                val inputStream = contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                bitmap
            } else {
                Log.d("ProfileActivity", "Intentando cargar como URI simple: $path")
                val uri = Uri.parse(path)
                val inputStream = contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                bitmap
            }
        } catch (e: Exception) {
            Log.e("ProfileActivity", "Error en loadBitmapFromPath: ${e.message}", e)
            null
        }
    }
    
    private fun updateUI(user: com.example.ejemplo2.data.entity.User) {
        val textViewFullName = findViewById<TextView>(R.id.textViewFullName)
        val textViewEmail = findViewById<TextView>(R.id.textViewEmail)
        val textViewAlias = findViewById<TextView>(R.id.textViewAlias)
        val textViewPhone = findViewById<TextView>(R.id.textViewPhone)
        val textViewAddress = findViewById<TextView>(R.id.textViewAddress)
        val labelPhone = findViewById<TextView>(R.id.labelPhone)
        val labelAddress = findViewById<TextView>(R.id.labelAddress)

        // Actualizar información básica
        textViewFullName.text = "${user.name} ${user.lastName}"
        textViewEmail.text = user.email
        textViewAlias.text = "@${user.alias}"

        // Actualizar avatar si existe
        if (!user.avatarPath.isNullOrBlank()) {
            loadAvatar(user.avatarPath)
        } else {
            Log.d("ProfileActivity", "Usuario no tiene avatar configurado")
            // Mostrar avatar por defecto más amigable
            val profileAvatar = findViewById<ImageView>(R.id.profileAvatar)
            profileAvatar.setImageResource(android.R.drawable.ic_menu_myplaces)
        }

        // Mostrar campos opcionales solo si tienen datos
        if (!user.phone.isNullOrBlank()) {
            textViewPhone.text = user.phone
            textViewPhone.visibility = TextView.VISIBLE
            labelPhone.visibility = TextView.VISIBLE
        } else {
            textViewPhone.visibility = TextView.GONE
            labelPhone.visibility = TextView.GONE
        }

        if (!user.address.isNullOrBlank()) {
            textViewAddress.text = user.address
            textViewAddress.visibility = TextView.VISIBLE
            labelAddress.visibility = TextView.VISIBLE
        } else {
            textViewAddress.visibility = TextView.GONE
            labelAddress.visibility = TextView.GONE
        }
        
        // Mostrar mensaje de bienvenida personalizado
        Toast.makeText(this, "¡Bienvenido de nuevo, ${user.name}!", Toast.LENGTH_SHORT).show()
        
        // Cargar recetas del usuario
        loadUserRecipes()
    }
    
    private fun setupUserRecipesRecyclerView() {
        val recyclerView = findViewById<RecyclerView>(R.id.userRecipesRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        
        userRecipesAdapter = UserRecipesAdapter(
            onRecipeClick = { recipe ->
                navigateToRecipeDetail(recipe)
            }
        )
        
        recyclerView.adapter = userRecipesAdapter
    }
    
    private fun loadUserRecipes() {
        if (currentUserId == -1L) return
        
        lifecycleScope.launch {
            try {
                val recipes = recipeRepository.getRecipesByAuthor(currentUserId)
                recipes.collect { recipeList ->
                    val noRecipesText = findViewById<TextView>(R.id.noRecipesText)
                    
                    if (recipeList.isEmpty()) {
                        noRecipesText.visibility = TextView.VISIBLE
                        findViewById<RecyclerView>(R.id.userRecipesRecyclerView).visibility = RecyclerView.GONE
                    } else {
                        noRecipesText.visibility = TextView.GONE
                        findViewById<RecyclerView>(R.id.userRecipesRecyclerView).visibility = RecyclerView.VISIBLE
                        userRecipesAdapter.updateRecipes(recipeList)
                    }
                }
            } catch (e: Exception) {
                Log.e("ProfileActivity", "Error cargando recetas del usuario: ${e.message}", e)
                Toast.makeText(this@ProfileActivity, "Error cargando recetas", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun navigateToRecipeDetail(recipe: Recipe) {
        val intent = Intent(this, RecipeDetailActivity::class.java)
        intent.putExtra("recipe_id", recipe.id)
        startActivity(intent)
    }
}
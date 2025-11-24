package com.example.ejemplo2

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.text.TextWatcher
import android.text.Editable
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
import com.example.ejemplo2.data.database.AppDatabase
import com.example.ejemplo2.data.entity.Favorite

class ProfileActivity : AppCompatActivity() {
    
    private lateinit var hybridRepository: HybridUserRepository
    private lateinit var recipeRepository: RecipeRepository
    private lateinit var userRecipesAdapter: UserRecipesAdapter
    private lateinit var likedRecipesAdapter: UserRecipesAdapter
    private var currentUserId: Long = -1
    private var currentUserEmail: String = ""
    private var allFavorites: List<Favorite> = emptyList() // Lista completa de favoritos para restaurar después de búsqueda
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        // Inicializar repositorios
        hybridRepository = HybridUserRepository(this)
        recipeRepository = RecipeRepository(this)
        
        // Configurar RecyclerView para recetas del usuario
        setupUserRecipesRecyclerView()
        
        // Configurar RecyclerView para recetas con like
        setupLikedRecipesRecyclerView()
        
        // Configurar barra de búsqueda de favoritos
        setupFavoritesSearchBar()

        // Obtener ID del usuario y email desde el intent
        currentUserId = intent.getLongExtra("user_id", -1)
        
        // SIEMPRE obtener el email del intent primero, y si viene vacío, intentar preservar uno existente
        val emailFromIntent = intent.getStringExtra("user_email") ?: ""
        currentUserEmail = if (emailFromIntent.isNotEmpty()) {
            emailFromIntent
        } else {
            // Intentar restaurar desde savedInstanceState si existe
            savedInstanceState?.getString("saved_user_email") ?: ""
        }
        
        // Restaurar userId desde savedInstanceState si no viene en el intent
        if (currentUserId == -1L && savedInstanceState != null) {
            currentUserId = savedInstanceState.getLong("saved_user_id", -1)
        }
        
        Log.d("ProfileActivity", "Email recibido del intent: '$emailFromIntent', Email final: '$currentUserEmail', UserId: $currentUserId")

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
        
        // Configurar navegación inferior
        setupBottomNavigation()
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

        // Guardar email del usuario actual
        currentUserEmail = user.email
        Log.d("ProfileActivity", "updateUI: Email guardado desde usuario: '$currentUserEmail'")

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
        
        // Cargar publicaciones guardadas (favoritos) desde SQLite
        if (currentUserId != -1L) {
            Log.d("ProfileActivity", "updateUI: Cargando publicaciones guardadas desde SQLite...")
            loadSavedRecipes()
        } else {
            Log.e("ProfileActivity", "updateUI: ⚠️ Usuario no logueado - no se pueden cargar publicaciones guardadas")
        }
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
    
    private fun setupLikedRecipesRecyclerView() {
        val recyclerView = findViewById<RecyclerView>(R.id.likedRecipesRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        
        likedRecipesAdapter = UserRecipesAdapter(
            onRecipeClick = { recipe ->
                navigateToRecipeDetail(recipe)
            }
        )
        
        recyclerView.adapter = likedRecipesAdapter
    }
    
    /**
     * Configurar barra de búsqueda de favoritos
     */
    private fun setupFavoritesSearchBar() {
        val searchEditText = findViewById<EditText>(R.id.favoritesSearchEditText)
        val searchButton = findViewById<ImageButton>(R.id.favoritesSearchButton)
        
        // Listener para el botón de búsqueda
        searchButton.setOnClickListener {
            performFavoritesSearch()
        }
        
        // Listener para cuando se presiona Enter en el teclado
        searchEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performFavoritesSearch()
                true
            } else {
                false
            }
        }
        
        // Listener para búsqueda en tiempo real mientras se escribe
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim() ?: ""
                if (query.isEmpty()) {
                    // Si está vacío, mostrar todos los favoritos
                    displayFavorites(allFavorites)
                } else {
                    // Realizar búsqueda
                    performFavoritesSearch()
                }
            }
        })
    }
    
    private fun loadUserRecipes() {
        if (currentUserId == -1L) {
            Log.e("ProfileActivity", "loadUserRecipes: Usuario no logueado - currentUserId=$currentUserId")
            return
        }
        
        Log.d("ProfileActivity", "=== CARGANDO MIS RECETAS DESDE SQLite ===")
        Log.d("ProfileActivity", "User ID (SQLite): $currentUserId")
        
        lifecycleScope.launch {
            try {
                val db = AppDatabase.getDatabase(this@ProfileActivity)
                val recipeDao = db.recipeDao()
                
                // Obtener recetas del usuario desde SQLite donde el usuario es el autor
                val recipesFlow = recipeDao.getRecipesByAuthor(currentUserId)
                
                recipesFlow.collect { recipeList ->
                    Log.d("ProfileActivity", "✓ Recetas del usuario obtenidas desde SQLite: ${recipeList.size}")
                    
                    val noRecipesText = findViewById<TextView>(R.id.noRecipesText)
                    
                    withContext(Dispatchers.Main) {
                        if (recipeList.isEmpty()) {
                            Log.d("ProfileActivity", "No hay recetas del usuario - mostrando mensaje")
                            noRecipesText.visibility = TextView.VISIBLE
                            findViewById<RecyclerView>(R.id.userRecipesRecyclerView).visibility = RecyclerView.GONE
                        } else {
                            Log.d("ProfileActivity", "✓ Mostrando ${recipeList.size} recetas del usuario")
                            noRecipesText.visibility = TextView.GONE
                            findViewById<RecyclerView>(R.id.userRecipesRecyclerView).visibility = RecyclerView.VISIBLE
                            userRecipesAdapter.updateRecipes(recipeList)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("ProfileActivity", "✗ EXCEPCIÓN cargando recetas del usuario: ${e.message}", e)
                Log.e("ProfileActivity", "Stack trace: ${e.stackTrace.joinToString("\n")}")
                withContext(Dispatchers.Main) {
                    val noRecipesText = findViewById<TextView>(R.id.noRecipesText)
                    noRecipesText.visibility = TextView.VISIBLE
                    findViewById<RecyclerView>(R.id.userRecipesRecyclerView).visibility = RecyclerView.GONE
                    Toast.makeText(this@ProfileActivity, "Error cargando recetas: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    /**
     * Cargar publicaciones guardadas (favoritos) desde SQLite
     */
    private fun loadSavedRecipes() {
        if (currentUserId == -1L) {
            Log.e("ProfileActivity", "loadSavedRecipes: Usuario no logueado - currentUserId=$currentUserId")
            return
        }
        
        Log.d("ProfileActivity", "=== CARGANDO PUBLICACIONES GUARDADAS (FAVORITOS) ===")
        Log.d("ProfileActivity", "User ID (SQLite): $currentUserId")
        
        lifecycleScope.launch {
            try {
                val db = AppDatabase.getDatabase(this@ProfileActivity)
                val favoriteDao = db.favoriteDao()
                
                // Obtener favoritos desde SQLite
                val favorites = withContext(Dispatchers.IO) {
                    favoriteDao.getFavoritesByUserId(currentUserId)
                }
                
                // Guardar lista completa de favoritos
                allFavorites = favorites
                
                Log.d("ProfileActivity", "✓ Publicaciones guardadas obtenidas: ${favorites.size}")
                
                // Mostrar favoritos
                displayFavorites(favorites)
            } catch (e: Exception) {
                Log.e("ProfileActivity", "✗ EXCEPCIÓN cargando publicaciones guardadas: ${e.message}", e)
                Log.e("ProfileActivity", "Stack trace: ${e.stackTrace.joinToString("\n")}")
                withContext(Dispatchers.Main) {
                    val noLikedRecipesText = findViewById<TextView>(R.id.noLikedRecipesText)
                    noLikedRecipesText.visibility = TextView.VISIBLE
                    findViewById<RecyclerView>(R.id.likedRecipesRecyclerView).visibility = RecyclerView.GONE
                }
            }
        }
    }
    
    /**
     * Realizar búsqueda de favoritos
     */
    private fun performFavoritesSearch() {
        val searchEditText = findViewById<EditText>(R.id.favoritesSearchEditText)
        val query = searchEditText.text.toString().trim()
        
        // Ocultar teclado
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(searchEditText.windowToken, 0)
        
        if (query.isEmpty()) {
            // Si está vacío, mostrar todos los favoritos
            displayFavorites(allFavorites)
            return
        }
        
        lifecycleScope.launch {
            try {
                val db = AppDatabase.getDatabase(this@ProfileActivity)
                val favoriteDao = db.favoriteDao()
                
                // Realizar búsqueda con el patrón LIKE
                val searchPattern = "%$query%"
                val searchResults = withContext(Dispatchers.IO) {
                    favoriteDao.searchFavorites(currentUserId, searchPattern)
                }
                
                Log.d("ProfileActivity", "✓ Resultados de búsqueda: ${searchResults.size}")
                
                // Mostrar resultados
                displayFavorites(searchResults)
                
            } catch (e: Exception) {
                Log.e("ProfileActivity", "✗ EXCEPCIÓN buscando favoritos: ${e.message}", e)
                Toast.makeText(this@ProfileActivity, "Error buscando favoritos: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    /**
     * Mostrar favoritos en el RecyclerView
     */
    private fun displayFavorites(favorites: List<Favorite>) {
        lifecycleScope.launch {
            // Convertir Favorite a Recipe para el adaptador
            val savedRecipes = favorites.map { favorite ->
                Recipe(
                    id = favorite.recipeId,
                    title = favorite.title,
                    description = favorite.description,
                    ingredients = "", // No guardamos ingredientes en favoritos
                    steps = "", // No guardamos pasos en favoritos
                    authorId = -1L, // No tenemos authorId en favoritos
                    authorName = favorite.authorName,
                    tags = favorite.tags,
                    cookingTime = favorite.cookingTime,
                    servings = favorite.servings,
                    rating = favorite.rating,
                    isPublished = true, // Asumimos que están publicadas
                    createdAt = favorite.createdAt,
                    updatedAt = favorite.createdAt
                )
            }
            
            val noLikedRecipesText = findViewById<TextView>(R.id.noLikedRecipesText)
            
            withContext(Dispatchers.Main) {
                if (savedRecipes.isEmpty()) {
                    Log.d("ProfileActivity", "No hay publicaciones guardadas - mostrando mensaje")
                    noLikedRecipesText.visibility = TextView.VISIBLE
                    findViewById<RecyclerView>(R.id.likedRecipesRecyclerView).visibility = RecyclerView.GONE
                } else {
                    Log.d("ProfileActivity", "✓ Mostrando ${savedRecipes.size} publicaciones guardadas")
                    noLikedRecipesText.visibility = TextView.GONE
                    findViewById<RecyclerView>(R.id.likedRecipesRecyclerView).visibility = RecyclerView.VISIBLE
                    likedRecipesAdapter.updateRecipes(savedRecipes)
                }
            }
        }
    }
    
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Guardar datos del usuario para preservarlos
        outState.putLong("saved_user_id", currentUserId)
        outState.putString("saved_user_email", currentUserEmail)
        Log.d("ProfileActivity", "Datos guardados en savedInstanceState - Email: '$currentUserEmail'")
    }
    
    private fun navigateToRecipeDetail(recipe: Recipe) {
        val intent = Intent(this, RecipeDetailActivity::class.java)
        intent.putExtra("recipe_id", recipe.id)
        intent.putExtra("user_id", currentUserId)
        // SIEMPRE pasar el email
        val emailToPass = currentUserEmail.ifEmpty { 
            // Intentar obtener del intent original si está vacío
            this.intent.getStringExtra("user_email") ?: "" 
        }
        intent.putExtra("user_email", emailToPass)
        startActivity(intent)
    }
    
    /**
     * Configura la navegación inferior
     */
    private fun setupBottomNavigation() {
        // Botón Inicio (navegar a MainActivity)
        val homeButton = findViewById<View>(R.id.homeButton)
        homeButton.setOnClickListener {
            // SIEMPRE pasar el email, incluso si está vacío
            val emailToPass = currentUserEmail.ifEmpty {
                // Intentar obtener del intent original si está vacío
                this.intent.getStringExtra("user_email") ?: ""
            }
            
            Log.d("ProfileActivity", "Navegando a MainActivity con email: '$emailToPass'")
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("user_id", currentUserId)
            intent.putExtra("user_email", emailToPass)
            
            // Si tenemos nombre de usuario, también pasarlo
            val userName = this.intent.getStringExtra("user_name")
            if (!userName.isNullOrEmpty()) {
                intent.putExtra("user_name", userName)
            }
            
            startActivity(intent)
        }

        // Botón Crear (navegar a CreateRecipeActivity)
        val createButton = findViewById<View>(R.id.createButton)
        createButton.setOnClickListener {
            if (currentUserId != -1L) {
                Log.d("ProfileActivity", "Navegando a CreateRecipeActivity con email: '$currentUserEmail'")
                val intent = Intent(this, CreateRecipeActivity::class.java)
                intent.putExtra("user_id", currentUserId)
                intent.putExtra("user_email", currentUserEmail)
                startActivity(intent)
            } else {
                Toast.makeText(this, "Debes iniciar sesión para crear recetas", Toast.LENGTH_SHORT).show()
            }
        }

        // Botón Perfil (ya estamos en esta pantalla)
        val profileButton = findViewById<View>(R.id.profileButton)
        profileButton.setOnClickListener {
            Toast.makeText(this, "Ya estás en tu perfil", Toast.LENGTH_SHORT).show()
            // Ya estamos en la pantalla de perfil, no hacer nada
        }
    }
}
package com.example.ejemplo2

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.ejemplo2.adapter.RecipeFeedAdapter
import com.example.ejemplo2.adapter.RecipeFeedItem
import com.example.ejemplo2.data.api.ApiService
import com.example.ejemplo2.utils.DatabaseResetHelper
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import com.example.ejemplo2.data.database.AppDatabase
import com.example.ejemplo2.data.entity.Favorite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    
    private var isUserLoggedIn = false
    private var currentUserId: Long = -1
    private var currentUserName: String = ""
    private var currentUserEmail: String = ""
    private lateinit var apiService: ApiService
    private lateinit var recipeFeedAdapter: RecipeFeedAdapter
    
    companion object {
        // Removido REQUEST_CODE_CREATE_PUBLICATION
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        Log.d("MainActivity", "onCreate iniciado")
        
        // Inicializar servicios
        apiService = ApiService(this)

        // Restaurar datos del usuario desde savedInstanceState si existe
        if (savedInstanceState != null) {
            currentUserId = savedInstanceState.getLong("saved_user_id", -1)
            currentUserName = savedInstanceState.getString("saved_user_name") ?: ""
            currentUserEmail = savedInstanceState.getString("saved_user_email") ?: ""
            isUserLoggedIn = savedInstanceState.getBoolean("saved_is_logged_in", false)
            Log.d("MainActivity", "Datos restaurados desde savedInstanceState - Email: '$currentUserEmail'")
        }

        checkUserLogin()

        setupRecipeFeed()
        setupSearchBar()
        setupBottomNavigation()

        // IMPORTANTE: Cargar recetas publicadas SIEMPRE, independientemente del estado de login
        // Las recetas deben mostrarse tanto para usuarios logueados como invitados
        // Esto se ejecuta después de setupRecipeFeed() para asegurar que el adapter esté inicializado
        loadRecipeFeed()
    }
    
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Guardar datos del usuario para preservarlos
        outState.putLong("saved_user_id", currentUserId)
        outState.putString("saved_user_name", currentUserName)
        outState.putString("saved_user_email", currentUserEmail)
        outState.putBoolean("saved_is_logged_in", isUserLoggedIn)
        Log.d("MainActivity", "Datos guardados en savedInstanceState - Email: '$currentUserEmail'")
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        Log.d("MainActivity", "onNewIntent - actualizando datos desde nuevo intent")
        // Actualizar datos cuando se abre MainActivity desde otra actividad
        checkUserLogin()
        // IMPORTANTE: Cargar recetas SIEMPRE después de actualizar datos del usuario
        // Esto asegura que las recetas se muestren incluso después del login
        loadRecipeFeed()
    }
    
    override fun onResume() {
        super.onResume()
        Log.d("MainActivity", "=== onResume - refrescando feed de recetas públicas ===")
        
        // Asegurar que tenemos el email más reciente del intent
        val emailFromIntent = intent.getStringExtra("user_email") ?: ""
        val userIdFromIntent = intent.getLongExtra("user_id", -1)
        
        // Actualizar datos del usuario si vienen en el intent
        if (emailFromIntent.isNotEmpty()) {
            if (emailFromIntent != currentUserEmail) {
                currentUserEmail = emailFromIntent
                Log.d("MainActivity", "✓ Email actualizado desde intent en onResume: '$currentUserEmail'")
            }
        }
        
        if (userIdFromIntent != -1L && userIdFromIntent != currentUserId) {
            currentUserId = userIdFromIntent
            isUserLoggedIn = true
            Log.d("MainActivity", "✓ User ID actualizado desde intent en onResume: $currentUserId")
        }
        
        val userNameFromIntent = intent.getStringExtra("user_name")
        if (!userNameFromIntent.isNullOrEmpty() && userNameFromIntent != currentUserName) {
            currentUserName = userNameFromIntent
            Log.d("MainActivity", "✓ User Name actualizado desde intent en onResume: '$currentUserName'")
        }
        
        Log.d("MainActivity", "Estado final - User ID: $currentUserId, Email: '$currentUserEmail', Name: '$currentUserName'")
        
        // IMPORTANTE: Cargar recetas publicadas SIEMPRE que se entre a la pantalla
        // Esto se ejecuta independientemente del estado de login (usuario o invitado)
        loadRecipeFeed()
    }
    
    private fun checkUserLogin() {
        // Verificar si hay datos de usuario en el intent
        val userId = intent.getLongExtra("user_id", -1)
        val userName = intent.getStringExtra("user_name")
        val userEmail = intent.getStringExtra("user_email") ?: ""
        
        // Si hay email en el intent, actualizarlo siempre
        if (userEmail.isNotEmpty()) {
            currentUserEmail = userEmail
            Log.d("MainActivity", "Email actualizado desde intent: '$currentUserEmail'")
        }
        
        if (userId != -1L && !userName.isNullOrBlank()) {
            val wasLoggedIn = isUserLoggedIn
            isUserLoggedIn = true
            currentUserId = userId
            currentUserName = userName
            // Preservar el email si ya existía y no viene en el intent
            if (userEmail.isEmpty() && currentUserEmail.isNotEmpty()) {
                Log.d("MainActivity", "Preservando email existente: '$currentUserEmail'")
            } else if (userEmail.isNotEmpty()) {
                currentUserEmail = userEmail
            }
            
            Log.d("MainActivity", "Usuario logueado: $userName (ID: $userId, Email: '$currentUserEmail')")
            
            // Si es un login nuevo (no estaba logueado antes), asegurar recarga del feed
            if (!wasLoggedIn) {
                Log.d("MainActivity", "✓ Login nuevo detectado - el feed se recargará automáticamente")
            }

            updateUserGreeting(userName, currentUserEmail)
            
            // Solo mostrar toast si es primera vez que entra
            if (intent.getBooleanExtra("first_login", false)) {
                Toast.makeText(this, "¡Bienvenido, $userName!", Toast.LENGTH_SHORT).show()
            }
        } else {
            // Si no hay datos en el intent pero tenemos datos guardados, mantenerlos
            if (currentUserId != -1L && currentUserName.isNotEmpty()) {
                isUserLoggedIn = true
                Log.d("MainActivity", "Usuario logueado (datos preservados): $currentUserName (ID: $currentUserId, Email: '$currentUserEmail')")
                updateUserGreeting(currentUserName, currentUserEmail)
            } else {
                isUserLoggedIn = false
                Log.d("MainActivity", "Usuario no logueado - modo invitado")
                // Ocultar saludo al usuario si no está logueado
                hideUserGreeting()
            }
        }
    }
    

    private fun setupBottomNavigation() {
        Log.d("MainActivity", "Configurando navegación inferior")
        
        // Botón Inicio
        val homeButton = findViewById<View>(R.id.homeButton)
        homeButton.setOnClickListener {
            Log.d("MainActivity", "Botón inicio clickeado - ya estamos en inicio")
            Toast.makeText(this, "Ya estás en la pantalla de inicio", Toast.LENGTH_SHORT).show()
            // Ya estamos en la pantalla de inicio, no hacer nada
            // O podríamos refrescar la pantalla si es necesario
        }

        // Botón Crear
        val createButton = findViewById<View>(R.id.createButton)
        createButton.setOnClickListener {
            Log.d("MainActivity", "Botón crear clickeado - navegando a CreateRecipeActivity")
            
            if (isUserLoggedIn && currentUserId != -1L) {
                // SIEMPRE pasar el email, incluso si está vacío
                val emailToPass = if (currentUserEmail.isNotEmpty()) {
                    currentUserEmail
                } else {
                    // Intentar obtener el email desde el intent original
                    intent.getStringExtra("user_email") ?: ""
                }
                
                Log.d("MainActivity", "Navegando a CreateRecipeActivity con email: '$emailToPass'")
                Toast.makeText(this, "Navegando a crear receta", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, CreateRecipeActivity::class.java)
                // Pasar datos del usuario para la receta - SIEMPRE incluir el email
                intent.putExtra("user_id", currentUserId)
                if (currentUserName.isNotEmpty()) {
                    intent.putExtra("user_name", currentUserName)
                }
                intent.putExtra("user_email", emailToPass) // SIEMPRE pasar el email
                startActivity(intent)
            } else {
                Toast.makeText(this, "Debes iniciar sesión para crear recetas", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, LoginActivity::class.java)
                startActivity(intent)
            }
        }

        // Botón Perfil
        val profileButton = findViewById<View>(R.id.profileButton)
        profileButton.setOnClickListener {
            Log.d("MainActivity", "Botón perfil clickeado")
            
            if (isUserLoggedIn && currentUserId != -1L) {
                // SIEMPRE pasar el email, incluso si está vacío
                val emailToPass = if (currentUserEmail.isNotEmpty()) {
                    currentUserEmail
                } else {
                    // Intentar obtener el email desde el intent original
                    intent.getStringExtra("user_email") ?: ""
                }
                
                Log.d("MainActivity", "Navegando a ProfileActivity con email: '$emailToPass'")
                Toast.makeText(this, "Navegando a perfil", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, ProfileActivity::class.java)
                // Pasar datos del usuario - SIEMPRE incluir el email
                intent.putExtra("user_id", currentUserId)
                intent.putExtra("user_email", emailToPass)
                if (currentUserName.isNotEmpty()) {
                    intent.putExtra("user_name", currentUserName)
                }
                startActivity(intent)
            } else {
                Toast.makeText(this, "Debes iniciar sesión para ver tu perfil", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, LoginActivity::class.java)
                startActivity(intent)
            }
        }
        
        Log.d("MainActivity", "Navegación inferior configurada")
    }

    private fun updateUserGreeting(userName: String, userEmail: String) {
        val userNameText = findViewById<TextView>(R.id.userNameText)
        val userEmailText = findViewById<TextView>(R.id.userEmailText)
        val userGreetingLayout = findViewById<View>(R.id.userGreetingLayout)
        
        userNameText.text = userName
        userEmailText.text = userEmail
        userGreetingLayout.visibility = View.VISIBLE
        
        Log.d("MainActivity", "Saludo actualizado: $userName ($userEmail)")
    }

    private fun hideUserGreeting() {
        val userGreetingLayout = findViewById<View>(R.id.userGreetingLayout)
        userGreetingLayout.visibility = View.GONE
        
        Log.d("MainActivity", "Saludo ocultado - usuario no logueado")
    }

    /**
     * Configurar barra de búsqueda
     */
    private fun setupSearchBar() {
        val searchEditText = findViewById<android.widget.EditText>(R.id.searchEditText)
        val searchButton = findViewById<android.widget.ImageButton>(R.id.searchButton)
        
        // Listener para el botón de búsqueda
        searchButton.setOnClickListener {
            performSearch()
        }
        
        // Listener para cuando se presiona Enter en el EditText
        searchEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                true
            } else {
                false
            }
        }
        
        Log.d("MainActivity", "Barra de búsqueda configurada")
    }
    
    /**
     * Realizar búsqueda de recetas
     */
    private fun performSearch() {
        val searchEditText = findViewById<android.widget.EditText>(R.id.searchEditText)
        val query = searchEditText.text.toString().trim()
        
        if (query.isEmpty()) {
            Toast.makeText(this, "Ingresa un término de búsqueda", Toast.LENGTH_SHORT).show()
            return
        }
        
        Log.d("MainActivity", "=== REALIZANDO BÚSQUEDA ===")
        Log.d("MainActivity", "Query: '$query'")
        
        // Ocultar teclado
        val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(searchEditText.windowToken, 0)
        
        lifecycleScope.launch {
            try {
                val result = apiService.searchRecipes(query)
                
                if (result.isSuccess) {
                    val recipes = result.getOrNull() ?: emptyList()
                    Log.d("MainActivity", "✓ Recetas encontradas: ${recipes.size}")
                    
                    if (recipes.isNotEmpty()) {
                        // Convertir RecipeFeedData a RecipeFeedItem
                        val feedItems = coroutineScope {
                            recipes.map { recipeData ->
                                async { mapToRecipeFeedItem(recipeData) }
                            }.awaitAll()
                        }
                        withContext(Dispatchers.Main) {
                            recipeFeedAdapter.updateRecipes(feedItems)
                            Toast.makeText(this@MainActivity, "${recipes.size} receta(s) encontrada(s)", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            recipeFeedAdapter.updateRecipes(emptyList())
                            Toast.makeText(this@MainActivity, "No se encontraron recetas", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Log.e("MainActivity", "✗ Error buscando recetas: ${result.exceptionOrNull()?.message}")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Error buscando recetas: ${result.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "✗ EXCEPCIÓN en performSearch: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error buscando recetas: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun setupRecipeFeed() {
        val recyclerView = findViewById<RecyclerView>(R.id.recipesRecyclerView)
        
        // Asegurar que el RecyclerView esté visible desde el inicio
        recyclerView.visibility = View.VISIBLE
        Log.d("MainActivity", "RecyclerView configurado - Visibilidad inicial: ${recyclerView.visibility == View.VISIBLE}")
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        
        recipeFeedAdapter = RecipeFeedAdapter(
            recipes = emptyList(),
            onItemClick = { recipe ->
                // Navegar a detalles de la receta
                // SIEMPRE obtener el email del usuario activo para poder comentar
                var emailToPass = currentUserEmail
                
                // Si el email está vacío, intentar obtenerlo de diferentes fuentes
                if (emailToPass.isEmpty()) {
                    // Primero del intent actual
                    emailToPass = this.intent.getStringExtra("user_email") ?: ""
                    
                    // Si aún está vacío y el usuario está logueado, intentar obtenerlo del sistema
                    if (emailToPass.isEmpty() && isUserLoggedIn && currentUserId != -1L) {
                        // El email debería estar en currentUserEmail si el usuario está logueado
                        Log.w("MainActivity", "⚠️ Email vacío pero usuario está logueado - Usuario ID: $currentUserId")
                    }
                }
                
                Log.d("MainActivity", "=== NAVEGANDO A RECETA ===")
                Log.d("MainActivity", "Recipe ID: ${recipe.id}")
                Log.d("MainActivity", "User ID: $currentUserId")
                Log.d("MainActivity", "User Email: '$emailToPass'")
                Log.d("MainActivity", "User Name: '$currentUserName'")
                Log.d("MainActivity", "Is Logged In: $isUserLoggedIn")
                
                val intent = Intent(this, RecipeDetailActivity::class.java)
                intent.putExtra("recipe_id", recipe.id)
                intent.putExtra("user_id", currentUserId)
                intent.putExtra("user_email", emailToPass) // SIEMPRE pasar el correo (puede estar vacío si no hay usuario)
                if (currentUserName.isNotEmpty()) {
                    intent.putExtra("user_name", currentUserName)
                }
                startActivity(intent)
            },
            onVoteClick = { recipe, position, voteType ->
                // Manejar voto de la receta (like o dislike)
                handleRecipeVote(recipe, position, voteType)
            },
            onFavoriteClick = { recipe, position, isFavorite ->
                // Manejar favorito de la receta
                handleRecipeFavorite(recipe, position, isFavorite)
            }
        )
        
        recyclerView.adapter = recipeFeedAdapter
        Log.d("MainActivity", "Adapter asignado al RecyclerView - Item count: ${recipeFeedAdapter.itemCount}")
    }
    

    /**
     * Carga el feed de recetas publicadas desde MySQL.
     * IMPORTANTE: Esta función se ejecuta SIEMPRE, independientemente de si hay un usuario logueado o no.
     * Las recetas deben mostrarse tanto para usuarios autenticados como para invitados (modo público).
     */
    private fun loadRecipeFeed() {
        // Verificar que el adapter esté inicializado antes de cargar
        if (!::recipeFeedAdapter.isInitialized) {
            Log.w("MainActivity", "⚠️ Adapter no inicializado aún - inicializando...")
            setupRecipeFeed()
        }
        
        lifecycleScope.launch {
            try {
                Log.d("MainActivity", "=== CARGANDO FEED DE RECETAS (modo público - sin importar login) ===")
                Log.d("MainActivity", "Estado de login: ${if (isUserLoggedIn) "Usuario logueado" else "Modo invitado"}")
                Log.d("MainActivity", "Adapter inicializado: ${::recipeFeedAdapter.isInitialized}")
                // Cargar SIEMPRE las últimas recetas publicadas desde MySQL
                // NO requiere autenticación - las recetas son públicas
                val result = apiService.getRecipesFeed()
                
                if (result.isSuccess) {
                    val recipesData = result.getOrNull() ?: emptyList()
                    Log.d("MainActivity", "Recetas publicadas cargadas: ${recipesData.size}")
                    
                    if (recipesData.isEmpty()) {
                        Log.w("MainActivity", "No llegaron recetas. Reintentando en 2s...")
                        // Primer reintento automático si viene vacío
                        withContext(Dispatchers.IO) {
                            kotlinx.coroutines.delay(2000)
                        }
                        val retry = apiService.getRecipesFeed()
                        if (retry.isSuccess) {
                            val retryData = retry.getOrNull() ?: emptyList()
                            if (retryData.isEmpty()) {
                                Log.d("MainActivity", "No hay recetas publicadas en la base de datos tras reintento")
                                withContext(Dispatchers.Main) { recipeFeedAdapter.updateRecipes(emptyList()) }
                                return@launch
                            } else {
                                Log.d("MainActivity", "Reintento exitoso: ${retryData.size} recetas")
                                // Sobrescribir recipesData para continuar el flujo normal
                                val feedItemsRetry = coroutineScope {
                                    retryData.map { recipeData ->
                                        async { mapToRecipeFeedItem(recipeData.copy(voteType = -1)) }
                                    }.awaitAll()
                                }
                                withContext(Dispatchers.Main) {
                                    val recyclerView = findViewById<RecyclerView>(R.id.recipesRecyclerView)
                                    if (recyclerView.adapter == null) recyclerView.adapter = recipeFeedAdapter
                                    recipeFeedAdapter.updateRecipes(feedItemsRetry)
                                    recyclerView.visibility = View.VISIBLE
                                }
                                return@launch
                            }
                        } else {
                            Log.e("MainActivity", "Error en reintento: ${retry.exceptionOrNull()?.message}")
                            withContext(Dispatchers.Main) { recipeFeedAdapter.updateRecipes(emptyList()) }
                            return@launch
                        }
                    }

                    val feedItems = coroutineScope {
                        recipesData.map { recipeData ->
                            async { mapToRecipeFeedItem(recipeData.copy(voteType = -1)) }
                        }.awaitAll()
                    }
                    
                    // Actualizar el adapter en el hilo principal
                    withContext(Dispatchers.Main) {
                        Log.d("MainActivity", "=== ACTUALIZANDO FEED CON ${feedItems.size} RECETAS ===")
                        
                        // Verificar que el RecyclerView está configurado y visible ANTES de actualizar
                        val recyclerView = findViewById<RecyclerView>(R.id.recipesRecyclerView)
                        
                        if (recyclerView.adapter == null) {
                            Log.e("MainActivity", "ERROR: RecyclerView no tiene adapter asignado - asignando...")
                            recyclerView.adapter = recipeFeedAdapter
                        }
                        
                        // Actualizar el adaptador con las nuevas recetas
                        recipeFeedAdapter.updateRecipes(feedItems)
                        
                        // Asegurar que el RecyclerView esté visible
                        recyclerView.visibility = View.VISIBLE
                        
                        // Forzar un redraw del RecyclerView
                        recyclerView.invalidate()
                        recyclerView.requestLayout()
                        
                        Log.d("MainActivity", "✓ Feed actualizado exitosamente")
                        Log.d("MainActivity", "  - Total items en adapter: ${recipeFeedAdapter.itemCount}")
                        Log.d("MainActivity", "  - RecyclerView visible: ${recyclerView.visibility == View.VISIBLE}")
                        Log.d("MainActivity", "  - RecyclerView adapter items: ${recyclerView.adapter?.itemCount}")
                        Log.d("MainActivity", "  - RecyclerView height: ${recyclerView.height}")
                        Log.d("MainActivity", "  - RecyclerView width: ${recyclerView.width}")
                        
                        // Verificar si hay recetas pero no se muestran
                        if (feedItems.isNotEmpty() && recyclerView.adapter?.itemCount == 0) {
                            Log.e("MainActivity", "⚠️ PROBLEMA: Hay ${feedItems.size} recetas pero adapter muestra 0 items")
                        }
                    }
                } else {
                    val error = result.exceptionOrNull()
                    Log.e("MainActivity", "❌ ERROR cargando feed: ${error?.message}")
                    Log.e("MainActivity", "Stack trace: ${error?.stackTrace?.joinToString("\n")}")
                    // Reintento automático en error transitorio
                    withContext(Dispatchers.IO) { kotlinx.coroutines.delay(2000) }
                    val retry = apiService.getRecipesFeed()
                    if (retry.isSuccess) {
                        val recipesData = retry.getOrNull() ?: emptyList()
                        Log.d("MainActivity", "Reintento exitoso: ${recipesData.size} recetas")
                        val feedItems = coroutineScope {
                            recipesData.map { recipeData ->
                                async { mapToRecipeFeedItem(recipeData.copy(voteType = -1)) }
                            }.awaitAll()
                        }
                        withContext(Dispatchers.Main) {
                            val recyclerView = findViewById<RecyclerView>(R.id.recipesRecyclerView)
                            if (recyclerView.adapter == null) recyclerView.adapter = recipeFeedAdapter
                            recipeFeedAdapter.updateRecipes(feedItems)
                            recyclerView.visibility = View.VISIBLE
                        }
                        return@launch
                    }
                    // Mostrar mensaje de error pero mantener el RecyclerView visible (puede tener recetas previas)
                    withContext(Dispatchers.Main) {
                        val recyclerView = findViewById<RecyclerView>(R.id.recipesRecyclerView)
                        recyclerView.visibility = View.VISIBLE // Mantener visible incluso si hay error
                        Toast.makeText(this@MainActivity, "Error cargando recetas: ${error?.message}", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "❌ EXCEPCIÓN en loadRecipeFeed", e)
                Log.e("MainActivity", "Stack trace: ${e.stackTrace.joinToString("\n")}")
                // Mantener RecyclerView visible incluso si hay error
                withContext(Dispatchers.Main) {
                    val recyclerView = findViewById<RecyclerView>(R.id.recipesRecyclerView)
                    recyclerView.visibility = View.VISIBLE
                    Toast.makeText(this@MainActivity, "Error cargando recetas: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun handleRecipeVote(recipe: RecipeFeedItem, position: Int, voteType: Int) {
        // Verificar si el usuario está logueado
        if (!isUserLoggedIn || currentUserId == -1L) {
            Log.e("MainActivity", "⚠️ Intento de voto sin usuario logueado - isUserLoggedIn: $isUserLoggedIn, currentUserId: $currentUserId")
            Toast.makeText(this, "Debes iniciar sesión para votar", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Verificar que tenemos el email necesario para obtener el ID de MySQL
        if (currentUserEmail.isEmpty()) {
            Log.e("MainActivity", "✗ ERROR: Email del usuario no disponible")
            Toast.makeText(this, "Error: Información de usuario incompleta", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Guardar el estado anterior para poder revertir si falla
        val previousVoteType = recipe.voteType
        
        // Verificar que tenemos los IDs necesarios
        Log.d("MainActivity", "=== INICIANDO VOTO ===")
        Log.d("MainActivity", "Recipe ID: ${recipe.id}")
        Log.d("MainActivity", "Current User Email: $currentUserEmail")
        Log.d("MainActivity", "Vote Type: $voteType")
        Log.d("MainActivity", "Recipe Title: ${recipe.title}")
        
        if (recipe.id <= 0) {
            Log.e("MainActivity", "✗ ERROR: Recipe ID inválido: ${recipe.id}")
            Toast.makeText(this, "Error: ID de receta inválido", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Actualizar UI inmediatamente
        recipeFeedAdapter.updateVoteStatus(position, voteType)
        
        // Enviar voteType directamente al servidor (-1 = quitar voto, 0 = dislike, 1 = like)
        // El servidor manejará la lógica de eliminación cuando voteType = -1
        val serverVoteType = voteType
        
        val voteAction = when (voteType) {
            1 -> "like agregado"
            0 -> "dislike agregado"
            else -> "voto removido"
        }
        Log.d("MainActivity", "$voteAction para receta: ${recipe.title}")
        
        lifecycleScope.launch {
            try {
                // PRIMERO: Obtener el ID de MySQL del usuario usando el email
                Log.d("MainActivity", "Obteniendo ID de MySQL del usuario con email: $currentUserEmail")
                val userResult = apiService.getUserByEmail(currentUserEmail)
                
                if (!userResult.isSuccess) {
                    Log.e("MainActivity", "✗ Error obteniendo usuario desde MySQL: ${userResult.exceptionOrNull()?.message}")
                    // Revertir el cambio en la UI si falla
                    recipeFeedAdapter.updateVoteStatus(position, previousVoteType)
                    Toast.makeText(this@MainActivity, "Error obteniendo información del usuario", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                val mysqlUser = userResult.getOrNull()
                if (mysqlUser == null) {
                    Log.e("MainActivity", "✗ Usuario no encontrado en MySQL con email: $currentUserEmail")
                    // Revertir el cambio en la UI si falla
                    recipeFeedAdapter.updateVoteStatus(position, previousVoteType)
                    Toast.makeText(this@MainActivity, "Error: Usuario no encontrado", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                val mysqlUserId = mysqlUser.id
                Log.d("MainActivity", "✓ ID de MySQL obtenido: $mysqlUserId")
                Log.d("MainActivity", "Llamando a syncVoteToMySQL con: recipeId=${recipe.id}, userId=$mysqlUserId, voteType=$serverVoteType")
                
                // SEGUNDO: Enviar el voto con el ID de MySQL
                val result = apiService.syncVoteToMySQL(recipe.id, mysqlUserId, serverVoteType)
                
                if (result.isSuccess) {
                    Log.d("MainActivity", "✓ Voto actualizado exitosamente en recipe_votes: recipe_id=${recipe.id}, user_id=$mysqlUserId, vote_type=$serverVoteType")
                } else {
                    Log.e("MainActivity", "✗ Error actualizando voto en recipe_votes: ${result.exceptionOrNull()?.message}")
                    // Revertir el cambio en la UI si falla
                    recipeFeedAdapter.updateVoteStatus(position, previousVoteType)
                    Toast.makeText(this@MainActivity, "Error actualizando voto: ${result.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "✗ EXCEPCIÓN en handleRecipeVote: ${e.message}", e)
                Log.e("MainActivity", "Stack trace: ${e.stackTrace.joinToString("\n")}")
                // Revertir el cambio en la UI si falla
                recipeFeedAdapter.updateVoteStatus(position, previousVoteType)
                Toast.makeText(this@MainActivity, "Error actualizando voto: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun handleRecipeFavorite(recipe: RecipeFeedItem, position: Int, isFavorite: Boolean) {
        // Verificar si el usuario está logueado
        if (!isUserLoggedIn || currentUserId == -1L) {
            Toast.makeText(this, "Debes iniciar sesión para guardar favoritos", Toast.LENGTH_SHORT).show()
            // Revertir el cambio en la UI
            recipeFeedAdapter.updateFavoriteStatus(position, false)
            return
        }
        
        // Guardar el estado anterior para poder revertir si falla
        val previousFavoriteState = recipe.isFavorite
        
        // Actualizar UI inmediatamente
        recipeFeedAdapter.updateFavoriteStatus(position, isFavorite)
        
        lifecycleScope.launch {
            try {
                val db = AppDatabase.getDatabase(this@MainActivity)
                val favoriteDao = db.favoriteDao()
                
                if (isFavorite) {
                    // Guardar como favorito
                    val favorite = Favorite(
                        recipeId = recipe.id,
                        userId = currentUserId,
                        title = recipe.title,
                        description = recipe.description,
                        authorName = recipe.authorName,
                        authorAlias = recipe.authorAlias,
                        cookingTime = recipe.cookingTime,
                        servings = recipe.servings,
                        rating = recipe.rating,
                        tags = recipe.tags,
                        imageData = recipe.imageData,
                        createdAt = System.currentTimeMillis()
                    )
                    
                    withContext(Dispatchers.IO) {
                        favoriteDao.insertFavorite(favorite)
                    }
                    
                    Log.d("MainActivity", "✓ Receta guardada como favorito: ${recipe.title}")
                    Toast.makeText(this@MainActivity, "Agregado a favoritos", Toast.LENGTH_SHORT).show()
                } else {
                    // Eliminar de favoritos
                    withContext(Dispatchers.IO) {
                        favoriteDao.deleteFavoriteByRecipeId(currentUserId, recipe.id)
                    }
                    
                    Log.d("MainActivity", "✓ Receta eliminada de favoritos: ${recipe.title}")
                    Toast.makeText(this@MainActivity, "Eliminado de favoritos", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "✗ Error manejando favorito: ${e.message}", e)
                // Revertir el cambio en la UI si falla
                recipeFeedAdapter.updateFavoriteStatus(position, previousFavoriteState)
                Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    /**
     * Verificar si una receta es favorita del usuario actual
     */
    private suspend fun isRecipeFavorite(recipeId: Long, userId: Long): Boolean {
        return if (userId != -1L) {
            try {
                val db = AppDatabase.getDatabase(this)
                val favoriteDao = db.favoriteDao()
                withContext(Dispatchers.IO) {
                    favoriteDao.isFavorite(userId, recipeId) > 0
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error verificando favorito: ${e.message}", e)
                false
            }
        } else {
            false
        }
    }
    
    /**
     * Convertir RecipeFeedData a RecipeFeedItem con verificación de favorito
     */
    private suspend fun mapToRecipeFeedItem(recipeData: ApiService.RecipeFeedData, userId: Long = currentUserId): RecipeFeedItem {
        val isFavorite = if (userId != -1L) {
            isRecipeFavorite(recipeData.id, userId)
        } else {
            false
        }
        
        return RecipeFeedItem(
            id = recipeData.id,
            title = recipeData.title,
            description = recipeData.description ?: "",
            authorName = recipeData.authorName,
            authorAlias = recipeData.authorAlias ?: recipeData.authorName,
            cookingTime = recipeData.cookingTime,
            servings = recipeData.servings,
            rating = recipeData.rating,
            tags = recipeData.tags,
            imageData = recipeData.imageData,
            images = recipeData.images,
            voteType = recipeData.voteType,
            isFavorite = isFavorite
        )
    }
    
    /**
     * Función para resetear solo SQLite
     */
    private fun resetSQLiteDatabase() {
        lifecycleScope.launch {
            try {
                Log.d("MainActivity", "=== INICIANDO RESET DE SQLite ===")
                Toast.makeText(this@MainActivity, "Reseteando SQLite...", Toast.LENGTH_SHORT).show()
                
                val result = DatabaseResetHelper.truncateSQLiteTables(this@MainActivity)
                
                if (result.isSuccess) {
                    Log.d("MainActivity", "✓ SQLite: ${result.getOrNull()}")
                    Toast.makeText(
                        this@MainActivity,
                        "✓ SQLite resetado exitosamente",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    val error = result.exceptionOrNull()
                    Log.e("MainActivity", "Error reseteando SQLite: ${error?.message}")
                    Toast.makeText(
                        this@MainActivity,
                        "Error: ${error?.message ?: "Error desconocido"}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error en resetSQLiteDatabase: ${e.message}", e)
                Toast.makeText(
                    this@MainActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    /**
     * Función para resetear todas las tablas (SQLite y MySQL)
     * Para usar, llama a esta función desde cualquier lugar, por ejemplo:
     * resetAllDatabases()
     */
    private fun resetAllDatabases() {
        lifecycleScope.launch {
            try {
                Log.d("MainActivity", "=== INICIANDO RESET DE BASES DE DATOS ===")
                Toast.makeText(this@MainActivity, "Reseteando bases de datos...", Toast.LENGTH_SHORT).show()
                
                val result = DatabaseResetHelper.resetAllDatabases(this@MainActivity, apiService)
                
                if (result.isSuccess) {
                    val (sqliteMsg, mysqlMsg) = result.getOrNull()!!
                    Log.d("MainActivity", "✓ SQLite: $sqliteMsg")
                    Log.d("MainActivity", "✓ MySQL: $mysqlMsg")
                    
                    Toast.makeText(
                        this@MainActivity,
                        "✓ Bases de datos reseteadas exitosamente",
                        Toast.LENGTH_LONG
                    ).show()
                    
                    // Recargar el feed después del reset
                    loadRecipeFeed()
                } else {
                    val error = result.exceptionOrNull()
                    Log.e("MainActivity", "Error reseteando bases de datos: ${error?.message}")
                    Toast.makeText(
                        this@MainActivity,
                        "Error: ${error?.message ?: "Error desconocido"}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error en resetAllDatabases: ${e.message}", e)
                Toast.makeText(
                    this@MainActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_LONG
                    ).show()
            }
        }
    }
}
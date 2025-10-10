package com.example.ejemplo2

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.ejemplo2.service.MigrationService
import kotlinx.coroutines.launch

/**
 * Actividad para manejar la migración de datos de SQLite a MySQL
 */
class MigrationActivity : AppCompatActivity() {
    
    private lateinit var migrationService: MigrationService
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_migration)
        
        migrationService = MigrationService(this)
        
        setupUI()
        setupClickListeners()
    }
    
    private fun setupUI() {
        // Configurar la barra de herramientas
        findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar).apply {
            setSupportActionBar(this)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.title = "Migración de Datos"
        }
        
        // Configurar estado inicial
        updateUIState(MigrationState.IDLE)
    }
    
    private fun setupClickListeners() {
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCheckApi).setOnClickListener {
            checkApiAvailability()
        }
        
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSetupDatabase).setOnClickListener {
            setupDatabase()
        }
        
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnMigrateUsers).setOnClickListener {
            migrateUsers()
        }
        
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnFullMigration).setOnClickListener {
            executeFullMigration()
        }
        
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnViewResults).setOnClickListener {
            viewMigrationResults()
        }
    }
    
    private fun checkApiAvailability() {
        updateUIState(MigrationState.CHECKING_API)
        
        lifecycleScope.launch {
            try {
                val result = migrationService.checkApiAvailability()
                result.fold(
                    onSuccess = {
                        updateUIState(MigrationState.API_AVAILABLE)
                        showMessage("✅ API disponible y funcionando")
                    },
                    onFailure = { error ->
                        updateUIState(MigrationState.API_ERROR)
                        showMessage("❌ Error: ${error.message}")
                    }
                )
            } catch (e: Exception) {
                updateUIState(MigrationState.API_ERROR)
                showMessage("❌ Error inesperado: ${e.message}")
            }
        }
    }
    
    private fun setupDatabase() {
        updateUIState(MigrationState.SETTING_UP_DATABASE)
        
        lifecycleScope.launch {
            try {
                val result = migrationService.setupMySQLDatabase()
                result.fold(
                    onSuccess = {
                        updateUIState(MigrationState.DATABASE_READY)
                        showMessage("✅ Base de datos configurada exitosamente")
                    },
                    onFailure = { error ->
                        updateUIState(MigrationState.DATABASE_ERROR)
                        showMessage("❌ Error configurando base de datos: ${error.message}")
                    }
                )
            } catch (e: Exception) {
                updateUIState(MigrationState.DATABASE_ERROR)
                showMessage("❌ Error inesperado: ${e.message}")
            }
        }
    }
    
    private fun migrateUsers() {
        updateUIState(MigrationState.MIGRATING)
        showMessage("🔄 Iniciando migración...")
        
        lifecycleScope.launch {
            try {
                showMessage("📱 Obteniendo usuarios de SQLite...")
                
                // Primero verificar si hay usuarios
                val usersResult = migrationService.getAllSQLiteUsers()
                if (usersResult.isFailure) {
                    updateUIState(MigrationState.MIGRATION_ERROR)
                    showMessage("❌ Error obteniendo usuarios: ${usersResult.exceptionOrNull()?.message}")
                    return@launch
                }
                
                val users = usersResult.getOrNull() ?: emptyList()
                showMessage("📊 Usuarios encontrados: ${users.size}")
                
                if (users.isEmpty()) {
                    updateUIState(MigrationState.MIGRATION_COMPLETED)
                    showMessage("ℹ️ No hay usuarios para migrar")
                    return@launch
                }
                
                showMessage("🔄 Migrando ${users.size} usuarios...")
                val result = migrationService.migrateAllUsers()
                result.fold(
                    onSuccess = { migrationResult ->
                        updateUIState(MigrationState.MIGRATION_COMPLETED)
                        showMigrationResults(migrationResult)
                    },
                    onFailure = { error ->
                        updateUIState(MigrationState.MIGRATION_ERROR)
                        showMessage("❌ Error en la migración: ${error.message}")
                    }
                )
            } catch (e: Exception) {
                updateUIState(MigrationState.MIGRATION_ERROR)
                showMessage("❌ Error inesperado: ${e.message}")
            }
        }
    }
    
    private fun executeFullMigration() {
        updateUIState(MigrationState.FULL_MIGRATION)
        
        lifecycleScope.launch {
            try {
                val result = migrationService.executeFullMigration()
                result.fold(
                    onSuccess = { migrationResult ->
                        updateUIState(MigrationState.MIGRATION_COMPLETED)
                        showMigrationResults(migrationResult)
                    },
                    onFailure = { error ->
                        updateUIState(MigrationState.MIGRATION_ERROR)
                        showMessage("❌ Error en la migración completa: ${error.message}")
                    }
                )
            } catch (e: Exception) {
                updateUIState(MigrationState.MIGRATION_ERROR)
                showMessage("❌ Error inesperado: ${e.message}")
            }
        }
    }
    
    private fun viewMigrationResults() {
        lifecycleScope.launch {
            try {
                val result = migrationService.getMySQLUsers()
                result.fold(
                    onSuccess = { users ->
                        showMessage("✅ Usuarios en MySQL: ${users.size}")
                        // Aquí podrías abrir una nueva actividad para mostrar los usuarios
                    },
                    onFailure = { error ->
                        showMessage("❌ Error obteniendo usuarios: ${error.message}")
                    }
                )
            } catch (e: Exception) {
                showMessage("❌ Error inesperado: ${e.message}")
            }
        }
    }
    
    private fun updateUIState(state: MigrationState) {
        val btnCheckApi = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCheckApi)
        val btnSetupDatabase = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSetupDatabase)
        val btnMigrateUsers = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnMigrateUsers)
        val btnFullMigration = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnFullMigration)
        val btnViewResults = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnViewResults)
        val progressBar = findViewById<android.widget.ProgressBar>(R.id.progressBar)
        val tvStatus = findViewById<android.widget.TextView>(R.id.tvStatus)
        
        when (state) {
            MigrationState.IDLE -> {
                btnCheckApi.isEnabled = true
                btnSetupDatabase.isEnabled = false
                btnMigrateUsers.isEnabled = false
                btnFullMigration.isEnabled = true
                btnViewResults.isEnabled = false
                progressBar.visibility = android.view.View.GONE
                tvStatus.text = "Listo para comenzar la migración"
            }
            
            MigrationState.CHECKING_API -> {
                btnCheckApi.isEnabled = false
                progressBar.visibility = android.view.View.VISIBLE
                tvStatus.text = "Verificando disponibilidad de la API..."
            }
            
            MigrationState.API_AVAILABLE -> {
                btnCheckApi.isEnabled = false
                btnSetupDatabase.isEnabled = true
                progressBar.visibility = android.view.View.GONE
                tvStatus.text = "✅ API disponible"
            }
            
            MigrationState.API_ERROR -> {
                btnCheckApi.isEnabled = true
                progressBar.visibility = android.view.View.GONE
                tvStatus.text = "❌ Error de API"
            }
            
            MigrationState.SETTING_UP_DATABASE -> {
                btnSetupDatabase.isEnabled = false
                progressBar.visibility = android.view.View.VISIBLE
                tvStatus.text = "Configurando base de datos MySQL..."
            }
            
            MigrationState.DATABASE_READY -> {
                btnSetupDatabase.isEnabled = false
                btnMigrateUsers.isEnabled = true
                progressBar.visibility = android.view.View.GONE
                tvStatus.text = "✅ Base de datos lista"
            }
            
            MigrationState.DATABASE_ERROR -> {
                btnSetupDatabase.isEnabled = true
                progressBar.visibility = android.view.View.GONE
                tvStatus.text = "❌ Error de base de datos"
            }
            
            MigrationState.MIGRATING -> {
                btnMigrateUsers.isEnabled = false
                progressBar.visibility = android.view.View.VISIBLE
                tvStatus.text = "Migrando usuarios..."
            }
            
            MigrationState.FULL_MIGRATION -> {
                btnFullMigration.isEnabled = false
                progressBar.visibility = android.view.View.VISIBLE
                tvStatus.text = "Ejecutando migración completa..."
            }
            
            MigrationState.MIGRATION_COMPLETED -> {
                btnMigrateUsers.isEnabled = false
                btnFullMigration.isEnabled = false
                btnViewResults.isEnabled = true
                progressBar.visibility = android.view.View.GONE
                tvStatus.text = "✅ Migración completada"
            }
            
            MigrationState.MIGRATION_ERROR -> {
                btnMigrateUsers.isEnabled = true
                btnFullMigration.isEnabled = true
                progressBar.visibility = android.view.View.GONE
                tvStatus.text = "❌ Error en la migración"
            }
        }
    }
    
    private fun showMigrationResults(result: com.example.ejemplo2.service.MigrationResult) {
        val message = buildString {
            appendLine("📊 Resultados de la Migración:")
            appendLine("• Total de usuarios: ${result.totalUsers}")
            appendLine("• Usuarios migrados: ${result.migratedUsers}")
            appendLine("• Usuarios fallidos: ${result.failedUsers}")
            appendLine("• Tasa de éxito: ${(result.successRate * 100).toInt()}%")
            
            if (result.errors.isNotEmpty()) {
                appendLine("\n❌ Errores:")
                result.errors.forEach { error ->
                    appendLine("• $error")
                }
            }
        }
        
        showMessage(message)
    }
    
    private fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}

/**
 * Estados de la migración
 */
enum class MigrationState {
    IDLE,
    CHECKING_API,
    API_AVAILABLE,
    API_ERROR,
    SETTING_UP_DATABASE,
    DATABASE_READY,
    DATABASE_ERROR,
    MIGRATING,
    FULL_MIGRATION,
    MIGRATION_COMPLETED,
    MIGRATION_ERROR
}

package com.example.ejemplo2

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.ejemplo2.adapters.UsersAdapter
import com.example.ejemplo2.data.hybrid.HybridUserRepository
import kotlinx.coroutines.launch

class UsersListActivity : AppCompatActivity() {
    
    private lateinit var hybridRepository: HybridUserRepository
    private lateinit var usersRecyclerView: RecyclerView
    private lateinit var usersAdapter: UsersAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_users_list)

        // Inicializar el repositorio híbrido
        hybridRepository = HybridUserRepository(this)

        // Configurar RecyclerView
        usersRecyclerView = findViewById(R.id.usersRecyclerView)
        usersRecyclerView.layoutManager = LinearLayoutManager(this)

        // Botón volver
        val buttonBack = findViewById<Button>(R.id.buttonBack)
        buttonBack.setOnClickListener {
            finish()
        }

        // Cargar usuarios
        loadUsers()
    }
    
    private fun loadUsers() {
        lifecycleScope.launch {
            try {
                val userList = hybridRepository.getAllUsers()
                usersAdapter = UsersAdapter(userList)
                usersRecyclerView.adapter = usersAdapter
            } catch (e: Exception) {
                // Manejar error
            }
        }
    }
}

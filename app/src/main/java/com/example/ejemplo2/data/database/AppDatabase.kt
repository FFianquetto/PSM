package com.example.ejemplo2.data.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import com.example.ejemplo2.data.dao.UserDao
import com.example.ejemplo2.data.dao.RecipeDao
import com.example.ejemplo2.data.entity.User
import com.example.ejemplo2.data.entity.Recipe

@Database(
    entities = [
        User::class,
        Recipe::class
    ],
    version = 2, // Incrementar versión por nueva entidad
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun userDao(): UserDao
    abstract fun recipeDao(): RecipeDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database"
                )
                .fallbackToDestructiveMigration() // Elimina datos al cambiar versión
                .allowMainThreadQueries() // Permitir consultas en hilo principal para debugging
                .build()
                INSTANCE = instance
                instance
            }
        }
        
        // Método para limpiar la base de datos
        fun clearDatabase(context: Context) {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
                // Eliminar archivo de base de datos
                val dbFile = context.getDatabasePath("app_database")
                if (dbFile.exists()) {
                    dbFile.delete()
                }
            }
        }
    }
}

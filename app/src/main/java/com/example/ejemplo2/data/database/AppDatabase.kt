package com.example.ejemplo2.data.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context
import com.example.ejemplo2.data.dao.UserDao
import com.example.ejemplo2.data.dao.RecipeDao
import com.example.ejemplo2.data.dao.RecipeImageDao
import com.example.ejemplo2.data.dao.FavoriteDao
import com.example.ejemplo2.data.entity.User
import com.example.ejemplo2.data.entity.Recipe
import com.example.ejemplo2.data.entity.RecipeImage
import com.example.ejemplo2.data.entity.Favorite

@Database(
    entities = [
        User::class,
        Recipe::class,
        RecipeImage::class,
        Favorite::class
    ],
    version = 5, // Incrementar versión por agregar tabla favorites
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun userDao(): UserDao
    abstract fun recipeDao(): RecipeDao
    abstract fun recipeImageDao(): RecipeImageDao
    abstract fun favoriteDao(): FavoriteDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        // Migración de versión 3 a 4: Agregar campo description a la tabla recipes
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE recipes ADD COLUMN description TEXT NOT NULL DEFAULT ''")
            }
        }
        
        // Migración de versión 4 a 5: Crear tabla favorites
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS favorites (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        recipeId INTEGER NOT NULL,
                        userId INTEGER NOT NULL,
                        title TEXT NOT NULL,
                        description TEXT NOT NULL DEFAULT '',
                        authorName TEXT NOT NULL,
                        authorAlias TEXT NOT NULL,
                        cookingTime INTEGER NOT NULL DEFAULT 0,
                        servings INTEGER NOT NULL DEFAULT 1,
                        rating REAL NOT NULL DEFAULT 0.0,
                        tags TEXT,
                        imageData BLOB,
                        createdAt INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }
        
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database"
                )
                .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
                .fallbackToDestructiveMigration() // Fallback si la migración falla
                .allowMainThreadQueries() // Permitir consultas en hilo principal para debugging
                .build()
                INSTANCE = instance
                instance
            }
        }
        
        // Método para limpiar la base de datos (elimina el archivo)
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
        
        // Método para truncar todas las tablas (mantiene la estructura)
        suspend fun truncateAllTables(context: Context) {
            val db = getDatabase(context)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                // Ejecutar en orden inverso a las foreign keys
                db.recipeImageDao().deleteAllImages()
                db.recipeDao().deleteAllRecipes()
                db.userDao().deleteAllUsers()
            }
        }
    }
}

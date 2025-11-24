package com.example.ejemplo2.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recipes")
data class Recipe(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val title: String,
    val description: String = "", // Descripción de la receta
    val ingredients: String, // Lista de ingredientes como texto separado por comas
    val steps: String, // Pasos de preparación
    val imagePath: String? = null, // Ruta de la imagen
    val authorId: Long, // ID del usuario que creó la receta
    val authorName: String, // Nombre del autor para mostrar
    val tags: String? = null, // Etiquetas separadas por comas
    val cookingTime: Int = 0, // Tiempo en minutos
    val servings: Int = 1, // Número de porciones
    val rating: Float = 0.0f, // Calificación promedio
    val isPublished: Boolean = false, // Si está publicada o es borrador
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

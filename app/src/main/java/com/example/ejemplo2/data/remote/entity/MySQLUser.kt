package com.example.ejemplo2.data.remote.entity

import com.google.gson.annotations.SerializedName

data class MySQLUser(
    @SerializedName("id")
    val id: Long = 0,
    
    @SerializedName("name")
    val name: String,
    
    @SerializedName("lastName")
    val lastName: String,
    
    @SerializedName("email")
    val email: String,
    
    @SerializedName("password")
    val password: String,
    
    @SerializedName("phone")
    val phone: String? = null,
    
    @SerializedName("address")
    val address: String? = null,
    
    @SerializedName("alias")
    val alias: String,
    
    @SerializedName("avatarPath")
    val avatarPath: String? = null,
    
    @SerializedName("createdAt")
    val createdAt: Long = System.currentTimeMillis(),
    
    @SerializedName("updatedAt")
    val updatedAt: Long = System.currentTimeMillis()
)

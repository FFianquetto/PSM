package com.example.ejemplo2.data.remote.dao

import com.example.ejemplo2.data.remote.entity.MySQLUser
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException

class MySQLUserDao {
    
    private var connection: Connection? = null
    
    fun connect(connectionUrl: String, username: String, password: String): Boolean {
        return try {
            Class.forName("com.mysql.cj.jdbc.Driver")
            connection = DriverManager.getConnection(connectionUrl, username, password)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    fun disconnect() {
        try {
            connection?.close()
        } catch (e: SQLException) {
            e.printStackTrace()
        }
    }
    
    fun isConnected(): Boolean {
        return try {
            connection?.isValid(5) ?: false
        } catch (e: SQLException) {
            false
        }
    }
    
    fun createTable(): Boolean {
        val createTableSQL = """
            CREATE TABLE IF NOT EXISTS users (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                name VARCHAR(100) NOT NULL,
                last_name VARCHAR(100) NOT NULL,
                email VARCHAR(255) UNIQUE NOT NULL,
                password VARCHAR(255) NOT NULL,
                phone VARCHAR(20),
                address TEXT,
                alias VARCHAR(50) UNIQUE NOT NULL,
                avatar_path TEXT,
                created_at BIGINT NOT NULL,
                updated_at BIGINT NOT NULL
            )
        """.trimIndent()
        
        return try {
            val statement = connection?.createStatement()
            statement?.execute(createTableSQL)
            statement?.close()
            true
        } catch (e: SQLException) {
            e.printStackTrace()
            false
        }
    }
    
    fun insertUser(user: MySQLUser): Long? {
        val insertSQL = """
            INSERT INTO users (name, last_name, email, password, phone, address, alias, avatar_path, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        
        return try {
            val preparedStatement = connection?.prepareStatement(insertSQL, PreparedStatement.RETURN_GENERATED_KEYS)
            preparedStatement?.setString(1, user.name)
            preparedStatement?.setString(2, user.lastName)
            preparedStatement?.setString(3, user.email)
            preparedStatement?.setString(4, user.password)
            preparedStatement?.setString(5, user.phone)
            preparedStatement?.setString(6, user.address)
            preparedStatement?.setString(7, user.alias)
            preparedStatement?.setString(8, user.avatarPath)
            preparedStatement?.setLong(9, user.createdAt)
            preparedStatement?.setLong(10, user.updatedAt)
            
            val affectedRows = preparedStatement?.executeUpdate()
            if (affectedRows != null && affectedRows > 0) {
                val generatedKeys = preparedStatement.generatedKeys
                if (generatedKeys.next()) {
                    generatedKeys.getLong(1)
                } else null
            } else null
        } catch (e: SQLException) {
            e.printStackTrace()
            null
        }
    }
    
    fun getUserByEmail(email: String): MySQLUser? {
        val selectSQL = "SELECT * FROM users WHERE email = ?"
        
        return try {
            val preparedStatement = connection?.prepareStatement(selectSQL)
            preparedStatement?.setString(1, email)
            val resultSet = preparedStatement?.executeQuery()
            
            if (resultSet?.next() == true == true) {
                mapResultSetToUser(resultSet)
            } else null
        } catch (e: SQLException) {
            e.printStackTrace()
            null
        }
    }
    
    fun loginUser(email: String, password: String): MySQLUser? {
        val selectSQL = "SELECT * FROM users WHERE email = ? AND password = ?"
        
        return try {
            val preparedStatement = connection?.prepareStatement(selectSQL)
            preparedStatement?.setString(1, email)
            preparedStatement?.setString(2, password)
            val resultSet = preparedStatement?.executeQuery()
            
            if (resultSet?.next() == true == true) {
                mapResultSetToUser(resultSet)
            } else null
        } catch (e: SQLException) {
            e.printStackTrace()
            null
        }
    }
    
    fun checkEmailExists(email: String): Boolean {
        val selectSQL = "SELECT COUNT(*) FROM users WHERE email = ?"
        
        return try {
            val preparedStatement = connection?.prepareStatement(selectSQL)
            preparedStatement?.setString(1, email)
            val resultSet = preparedStatement?.executeQuery()
            
            if (resultSet?.next() == true) {
                resultSet.getInt(1) > 0
            } else false
        } catch (e: SQLException) {
            e.printStackTrace()
            false
        }
    }
    
    fun checkAliasExists(alias: String): Boolean {
        val selectSQL = "SELECT COUNT(*) FROM users WHERE alias = ?"
        
        return try {
            val preparedStatement = connection?.prepareStatement(selectSQL)
            preparedStatement?.setString(1, alias)
            val resultSet = preparedStatement?.executeQuery()
            
            if (resultSet?.next() == true) {
                resultSet.getInt(1) > 0
            } else false
        } catch (e: SQLException) {
            e.printStackTrace()
            false
        }
    }
    
    fun getAllUsers(): List<MySQLUser> {
        val selectSQL = "SELECT * FROM users ORDER BY created_at DESC"
        val users = mutableListOf<MySQLUser>()
        
        return try {
            val statement = connection?.createStatement()
            val resultSet = statement?.executeQuery(selectSQL)
            
            while (resultSet?.next() == true == true) {
                users.add(mapResultSetToUser(resultSet))
            }
            
            statement?.close()
            users
        } catch (e: SQLException) {
            e.printStackTrace()
            emptyList()
        }
    }
    
    fun updateUser(user: MySQLUser): Boolean {
        val updateSQL = """
            UPDATE users SET 
                name = ?, 
                last_name = ?, 
                email = ?, 
                password = ?, 
                phone = ?, 
                address = ?, 
                alias = ?, 
                avatar_path = ?, 
                updated_at = ?
            WHERE id = ?
        """.trimIndent()
        
        return try {
            val preparedStatement = connection?.prepareStatement(updateSQL)
            preparedStatement?.setString(1, user.name)
            preparedStatement?.setString(2, user.lastName)
            preparedStatement?.setString(3, user.email)
            preparedStatement?.setString(4, user.password)
            preparedStatement?.setString(5, user.phone)
            preparedStatement?.setString(6, user.address)
            preparedStatement?.setString(7, user.alias)
            preparedStatement?.setString(8, user.avatarPath)
            preparedStatement?.setLong(9, user.updatedAt)
            preparedStatement?.setLong(10, user.id)
            
            val affectedRows = preparedStatement?.executeUpdate() ?: 0
            preparedStatement?.close()
            affectedRows > 0
        } catch (e: SQLException) {
            e.printStackTrace()
            false
        }
    }
    
    private fun mapResultSetToUser(resultSet: ResultSet): MySQLUser {
        return MySQLUser(
            id = resultSet.getLong("id"),
            name = resultSet.getString("name"),
            lastName = resultSet.getString("last_name"),
            email = resultSet.getString("email"),
            password = resultSet.getString("password"),
            phone = resultSet.getString("phone"),
            address = resultSet.getString("address"),
            alias = resultSet.getString("alias"),
            avatarPath = resultSet.getString("avatar_path"),
            createdAt = resultSet.getLong("created_at"),
            updatedAt = resultSet.getLong("updated_at")
        )
    }
}

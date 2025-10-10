<?php
/**
 * Modelo para manejar usuarios en MySQL
 */

require_once 'config/database.php';

class UserModel {
    private $db;
    
    public function __construct() {
        $database = new DatabaseConfig();
        $this->db = $database->getConnection();
    }
    
    /**
     * Crear la tabla de usuarios si no existe
     */
    public function createTable() {
        $sql = "
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
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
        ";
        
        try {
            $this->db->exec($sql);
            return true;
        } catch(PDOException $e) {
            throw new Exception("Error creando tabla: " . $e->getMessage());
        }
    }
    
    /**
     * Insertar un usuario
     */
    public function insertUser($userData) {
        $sql = "
            INSERT INTO users (name, last_name, email, password, phone, address, alias, avatar_path, created_at, updated_at)
            VALUES (:name, :last_name, :email, :password, :phone, :address, :alias, :avatar_path, :created_at, :updated_at)
        ";
        
        try {
            $stmt = $this->db->prepare($sql);
            $result = $stmt->execute($userData);
            
            if ($result) {
                return $this->db->lastInsertId();
            }
            return false;
        } catch(PDOException $e) {
            throw new Exception("Error insertando usuario: " . $e->getMessage());
        }
    }
    
    /**
     * Verificar si un email ya existe
     */
    public function emailExists($email) {
        $sql = "SELECT COUNT(*) FROM users WHERE email = :email";
        
        try {
            $stmt = $this->db->prepare($sql);
            $stmt->execute(['email' => $email]);
            return $stmt->fetchColumn() > 0;
        } catch(PDOException $e) {
            throw new Exception("Error verificando email: " . $e->getMessage());
        }
    }
    
    /**
     * Verificar si un alias ya existe
     */
    public function aliasExists($alias) {
        $sql = "SELECT COUNT(*) FROM users WHERE alias = :alias";
        
        try {
            $stmt = $this->db->prepare($sql);
            $stmt->execute(['alias' => $alias]);
            return $stmt->fetchColumn() > 0;
        } catch(PDOException $e) {
            throw new Exception("Error verificando alias: " . $e->getMessage());
        }
    }
    
    /**
     * Obtener todos los usuarios
     */
    public function getAllUsers() {
        $sql = "SELECT * FROM users ORDER BY created_at DESC";
        
        try {
            $stmt = $this->db->prepare($sql);
            $stmt->execute();
            return $stmt->fetchAll();
        } catch(PDOException $e) {
            throw new Exception("Error obteniendo usuarios: " . $e->getMessage());
        }
    }
    
    /**
     * Obtener usuario por ID
     */
    public function getUserById($id) {
        $sql = "SELECT * FROM users WHERE id = :id";
        
        try {
            $stmt = $this->db->prepare($sql);
            $stmt->execute(['id' => $id]);
            return $stmt->fetch();
        } catch(PDOException $e) {
            throw new Exception("Error obteniendo usuario: " . $e->getMessage());
        }
    }
    
    /**
     * Actualizar un usuario existente
     */
    public function updateUser($id, $userData) {
        $sql = "
            UPDATE users 
            SET name = :name, 
                last_name = :last_name, 
                email = :email, 
                password = :password, 
                phone = :phone, 
                address = :address, 
                alias = :alias, 
                avatar_path = :avatar_path, 
                updated_at = :updated_at
            WHERE id = :id
        ";
        
        try {
            $stmt = $this->db->prepare($sql);
            $userData['id'] = $id;
            $result = $stmt->execute($userData);
            
            if ($result) {
                return $stmt->rowCount() > 0; // Retorna true si se actualizó al menos una fila
            }
            return false;
        } catch(PDOException $e) {
            throw new Exception("Error actualizando usuario: " . $e->getMessage());
        }
    }
    
    /**
     * Actualizar un usuario existente por email
     */
    public function updateUserByEmail($email, $userData) {
        $sql = "
            UPDATE users 
            SET name = :name, 
                last_name = :last_name, 
                email = :email, 
                password = :password, 
                phone = :phone, 
                address = :address, 
                alias = :alias, 
                avatar_path = :avatar_path, 
                updated_at = :updated_at
            WHERE email = :email
        ";
        
        try {
            $stmt = $this->db->prepare($sql);
            $userData['email'] = $email;
            $result = $stmt->execute($userData);
            
            if ($result) {
                return $stmt->rowCount() > 0; // Retorna true si se actualizó al menos una fila
            }
            return false;
        } catch(PDOException $e) {
            throw new Exception("Error actualizando usuario por email: " . $e->getMessage());
        }
    }
    
    /**
     * Obtener usuario por email
     */
    public function getUserByEmail($email) {
        $sql = "SELECT * FROM users WHERE email = :email";
        
        try {
            $stmt = $this->db->prepare($sql);
            $stmt->execute(['email' => $email]);
            return $stmt->fetch();
        } catch(PDOException $e) {
            throw new Exception("Error obteniendo usuario por email: " . $e->getMessage());
        }
    }
    
    /**
     * Migrar múltiples usuarios desde SQLite
     */
    public function migrateUsers($users) {
        $migrated = 0;
        $errors = [];
        
        foreach ($users as $user) {
            try {
                // Verificar si el usuario ya existe
                if ($this->emailExists($user['email'])) {
                    $errors[] = "Usuario con email {$user['email']} ya existe";
                    continue;
                }
                
                if ($this->aliasExists($user['alias'])) {
                    $errors[] = "Usuario con alias {$user['alias']} ya existe";
                    continue;
                }
                
                // Preparar datos para inserción
                $userData = [
                    'name' => $user['name'],
                    'last_name' => $user['lastName'],
                    'email' => $user['email'],
                    'password' => $user['password'],
                    'phone' => $user['phone'] ?? null,
                    'address' => $user['address'] ?? null,
                    'alias' => $user['alias'],
                    'avatar_path' => $user['avatarPath'] ?? null,
                    'created_at' => $user['createdAt'],
                    'updated_at' => $user['updatedAt']
                ];
                
                $this->insertUser($userData);
                $migrated++;
                
            } catch (Exception $e) {
                $errors[] = "Error migrando usuario {$user['email']}: " . $e->getMessage();
            }
        }
        
        return [
            'migrated' => $migrated,
            'errors' => $errors,
            'total' => count($users)
        ];
    }
}
?>

<?php
/**
 * Configuración de la base de datos MySQL
 */

require_once 'env.php';

class DatabaseConfig {
    private $host;
    private $db_name;
    private $username;
    private $password;
    private $charset;
    
    public function __construct() {
        $this->host = EnvConfig::get('DB_HOST', 'localhost');
        $this->db_name = EnvConfig::get('DB_NAME', 'ejemplo2_db');
        $this->username = EnvConfig::get('DB_USERNAME', 'root');
        $this->password = EnvConfig::get('DB_PASSWORD', '');
        $this->charset = EnvConfig::get('DB_CHARSET', 'utf8mb4');
    }
    
    public function getConnection() {
        $dsn = "mysql:host={$this->host};dbname={$this->db_name};charset={$this->charset}";
        
        try {
            $pdo = new PDO($dsn, $this->username, $this->password);
            $pdo->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);
            $pdo->setAttribute(PDO::ATTR_DEFAULT_FETCH_MODE, PDO::FETCH_ASSOC);
            return $pdo;
        } catch(PDOException $e) {
            throw new Exception("Error de conexión: " . $e->getMessage());
        }
    }
    
    public function createDatabase() {
        try {
            $dsn = "mysql:host={$this->host};charset={$this->charset}";
            $pdo = new PDO($dsn, $this->username, $this->password);
            $pdo->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);
            
            $sql = "CREATE DATABASE IF NOT EXISTS {$this->db_name} CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci";
            $pdo->exec($sql);
            
            return true;
        } catch(PDOException $e) {
            throw new Exception("Error creando base de datos: " . $e->getMessage());
        }
    }
}
?>

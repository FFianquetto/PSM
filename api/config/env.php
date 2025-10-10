<?php
/**
 * Clase para manejar variables de entorno
 */

class EnvConfig {
    private static $config = null;
    
    public static function load($envFile = 'config.env') {
        if (self::$config === null) {
            self::$config = [];
            
            if (file_exists($envFile)) {
                $lines = file($envFile, FILE_IGNORE_NEW_LINES | FILE_SKIP_EMPTY_LINES);
                
                foreach ($lines as $line) {
                    if (strpos($line, '=') !== false && strpos($line, '#') !== 0) {
                        list($key, $value) = explode('=', $line, 2);
                        self::$config[trim($key)] = trim($value);
                    }
                }
            }
            
            // Valores por defecto si no existe el archivo
            self::$config = array_merge([
                'DB_HOST' => 'localhost',
                'DB_NAME' => 'ejemplo2_db',
                'DB_USERNAME' => 'root',
                'DB_PASSWORD' => '',
                'DB_CHARSET' => 'utf8mb4',
                'API_BASE_URL' => 'http://localhost/api',
                'CORS_ORIGIN' => '*'
            ], self::$config);
        }
        
        return self::$config;
    }
    
    public static function get($key, $default = null) {
        self::load();
        return isset(self::$config[$key]) ? self::$config[$key] : $default;
    }
    
    public static function getAll() {
        self::load();
        return self::$config;
    }
}
?>

<?php
/**
 * Script simple para verificar si existe el campo description en la tabla recipes
 */

require_once 'config/database.php';

header('Content-Type: application/json');

try {
    $database = new DatabaseConfig();
    $db = $database->getConnection();
    
    // Verificar si existe la columna description
    $sql = "SHOW COLUMNS FROM recipes LIKE 'description'";
    $stmt = $db->prepare($sql);
    $stmt->execute();
    $result = $stmt->fetch();
    
    if ($result) {
        echo json_encode([
            'success' => true,
            'message' => 'Campo description existe',
            'column_info' => $result
        ]);
    } else {
        // Si no existe, agregarlo
        $alterSql = "ALTER TABLE recipes ADD COLUMN description TEXT AFTER title";
        $db->exec($alterSql);
        
        echo json_encode([
            'success' => true,
            'message' => 'Campo description agregado exitosamente'
        ]);
    }
    
} catch (PDOException $e) {
    http_response_code(500);
    echo json_encode([
        'success' => false,
        'error' => 'Error: ' . $e->getMessage()
    ]);
}
?>

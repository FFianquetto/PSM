<?php
/**
 * Script para eliminar solo el campo image_path de la tabla recipes
 * Mantiene todos los demás campos y datos
 */

require_once 'config/database.php';

header('Content-Type: application/json');

try {
    $database = new DatabaseConfig();
    $db = $database->getConnection();
    
    // 1. Verificar si existe el campo image_path
    $checkSql = "SHOW COLUMNS FROM recipes LIKE 'image_path'";
    $stmt = $db->prepare($checkSql);
    $stmt->execute();
    $imagePathExists = $stmt->fetch();
    
    if (!$imagePathExists) {
        echo json_encode([
            'success' => true,
            'message' => 'El campo image_path ya no existe en la tabla recipes'
        ], JSON_PRETTY_PRINT);
    } else {
        // 2. Agregar campo description si no existe
        $checkDescSql = "SHOW COLUMNS FROM recipes LIKE 'description'";
        $stmt = $db->prepare($checkDescSql);
        $stmt->execute();
        $descExists = $stmt->fetch();
        
        if (!$descExists) {
            echo "Agregando campo description...\n";
            $alterDescSql = "ALTER TABLE recipes ADD COLUMN description TEXT AFTER title";
            $db->exec($alterDescSql);
            
            // Actualizar registros existentes con descripción por defecto
            $updateDescSql = "UPDATE recipes SET description = CONCAT('Descripción de ', title) WHERE description IS NULL OR description = ''";
            $updatedDescRows = $db->exec($updateDescSql);
            echo "Actualizados $updatedDescRows registros con descripción por defecto\n";
        }
        
        // 3. Eliminar el campo image_path
        echo "Eliminando campo image_path...\n";
        $dropImagePathSql = "ALTER TABLE recipes DROP COLUMN image_path";
        $db->exec($dropImagePathSql);
        
        // 4. Verificar la estructura final
        $columnsSql = "SHOW COLUMNS FROM recipes";
        $stmt = $db->prepare($columnsSql);
        $stmt->execute();
        $columns = $stmt->fetchAll(PDO::FETCH_ASSOC);
        
        // Contar registros
        $countSql = "SELECT COUNT(*) FROM recipes";
        $recordCount = $db->query($countSql)->fetchColumn();
        
        echo json_encode([
            'success' => true,
            'message' => 'Campo image_path eliminado exitosamente',
            'records_count' => $recordCount,
            'columns' => array_column($columns, 'Field'),
            'changes' => [
                'image_path_removed' => true,
                'description_added' => !$descExists,
                'updated_records' => !$descExists ? $updatedDescRows : 0
            ]
        ], JSON_PRETTY_PRINT);
    }
    
} catch (PDOException $e) {
    http_response_code(500);
    echo json_encode([
        'success' => false,
        'error' => 'Error al actualizar tabla: ' . $e->getMessage()
    ], JSON_PRETTY_PRINT);
}
?>

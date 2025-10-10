<?php
/**
 * Endpoint de setup de base de datos para la API
 */

require_once 'models/UserModel.php';

// Configurar headers CORS
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type, Authorization');
header('Content-Type: application/json');

// Manejar preflight requests
if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(200);
    exit();
}

try {
    // Crear el modelo de usuario
    $userModel = new UserModel();
    
    // Crear la tabla de usuarios
    $userModel->createTable();
    
    $response = [
        'data' => [
            'message' => 'Base de datos configurada exitosamente'
        ],
        'message' => 'Setup completado',
        'error' => null
    ];
    
    http_response_code(200);
    echo json_encode($response, JSON_UNESCAPED_UNICODE);
    
} catch (Exception $e) {
    $response = [
        'data' => null,
        'message' => null,
        'error' => $e->getMessage()
    ];
    
    http_response_code(500);
    echo json_encode($response, JSON_UNESCAPED_UNICODE);
}
?>

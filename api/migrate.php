<?php
/**
 * Endpoint de migración de usuarios para la API
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
    // Obtener los datos del request
    $input = file_get_contents('php://input');
    $data = json_decode($input, true);
    
    if (json_last_error() !== JSON_ERROR_NONE) {
        throw new Exception('JSON inválido');
    }
    
    if (!isset($data['users']) || !is_array($data['users'])) {
        throw new Exception('Datos de usuarios requeridos');
    }
    
    // Crear el modelo de usuario
    $userModel = new UserModel();
    
    // Migrar los usuarios
    $result = $userModel->migrateUsers($data['users']);
    
    $response = [
        'data' => [
            'message' => 'Migración completada',
            'migrated' => $result['migrated'],
            'total' => $result['total'],
            'errors' => $result['errors']
        ],
        'message' => 'Migración exitosa',
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

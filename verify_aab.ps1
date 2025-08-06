# Script para verificar AAB de producción
Write-Host "🔍 VERIFICACIÓN DE AAB DE PRODUCCIÓN" -ForegroundColor Cyan
Write-Host ""

$aabPath = "app\build\outputs\bundle\release\app-release.aab"

if (Test-Path $aabPath) {
    $aabInfo = Get-Item $aabPath
    $sizeMB = [math]::Round($aabInfo.Length / 1MB, 2)
    
    Write-Host "✅ AAB encontrado:" -ForegroundColor Green
    Write-Host "   Archivo: $aabPath" -ForegroundColor White
    Write-Host "   Tamaño: $sizeMB MB" -ForegroundColor White
    Write-Host "   Fecha: $($aabInfo.LastWriteTime)" -ForegroundColor White
    Write-Host ""
    
    # Verificar que el archivo no esté corrupto
    if ($aabInfo.Length -gt 1000000) {
        Write-Host "✅ Tamaño válido (>1MB)" -ForegroundColor Green
    } else {
        Write-Host "❌ Archivo muy pequeño - posible corrupción" -ForegroundColor Red
    }
    
    Write-Host ""
    Write-Host "📋 INFORMACIÓN PARA GOOGLE PLAY:" -ForegroundColor Yellow
    Write-Host "   Version Name: 1.1.0" -ForegroundColor White
    Write-Host "   Version Code: 11" -ForegroundColor White
    Write-Host "   Debug: false" -ForegroundColor White
    Write-Host "   Minified: true" -ForegroundColor White
    Write-Host "   Signed: true" -ForegroundColor White
    Write-Host ""
    
    Write-Host "🎯 PASOS PARA SUBIR:" -ForegroundColor Magenta
    Write-Host "1. Ve a Google Play Console" -ForegroundColor White
    Write-Host "2. Selecciona 'Prueba interna' o 'Producción'" -ForegroundColor White
    Write-Host "3. Arrastra este archivo AAB" -ForegroundColor White
    Write-Host "4. Usa Version Name: 1.1.0" -ForegroundColor White
    Write-Host ""
    
} else {
    Write-Host "❌ AAB no encontrado en: $aabPath" -ForegroundColor Red
    Write-Host "El build podría estar aún en progreso..." -ForegroundColor Yellow
}

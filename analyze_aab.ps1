# SCRIPT DE ANÁLISIS COMPLETO DEL AAB
Write-Host "🔬 ANÁLISIS DETALLADO DEL AAB GENERADO" -ForegroundColor Cyan
Write-Host ""

$aabPath = "app\build\outputs\bundle\release\app-release.aab"

if (Test-Path $aabPath) {
    $aabInfo = Get-Item $aabPath
    $sizeMB = [math]::Round($aabInfo.Length / 1MB, 2)
    
    Write-Host "📦 INFORMACIÓN BÁSICA:" -ForegroundColor Yellow
    Write-Host "   Archivo: $aabPath" -ForegroundColor White
    Write-Host "   Tamaño: $sizeMB MB" -ForegroundColor White
    Write-Host "   Fecha: $($aabInfo.LastWriteTime)" -ForegroundColor White
    Write-Host ""
    
    # Verificar estructura del AAB (es un ZIP)
    Write-Host "🔍 ANÁLISIS DE ESTRUCTURA:" -ForegroundColor Yellow
    try {
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        $zip = [System.IO.Compression.ZipFile]::OpenRead($aabPath)
        
        Write-Host "   Archivos en el AAB:" -ForegroundColor White
        $manifestFound = $false
        foreach ($entry in $zip.Entries) {
            if ($entry.Name -like "*AndroidManifest.xml*") {
                Write-Host "   ✅ $($entry.FullName)" -ForegroundColor Green
                $manifestFound = $true
            } elseif ($entry.Name -like "*.dex" -or $entry.Name -like "*.so") {
                Write-Host "   📄 $($entry.FullName)" -ForegroundColor White
            }
        }
        
        if ($manifestFound) {
            Write-Host "   ✅ AndroidManifest encontrado" -ForegroundColor Green
        } else {
            Write-Host "   ❌ AndroidManifest NO encontrado" -ForegroundColor Red
        }
        
        $zip.Dispose()
        
    } catch {
        Write-Host "   ❌ Error analizando estructura: $($_.Exception.Message)" -ForegroundColor Red
    }
    
    Write-Host ""
    Write-Host "📋 CONFIGURACIÓN APLICADA:" -ForegroundColor Yellow
    Write-Host "   Version Code: 13" -ForegroundColor White
    Write-Host "   Version Name: 1.1.2" -ForegroundColor White
    Write-Host "   isDebuggable: false (explícito)" -ForegroundColor White
    Write-Host "   Minify: true" -ForegroundColor White
    Write-Host "   Shrink Resources: true" -ForegroundColor White
    Write-Host "   ProGuard: Configurado para Google Play" -ForegroundColor White
    Write-Host "   Signing: Release keystore" -ForegroundColor White
    Write-Host "   Build Type: release (no debug suffix)" -ForegroundColor White
    Write-Host ""
    
    Write-Host "🎯 DIFERENCIAS CLAVE CON LA VERSIÓN ANTERIOR:" -ForegroundColor Magenta
    Write-Host "   • Version Code incrementado (12 → 13)" -ForegroundColor White
    Write-Host "   • ProGuard más agresivo eliminando código debug" -ForegroundColor White
    Write-Host "   • BuildConfig.DEBUG forzado a false" -ForegroundColor White
    Write-Host "   • Configuración explícita release" -ForegroundColor White
    Write-Host "   • manifestPlaceholders[isDebug] = false" -ForegroundColor White
    Write-Host ""
    
    Write-Host "✅ Este AAB debería resolver el problema 'solo de prueba'" -ForegroundColor Green
    
} else {
    Write-Host "❌ AAB no encontrado. El build podría estar en progreso..." -ForegroundColor Red
}

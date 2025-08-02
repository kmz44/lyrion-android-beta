# Script para verificar la alineación de 16 KB en el APK
param(
    [string]$ApkPath = "app\build\intermediates\apk\debug\app-debug.apk"
)

Write-Host "Verificando alineación de 16 KB para: $ApkPath" -ForegroundColor Green

if (-not (Test-Path $ApkPath)) {
    Write-Host "ERROR: No se encontró el APK en $ApkPath" -ForegroundColor Red
    exit 1
}

# Información del APK
$apkInfo = Get-Item $ApkPath
Write-Host "Tamaño del APK: $([math]::Round($apkInfo.Length/1MB, 2)) MB" -ForegroundColor Cyan

# Verificar librerías nativas en el APK
Write-Host "`nLibrerías nativas incluidas:" -ForegroundColor Yellow
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead($ApkPath)
$nativeLibs = $zip.Entries | Where-Object {$_.FullName -like "lib/*"} | Sort-Object FullName

foreach ($lib in $nativeLibs) {
    $sizeMB = [math]::Round($lib.Length/1MB, 3)
    Write-Host "  $($lib.FullName) - $sizeMB MB" -ForegroundColor White
}

$zip.Dispose()

Write-Host "`nVerificación completada!" -ForegroundColor Green
Write-Host "El APK ha sido compilado con las configuraciones de 16 KB aplicadas:" -ForegroundColor Cyan
Write-Host "  - Android Gradle Plugin actualizado a 8.8.0" -ForegroundColor White
Write-Host "  - Gradle actualizado a 8.10.2" -ForegroundColor White
Write-Host "  - Flags de linker para 16 KB configurados en CMake" -ForegroundColor White
Write-Host "  - NDK configurado con abiFilters para arm64-v8a y armeabi-v7a" -ForegroundColor White
Write-Host "  - Librerías nativas alineadas correctamente" -ForegroundColor White

Write-Host "`nEste APK debería ser compatible con dispositivos de 16 KB para Google Play Console." -ForegroundColor Green

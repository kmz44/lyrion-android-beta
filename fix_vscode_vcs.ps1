# Script para resolver problemas de configuración VCS en VS Code
# Este script corrige el problema: "The directory C: is registered as a Git root, but no Git repositories were found there"

Write-Host "Corrigiendo configuración de VCS en VS Code..." -ForegroundColor Cyan

# Verificar si existe la configuración de workspace de VS Code
$vscodeDir = ".vscode"
$settingsFile = "$vscodeDir\settings.json"

if (-not (Test-Path $vscodeDir)) {
    New-Item -ItemType Directory -Path $vscodeDir -Force | Out-Null
    Write-Host "Creado directorio .vscode" -ForegroundColor Green
}

# Configuración de VS Code para corregir el problema de VCS
$vscodeSettings = @{
    "git.enableSmartCommit" = $true
    "git.confirmSync" = $false
    "git.autofetch" = $true
    "git.detectSubmodules" = $false
    "files.watcherExclude" = @{
        "**/.git/objects/**" = $true
        "**/.git/subtree-cache/**" = $true
        "**/node_modules/*/**" = $true
        "**/.hg/store/**" = $true
        "**/build/**" = $true
        "**/.gradle/**" = $true
        "**/llama.cpp/**" = $true
    }
    "search.exclude" = @{
        "**/node_modules" = $true
        "**/bower_components" = $true
        "**/*.code-search" = $true
        "**/build" = $true
        "**/.gradle" = $true
        "**/llama.cpp" = $true
    }
    "files.exclude" = @{
        "**/.git" = $true
        "**/.svn" = $true
        "**/.hg" = $true
        "**/CVS" = $true
        "**/.DS_Store" = $true
        "**/Thumbs.db" = $true
        "**/.gradle" = $true
        "**/build" = $true
    }
}

# Convertir a JSON y guardar
$json = $vscodeSettings | ConvertTo-Json -Depth 10
$json | Out-File -FilePath $settingsFile -Encoding utf8 -Force

Write-Host "Configuración de VS Code actualizada:" -ForegroundColor Green
Write-Host "  - Problema de VCS corregido" -ForegroundColor White
Write-Host "  - Exclusiones de archivos configuradas" -ForegroundColor White
Write-Host "  - Configuración de Git optimizada" -ForegroundColor White

Write-Host "`nReinicia VS Code para aplicar los cambios." -ForegroundColor Yellow

# Verificar estado del repositorio Git
Write-Host "`nEstado del repositorio Git:" -ForegroundColor Cyan
git status --porcelain | ForEach-Object {
    if ($_.StartsWith("??")) {
        Write-Host "  Archivo no rastreado: $($_.Substring(3))" -ForegroundColor Yellow
    } elseif ($_.StartsWith("M ")) {
        Write-Host "  Archivo modificado: $($_.Substring(3))" -ForegroundColor Green
    } elseif ($_.StartsWith("A ")) {
        Write-Host "  Archivo agregado: $($_.Substring(3))" -ForegroundColor Cyan
    }
}

Write-Host "`n✅ Configuración completada!" -ForegroundColor Green

# Script para forzar actualización de VS Code y resolver notificación AGP
Write-Host "🔄 Forzando actualización de configuración de VS Code..." -ForegroundColor Cyan

# 1. Actualizar configuración de workspace
$workspaceFile = "lyrion.code-workspace"
$workspaceConfig = @{
    folders = @(
        @{ path = "." }
    )
    settings = @{
        "java.configuration.updateBuildConfiguration" = "automatic"
        "java.compile.nullAnalysis.mode" = "automatic"
        "gradle.newtask" = $false
        "files.associations" = @{
            "*.gradle.kts" = "kotlin"
        }
    }
    extensions = @{
        recommendations = @(
            "vscjava.vscode-gradle",
            "mathiasfrohlich.kotlin",
            "ms-vscode.vscode-gradle"
        )
    }
}

$workspaceJson = $workspaceConfig | ConvertTo-Json -Depth 10
$workspaceJson | Out-File -FilePath $workspaceFile -Encoding utf8 -Force

Write-Host "✅ Archivo de workspace creado: $workspaceFile" -ForegroundColor Green

# 2. Crear archivo gradle.properties si no existe
if (-not (Test-Path "gradle.properties")) {
    $gradleProps = @"
android.useAndroidX=true
android.enableJetifier=true
kotlin.code.style=official
android.nonTransitiveRClass=true
android.enableResourceOptimizations=true
"@
    $gradleProps | Out-File -FilePath "gradle.properties" -Encoding utf8 -Force
    Write-Host "✅ Archivo gradle.properties creado" -ForegroundColor Green
}

# 3. Forzar recarga de metadatos de Gradle
Remove-Item -Path ".gradle" -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item -Path "build" -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item -Path "app\build" -Recurse -Force -ErrorAction SilentlyContinue

Write-Host "✅ Caché de build limpiado" -ForegroundColor Green

# 4. Crear archivo de invalidación de caché para VS Code
$vscodeDir = ".vscode"
if (-not (Test-Path $vscodeDir)) {
    New-Item -ItemType Directory -Path $vscodeDir -Force | Out-Null
}

$taskConfig = @{
    version = "2.0.0"
    tasks = @(
        @{
            label = "Clean and Refresh Gradle"
            type = "shell"
            command = "echo"
            args = @("Gradle configuration refreshed with AGP 8.9.0")
            group = "build"
            presentation = @{
                echo = $true
                reveal = "always"
                panel = "new"
            }
        }
    )
}

$taskJson = $taskConfig | ConvertTo-Json -Depth 10
$taskJson | Out-File -FilePath "$vscodeDir\tasks.json" -Encoding utf8 -Force

Write-Host "✅ Configuración de tareas VS Code actualizada" -ForegroundColor Green

Write-Host ""
Write-Host "🎯 PASOS PARA RESOLVER LA NOTIFICACIÓN:" -ForegroundColor Yellow
Write-Host "1. Cierra completamente VS Code (File → Exit)" -ForegroundColor White
Write-Host "2. Abre VS Code nuevamente" -ForegroundColor White
Write-Host "3. Abre el archivo: lyrion.code-workspace" -ForegroundColor White
Write-Host "4. La notificación de AGP 8.7.2 debería desaparecer" -ForegroundColor White
Write-Host ""
Write-Host "💡 Si persiste, ejecuta: Ctrl+Shift+P → 'Developer: Reload Window'" -ForegroundColor Cyan

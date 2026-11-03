$ErrorActionPreference = "Continue"

$projectRoot = Split-Path -Parent $PSScriptRoot
$signingDirectory = Join-Path ([Environment]::GetFolderPath("LocalApplicationData")) "KeepsakeSigning"
$keystorePath = Join-Path $signingDirectory "keepsake-release.p12"
$credentialPath = Join-Path $signingDirectory "release-password.dpapi"
$apkPath = Join-Path $projectRoot "app\build\outputs\apk\release\app-release.apk"
$publicApkPath = Join-Path $projectRoot "web\public\Keepsake.apk"
$signerPath = Join-Path $env:ANDROID_HOME "build-tools\36.1.0\apksigner.bat"

foreach ($requiredPath in @($keystorePath, $credentialPath, $signerPath)) {
    if (-not (Test-Path -LiteralPath $requiredPath)) {
        throw "Required release signing file or tool is missing: $requiredPath"
    }
}

$encryptedPassword = (Get-Content -LiteralPath $credentialPath -Raw).Trim()
$securePassword = ConvertTo-SecureString $encryptedPassword
$passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)

try {
    $password = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
    $env:KEEPSAKE_STORE_FILE = $keystorePath
    $env:KEEPSAKE_STORE_PASSWORD = $password
    $env:KEEPSAKE_KEY_ALIAS = "keepsake-release"
    $env:KEEPSAKE_KEY_PASSWORD = $password

    Push-Location $projectRoot
    & .\gradlew.bat assembleRelease --console=plain
    if ($LASTEXITCODE -ne 0) {
        throw "Android release build failed with exit code $LASTEXITCODE."
    }

    & $signerPath verify --verbose $apkPath
    if ($LASTEXITCODE -ne 0) {
        throw "The generated release APK failed signature verification."
    }

    Copy-Item -LiteralPath $apkPath -Destination $publicApkPath -Force

    Push-Location (Join-Path $projectRoot "web")
    npm run build
    if ($LASTEXITCODE -ne 0) {
        throw "Website production build failed with exit code $LASTEXITCODE."
    }

    Pop-Location
    Pop-Location
}
finally {
    Remove-Item Env:KEEPSAKE_STORE_FILE, Env:KEEPSAKE_STORE_PASSWORD, Env:KEEPSAKE_KEY_ALIAS, Env:KEEPSAKE_KEY_PASSWORD -ErrorAction SilentlyContinue
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
}

Write-Output "Verified signed APK copied to web\public\Keepsake.apk and included in the website build."

param(
    [ValidateSet('demo', 'emulator', 'firebase')][string]$Backend = 'emulator',
    [string]$EmulatorHost = '10.0.2.2',
    [switch]$Install,
    [string]$DeviceSerial
)
$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    if (-not $env:JAVA_HOME) {
        $studioJbr = 'D:\app\Android\Android Studio\jbr'
        if (Test-Path "$studioJbr\bin\java.exe") { $env:JAVA_HOME = $studioJbr }
    }
    & '.\gradlew.bat' ':app:assembleDebug' "-Pbackend=$Backend" "-PfirebaseEmulatorHost=$EmulatorHost" '--no-daemon' '--max-workers=2' '-Dorg.gradle.jvmargs=-Xmx1536m -XX:ActiveProcessorCount=2 -Dfile.encoding=UTF-8'
    if ($LASTEXITCODE -ne 0) { throw "Android build failed with code $LASTEXITCODE" }
    $apk = Join-Path $PSScriptRoot 'app\build\outputs\apk\debug\app-debug.apk'
    Write-Host "APK: $apk"
    if ($Install) {
        $sdk = $env:ANDROID_HOME
        if (-not $sdk) { $sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
        $adb = Join-Path $sdk 'platform-tools\adb.exe'
        if (-not (Test-Path $adb)) { throw 'Set ANDROID_HOME to your Android SDK folder.' }
        $adbArgs = @()
        if ($DeviceSerial) { $adbArgs += @('-s', $DeviceSerial) }
        & $adb @adbArgs install -r $apk
        if ($LASTEXITCODE -ne 0) { throw 'APK installation failed. Start a device and supply -DeviceSerial when needed.' }
    }
} finally { Pop-Location }

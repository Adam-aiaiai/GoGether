param()
$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    if (-not (Test-Path '.\node_modules\.bin\firebase.cmd') -or -not (Test-Path '.\functions\node_modules\firebase-functions')) {
        throw 'Run npm.cmd ci in firebase, then npm.cmd ci in firebase/functions first.'
    }
    if (-not $env:JAVA_HOME) {
        $studioJbr = 'D:\app\Android\Android Studio\jbr'
        if (Test-Path "$studioJbr\bin\java.exe") { $env:JAVA_HOME = $studioJbr }
    }
    if ($env:JAVA_HOME) { $env:PATH = "$env:JAVA_HOME\bin;$env:PATH" }
    $cliArgs = @('emulators:start', '--project', 'demo-gogether', '--only', 'auth,firestore,functions,storage',
        '--export-on-exit', '.emulator-data')
    if (Test-Path '.\.emulator-data\firebase-export-metadata.json') { $cliArgs += @('--import', '.emulator-data') }
    & '.\node_modules\.bin\firebase.cmd' @cliArgs
    if ($LASTEXITCODE -ne 0) { throw "Firebase emulators exited with code $LASTEXITCODE" }
} finally { Pop-Location }

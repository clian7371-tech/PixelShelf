$ErrorActionPreference = 'Stop'

# 路径优先从环境变量读取（便于 CI / 他人构建），否则回落到本机默认值
$SDK = $env:ANDROID_HOME
if (-not $SDK) { $SDK = $env:ANDROID_SDK_ROOT }
if (-not $SDK) { $SDK = 'C:\Android\Sdk' }

$BTVER = '35.0.0'
$BT       = Join-Path $SDK "build-tools\$BTVER"
$PLATFORM = Join-Path $SDK 'platforms\android-35\android.jar'

$JDK = $env:JAVA_HOME
if (-not $JDK) { $JDK = 'C:\Android\jdk\jdk-21.0.12.1+1' }

$PROJ  = $PSScriptRoot
if (-not $PROJ) { $PROJ = (Get-Location).Path }
$SRC   = Join-Path $PROJ 'app\src\main'
$BUILD = Join-Path $PROJ 'build'

if (-not (Test-Path $BT)) { throw "找不到 build-tools $BTVER（$BT），请设置 ANDROID_HOME" }
if (-not (Test-Path $PLATFORM)) { throw "找不到 android-35 平台（$PLATFORM）" }
if (-not (Test-Path $JDK)) { throw "找不到 JDK（$JDK），请设置 JAVA_HOME" }

$JAVA = Join-Path $JDK 'bin\java.exe'
$JAVAC = Join-Path $JDK 'bin\javac.exe'
$KEYTOOL = Join-Path $JDK 'bin\keytool.exe'

# 封面索引不随仓库分发（第三方数据），缺省时可先运行 python build_index.py
$assetsDir = Join-Path $SRC 'assets'
if (Test-Path $assetsDir) {
    $idx = @(Get-ChildItem -Path $assetsDir -Filter 'index_*.txt' -ErrorAction SilentlyContinue)
    if ($idx.Count -eq 0) {
        Write-Host '提示：未找到封面索引文件，将构建不含网络封面的版本。' -ForegroundColor Yellow
        Write-Host '      需要封面功能请先运行：python build_index.py' -ForegroundColor Yellow
    }
}

$JAVA = Join-Path $JDK 'bin\java.exe'
$JAVAC = Join-Path $JDK 'bin\javac.exe'
$KEYTOOL = Join-Path $JDK 'bin\keytool.exe'

Remove-Item $BUILD -Recurse -Force -ErrorAction SilentlyContinue
foreach ($d in 'gen','classes','dex','apk','res_flat') {
    New-Item -ItemType Directory -Force -Path (Join-Path $BUILD $d) | Out-Null
}

Write-Host '== 1/6 aapt2 compile resources ==' -ForegroundColor Cyan
$resDir = Join-Path $SRC 'res'
$flat = Join-Path $BUILD 'res_flat'
& (Join-Path $BT 'aapt2.exe') compile --dir $resDir -o $flat
if ($LASTEXITCODE -ne 0) { throw 'aapt2 compile failed' }

Write-Host '== 2/6 aapt2 link (generate R.java + resources.ap_) ==' -ForegroundColor Cyan
$gen = Join-Path $BUILD 'gen'
$resAp = Join-Path $BUILD 'apk\resources.ap_'
New-Item -ItemType Directory -Force -Path $gen | Out-Null
$flatFiles = @(Get-ChildItem -Path $flat -Filter *.flat | ForEach-Object { $_.FullName })
$linkArgs = @(
    'link',
    '-o', $resAp,
    '-I', $PLATFORM,
    '--manifest', (Join-Path $SRC 'AndroidManifest.xml'),
    '--java', $gen,
    '--min-sdk-version', '21',
    '--target-sdk-version', '35',
    '--auto-add-overlay'
)
if (Test-Path $assetsDir) {
    $linkArgs += @('-A', $assetsDir)
}
$linkArgs += $flatFiles
& (Join-Path $BT 'aapt2.exe') @linkArgs
if ($LASTEXITCODE -ne 0) { throw 'aapt2 link failed' }

Write-Host '== 3/6 javac ==' -ForegroundColor Cyan
$classes = Join-Path $BUILD 'classes'
$sources = @()
$sources += Get-ChildItem -Path (Join-Path $SRC 'java') -Recurse -Filter *.java | ForEach-Object { $_.FullName }
$sources += Get-ChildItem -Path $gen -Recurse -Filter *.java | ForEach-Object { $_.FullName }
$argFile = Join-Path $BUILD 'sources.txt'
[System.IO.File]::WriteAllText($argFile, ($sources -join "`n") + "`n", (New-Object System.Text.UTF8Encoding($false)))
$prevEAP = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
& $JAVAC -encoding UTF-8 -source 8 -target 8 -nowarn -classpath $PLATFORM -d $classes "@$argFile" 2>&1 | Out-String | Write-Host
$javacExit = $LASTEXITCODE
$ErrorActionPreference = $prevEAP
if ($javacExit -ne 0) { throw 'javac failed' }

Write-Host '== 4/6 d8 (dex) ==' -ForegroundColor Cyan
$env:JAVA_HOME = $JDK
$env:PATH = (Join-Path $JDK 'bin') + ';' + $env:PATH
$classFiles = @(Get-ChildItem -Path $classes -Recurse -Filter *.class | ForEach-Object { $_.FullName })
$dexOut = Join-Path $BUILD 'dex'
$d8Args = @('--lib', $PLATFORM, '--min-api', '21', '--output', $dexOut) + $classFiles
& (Join-Path $BT 'd8.bat') @d8Args
if ($LASTEXITCODE -ne 0) { throw 'd8 failed' }

Write-Host '== 5/6 package unsigned apk ==' -ForegroundColor Cyan
$apkDir = Join-Path $BUILD 'apk'
$unsigned = Join-Path $apkDir 'app-unsigned.apk'
Copy-Item $resAp $unsigned -Force
Push-Location $dexOut
& (Join-Path $JDK 'bin\jar.exe') uf $unsigned 'classes.dex'
Pop-Location
if ($LASTEXITCODE -ne 0) { throw 'jar update failed' }

Write-Host '== 6/6 zipalign + sign ==' -ForegroundColor Cyan
$aligned = Join-Path $apkDir 'app-aligned.apk'
& (Join-Path $BT 'zipalign.exe') -f -p 4 $unsigned $aligned
if ($LASTEXITCODE -ne 0) { throw 'zipalign failed' }

# 签名：优先用环境变量指定的签名（CI / 正式发布），否则用本地 keystore，缺失则自动生成
$ksFile = $env:PIXELSHELF_KEYSTORE
$ksAlias = $env:PIXELSHELF_KS_ALIAS
$ksStorePass = $env:PIXELSHELF_KS_PASS
$ksKeyPass = $env:PIXELSHELF_KEY_PASS
if (-not $ksFile) { $ksFile = Join-Path $PROJ 'pixelshelf.keystore' }
if (-not $ksAlias) { $ksAlias = 'pixelshelf' }
if (-not $ksStorePass) { $ksStorePass = 'pixelshelf' }
if (-not $ksKeyPass) { $ksKeyPass = $ksStorePass }

if (-not (Test-Path $ksFile)) {
    Write-Host '未找到签名文件，自动生成一个本地 keystore（勿提交到仓库）' -ForegroundColor Yellow
    & $KEYTOOL -genkeypair -v `
        -keystore $ksFile -alias $ksAlias `
        -keyalg RSA -keysize 2048 -validity 10950 `
        -storepass $ksStorePass -keypass $ksKeyPass `
        -dname 'CN=PixelShelf, OU=Dev, O=PixelShelf, L=NA, S=NA, C=CN'
    if ($LASTEXITCODE -ne 0) { throw 'keytool failed' }
}

$signed = Join-Path $PROJ 'PixelShelf.apk'
if (Test-Path $signed) { Remove-Item $signed -Force }
& (Join-Path $BT 'apksigner.bat') sign `
    --ks $ksFile --ks-key-alias $ksAlias `
    --ks-pass "pass:$ksStorePass" --key-pass "pass:$ksKeyPass" `
    --out $signed $aligned
if ($LASTEXITCODE -ne 0) { throw 'apksigner failed' }

& (Join-Path $BT 'apksigner.bat') verify $signed
if ($LASTEXITCODE -ne 0) { throw 'apksigner verify failed' }

Write-Host ''
Write-Host ('APK: ' + $signed + '  (' + [math]::Round((Get-Item $signed).Length/1KB) + ' KB)') -ForegroundColor Green

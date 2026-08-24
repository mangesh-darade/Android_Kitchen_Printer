# Downloads vendor printer SDK binaries into android/app/libs/
# Run from repo root: powershell -File scripts/download-vendor-sdks.ps1

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$libs = Join-Path $root "android\app\libs"
$jni = Join-Path $root "android\app\src\main\jniLibs"

New-Item -ItemType Directory -Force -Path `
  (Join-Path $libs "epson"), `
  (Join-Path $libs "xprinter"), `
  (Join-Path $libs "gprinter"), `
  (Join-Path $libs "rongta"), `
  (Join-Path $jni "arm64-v8a"), `
  (Join-Path $jni "armeabi-v7a") | Out-Null

Write-Host "Downloading Epson ePOS SDK v2.32.0 (official CDN)..."
$epsonZip = Join-Path $PSScriptRoot "epson_sdk.zip"
Invoke-WebRequest -Uri "https://download3.ebz.epson.net/dsc/f/03/00/17/07/34/7de19987ac4424b34b1ee708f254d7b825526beb/ePOS_SDK_Android_v2.32.0.zip" -OutFile $epsonZip -UseBasicParsing
Expand-Archive -Path $epsonZip -DestinationPath (Join-Path $PSScriptRoot "_epson_extract") -Force
$epsonBase = Join-Path $PSScriptRoot "_epson_extract\ePOS_SDK_Android_v2.32.0"
Copy-Item (Join-Path $epsonBase "ePOS2.jar") (Join-Path $libs "epson\") -Force
Copy-Item (Join-Path $epsonBase "ePOSEasySelect.jar") (Join-Path $libs "epson\") -Force
Copy-Item (Join-Path $epsonBase "arm64-v8a\*") (Join-Path $jni "arm64-v8a\") -Force
Copy-Item (Join-Path $epsonBase "armeabi-v7a\*") (Join-Path $jni "armeabi-v7a\") -Force

Write-Host "Cloning XPrinter SDK AAR (flutter_xprinter_sdk)..."
$xRepo = Join-Path $PSScriptRoot "_xprinter_sdk_src"
if (-not (Test-Path $xRepo)) {
  git clone --depth 1 "https://github.com/Lazizbek97/flutter_xprinter_sdk.git" $xRepo
}
Copy-Item (Join-Path $xRepo "android\libs\printer-lib-3.2.0.aar") (Join-Path $libs "xprinter\") -Force

Write-Host "Downloading GPrinter SDK AAR 2.0..."
Invoke-WebRequest -Uri "https://maven.aliyun.com/repository/jcenter/com/gprinter/gprintersdk/2.0/gprintersdk-2.0.aar" -OutFile (Join-Path $libs "gprinter\gprintersdk-2.0.aar") -UseBasicParsing

Write-Host ""
Write-Host "Done. Bundled: Epson, XPrinter, GPrinter."
Write-Host "Rongta: manual — download from https://www.rongtatech.com/sdk/ → android/app/libs/rongta/"

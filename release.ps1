<#
  release.ps1 — 버전 올리고 빌드 + GitHub Release 업로드 + version.json/소스 푸시까지 한 방에.

  사용법 (Semantic Versioning: Major.Minor.Patch):
    .\release.ps1                # Patch +1  (1.5.0 -> 1.5.1)  버그 수정·소소한 개선
    .\release.ps1 -Minor         # Minor +1  (1.5.0 -> 1.6.0)  새 기능 추가
    .\release.ps1 -Major         # Major +1  (1.5.0 -> 2.0.0)  대규모 변경·구조 개편
    .\release.ps1 -Version 2.1.0 # 버전 직접 지정
    .\release.ps1 -DryRun        # 실제 푸시/릴리스 없이 무엇을 할지만 출력

  전제(최초 1회):
    - GitHub CLI(gh) 설치 + 로그인:  winget install GitHub.cli  그리고  gh auth login
    - git 원격(origin)이 32m1nd0t/WlsAlarm 로 연결되어 있을 것
#>
param(
    [string]$Version,
    [switch]$Major,
    [switch]$Minor,
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

# ----- 설정 -----
$ProjectRoot = $PSScriptRoot
$Gradle      = Join-Path $ProjectRoot "app\build.gradle.kts"
$VersionJson = Join-Path $ProjectRoot "version.json"
$ApkOut      = Join-Path $ProjectRoot "app\build\outputs\apk\debug\app-debug.apk"
$AssetName   = "WlsReminder.apk"   # 버전마다 동일하게 유지 → 다운로드 URL 고정
$Repo        = "32m1nd0t/WlsAlarm"
$DownloadUrl = "https://github.com/$Repo/releases/latest/download/$AssetName"

# Android Studio 내장 JDK (JAVA_HOME 비어 있을 때만)
if (-not $env:JAVA_HOME) {
    $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
}

# gh 가 PATH 에 없으면 기본 설치 경로 보강 (설치 직후 PATH 미반영 대비)
if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    $ghDir = "C:\Program Files\GitHub CLI"
    if (Test-Path (Join-Path $ghDir "gh.exe")) { $env:PATH = "$env:PATH;$ghDir" }
}

function Write-NoBom([string]$path, [string]$text) {
    $enc = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($path, $text, $enc)
}

# ----- 1. 현재 버전 읽기 -----
$gradleText = Get-Content $Gradle -Raw
$curCode = [int]([regex]::Match($gradleText, 'versionCode\s*=\s*(\d+)').Groups[1].Value)
$curName = [regex]::Match($gradleText, 'versionName\s*=\s*"([^"]+)"').Groups[1].Value
if (-not $curName) { throw "build.gradle.kts 에서 versionName 을 찾지 못했습니다." }

# ----- 2. 새 버전 계산 -----
$newCode = $curCode + 1
if ($Version) {
    $newName = $Version
} else {
    $parts = $curName.Split(".")
    $vMajor = [int]$parts[0]
    $vMinor = if ($parts.Length -gt 1) { [int]$parts[1] } else { 0 }
    $vPatch = if ($parts.Length -gt 2) { [int]$parts[2] } else { 0 }
    if     ($Major) { $vMajor++; $vMinor = 0; $vPatch = 0 }
    elseif ($Minor) { $vMinor++;              $vPatch = 0 }
    else            {                          $vPatch++   }
    $newName = "$vMajor.$vMinor.$vPatch"
}
Write-Host "버전: $curName (code $curCode)  ->  $newName (code $newCode)" -ForegroundColor Cyan

# 동일 태그 존재 검사
$existingTag = git tag --list "v$newName"
if ($existingTag) { throw "태그 v$newName 가 이미 존재합니다. -Version 으로 다른 버전을 지정하세요." }

if ($DryRun) {
    Write-Host "[DryRun] build.gradle.kts: versionCode=$newCode, versionName=$newName" -ForegroundColor Yellow
    Write-Host "[DryRun] version.json url: $DownloadUrl" -ForegroundColor Yellow
    Write-Host "[DryRun] gradlew assembleDebug -> $AssetName 업로드 -> git push + gh release v$newName" -ForegroundColor Yellow
    return
}

# ----- 3. build.gradle.kts 갱신 -----
$gradleText = $gradleText -replace 'versionCode\s*=\s*\d+',        "versionCode = $newCode"
$gradleText = $gradleText -replace 'versionName\s*=\s*"[^"]+"',    "versionName = `"$newName`""
Write-NoBom $Gradle $gradleText

# ----- 4. version.json 갱신 -----
$jsonText = "{`n  `"version`": `"$newName`",`n  `"url`": `"$DownloadUrl`"`n}`n"
Write-NoBom $VersionJson $jsonText

# ----- 5. APK 빌드 -----
Write-Host "APK 빌드 중..." -ForegroundColor Cyan
& "$ProjectRoot\gradlew.bat" assembleDebug
if ($LASTEXITCODE -ne 0) { throw "Gradle 빌드 실패" }
if (-not (Test-Path $ApkOut)) { throw "APK 산출물을 찾지 못함: $ApkOut" }

# 고정 이름으로 복사 (Release 에셋 이름 고정)
$releaseApk = Join-Path $ProjectRoot $AssetName
Copy-Item $ApkOut $releaseApk -Force

# ----- 6. git 커밋 + 태그 + 푸시 -----
git add -A
git commit -m "release: v$newName (code $newCode)"
git tag -a "v$newName" -m "v$newName"
$branch = git rev-parse --abbrev-ref HEAD
git push origin $branch --follow-tags
if ($LASTEXITCODE -ne 0) { throw "git push 실패 (인증/원격 설정 확인)" }

# ----- 7. GitHub Release 생성 + APK 업로드 -----
gh release create "v$newName" $releaseApk --repo $Repo --title "v$newName" --notes "버전 $newName 업데이트" --latest
if ($LASTEXITCODE -ne 0) { throw "gh release 생성 실패 (gh 설치/로그인 확인)" }

Write-Host ""
Write-Host "완료! ✅  v$newName 배포됨" -ForegroundColor Green
Write-Host "다운로드 URL: $DownloadUrl" -ForegroundColor Green

param(
  [string]$Version = "",
  [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"

$root = Resolve-Path (Join-Path $PSScriptRoot "..")
$buildFile = Join-Path $root "app\build.gradle.kts"
$buildContent = Get-Content $buildFile -Raw

# 1. Parse Version details from build.gradle.kts automatically if not provided
if ($Version -eq "") {
  if ($buildContent -notmatch 'versionName\s*=\s*"(?<version>\d+\.\d+\.\d+)"') {
    throw "app/build.gradle.kts에서 versionName을 찾을 수 없습니다."
  }
  $Version = $Matches.version
}

if ($buildContent -notmatch 'versionCode\s*=\s*(?<code>\d+)') {
  throw "app/build.gradle.kts에서 versionCode를 찾을 수 없습니다."
}
$versionCode = $Matches.code

Write-Host "릴리즈 타겟 분석 완료: Version Name = v$Version, Version Code = $versionCode"

# 2. Resolve Windows Desktop Path
$desktop = [Environment]::GetFolderPath("Desktop")
if ([string]::IsNullOrWhiteSpace($desktop)) {
  $desktop = Join-Path $env:USERPROFILE "Desktop"
}
# Fallback to OneDrive Desktop if default is missing or redirects
if (-not (Test-Path $desktop)) {
  $desktop = Join-Path $env:USERPROFILE "OneDrive\바탕 화면"
}
if (-not (Test-Path $desktop)) {
  $desktop = Join-Path $env:USERPROFILE "OneDrive\Desktop"
}

# Always create and use the 'Build' folder on the Desktop
$buildDir = Join-Path $desktop "Build"
if (-not (Test-Path $buildDir)) {
  New-Item -ItemType Directory -Path $buildDir | Out-Null
  Write-Host "생성된 Build 폴더 경로: $buildDir"
} else {
  Write-Host "기존 Build 폴더 경로 사용: $buildDir"
}

Write-Host "바탕화면 복사 목적지 경로: $buildDir"

# 3. Path references
$aabSource = Join-Path $root "app\build\outputs\bundle\release\app-release.aab"
$notesKoSource = Join-Path $root "play-console\release-notes\$Version-ko-KR.txt"
$notesEnSource = Join-Path $root "play-console\release-notes\$Version-en-US.txt"

$aabTarget = Join-Path $buildDir "PulpitInk-v$Version-vc$versionCode.aab"
$notesTarget = Join-Path $buildDir "PulpitInk-v$Version-vc$versionCode-release-notes.txt"

# 4. Generate Combined Release Notes
$combinedNotes = ""
if (Test-Path $notesKoSource) {
  $koContent = Get-Content $notesKoSource -Encoding utf8 -Raw
  $combinedNotes += "<ko-KR>`n$($koContent.Trim())`n</ko-KR>`n"
} else {
  Write-Warning "한국어 출시 노트가 존재하지 않습니다: $notesKoSource"
}

if (Test-Path $notesEnSource) {
  $enContent = Get-Content $notesEnSource -Encoding utf8 -Raw
  $combinedNotes += "`n<en-US>`n$($enContent.Trim())`n</en-US>`n"
} else {
  Write-Warning "영어 출시 노트가 존재하지 않습니다: $notesEnSource"
}

if ($combinedNotes -ne "") {
  # Play Console hard limit: 500 Unicode chars per locale block (excluding tags).
  # Over-limit text is silently truncated by Play Console — abort export instead
  # of letting a bad file reach the desktop.
  $localePattern = '<(ko-KR|en-US|ja-JP|zh-CN|zh-TW)>([\s\S]*?)</\1>'
  $violations = @()
  foreach ($match in [regex]::Matches($combinedNotes, $localePattern)) {
      $locale = $match.Groups[1].Value
      $body = $match.Groups[2].Value.Trim()
      $len = $body.Length
      $status = if ($len -gt 500) { 'OVER' } else { 'OK' }
      Write-Host ("  {0,-7}  {1,4} / 500  {2}" -f $locale, $len, $status)
      if ($len -gt 500) {
          $violations += "$locale ($len chars, $($len - 500) over)"
      }
  }
  if ($violations.Count -gt 0) {
      throw "Play Console release notes exceed the 500-character limit per locale: " +
          ($violations -join ', ') +
          ". Trim before exporting."
  }

  [System.IO.File]::WriteAllText($notesTarget, $combinedNotes)
  Write-Host "통합 출시 노트 내보내기 완료: $notesTarget"
}

# 5. Build AAB Bundle via Gradle
if (-not $SkipBuild) {
  Write-Host "Gradle 릴리즈 AAB 빌드를 시작합니다 (gradlew :app:bundleRelease)..."
  Push-Location $root
  try {
    & .\gradlew.bat :app:bundleRelease
    if ($LASTEXITCODE -ne 0) {
      throw "Gradle bundleRelease 빌드 실패"
    }
  } finally {
    Pop-Location
  }
}

# 6. Verify and Copy signed AAB
if (-not (Test-Path $aabSource)) {
  throw "AAB 파일이 존재하지 않습니다. 먼저 릴리즈 빌드를 완료하세요: $aabSource"
}

Copy-Item -LiteralPath $aabSource -Destination $aabTarget -Force

Write-Host "`n===============================================" -ForegroundColor Green
Write-Host "Pulpit Ink v$Version-vc$versionCode Desktop Export Successful!" -ForegroundColor Green
Write-Host "AAB: $aabTarget" -ForegroundColor Cyan
Write-Host "Notes: $notesTarget" -ForegroundColor Cyan
Write-Host "===============================================" -ForegroundColor Green

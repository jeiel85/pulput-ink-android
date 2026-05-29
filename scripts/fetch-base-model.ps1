#requires -Version 5.1
<#
.SYNOPSIS
  Fetches the default Whisper model (ggml-base.bin) into the Play Asset Delivery
  pack so it can be bundled into the app bundle (AAB).

.DESCRIPTION
  The 142 MB model binary is NOT committed to git (see .gitignore). It must be
  present at build time for the :base_model asset pack to be packaged. Run this
  before building a release bundle:

      pwsh -File scripts/fetch-base-model.ps1

  Re-running is cheap: if a valid file already exists it is left untouched.
  Pass -Force to re-download.

.NOTES
  Source: https://huggingface.co/ggerganov/whisper.cpp (ggml-base.bin, ~142 MB)
#>
[CmdletBinding()]
param(
  [switch]$Force
)

$ErrorActionPreference = 'Stop'

$ModelUrl   = 'https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin'
# ggml-base.bin is ~141 MB. A correct download must clear this floor; anything
# smaller is almost certainly an HTML error page or a truncated transfer.
$MinBytes   = 120MB

$repoRoot   = Split-Path -Parent $PSScriptRoot
$targetDir  = Join-Path $repoRoot 'base_model\src\main\assets\models'
$targetFile = Join-Path $targetDir 'ggml-base.bin'
$tmpFile    = "$targetFile.download"

New-Item -ItemType Directory -Force -Path $targetDir | Out-Null

if ((Test-Path $targetFile) -and -not $Force) {
  $len = (Get-Item $targetFile).Length
  if ($len -ge $MinBytes) {
    Write-Host "ggml-base.bin already present ($([math]::Round($len/1MB,1)) MB) — skipping download."
    Write-Host "Pass -Force to re-download."
    exit 0
  }
  Write-Warning "Existing ggml-base.bin is only $([math]::Round($len/1MB,1)) MB (< $([math]::Round($MinBytes/1MB)) MB floor); re-downloading."
}

Write-Host "Downloading ggml-base.bin from Hugging Face..."
Write-Host "  $ModelUrl"

if (Test-Path $tmpFile) { Remove-Item $tmpFile -Force }

try {
  # Invoke-WebRequest streams to disk and follows the HF CDN redirect.
  $ProgressPreference = 'SilentlyContinue'  # progress bar slows large downloads dramatically
  Invoke-WebRequest -Uri $ModelUrl -OutFile $tmpFile -MaximumRedirection 5
} catch {
  if (Test-Path $tmpFile) { Remove-Item $tmpFile -Force }
  throw "Download failed: $($_.Exception.Message)"
}

$downloaded = (Get-Item $tmpFile).Length
if ($downloaded -lt $MinBytes) {
  Remove-Item $tmpFile -Force
  throw "Downloaded file is only $([math]::Round($downloaded/1MB,1)) MB (expected >= $([math]::Round($MinBytes/1MB)) MB). Aborting — likely a network error or changed URL."
}

if (Test-Path $targetFile) { Remove-Item $targetFile -Force }
Move-Item $tmpFile $targetFile

Write-Host "Done. ggml-base.bin ready ($([math]::Round($downloaded/1MB,1)) MB) at:"
Write-Host "  $targetFile"

# Creates .env from .env.example with fresh random secrets. Refuses to overwrite an existing .env.
# Usage (repo root): infra/scripts/init-env.ps1 [-StaffPassword "Something-Memorable-1"]
param([string]$StaffPassword = "")

$root = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$example = Join-Path $root ".env.example"
$target = Join-Path $root ".env"
if (Test-Path $target) { Write-Error ".env already exists - delete it first if you really want new secrets."; exit 1 }

function New-Secret([int]$bytes = 24) {
    $b = New-Object byte[] $bytes
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b)
    # URL/shell-safe alphabet (no quotes, $, #, @, :, /)
    ([Convert]::ToBase64String($b)).Replace("+", "A").Replace("/", "B").TrimEnd("=")
}
if (-not $StaffPassword) { $StaffPassword = "Demo-" + (New-Secret 6) + "-1" }

$out = foreach ($line in Get-Content $example) {
    if ($line -match '^\s*#' -or $line -notmatch '=') { $line; continue }
    $name = $line.Split("=", 2)[0]
    switch ($name) {
        "RR_SEED_STAFF_PASSWORD" { "$name=$StaffPassword" }
        "RR_JWT_SECRET"          { "$name=$(New-Secret 48)" }
        default                  { "$name=$(New-Secret)" }
    }
}
[IO.File]::WriteAllLines($target, $out)
Write-Host "Wrote $target. Staff password for the demo: $StaffPassword"

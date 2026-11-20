# "Break the gateway" on demand (Part 2 §12.2).
# Usage: infra/scripts/chaos.ps1 <healthy|slow|flaky|lost-responses|lost-all|blackhole|declines|processing|status>
# The admin token comes from $env:GATEWAY_ADMIN_TOKEN, else from .env at the repo root. Nothing secret is stored here.
param([Parameter(Mandatory = $true)][string]$Profile,
      [string]$Gateway = "http://localhost:8100")

$Token = $env:GATEWAY_ADMIN_TOKEN
if (-not $Token) {
    $envFile = Join-Path $PSScriptRoot "..\..\.env"
    if (Test-Path $envFile) {
        $line = Get-Content $envFile | Where-Object { $_ -match '^GATEWAY_ADMIN_TOKEN=' } | Select-Object -First 1
        if ($line) { $Token = $line.Split("=", 2)[1] }
    }
}
if (-not $Token) { Write-Error "GATEWAY_ADMIN_TOKEN not set (env or .env)"; exit 1 }

$headers = @{ "X-Admin-Token" = $Token }
if ($Profile -eq "status") {
    Invoke-RestMethod -Uri "$Gateway/admin/faults" -Headers $headers | ConvertTo-Json
} else {
    Invoke-RestMethod -Method Post -Uri "$Gateway/admin/faults/profile/$Profile" -Headers $headers | ConvertTo-Json
}

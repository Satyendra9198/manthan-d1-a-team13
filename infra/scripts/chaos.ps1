# "Break the gateway" on demand (Part 2 §12.2).
# Usage: infra/scripts/chaos.ps1 <healthy|slow|flaky|lost-responses|blackhole|declines|processing|status>
param([Parameter(Mandatory = $true)][string]$Profile,
      [string]$Gateway = "http://localhost:8100",
      [string]$Token = $(if ($env:GATEWAY_ADMIN_TOKEN) { $env:GATEWAY_ADMIN_TOKEN } else { "dev-admin-token" }))

$headers = @{ "X-Admin-Token" = $Token }
if ($Profile -eq "status") {
    Invoke-RestMethod -Uri "$Gateway/admin/faults" -Headers $headers | ConvertTo-Json
} else {
    Invoke-RestMethod -Method Post -Uri "$Gateway/admin/faults/profile/$Profile" -Headers $headers | ConvertTo-Json
}

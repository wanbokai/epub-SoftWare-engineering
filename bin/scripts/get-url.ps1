# Show current public tunnel URL (extracted from cloudflared log).

$TunnelLog = "E:\software-reader\bin\scripts\logs\cloudflared.log"

if (-not (Test-Path $TunnelLog)) {
    Write-Host "Tunnel log not found. Run start-reader.ps1 first." -ForegroundColor Yellow
    exit 1
}

$matches = Select-String -Path $TunnelLog -Pattern "https://[a-z0-9\-]+\.trycloudflare\.com" -AllMatches
if ($matches) {
    $url = ($matches | Select-Object -Last 1).Matches.Value | Select-Object -Last 1
    Write-Host ""
    Write-Host "==============================================" -ForegroundColor Cyan
    Write-Host "  Reader public URL:" -ForegroundColor Cyan
    Write-Host "  $url" -ForegroundColor Green
    Write-Host "==============================================" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "(Open this URL in any browser. Keep this PC powered on.)"
    try { Set-Clipboard -Value $url -ErrorAction Stop; Write-Host "Copied to clipboard." -ForegroundColor Green } catch {}
} else {
    Write-Host "No trycloudflare.com URL in log yet. Tunnel may still be starting; retry in a few seconds." -ForegroundColor Yellow
}

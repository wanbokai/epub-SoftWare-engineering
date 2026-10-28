# Reader startup script. Starts SeaweedFS -> backend -> cloudflared tunnel.

$ErrorActionPreference = "Continue"

# --- Fix PowerShell 5.1 env-var case collision ---
foreach ($k in @("http_proxy", "https_proxy", "all_proxy", "no_proxy")) {
    [Environment]::SetEnvironmentVariable($k, $null, "Process")
}

$Weed       = "E:\software-reader\bin\seaweedfs\weed.exe"
$WeedData   = "E:\software-reader\seaweed-data"
$Jar        = "E:\software-reader\reader-api\target\reader-api-0.0.1-SNAPSHOT.jar"
$Java       = "C:\Program Files\Java\jdk-26.0.2.1\bin\java.exe"
$Cloudflared = "C:\Program Files (x86)\cloudflared\cloudflared.exe"
$LogDir     = "E:\software-reader\bin\scripts\logs"
$TunnelLog  = "$LogDir\cloudflared.log"

if (-not (Test-Path $LogDir)) { New-Item -ItemType Directory -Path $LogDir -Force | Out-Null }

# 1. SeaweedFS (use cmd start /min, more reliable than Start-Process here)
$weedRunning = Get-NetTCPConnection -LocalPort 19333 -State Listen -ErrorAction SilentlyContinue
if (-not $weedRunning) {
    $weedArgs = "server -dir `"$WeedData`" -filer -s3 -s3.port 8333 -ip 127.0.0.1 -volume.port 18080 -master.port 19333"
    Start-Process -FilePath "cmd.exe" -ArgumentList @("/c", "start", "`"weed`"", "/min", "`"$Weed`"", $weedArgs)
    Write-Output "[START] SeaweedFS"
} else {
    Write-Output "[SKIP] SeaweedFS already running"
}

# 2. Backend
$apiRunning = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
if (-not $apiRunning) {
    try {
        Start-Process -FilePath $Java -ArgumentList @("-jar",$Jar,"--server.port=8080","--logging.file.name=$LogDir\backend.log")
        Write-Output "[START] Backend on 8080"
    } catch {
        Write-Output ("[FAIL] Backend: " + $_.Exception.Message)
    }
} else {
    Write-Output "[SKIP] Backend already running on 8080"
}

# 3. cloudflared tunnel
$tunnelRunning = Get-Process cloudflared -ErrorAction SilentlyContinue
if (-not $tunnelRunning) {
    try {
        Start-Process -FilePath $Cloudflared -ArgumentList @("tunnel","--url","http://localhost:8080","--logfile",$TunnelLog,"--loglevel","info")
        Write-Output "[START] cloudflared tunnel"
    } catch {
        Write-Output ("[FAIL] cloudflared: " + $_.Exception.Message)
    }
} else {
    Write-Output "[SKIP] cloudflared already running"
}

Write-Output "Done. After ~20s run get-url.ps1 to see the public URL."

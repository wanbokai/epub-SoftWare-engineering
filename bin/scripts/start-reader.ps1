# Reader startup script (autostart). Starts SeaweedFS -> backend -> cloudflared tunnel.
# Tunnel log is written to a file so the public URL can be extracted later.

$ErrorActionPreference = "Continue"

# --- 修复 PowerShell 5.1 环境变量大小写冲突 ---
# 环境里同时存在 HTTP_PROXY/http_proxy 等大小写变体，Windows 不区分大小写，
# 会导致 Start-Process 给子进程准备环境时抛「已添加具有相同键的项」异常。
# 这里在启动前，把冲突的小写变体在当前进程里移除，统一保留大写版本。
foreach ($k in @("http_proxy", "https_proxy", "all_proxy", "no_proxy")) {
    [Environment]::SetEnvironmentVariable($k, $null, "Process")
}
# 确保大写代理变量有值（若原先只有小写，则补回大写，避免丢代理）
if (-not $env:HTTP_PROXY -and -not $env:http_proxy) {
    # 无代理，无需处理
}

$Weed       = "E:\software-reader\bin\seaweedfs\weed.exe"
$WeedData   = "E:/software-reader/seaweed-data"
$Jar        = "E:\software-reader\reader-api\target\reader-api-0.0.1-SNAPSHOT.jar"
$Java       = "C:\Program Files\Java\jdk-26.0.2.1\bin\java.exe"
$Cloudflared = "C:\Program Files (x86)\cloudflared\cloudflared.exe"
$LogDir     = "E:\software-reader\bin\scripts\logs"
$TunnelLog  = "$LogDir\cloudflared.log"

if (-not (Test-Path $LogDir)) { New-Item -ItemType Directory -Path $LogDir -Force | Out-Null }

# 1. SeaweedFS
$weedRunning = Get-NetTCPConnection -LocalPort 19333 -State Listen -ErrorAction SilentlyContinue
if (-not $weedRunning) {
    Start-Process -FilePath $Weed -ArgumentList "server","-dir",$WeedData,"-filer","-s3","-s3.port","8333","-ip","127.0.0.1","-volume.port","18080","-master.port","19333" -WindowStyle Hidden
    Write-Output "[START] SeaweedFS"
} else {
    Write-Output "[SKIP] SeaweedFS already running"
}

# 2. Backend
$apiRunning = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
if (-not $apiRunning) {
    Start-Process -FilePath $Java -ArgumentList "-jar",$Jar,"--server.port=8080" -WindowStyle Hidden
    Write-Output "[START] Backend on 8080"
} else {
    Write-Output "[SKIP] Backend already running on 8080"
}

# 3. cloudflared tunnel
$tunnelRunning = Get-Process cloudflared -ErrorAction SilentlyContinue
if (-not $tunnelRunning) {
    Start-Process -FilePath $Cloudflared -ArgumentList "tunnel","--url","http://localhost:8080","--logfile",$TunnelLog,"--loglevel","info" -WindowStyle Hidden
    Write-Output "[START] cloudflared tunnel"
} else {
    Write-Output "[SKIP] cloudflared already running"
}

Write-Output "Done. Run get-url.ps1 after the tunnel URL is generated."

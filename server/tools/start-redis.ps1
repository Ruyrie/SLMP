# 本机开发用：后台启动 Redis 7.x（端口 6379，开启 AOF，无密码）
# 用法：powershell -ExecutionPolicy Bypass -File .\start-redis.ps1

$ErrorActionPreference = 'Stop'

$Port     = 6379
$RedisDir = Join-Path $PSScriptRoot 'redis'
$RedisExe = Join-Path $RedisDir 'redis-server.exe'
$DataDir  = Join-Path $RedisDir 'data'
$LogFile  = Join-Path $RedisDir 'redis.log'
$ErrFile  = Join-Path $RedisDir 'redis.err.log'

if (-not (Test-Path $RedisExe)) {
    Write-Host "[Redis] 未找到 $RedisExe，请先按 README.md 下载 Redis。" -ForegroundColor Red
    exit 1
}

# 端口已被监听则提示并退出
$listener = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
if ($listener) {
    $owner = Get-Process -Id $listener.OwningProcess -ErrorAction SilentlyContinue
    Write-Host "[Redis] 端口 $Port 已被进程 $($owner.ProcessName) (PID $($listener.OwningProcess)) 监听，跳过启动。" -ForegroundColor Yellow
    exit 0
}

New-Item -ItemType Directory -Force -Path $DataDir | Out-Null

# 以 redis 目录为工作目录，数据目录用相对路径，避免 msys2 对 Windows 盘符路径的兼容问题
# redis.conf 中 logfile 为空（输出到 stdout），这里把 stdout 重定向到 redis.log
$redisArgs = @('redis.conf', '--port', "$Port", '--appendonly', 'yes', '--dir', './data')

$proc = Start-Process -FilePath $RedisExe `
    -ArgumentList $redisArgs `
    -WorkingDirectory $RedisDir `
    -RedirectStandardOutput $LogFile `
    -RedirectStandardError $ErrFile `
    -WindowStyle Hidden `
    -PassThru

Write-Host "[Redis] 已启动 redis-server (PID $($proc.Id))，端口 $Port，数据目录 $DataDir，日志 $LogFile" -ForegroundColor Green

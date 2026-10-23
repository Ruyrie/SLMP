# 本机开发用：停止 Redis 与 MinIO（按进程名 redis-server、minio）
# 用法：powershell -ExecutionPolicy Bypass -File .\stop-dev.ps1

$RedisCli = Join-Path $PSScriptRoot 'redis\redis-cli.exe'

# Redis 先尝试 shutdown 正常关闭，让 AOF 落盘；超时后再强制结束
$redisProcs = @(Get-Process -Name 'redis-server' -ErrorAction SilentlyContinue)
if ($redisProcs.Count -gt 0 -and (Test-Path $RedisCli)) {
    & $RedisCli -p 6379 shutdown 2>$null | Out-Null
    for ($i = 0; $i -lt 10; $i++) {
        if (-not (Get-Process -Name 'redis-server' -ErrorAction SilentlyContinue)) { break }
        Start-Sleep -Milliseconds 500
    }
}

foreach ($name in @('redis-server', 'minio')) {
    $before = if ($name -eq 'redis-server') { $redisProcs } else { @(Get-Process -Name $name -ErrorAction SilentlyContinue) }
    if ($before.Count -eq 0) {
        Write-Host "[$name] 未在运行" -ForegroundColor DarkGray
        continue
    }
    # 仍存活的进程强制结束
    Get-Process -Name $name -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
    foreach ($p in $before) {
        Write-Host "[$name] 已停止 PID $($p.Id)" -ForegroundColor Green
    }
}

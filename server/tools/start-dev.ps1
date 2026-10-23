# 本机开发用：一键启动 Redis + MinIO，并检查端口是否在监听
# 用法：powershell -ExecutionPolicy Bypass -File .\start-dev.ps1

& (Join-Path $PSScriptRoot 'start-redis.ps1')
& (Join-Path $PSScriptRoot 'start-minio.ps1')

# 等待服务就绪（最多约 15 秒）
$ports = [ordered]@{ 'Redis' = 6379; 'MinIO API' = 9000; 'MinIO Console' = 9001 }
Start-Sleep -Seconds 3
for ($i = 0; $i -lt 12; $i++) {
    $pending = @($ports.Values | Where-Object { -not (Get-NetTCPConnection -LocalPort $_ -State Listen -ErrorAction SilentlyContinue) })
    if ($pending.Count -eq 0) { break }
    Start-Sleep -Seconds 1
}

Write-Host ''
Write-Host '===== 端口检查 =====' -ForegroundColor Cyan
$allOk = $true
foreach ($item in $ports.GetEnumerator()) {
    if (Get-NetTCPConnection -LocalPort $item.Value -State Listen -ErrorAction SilentlyContinue) {
        Write-Host ("[OK]   {0,-14} 端口 {1} 正在监听" -f $item.Key, $item.Value) -ForegroundColor Green
    }
    else {
        Write-Host ("[FAIL] {0,-14} 端口 {1} 未监听，请查看对应日志" -f $item.Key, $item.Value) -ForegroundColor Red
        $allOk = $false
    }
}

if (-not $allOk) { exit 1 }

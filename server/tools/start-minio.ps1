# 本机开发用：后台启动 MinIO（API 端口 9000，控制台端口 9001）
# 用法：powershell -ExecutionPolicy Bypass -File .\start-minio.ps1

$ErrorActionPreference = 'Stop'

$ApiPort     = 9000
$ConsolePort = 9001
$RootUser     = 'minioadmin'
$RootPassword = 'minioadmin123'

$MinioDir = Join-Path $PSScriptRoot 'minio'
$MinioExe = Join-Path $MinioDir 'minio.exe'
$DataDir  = Join-Path $MinioDir 'data'
$LogFile  = Join-Path $MinioDir 'minio.log'
$OutFile  = Join-Path $MinioDir 'minio.out.log'

if (-not (Test-Path $MinioExe)) {
    Write-Host "[MinIO] 未找到 $MinioExe，请先按 README.md 下载 MinIO。" -ForegroundColor Red
    exit 1
}

# 任一端口已被监听则提示并退出
foreach ($p in @($ApiPort, $ConsolePort)) {
    $listener = Get-NetTCPConnection -LocalPort $p -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($listener) {
        $owner = Get-Process -Id $listener.OwningProcess -ErrorAction SilentlyContinue
        Write-Host "[MinIO] 端口 $p 已被进程 $($owner.ProcessName) (PID $($listener.OwningProcess)) 监听，跳过启动。" -ForegroundColor Yellow
        exit 0
    }
}

New-Item -ItemType Directory -Force -Path $DataDir | Out-Null

$minioArgs = @('server', 'data', '--address', ":$ApiPort", '--console-address', ":$ConsolePort")

# 账号密码只在本脚本进程内临时设置，子进程继承后立即还原，避免污染调用方会话
$oldUser = $env:MINIO_ROOT_USER
$oldPass = $env:MINIO_ROOT_PASSWORD
try {
    $env:MINIO_ROOT_USER     = $RootUser
    $env:MINIO_ROOT_PASSWORD = $RootPassword

    # MinIO 的启动信息和运行日志输出到 stderr，因此 stderr 写入 minio.log
    $proc = Start-Process -FilePath $MinioExe `
        -ArgumentList $minioArgs `
        -WorkingDirectory $MinioDir `
        -RedirectStandardOutput $OutFile `
        -RedirectStandardError $LogFile `
        -WindowStyle Hidden `
        -PassThru
}
finally {
    $env:MINIO_ROOT_USER     = $oldUser
    $env:MINIO_ROOT_PASSWORD = $oldPass
}

Write-Host "[MinIO] 已启动 minio (PID $($proc.Id))，API http://127.0.0.1:$ApiPort ，控制台 http://127.0.0.1:$ConsolePort ，账号 $RootUser / $RootPassword" -ForegroundColor Green
Write-Host "[MinIO] 数据目录 $DataDir，日志 $LogFile" -ForegroundColor Green

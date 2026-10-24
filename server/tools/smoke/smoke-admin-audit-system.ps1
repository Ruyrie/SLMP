# 管理端「审核中心 + 用户与系统」运行态冒烟（默认 18081 / zhimu_admin_audit_test / Redis 14）
# 用法：pwsh -File server/tools/smoke/smoke-admin-audit-system.ps1
# 说明：只发 GET；登录后若 must_change_password=true，会用 NewPassword 改密并重新登录（只影响冒烟库）。
param(
    [string]$BaseUrl = 'http://127.0.0.1:18081',
    [string]$Username = 'zhoumin',
    [string]$Password = 'Zmt12345',
    [string]$NewPassword = 'Zmt12345A',
    [string]$RedisCli = '',
    [int]$RedisDatabase = 14,
    [string]$RedisPassword = '',
    [string]$Origin = 'http://127.0.0.1:5173'
)

$ErrorActionPreference = 'Stop'
$headers = @{ 'X-Client' = 'ADMIN_WEB'; 'Origin' = $Origin }
$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
if (-not $RedisCli) {
    $bundled = Join-Path $PSScriptRoot '..\redis\redis-cli.exe'
    $onPath = Get-Command redis-cli -ErrorAction SilentlyContinue
    $RedisCli = if (Test-Path $bundled) { $bundled } elseif ($onPath) { $onPath.Source } else { $bundled }
}

function Invoke-Api {
    param([string]$Method, [string]$Path, [object]$Body = $null)
    $params = @{
        Uri = "$BaseUrl$Path"; Method = $Method; Headers = $headers
        WebSession = $session; SkipHttpErrorCheck = $true; TimeoutSec = 60
    }
    if ($null -ne $Body) {
        $params.ContentType = 'application/json; charset=utf-8'
        $params.Body = ($Body | ConvertTo-Json -Compress -Depth 10)
    }
    $resp = Invoke-WebRequest @params
    $text = [System.Text.Encoding]::UTF8.GetString($resp.RawContentStream.ToArray())
    $json = $null
    try { $json = $text | ConvertFrom-Json -Depth 50 } catch { }
    [pscustomobject]@{
        Http = [int]$resp.StatusCode; Text = $text; Json = $json
        Code = if ($null -ne $json) { $json.code } else { $null }
        Message = if ($null -ne $json) { $json.message } else { '' }
        Data = if ($null -ne $json) { $json.data } else { $null }
    }
}

function New-Captcha {
    $captcha = Invoke-Api -Method 'GET' -Path '/admin/v1/auth/captcha'
    if ($captcha.Http -ne 200 -or $captcha.Code -ne 0) { throw "验证码接口失败：$($captcha.Text)" }
    $id = $captcha.Data.captcha_id
    if (-not (Test-Path $RedisCli)) {
        throw "未找到 redis-cli（$RedisCli），无法自动读取图形验证码；请从 Redis 读取 admin:captcha:$id 后手工重试"
    }
    $cliArgs = @('-h', '127.0.0.1', '-p', '6379', '-n', "$RedisDatabase")
    if ($RedisPassword) { $cliArgs += @('-a', $RedisPassword, '--no-auth-warning') }
    $cliArgs += @('GET', "admin:captcha:$id")
    $code = (& $RedisCli @cliArgs 2>&1 | Out-String).Trim()
    if (-not $code -or $code.StartsWith('Could not connect')) {
        throw "读取 Redis 验证码失败：$code"
    }
    [pscustomobject]@{ Id = $id; Code = $code }
}

function Login {
    $captcha = New-Captcha
    $result = Invoke-Api -Method 'POST' -Path '/admin/v1/auth/login' -Body @{
        username = $Username; password = $Password
        captcha_id = $captcha.Id; captcha_code = $captcha.Code
    }
    if ($result.Http -ne 200 -or $result.Code -ne 0) {
        throw "管理员登录失败：http=$($result.Http) $($result.Text)"
    }
    $result
}

$login = Login
if ($login.Data.must_change_password) {
    $change = Invoke-Api -Method 'PUT' -Path '/admin/v1/auth/password' -Body @{
        old_password = $Password; new_password = $NewPassword; confirm_password = $NewPassword
    }
    if ($change.Http -ne 200 -or $change.Code -ne 0) {
        throw "首次改密失败：http=$($change.Http) $($change.Text)"
    }
    $Password = $NewPassword
    $session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
    $login = Login
    Write-Host "已完成首次改密并重新登录" -ForegroundColor Yellow
}

$checks = @(
    @{ Name = '四类待审核角标'; Path = '/admin/v1/audit/pending-counts' },
    @{ Name = 'R01 注册审核列表'; Path = '/admin/v1/farm-audits?page=1&size=20' },
    @{ Name = 'R01 注册审核详情'; Path = '/admin/v1/farm-audits/3002' },
    @{ Name = 'R02 资料变更列表'; Path = '/admin/v1/farm-changes?page=1&size=20' },
    @{ Name = 'R03 修改申请列表'; Path = '/admin/v1/change-requests?page=1&size=20' },
    @{ Name = 'R04 账号找回列表'; Path = '/admin/v1/account-recoveries?page=1&size=20' },
    @{ Name = 'S02 平台概览'; Path = '/admin/v1/overview' },
    @{ Name = 'S02 养殖场分布'; Path = '/admin/v1/overview/farm-map?level=PROVINCE' },
    @{ Name = 'S02 地区明细'; Path = '/admin/v1/overview/regions/150000' },
    @{ Name = 'S02 近30天趋势'; Path = '/admin/v1/overview/trend?metric=ACTIVE_USER' },
    @{ Name = 'S03 用户账号列表'; Path = '/admin/v1/users?page=1&size=20' },
    @{ Name = 'S03 用户账号详情'; Path = '/admin/v1/users/10001' },
    @{ Name = 'S04 养殖场列表'; Path = '/admin/v1/farms?page=1&size=20' },
    @{ Name = 'S04 养殖场详情'; Path = '/admin/v1/farms/1001' }
)

$failures = 0
foreach ($check in $checks) {
    $result = Invoke-Api -Method 'GET' -Path $check.Path
    $ok = $result.Http -eq 200 -and $result.Code -eq 0 -and $null -ne $result.Data
    if (-not $ok) { $failures++ }
    $color = if ($ok) { 'Green' } else { 'Red' }
    $snippet = if ($ok) { '' } else { $result.Text.Substring(0, [Math]::Min(240, $result.Text.Length)) }
    Write-Host ("[{0}] {1,-18} {2,-52} http={3} code={4} {5}" -f
        $(if ($ok) { 'OK' } else { 'FAIL' }), $check.Name, $check.Path, $result.Http, $result.Code, $snippet) -ForegroundColor $color
}
Write-Host ''
Write-Host ("合计 {0}，通过 {1}，失败 {2}" -f $checks.Count, ($checks.Count - $failures), $failures) `
    -ForegroundColor $(if ($failures -eq 0) { 'Green' } else { 'Red' })
if ($failures -gt 0) { exit 1 }

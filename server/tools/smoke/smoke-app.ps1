# 智牧通 App 端冒烟（服务端启动后执行）
# 用法：powershell -ExecutionPolicy Bypass -File server/tools/smoke/smoke-app.ps1 -BaseUrl http://127.0.0.1:8080
param(
    [string]$BaseUrl = 'http://127.0.0.1:8080',
    [string]$Phone = '13800006620',
    [string]$Password = 'Zmt12345',
    [long]$FarmId = 1001
)

$ErrorActionPreference = 'Continue'
$script:failed = 0

function Call-Api {
    param(
        [string]$Method,
        [string]$Path,
        [string]$Token = '',
        [object]$Body = $null
    )
    $headers = @{
        'X-Client'     = 'APP_ANDROID'
        'X-Farm-Id'    = "$FarmId"
        'X-App-Version'= '1.0.0'
    }
    if ($Token) { $headers['Authorization'] = "Bearer $Token" }
    $uri = "$BaseUrl$Path"
    try {
        $params = @{ Uri = $uri; Method = $Method; Headers = $headers; TimeoutSec = 30 }
        if ($null -ne $Body) {
            $params['ContentType'] = 'application/json; charset=utf-8'
            $params['Body'] = ($Body | ConvertTo-Json -Depth 8 -Compress)
        }
        $resp = Invoke-RestMethod @params
        $code = $resp.code
        $ok = ($code -eq 0)
        if (-not $ok) { $script:failed++ }
        [pscustomobject]@{
            Method = $Method
            Path = $Path
            Http = 200
            Code = $code
            Ok = $ok
            Message = $resp.message
        }
    } catch {
        $script:failed++
        $status = ''
        $code = ''
        $message = $_.Exception.Message
        if ($_.Exception.Response) {
            $status = [int]$_.Exception.Response.StatusCode
            try {
                $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
                $text = $reader.ReadToEnd()
                if ($text) {
                    $json = $text | ConvertFrom-Json
                    $code = $json.code
                    $message = $json.message
                }
            } catch { }
        }
        [pscustomobject]@{
            Method = $Method
            Path = $Path
            Http = $status
            Code = $code
            Ok = $false
            Message = $message
        }
    }
}

Write-Host "登录 $Phone ..." -ForegroundColor Cyan
$login = Call-Api -Method POST -Path '/api/v1/auth/login/password' -Body @{ phone = $Phone; password = $Password }
$token = ''
if ($login.Ok) {
    $raw = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/login/password" -Method POST -ContentType 'application/json' `
        -Body (@{ phone = $Phone; password = $Password } | ConvertTo-Json -Compress)
    $token = $raw.data.access_token
}
if (-not $token) {
    Write-Host '登录失败，后续请求会返回 401。' -ForegroundColor Red
}

$checks = @(
    @('GET', '/api/v1/ping'),
    @('GET', '/api/v1/farms'),
    @('GET', "/api/v1/farms/$FarmId/context"),
    @('GET', '/api/v1/base/categories'),
    @('GET', '/api/v1/base/dicts/PEN_TYPE'),
    @('GET', '/api/v1/home'),
    @('GET', '/api/v1/todos/today?page=1&size=20'),
    @('GET', '/api/v1/messages?page=1&size=20'),
    @('GET', '/api/v1/reminder-settings'),
    @('GET', '/api/v1/production/overview'),
    @('GET', '/api/v1/stock/summary'),
    @('GET', '/api/v1/pens'),
    @('GET', '/api/v1/batches'),
    @('GET', '/api/v1/breeding/overview'),
    @('GET', '/api/v1/feed-tasks/today/summary'),
    @('GET', '/api/v1/feed-tasks/today?page=1&size=20'),
    @('GET', '/api/v1/weigh-records/batch-options'),
    @('GET', '/api/v1/inspections/summary'),
    @('GET', '/api/v1/feed-stock/overview'),
    @('GET', '/api/v1/drug-stock/overview'),
    @('GET', '/api/v1/drug-lots?page=1&size=20'),
    @('GET', '/api/v1/business-overview'),
    @('GET', '/api/v1/finance-records/monthly-summary'),
    @('GET', '/api/v1/sale-orders?page=1&size=20'),
    @('GET', '/api/v1/ai/sessions?page=1&size=20'),
    @('GET', '/api/v1/courses?page=1&size=20'),
    @('GET', '/api/v1/course-categories')
)

$results = @()
foreach ($c in $checks) {
    $r = Call-Api -Method $c[0] -Path $c[1] -Token $token
    $results += $r
    $color = if ($r.Ok) { 'Green' } else { 'Red' }
    Write-Host ("{0,-5} {1,-55} http={2} code={3} {4}" -f $r.Method, $r.Path, $r.Http, $r.Code, $r.Message) -ForegroundColor $color
}

Write-Host ''
Write-Host ("合计 {0} 项，失败 {1} 项" -f $results.Count, $script:failed) -ForegroundColor $(if ($script:failed -eq 0) { 'Green' } else { 'Red' })
if ($script:failed -gt 0) { exit 1 }

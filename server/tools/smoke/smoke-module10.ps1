# 模块10 运行态冒烟（8081）：按 App 实际传参口径的 21 个 GET，用于最终验收“101 调用 = 80 主清单 + 21 模块10”。
# 用法：pwsh -NoProfile -File server/tools/smoke/smoke-module10.ps1 [-OutDir ...]
# 只发 GET + 登录 POST；不改数据。结果写 <OutDir>/module10-results.json。
param(
    [string]$BaseUrl = 'http://127.0.0.1:8081',
    [string]$Phone = '13800006620',
    [string]$Password = 'Zmt12345',
    [long]$FarmId = 1001,
    [string]$OutDir = ''
)

$ErrorActionPreference = 'Stop'
if (-not $OutDir) { $OutDir = Join-Path $PSScriptRoot 'logs\final\module10' }
New-Item -ItemType Directory -Force $OutDir | Out-Null

function Get-Text($resp) {
    [System.Text.Encoding]::UTF8.GetString($resp.RawContentStream.ToArray())
}

function Invoke-Json {
    param([string]$Method, [string]$Path, [hashtable]$Headers, [string]$Body)
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $params = @{
        Uri = "$BaseUrl$Path"; Method = $Method; Headers = $Headers
        SkipHttpErrorCheck = $true; TimeoutSec = 60
    }
    if ($Body) { $params.ContentType = 'application/json; charset=utf-8'; $params.Body = $Body }
    try {
        $resp = Invoke-WebRequest @params
        $sw.Stop()
        [pscustomobject]@{ Http = [int]$resp.StatusCode; Text = (Get-Text $resp); Ms = $sw.ElapsedMilliseconds }
    } catch {
        $sw.Stop()
        [pscustomobject]@{ Http = -1; Text = $_.Exception.Message; Ms = $sw.ElapsedMilliseconds }
    }
}

$loginHeaders = @{ 'X-Client' = 'APP_ANDROID'; 'X-Farm-Id' = "$FarmId"; 'X-App-Version' = '1.0.0' }
$loginBody = @{ phone = $Phone; password = $Password } | ConvertTo-Json -Compress
$login = Invoke-Json -Method 'POST' -Path '/api/v1/auth/login/password' -Headers $loginHeaders -Body $loginBody
$loginJson = $null
try { $loginJson = $login.Text | ConvertFrom-Json -Depth 50 } catch { }
if ($login.Http -ne 200 -or -not $loginJson -or $loginJson.code -ne 0) {
    Write-Host "登录失败 http=$($login.Http) body=$($login.Text.Substring(0, [Math]::Min(300, $login.Text.Length)))" -ForegroundColor Red
    exit 2
}
$headers = @{
    'Authorization' = "Bearer $($loginJson.data.access_token)"; 'X-Farm-Id' = "$FarmId"
    'X-Client' = 'APP_ANDROID'; 'X-App-Version' = '1.0.0'
}
Write-Host ("登录成功 user={0} farm={1}" -f $loginJson.data.user.name, $FarmId) -ForegroundColor Green

# 12 个基础调用 + 9 个详情/补参调用 = 21（与报告 12.4 的 App 口径一致）
$checks = @(
    @{ P = '/api/v1/business-overview'; K = 'business-overview' }
    @{ P = '/api/v1/finance-records?month=2026-10&page=1&size=20'; K = 'finance-records 默认无 keyword（历史 500）' }
    @{ P = '/api/v1/finance-categories'; K = 'finance-categories' }
    @{ P = '/api/v1/market-prices/filters'; K = 'market-prices/filters' }
    @{ P = '/api/v1/market-prices/trend?province_code=150000&category_id=101&breed_id=0&price_type=SLAUGHTER&range_days=30'; K = 'market-prices/trend' }
    @{ P = '/api/v1/market-prices/daily?province_code=150000&category_id=101&breed_id=0&price_type=SLAUGHTER&page=1&size=20'; K = 'market-prices/daily' }
    @{ P = '/api/v1/sale-orders?page=1&size=20'; K = 'sale-orders' }
    @{ P = '/api/v1/batch-settlements?page=1&size=20'; K = 'batch-settlements' }
    @{ P = '/api/v1/business-reports/periods?report_type=MONTHLY'; K = 'business-reports/periods' }
    @{ P = '/api/v1/business-reports/view?report_type=TO_DATE'; K = 'business-reports/view TO_DATE' }
    @{ P = '/api/v1/business-reports/trend?report_type=TO_DATE&metric=BALANCE'; K = 'business-reports/trend' }
    @{ P = '/api/v1/business-reports/ai-summary?report_type=TO_DATE'; K = 'ai-summary TO_DATE' }
    @{ P = '/api/v1/finance-records/8202'; K = 'finance-records/8202' }
    @{ P = '/api/v1/finance-records/monthly-summary?month=2026-10'; K = 'monthly-summary' }
    @{ P = '/api/v1/sale-orders/9102'; K = 'sale-orders/9102' }
    @{ P = '/api/v1/sale-orders/batch-options'; K = 'sale-orders/batch-options' }
    @{ P = '/api/v1/sale-orders/receivables?due_filter=ALL&page=1&size=20'; K = 'sale-orders/receivables' }
    @{ P = '/api/v1/sale-orders/prefill'; K = 'sale-orders/prefill' }
    @{ P = '/api/v1/batch-settlements/3001'; K = 'batch-settlements/3001' }
    @{ P = '/api/v1/business-reports/view?report_type=MONTHLY&period_start=2026-09-01'; K = 'business-reports/view MONTHLY' }
    @{ P = '/api/v1/business-reports/ai-summary?report_type=MONTHLY&period_start=2026-09-01'; K = 'ai-summary MONTHLY（AI 未配置，预期 200/status=FAILED）' }
)

$results = [System.Collections.Generic.List[object]]::new()
$i = 0
foreach ($c in $checks) {
    $i++
    $r = Invoke-Json -Method 'GET' -Path $c.P -Headers $headers
    $json = $null
    try { $json = $r.Text | ConvertFrom-Json -Depth 100 } catch { }
    $code = $null; $msg = ''
    if ($null -ne $json) { $code = $json.code; $msg = $json.message }
    $slug = ($c.P -replace '^/api/v1/', '' -replace '[^A-Za-z0-9]+', '_').Trim('_')
    $bodyFile = Join-Path $OutDir ("module10_{0}_http{1}.json" -f $slug, $r.Http)
    Set-Content -Path $bodyFile -Value $r.Text -Encoding utf8NoBOM
    $is5xx = $r.Http -ge 500
    $ok = ($r.Http -eq 200 -and $code -eq 0)
    $results.Add([pscustomobject]@{
            No = $i; Path = $c.P; Key = $c.K; Http = $r.Http; Code = $code; Message = $msg
            Ms = $r.Ms; Is5xx = $is5xx; Ok = $ok; BodyFile = $bodyFile
        })
    $color = if ($is5xx) { 'Red' } elseif ($ok) { 'Green' } else { 'Yellow' }
    Write-Host ("[{0,2}/{1}] {2,-92} http={3} code={4} {5}ms" -f $i, $checks.Count, $c.P, $r.Http, $code, $r.Ms) -ForegroundColor $color
}

$meta = [pscustomobject]@{
    BaseUrl = $BaseUrl; FarmId = $FarmId; GeneratedAt = (Get-Date).ToString('s')
    Total = $results.Count
    Passed = @($results | Where-Object { $_.Ok }).Count
    Failed = @($results | Where-Object { -not $_.Ok }).Count
    Server5xx = @($results | Where-Object { $_.Is5xx }).Count
}
[pscustomobject]@{ Meta = $meta; Results = $results } | ConvertTo-Json -Depth 10 |
    Set-Content (Join-Path $OutDir 'module10-results.json') -Encoding utf8NoBOM

Write-Host ''
Write-Host ("模块10 合计 {0}，通过 {1}，失败 {2}，5xx {3}" -f $meta.Total, $meta.Passed, $meta.Failed, $meta.Server5xx) `
    -ForegroundColor $(if ($meta.Server5xx -gt 0 -or $meta.Failed -gt 0) { 'Yellow' } else { 'Green' })
foreach ($b in ($results | Where-Object { -not $_.Ok })) {
    Write-Host ("FAIL {0} http={1} code={2} {3}" -f $b.Path, $b.Http, $b.Code, $b.Message) -ForegroundColor Yellow
}
Write-Host "结果文件：$(Join-Path $OutDir 'module10-results.json')"
if ($meta.Server5xx -gt 0) { exit 1 }

# U01 修改申请表单运行态验收（8081）：覆盖 17 种记录类型，重点确认不再出现“handler 未生效”的 42207。
# 用法：pwsh -NoProfile -File server/tools/smoke/smoke-u01-form.ps1 [-OutDir ...]
# 只发登录 POST + GET；不改数据。结果写 <OutDir>/u01-results.json。
#
# 判定口径（与 App/服务端约定一致）：
#   200 + code=0 + fields 非空        -> PASS
#   42207 + “这条记录可以直接修改…”  -> DIRECT_EDIT（handler 已生效，记录尚可直接改，按业务规则无需申请）
#   42207 + “该记录类型暂不支持…”    -> HANDLER_MISSING（验收失败，必须报告）
#   40401                             -> BAD_ID（换记录 ID 复测）
param(
    [string]$BaseUrl = 'http://127.0.0.1:8081',
    [string]$Phone = '13800006620',
    [string]$Password = 'Zmt12345',
    [long]$FarmId = 1001,
    [string]$OutDir = ''
)

$ErrorActionPreference = 'Stop'
if (-not $OutDir) { $OutDir = Join-Path $PSScriptRoot 'logs\final\u01' }
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

# type / biz_id / 说明。primary 为任务指定 ID，alternate 为“需要走修改申请”的可用记录。
# v9_11 未提供示例数据，IMMUNE_RECORD / MEDICATION_RECORD / DISINFECT_RECORD 无记录可测（脚本仍登记为 SKIP）。
$cases = @(
    @{ When = '指定'; Type = 'BATCH_ENTRY'; Id = 3001; Note = '任务指定；入栏 handler 的 biz_id 是流水 ID，3001 是批次 ID' }
    @{ When = '替代'; Type = 'BATCH_ENTRY'; Id = 901; Note = 't_batch_event#901 ENTRY（2026-10-01，批次有后续死淘）' }
    @{ When = '指定'; Type = 'BATCH_TRANSFER'; Id = 907; Note = 't_batch_event#907 TRANSFER（批次 3011 已结束）' }
    @{ When = '指定'; Type = 'BATCH_DEATH'; Id = 903; Note = 't_batch_event#903 DEATH（2026-10-08，超过 3 天）' }
    @{ When = '指定'; Type = 'FARROWING'; Id = 8118; Note = '任务指定；未断奶、未转批次，仍在直接修改期' }
    @{ When = '替代'; Type = 'FARROWING'; Id = 8161; Note = '已断奶并转批次 3052，必须走修改申请' }
    @{ When = '指定'; Type = 'INCUBATION'; Id = 6101; Note = '任务指定；CANDLED 未出雏，可直接改' }
    @{ When = '替代'; Type = 'INCUBATION'; Id = 6004; Note = 'farm 1002（王建国为 MANAGER），已出雏且转入批次 3272' }
    @{ When = '指定'; Type = 'BREEDING_EXIT'; Id = 4102; Note = 't_breeding_animal#4102 已淘汰（CULL）' }
    @{ When = '指定'; Type = 'FEED_RECORD'; Id = 7106; Note = '任务指定；2026-10-19 当天记录，可直接改' }
    @{ When = '替代'; Type = 'FEED_RECORD'; Id = 7001; Note = '2026-10-18，非当天，必须走修改申请' }
    @{ When = '指定'; Type = 'WEIGH_RECORD'; Id = 3300; Note = '任务指定；批次 3001 在养，按规则可直接改；样例中无已结束批次的称重记录' }
    @{ When = '指定'; Type = 'INSPECTION'; Id = 50121; Note = '任务指定当日（2026-10-19）记录，仍在直接修改期' }
    @{ When = '替代'; Type = 'INSPECTION'; Id = 50060; Note = '2026-10-13，超过 3 天，必须走修改申请' }
    @{ When = '替代'; Type = 'INBOUND'; Id = 6201; Note = '入库 6201（饲料 5101）之后已有出库流水，必须走修改申请' }
    @{ When = '指定'; Type = 'STOCKTAKE'; Id = 6301; Note = '已确认盘点单，必须走修改申请' }
    @{ When = '指定'; Type = 'LOSS'; Id = 6401; Note = '报损单，必须走修改申请' }
    @{ When = '指定'; Type = 'OUTBOUND'; Id = 6501; Note = '2026-10-16 登记，非当天，必须走修改申请' }
    @{ When = '指定'; Type = 'SALE_ORDER'; Id = 9106; Note = '任务指定；未收款（received_amount=0），可直接改' }
    @{ When = '替代'; Type = 'SALE_ORDER'; Id = 9102; Note = '已有收款 100000，必须走修改申请' }
)

$results = [System.Collections.Generic.List[object]]::new()
$i = 0
foreach ($c in $cases) {
    $i++
    $path = "/api/v1/change-requests/form?biz_type=$($c.Type)&biz_id=$($c.Id)"
    $r = Invoke-Json -Method 'GET' -Path $path -Headers $headers
    $json = $null
    try { $json = $r.Text | ConvertFrom-Json -Depth 100 } catch { }
    $code = $null; $msg = ''; $data = $null
    if ($null -ne $json) { $code = $json.code; $msg = $json.message; $data = $json.data }
    $props = @()
    if ($null -ne $data -and $null -ne $data.PSObject.Properties['fields']) {
        $props = @($data.fields)
    } else { $props = @() }
    $fieldCodes = ($props | ForEach-Object { $_.field_code }) -join ','
    $bizTypeText = if ($null -ne $data) { $data.biz_type_text } else { $null }
    $recordVersion = if ($null -ne $data) { $data.record_version } else { $null }

    $status = 'FAIL'
    if ($r.Http -eq 200 -and $code -eq 0 -and $props.Count -gt 0 -and $recordVersion) {
        $status = 'PASS'
    } elseif ($code -eq 42207 -and $msg -like '*可以直接修改*') {
        $status = 'DIRECT_EDIT'
    } elseif ($code -eq 42207 -and $msg -like '*暂不支持提交修改申请*') {
        $status = 'HANDLER_MISSING'
    } elseif ($code -eq 40401) {
        $status = 'BAD_ID'
    } elseif ($r.Http -ge 500) {
        $status = 'SERVER_ERROR'
    } elseif ($code -eq 42211) {
        $status = 'BLOCKED_STATE'
    }

    $slug = ($path -replace '^/api/v1/change-requests/form\?', '' -replace '[^A-Za-z0-9]+', '_').Trim('_')
    $bodyFile = Join-Path $OutDir ("u01_{0}_http{1}.json" -f $slug, $r.Http)
    Set-Content -Path $bodyFile -Value $r.Text -Encoding utf8NoBOM

    $results.Add([pscustomobject]@{
            No = $i; When = $c.When; BizType = $c.Type; BizId = $c.Id; Note = $c.Note
            Http = $r.Http; Code = $code; Message = $msg; Status = $status
            BizTypeText = $bizTypeText; RecordVersion = $recordVersion
            FieldCount = $props.Count; FieldCodes = $fieldCodes; Ms = $r.Ms; BodyFile = $bodyFile
        })
    $color = switch ($status) { 'PASS' { 'Green' } 'DIRECT_EDIT' { 'Yellow' } 'BAD_ID' { 'Yellow' } 'BLOCKED_STATE' { 'Yellow' } default { 'Red' } }
    Write-Host ("[{0,2}/{1}] {2,-6} {3,-16} id={4,-5} http={5} code={6} fields={7} {8} {9}" -f `
            $i, $cases.Count, $c.When, $c.Type, $c.Id, $r.Http, $code, $props.Count, $status, $msg) -ForegroundColor $color
}

# 17 种类型的覆盖统计：PASS / DIRECT_EDIT / SKIP（无样例数据）/ FAIL
$allTypes = @('BATCH_ENTRY', 'BATCH_TRANSFER', 'BATCH_DEATH', 'FARROWING', 'INCUBATION', 'BREEDING_EXIT',
    'FEED_RECORD', 'WEIGH_RECORD', 'INSPECTION', 'IMMUNE_RECORD', 'MEDICATION_RECORD', 'DISINFECT_RECORD',
    'INBOUND', 'STOCKTAKE', 'LOSS', 'OUTBOUND', 'SALE_ORDER')
$healthTypes = @('IMMUNE_RECORD', 'MEDICATION_RECORD', 'DISINFECT_RECORD')
$coverage = foreach ($t in $allTypes) {
    $rows = @($results | Where-Object { $_.BizType -eq $t })
    $pass = @($rows | Where-Object { $_.Status -eq 'PASS' }).Count
    $direct = @($rows | Where-Object { $_.Status -eq 'DIRECT_EDIT' }).Count
    $missing = @($rows | Where-Object { $_.Status -eq 'HANDLER_MISSING' }).Count
    $bad = @($rows | Where-Object { $_.Status -eq 'BAD_ID' }).Count
    $state = if ($pass -gt 0) { 'PASS' } elseif ($healthTypes -contains $t) { 'SKIP_NO_SAMPLE' }
        elseif ($missing -gt 0) { 'HANDLER_MISSING' } elseif ($bad -gt 0) { 'BAD_ID' }
        elseif (@($rows | Where-Object { $_.Status -eq 'SERVER_ERROR' }).Count -gt 0) { 'SERVER_ERROR' }
        elseif (@($rows | Where-Object { $_.Status -eq 'BLOCKED_STATE' }).Count -gt 0) { 'BLOCKED_STATE' }
        elseif ($direct -gt 0) { 'DIRECT_EDIT_ONLY' } else { 'FAIL' }
    [pscustomobject]@{ BizType = $t; State = $state; PassCalls = $pass; DirectEditCalls = $direct; BadIdCalls = $bad; HandlerMissingCalls = $missing }
}

$meta = [pscustomobject]@{
    BaseUrl = $BaseUrl; FarmId = $FarmId; GeneratedAt = (Get-Date).ToString('s')
    Calls = $results.Count
    Passed = @($results | Where-Object { $_.Status -eq 'PASS' }).Count
    DirectEdit = @($results | Where-Object { $_.Status -eq 'DIRECT_EDIT' }).Count
    BadId = @($results | Where-Object { $_.Status -eq 'BAD_ID' }).Count
    HandlerMissing = @($results | Where-Object { $_.Status -eq 'HANDLER_MISSING' }).Count
    Server5xx = @($results | Where-Object { $_.Http -ge 500 }).Count
    BlockedState = @($results | Where-Object { $_.Status -eq 'BLOCKED_STATE' }).Count
    TypesPassed = @($coverage | Where-Object { $_.State -eq 'PASS' }).Count
    TypesSkipped = @($coverage | Where-Object { $_.State -eq 'SKIP_NO_SAMPLE' }).Count
}
[pscustomobject]@{ Meta = $meta; Results = $results; Coverage = $coverage } | ConvertTo-Json -Depth 10 |
    Set-Content (Join-Path $OutDir 'u01-results.json') -Encoding utf8NoBOM

Write-Host ''
Write-Host ("U01 调用 {0}：PASS {1}，DIRECT_EDIT {2}，BLOCKED_STATE {3}，BAD_ID {4}，5xx {5}，HANDLER_MISSING {6}" -f `
        $meta.Calls, $meta.Passed, $meta.DirectEdit, $meta.BlockedState, $meta.BadId, $meta.Server5xx, $meta.HandlerMissing) `
    -ForegroundColor $(if ($meta.Server5xx -gt 0 -or $meta.HandlerMissing -gt 0) { 'Red' } else { 'Green' })
Write-Host ("17 种类型：PASS {0}，无样例数据 {1}" -f $meta.TypesPassed, $meta.TypesSkipped)
Write-Host "结果文件：$(Join-Path $OutDir 'u01-results.json')"
if ($meta.Server5xx -gt 0 -or $meta.HandlerMissing -gt 0) { exit 1 }

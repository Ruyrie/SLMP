# 智牧通服务端运行态冒烟（8081 / 独立库 zhimu_smoke）
# 用法：pwsh -File server/tools/smoke/smoke-8081.ps1
# 说明：只发 GET（外加登录 POST），不改数据；结果写 logs/smoke-results.json，响应体写 logs/responses/。
param(
    [string]$BaseUrl = 'http://127.0.0.1:8081',
    [string]$Phone = '13800006620',
    [string]$Password = 'Zmt12345',
    [long]$FarmId = 1001,
    [string]$OutDir = ''
)

$ErrorActionPreference = 'Stop'
if (-not $OutDir) { $OutDir = Join-Path $PSScriptRoot 'logs' }
$respDir = Join-Path $OutDir 'responses'
New-Item -ItemType Directory -Force $OutDir, $respDir | Out-Null
$workspace = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$mockRoot = Join-Path $workspace 'module_{0}\src\debug\assets\mock'
$mockModule = @{ '03' = 'module_today'; '04' = 'module_herd'; '05' = 'module_breed'; '06' = 'module_feed'; '07' = 'module_inspect'; '09' = 'module_stock'; '11' = 'module_ai' }

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
        $text = Get-Text $resp
        $json = $null
        try { $json = $text | ConvertFrom-Json -Depth 100 } catch { }
        [pscustomobject]@{ Http = [int]$resp.StatusCode; Text = $text; Json = $json; Ms = $sw.ElapsedMilliseconds }
    } catch {
        $sw.Stop()
        [pscustomobject]@{ Http = -1; Text = $_.Exception.Message; Json = $null; Ms = $sw.ElapsedMilliseconds }
    }
}

# 把响应 data 拍平成 "路径 -> 非空值"（数组取前若干项并集），用于忽略 null 省略、顺序/状态差异
function Flatten-Shape {
    param($Value, [string]$Path, [hashtable]$Map, [int]$Depth, [int]$MaxItems = 10)
    if ($null -eq $Value) { return }
    if ($Depth -gt 4) { return }
    if ($Value -is [System.Array]) {
        $n = 0
        foreach ($item in $Value) {
            if ($n -ge $MaxItems) { break }
            Flatten-Shape -Value $item -Path $Path -Map $Map -Depth ($Depth + 1) -MaxItems $MaxItems
            $n++
        }
        return
    }
    if ($Value -is [pscustomobject]) {
        foreach ($p in $Value.PSObject.Properties) {
            if ($null -eq $p.Value) { continue }
            $child = if ($Path) { "$Path.$($p.Name)" } else { $p.Name }
            if ($p.Value -is [pscustomobject] -or $p.Value -is [System.Array]) {
                Flatten-Shape -Value $p.Value -Path $child -Map $Map -Depth ($Depth + 1) -MaxItems $MaxItems
            } else {
                $Map[$child] = $p.Value
            }
        }
        return
    }
}

# mock 中非空、但真实响应里完全不存在的字段路径
function Compare-Shape {
    param($Mock, $Actual)
    $mockMap = @{}; $actMap = @{}
    # mock 只取前 3 项，避免把“别的状态才有的字段”算成缺失；真实响应取前 50 项做并集
    Flatten-Shape -Value $Mock -Path 'data' -Map $mockMap -Depth 0 -MaxItems 3
    Flatten-Shape -Value $Actual -Path 'data' -Map $actMap -Depth 0 -MaxItems 50
    $diff = [System.Collections.Generic.List[string]]::new()
    foreach ($k in ($mockMap.Keys | Sort-Object)) {
        if (-not $actMap.ContainsKey($k)) { $diff.Add("$k 缺失（mock 非空）") }
    }
    $diff
}

function Save-Body {
    param([string]$Module, [string]$Path, [int]$Http, [string]$Text)
    $slug = ($Path -replace '^/api/v1/', '' -replace '[^A-Za-z0-9]+', '_').Trim('_')
    $file = Join-Path $respDir ("{0}_{1}_http{2}.json" -f $Module, $slug, $Http)
    Set-Content -Path $file -Value $Text -Encoding utf8NoBOM
    $file
}

$loginHeaders = @{ 'X-Client' = 'APP_ANDROID'; 'X-Farm-Id' = "$FarmId"; 'X-App-Version' = '1.0.0' }
$loginBody = @{ phone = $Phone; password = $Password } | ConvertTo-Json -Compress
$login = Invoke-Json -Method 'POST' -Path '/api/v1/auth/login/password' -Headers $loginHeaders -Body $loginBody
$token = $null
if ($login.Json -and $login.Json.code -eq 0) { $token = $login.Json.data.access_token }
if (-not $token) {
    Write-Host "登录失败：http=$($login.Http) body=$($login.Text.Substring(0,[Math]::Min(300,$login.Text.Length)))" -ForegroundColor Red
    exit 2
}
Write-Host ("登录成功 user={0} farm={1}" -f $login.Json.data.user.name, $FarmId) -ForegroundColor Green

$headers = @{
    'Authorization' = "Bearer $token"; 'X-Farm-Id' = "$FarmId"
    'X-Client' = 'APP_ANDROID'; 'X-App-Version' = '1.0.0'
}

$checks = @(
    # ---- 03 今日（home / 待办 / 消息 / 提醒） ----
    @{ M = '03'; P = '/api/v1/home'; K = 'today/home_owner.json' }
    @{ M = '03'; P = '/api/v1/todos/today'; K = 'today/today_owner.json' }
    @{ M = '03'; P = '/api/v1/todos/calendar?start_date=2026-10-19&end_date=2026-10-25'; K = 'today/calendar_week_owner.json' }
    @{ M = '03'; P = '/api/v1/todos/weekly?week_start=2026-10-19&per_day=3'; K = 'today/weekly_current.json' }
    @{ M = '03'; P = '/api/v1/todos/overdue'; K = 'today/overdue_owner.json' }
    @{ M = '03'; P = '/api/v1/messages?page=1&size=20'; K = 'today/messages_owner_p1.json' }
    @{ M = '03'; P = '/api/v1/messages/unread-count'; K = 'today/unread_owner.json' }
    @{ M = '03'; P = '/api/v1/reminder-settings'; K = 'today/reminder_setting.json' }
    # ---- 04 猪群（概览 / 盘点 / 猪舍 / 批次） ----
    @{ M = '04'; P = '/api/v1/production/overview'; K = 'herd/overview_owner.json' }
    @{ M = '04'; P = '/api/v1/my-pens'; K = 'herd/my_pens_staff.json' }
    @{ M = '04'; P = '/api/v1/stock/summary'; K = 'herd/stock_summary.json' }
    @{ M = '04'; P = '/api/v1/stock/pens'; K = 'herd/stock_pens.json' }
    @{ M = '04'; P = '/api/v1/stock/categories'; K = 'herd/stock_categories.json' }
    @{ M = '04'; P = '/api/v1/pens'; K = '' }
    @{ M = '04'; P = '/api/v1/pens/form-options'; K = 'herd/pen_form_options.json' }
    @{ M = '04'; P = '/api/v1/pens/2003'; K = 'herd/pen_2003.json' }
    @{ M = '04'; P = '/api/v1/pens/2003/executors'; K = 'herd/executors_2003.json' }
    @{ M = '04'; P = '/api/v1/batches?page=1&size=20'; K = 'herd/batches_active.json' }
    @{ M = '04'; P = '/api/v1/batches/3001'; K = 'herd/batch_3001.json' }
    @{ M = '04'; P = '/api/v1/batches/3001/timeline'; K = 'herd/timeline_3001.json' }
    @{ M = '04'; P = '/api/v1/batches/3001/pen-stocks'; K = 'herd/pen_stocks_3001.json' }
    # ---- 05 繁育 ----
    @{ M = '05'; P = '/api/v1/breeding/overview'; K = 'breed/overview_owner.json' }
    @{ M = '05'; P = '/api/v1/breeding-animals?page=1&size=20'; K = 'breed/animals_all_p1.json' }
    @{ M = '05'; P = '/api/v1/breeding-animals/options'; K = '' }
    @{ M = '05'; P = '/api/v1/breeding-animals/0131'; K = 'breed/animal_0131.json' }
    @{ M = '05'; P = '/api/v1/breeding-animals/0131/records'; K = 'breed/records_0131.json' }
    @{ M = '05'; P = '/api/v1/breeding-animals/0131/register-context'; K = '' }
    @{ M = '05'; P = '/api/v1/breeding/pending-checks'; K = 'breed/pending_checks.json' }
    @{ M = '05'; P = '/api/v1/breeding/farrow-due'; K = 'breed/farrow_due_owner.json' }
    @{ M = '05'; P = '/api/v1/breeding/hatch-due'; K = 'breed/hatch_due_1002.json' }
    @{ M = '05'; P = '/api/v1/farrowings/8118'; K = 'breed/farrowing_8118.json' }
    @{ M = '05'; P = '/api/v1/incubations/6001'; K = 'breed/incubation_6001.json' }
    # ---- 06 饲喂 ----
    @{ M = '06'; P = '/api/v1/feed-tasks/today/summary'; K = 'feed/summary_owner.json' }
    @{ M = '06'; P = '/api/v1/feed-tasks/today?page=1&size=20'; K = 'feed/tasks_owner.json' }
    @{ M = '06'; P = '/api/v1/feed-tasks/70107'; K = '' }
    @{ M = '06'; P = '/api/v1/feed-plans/batch-options'; K = 'feed/plan_batches.json' }
    @{ M = '06'; P = '/api/v1/batches/3001/feed-plan'; K = 'feed/plan_3001.json' }
    @{ M = '06'; P = '/api/v1/batches/3001/feed-plan/standard'; K = 'feed/std_fatten.json' }
    @{ M = '06'; P = '/api/v1/feed-executions/summary'; K = '' }
    @{ M = '06'; P = '/api/v1/feed-executions/attention'; K = 'feed/attention_all.json' }
    @{ M = '06'; P = '/api/v1/feed-executions/daily'; K = 'feed/day_generic.json' }
    @{ M = '06'; P = '/api/v1/weigh-records/batch-options'; K = 'feed/weigh_options_all.json' }
    @{ M = '06'; P = '/api/v1/batches/3001/weigh-form'; K = 'feed/weigh_form_3001.json' }
    @{ M = '06'; P = '/api/v1/batches/3001/weigh-records'; K = 'feed/weigh_records_3001.json' }
    @{ M = '06'; P = '/api/v1/batches/3001/growth-curve'; K = 'feed/curve_3001.json' }
    @{ M = '06'; P = '/api/v1/batches/3001/growth-compare-options'; K = 'feed/compare_options_3001.json' }
    # ---- 07 巡检 ----
    @{ M = '07'; P = '/api/v1/inspections/summary'; K = 'inspect/summary_owner.json' }
    @{ M = '07'; P = '/api/v1/inspections?page=1&size=20'; K = 'inspect/list_owner_all.json' }
    @{ M = '07'; P = '/api/v1/inspections/recent-issues'; K = 'inspect/recent_issues.json' }
    @{ M = '07'; P = '/api/v1/inspection-settings/current'; K = 'inspect/setting.json' }
    @{ M = '07'; P = '/api/v1/inspections/form-context?pen_id=2003'; K = 'inspect/form_2003_registered_owner.json' }
    @{ M = '07'; P = '/api/v1/pens/2003/inspections'; K = 'inspect/history_2003.json' }
    @{ M = '07'; P = '/api/v1/pens/2003/inspection-overview'; K = 'inspect/overview_2003.json' }
    @{ M = '07'; P = '/api/v1/symptoms'; K = 'inspect/symptoms_pig.json' }
    @{ M = '07'; P = '/api/v1/abnormal-reports/form-context'; K = 'inspect/report_form_direct.json' }
    # ---- 09 库存 ----
    @{ M = '09'; P = '/api/v1/feed-stock/overview'; K = 'stock/feed_overview.json' }
    @{ M = '09'; P = '/api/v1/feeds?page=1&size=20'; K = 'stock/feeds_page.json' }
    @{ M = '09'; P = '/api/v1/feeds/options'; K = '' }
    @{ M = '09'; P = '/api/v1/feeds/5101'; K = 'stock/feed_5101.json' }
    @{ M = '09'; P = '/api/v1/feed-flows?page=1&size=20'; K = 'stock/feed_flows_all.json' }
    @{ M = '09'; P = '/api/v1/stocktakes?page=1&size=20'; K = 'stock/stocktakes_page.json' }
    @{ M = '09'; P = '/api/v1/stocktakes/sheet'; K = 'stock/stocktake_sheet.json' }
    @{ M = '09'; P = '/api/v1/drug-stock/overview'; K = 'stock/drug_overview.json' }
    @{ M = '09'; P = '/api/v1/drug-lots?page=1&size=20'; K = 'stock/lots_normal.json' }
    @{ M = '09'; P = '/api/v1/drug-lots/options'; K = '' }
    @{ M = '09'; P = '/api/v1/drug-flows?page=1&size=20'; K = 'stock/dflows_all.json' }
    @{ M = '09'; P = '/api/v1/drugs?page=1&size=20'; K = 'stock/drugs_archive.json' }
    @{ M = '09'; P = '/api/v1/drugs/options'; K = '' }
    @{ M = '09'; P = '/api/v1/stock-settings'; K = 'stock/stock_setting.json' }
    @{ M = '09'; P = '/api/v1/stock-partners/recent'; K = 'stock/partners_supplier.json' }
    # ---- 11 AI 与课程 ----
    @{ M = '11'; P = '/api/v1/ai/sessions?page=1&size=20'; K = 'ai/sessions_owner.json' }
    @{ M = '11'; P = '/api/v1/ai/linkable-batches'; K = 'ai/linkable_owner.json' }
    @{ M = '11'; P = '/api/v1/course-categories'; K = 'ai/categories.json' }
    @{ M = '11'; P = '/api/v1/courses/continue'; K = 'ai/continue.json' }
    @{ M = '11'; P = '/api/v1/course-topics'; K = 'ai/topics.json' }
    @{ M = '11'; P = '/api/v1/courses?page=1&size=20'; K = 'ai/courses_all.json' }
    @{ M = '11'; P = '/api/v1/course-progress'; K = '' }
    # ---- 3B 公共字典 ----
    @{ M = '3B'; P = '/api/v1/base/categories'; K = '' }
    @{ M = '3B'; P = '/api/v1/base/dicts/PEN_TYPE'; K = '' }
    @{ M = '3B'; P = '/api/v1/base/drug-catalog?drug_category=VACCINE'; K = 'stock/catalog_vaccine.json' }
)

$results = [System.Collections.Generic.List[object]]::new()
$i = 0
foreach ($c in $checks) {
    $i++
    $r = Invoke-Json -Method 'GET' -Path $c.P -Headers $headers
    $json = $r.Json
    $code = $null; $msg = ''; $data = $null
    if ($null -ne $json) { $code = $json.code; $msg = $json.message; $data = $json.data }
    $dataNull = ($null -eq $data)
    $itemCount = $null; $total = $null
    if ($data -is [pscustomobject]) {
        if ($data.PSObject.Properties['items']) { $itemCount = @($data.items).Count; $total = $data.total }
        elseif ($data.PSObject.Properties['list']) { $itemCount = @($data.list).Count; $total = $data.total }
    } elseif ($data -is [System.Array]) { $itemCount = $data.Count }
    $diffs = [System.Collections.Generic.List[string]]::new()
    $mockFile = ''
    if ($c.K) {
        $mockFile = Join-Path (Join-Path $workspace ($mockModule[$c.M])) ("src\debug\assets\mock\" + $c.K.Replace('/', '\'))
        if (Test-Path $mockFile) {
            $mock = Get-Content $mockFile -Raw -Encoding utf8 | ConvertFrom-Json -Depth 100
            $diffs = Compare-Shape -Mock $mock.data -Actual $data
        } else { $mockFile = "MISSING:$mockFile" }
    }
    $bodyFile = Save-Body -Module $c.M -Path $c.P -Http $r.Http -Text $r.Text
    $snippet = if ($r.Http -ne 200 -or ($code -ne $null -and $code -ne 0)) { $r.Text.Substring(0, [Math]::Min(300, $r.Text.Length)) } else { '' }
    $ok = ($r.Http -eq 200 -and $code -eq 0 -and -not $dataNull)
    $results.Add([pscustomobject]@{
            No = $i; Module = $c.M; Path = $c.P; Http = $r.Http; Code = $code; Message = $msg; DataNull = $dataNull
            Items = $itemCount; Total = $total; Ms = $r.Ms; Mock = $c.K; Diffs = @($diffs)
            Snippet = $snippet; BodyFile = $bodyFile; Ok = $ok
        })
    $color = if ($ok -and $diffs.Count -eq 0) { 'Green' } elseif ($ok) { 'Yellow' } else { 'Red' }
    Write-Host ("[{0,2}/{1}] {2,-4} {3,-58} http={4} code={5} {6}ms diff={7}" -f $i, $checks.Count, $c.M, $c.P, $r.Http, $code, $r.Ms, $diffs.Count) -ForegroundColor $color
}

$jsonOut = Join-Path $OutDir 'smoke-results.json'

# ---- 补参复测：清单里没带 contract 必需参数/ID 的接口，用正确参数再调一次，区分“参数缺失”与“真实缺陷” ----
$retests = @(
    @{ M = '05'; P = '/api/v1/breeding-animals/4131'; Note = '0131 的数值 id 为 4131（路径按 id 解析）' }
    @{ M = '05'; P = '/api/v1/breeding-animals/4131/records'; Note = '同上' }
    @{ M = '05'; P = '/api/v1/breeding-animals/4131/register-context?type=FARROWING'; Note = 'register-context 需 type' }
    @{ M = '05'; P = '/api/v1/breeding-animals/options?purpose=MATING'; Note = 'options 需 purpose' }
    @{ M = '06'; P = '/api/v1/feed-executions/summary?batch_id=3001'; Note = '需 batch_id 或 all_batches' }
    @{ M = '06'; P = '/api/v1/feed-executions/attention?all_batches=true&page=1&size=20'; Note = '需 batch_id 或 all_batches' }
    @{ M = '06'; P = '/api/v1/feed-executions/daily?batch_id=3001&date=2026-10-18'; Note = '需 batch_id + date（统计截止昨天）' }
    @{ M = '07'; P = '/api/v1/symptoms?batch_id=3001'; Note = '需 batch_id' }
    @{ M = '09'; P = '/api/v1/drug-lots/options?drug_id=5001'; Note = '需 drug_id（用于确认 500 根因）' }
)
$retestResults = [System.Collections.Generic.List[object]]::new()
foreach ($rt in $retests) {
    $r = Invoke-Json -Method 'GET' -Path $rt.P -Headers $headers
    $code = $null; $msg = ''; $data = $null
    if ($null -ne $r.Json) { $code = $r.Json.code; $msg = $r.Json.message; $data = $r.Json.data }
    $bodyFile = Save-Body -Module ("retest-" + $rt.M) -Path $rt.P -Http $r.Http -Text $r.Text
    $retestResults.Add([pscustomobject]@{ Module = $rt.M; Path = $rt.P; Http = $r.Http; Code = $code; Message = $msg; Ms = $r.Ms; Note = $rt.Note; DataNull = ($null -eq $data); BodyFile = $bodyFile })
    Write-Host ("[retest] {0,-4} {1,-60} http={2} code={3} {4}" -f $rt.M, $rt.P, $r.Http, $code, $msg) -ForegroundColor $(if ($r.Http -eq 200 -and $code -eq 0) { 'Green' } else { 'Yellow' })
}

$meta = [pscustomobject]@{
    BaseUrl = $BaseUrl; FarmId = $FarmId; GeneratedAt = (Get-Date).ToString('s')
    Total = $results.Count; Failed = @($results | Where-Object { -not $_.Ok }).Count
    WithDiffs = @($results | Where-Object { $_.Diffs.Count -gt 0 }).Count
}
[pscustomobject]@{ Meta = $meta; Results = $results; Retests = $retestResults } | ConvertTo-Json -Depth 12 | Set-Content $jsonOut -Encoding utf8NoBOM

$bad = @($results | Where-Object { -not $_.Ok })
Write-Host ''
Write-Host ("合计 {0}，通过 {1}，失败 {2}，字段差异 {3}" -f $results.Count, ($results.Count - $bad.Count), $bad.Count, $meta.WithDiffs) -ForegroundColor $(if ($bad.Count) { 'Red' } else { 'Green' })
foreach ($b in $bad) { Write-Host ("FAIL {0} http={1} code={2} {3}" -f $b.Path, $b.Http, $b.Code, $b.Message) -ForegroundColor Red }
Write-Host "结果文件：$jsonOut"
if ($bad.Count -gt 0) { exit 1 }

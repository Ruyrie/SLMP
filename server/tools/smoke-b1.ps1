# 第 1 批冒烟测试：应用启动后执行（默认 http://127.0.0.1:8080）。
# 依赖 dev 示例数据：王建国 13800006620 / Zmt12345，东山猪场 1001。
# 用法：powershell -ExecutionPolicy Bypass -File .\smoke-b1.ps1
param(
    [string]$BaseUrl = 'http://127.0.0.1:8080',
    [string]$OwnerPhone = '13800006620',
    [string]$OwnerPassword = 'Zmt12345',
    [string]$StaffPhone = '13700005678',
    [string]$StaffPassword = 'Zmt12345'
)

$ErrorActionPreference = 'Stop'
$script:failed = 0
$script:passed = 0
$script:warned = 0

function Write-Pass($name) {
    $script:passed++
    Write-Host "[PASS] $name" -ForegroundColor Green
}

function Write-Fail($name, $detail) {
    $script:failed++
    Write-Host "[FAIL] $name  $detail" -ForegroundColor Red
}

function Write-Warn($name, $detail) {
    $script:warned++
    Write-Host "[WARN] $name  $detail" -ForegroundColor Yellow
}

function Invoke-Api {
    param(
        [string]$Method,
        [string]$Path,
        $Body = $null,
        [string]$Token = $null,
        $FarmId = $null,
        [hashtable]$ExtraHeaders = @{}
    )
    $headers = @{
        'X-Client'      = 'APP_ANDROID'
        'X-App-Version' = '1.0.0'
    }
    if ($Token) { $headers['Authorization'] = "Bearer $Token" }
    if ($FarmId) { $headers['X-Farm-Id'] = [string]$FarmId }
    foreach ($k in $ExtraHeaders.Keys) { $headers[$k] = $ExtraHeaders[$k] }
    $params = @{
        Method             = $Method
        Uri                = "$BaseUrl$Path"
        Headers            = $headers
        SkipHttpErrorCheck = $true
        TimeoutSec         = 30
    }
    if ($null -ne $Body) {
        $params['Body'] = ($Body | ConvertTo-Json -Depth 10 -Compress)
        $params['ContentType'] = 'application/json'
    }
    $resp = Invoke-WebRequest @params
    $json = $null
    try { $json = $resp.Content | ConvertFrom-Json } catch { }
    [pscustomobject]@{
        Status = [int]$resp.StatusCode
        Code   = if ($json) { $json.code } else { $null }
        Json   = $json
        Raw    = $resp.Content
    }
}

function Expect-Ok($name, $r) {
    if ($r.Status -eq 200 -and $r.Code -eq 0) { Write-Pass $name; return $true }
    Write-Fail $name "status=$($r.Status) body=$($r.Raw)"
    return $false
}

function Login($phone, $password) {
    $r = Invoke-Api -Method POST -Path '/api/v1/auth/login/password' -Body @{
        phone       = $phone
        password    = $password
        device_id   = 'smoke-pc'
        device_model = 'PowerShell'
        os_version  = 'Windows'
    }
    if ($r.Status -ne 200 -or $r.Code -ne 0) { throw "登录失败 phone=$phone status=$($r.Status) body=$($r.Raw)" }
    return $r.Json.data
}

Write-Host "== 智牧通第 1 批冒烟 $BaseUrl ==" -ForegroundColor Cyan

# 探活与公共接口
Expect-Ok 'GET /api/v1/ping' (Invoke-Api -Method GET -Path '/api/v1/ping') | Out-Null
Expect-Ok 'GET /api/v1/agreements/current' (Invoke-Api -Method GET -Path '/api/v1/agreements/current') | Out-Null
$smsPhone = '136' + (Get-Random -Minimum 10000000 -Maximum 99999999)
Expect-Ok 'POST /api/v1/sms-codes (REGISTER 新号)' (Invoke-Api -Method POST -Path '/api/v1/sms-codes' -Body @{ phone = $smsPhone; scene = 'REGISTER' }) | Out-Null

# 所有者登录
$owner = Login $OwnerPhone $OwnerPassword
$ownerToken = $owner.access_token
if ($ownerToken) { Write-Pass '所有者登录返回 access_token' } else { Write-Fail '所有者登录返回 access_token' 'data.access_token 为空' }
if ($owner.refresh_token) { Write-Pass '所有者登录返回 refresh_token' } else { Write-Fail '所有者登录返回 refresh_token' 'data.refresh_token 为空' }

$refreshed = Invoke-Api -Method POST -Path '/api/v1/auth/token/refresh' -Body @{ refresh_token = $owner.refresh_token }
if (Expect-Ok 'A06 刷新令牌' $refreshed) {
    if ($refreshed.Json.data.access_token -and $refreshed.Json.data.refresh_token) { Write-Pass 'A06 轮换返回新令牌' }
    else { Write-Fail 'A06 轮换返回新令牌' '缺少 access_token/refresh_token' }
}

Expect-Ok 'A08 当前会话' (Invoke-Api -Method GET -Path '/api/v1/auth/session' -Token $ownerToken) | Out-Null
$overview = Invoke-Api -Method GET -Path '/api/v1/account/overview' -Token $ownerToken -FarmId 1001
Expect-Ok 'A15 我的页汇总' $overview | Out-Null
if ($overview.Json.data.profile_version -ne $null) { Write-Pass 'A15 profile_version 有值' } else { Write-Fail 'A15 profile_version 有值' '缺失' }
if ($overview.Json.data.user.user_id -eq 10001) { Write-Pass 'A15 示例账号 ID=10001' } else { Write-Fail 'A15 示例账号 ID=10001' "实际 $($overview.Json.data.user.user_id)" }

$farms = Invoke-Api -Method GET -Path '/api/v1/farms?relation=ALL&page=1&size=20' -Token $ownerToken -FarmId 1001
Expect-Ok 'F01 我的养殖场' $farms | Out-Null
$farm1001 = $farms.Json.data.list | Where-Object { $_.farm_id -eq 1001 } | Select-Object -First 1
if ($farm1001) { Write-Pass 'F01 包含东山猪场 1001' } else { Write-Fail 'F01 包含东山猪场 1001' '未找到' }

$ctx = Invoke-Api -Method GET -Path '/api/v1/farms/1001/context' -Token $ownerToken
Expect-Ok 'F02 养殖场上下文' $ctx | Out-Null
if ($ctx.Json.data.permissions) { Write-Pass 'F02 返回 permissions' } else { Write-Fail 'F02 返回 permissions' '缺失' }

$members = Invoke-Api -Method GET -Path '/api/v1/farms/1001/members?role=ALL&page=1&size=20' -Token $ownerToken
Expect-Ok 'F16 成员列表' $members | Out-Null
if (($members.Json.data.list | Measure-Object).Count -ge 3) { Write-Pass 'F16 至少 3 名成员' } else { Write-Fail 'F16 至少 3 名成员' "实际 $(($members.Json.data.list | Measure-Object).Count)" }

$pens = Invoke-Api -Method GET -Path '/api/v1/farms/1001/pen-options' -Token $ownerToken
Expect-Ok 'F18 可分配栏舍' $pens | Out-Null
if (($pens.Json.data | Measure-Object).Count -ge 1) { Write-Pass 'F18 返回栏舍数组' } else { Write-Fail 'F18 返回栏舍数组' '为空' }

$detail = Invoke-Api -Method GET -Path '/api/v1/farms/1001' -Token $ownerToken
Expect-Ok 'F09 养殖场信息' $detail | Out-Null
$version = $detail.Json.data.basic.version
if ($null -ne $version) { Write-Pass "F09 basic.version=$version" } else { Write-Fail 'F09 basic.version' '缺失' }

# F10 写操作：既验证完整 VO，也产生一条留痕供 U14 查询
if ($null -ne $version) {
    $updated = Invoke-Api -Method PATCH -Path '/api/v1/farms/1001/basic' -Token $ownerToken -Body @{
        intro   = "冒烟测试 $(Get-Date -Format 'HHmmss')"
        version = $version
    }
    if (Expect-Ok 'F10 修改基本信息' $updated) {
        if ($updated.Json.data.intro) { Write-Pass 'F10 返回完整 FarmBasicVO' } else { Write-Fail 'F10 返回完整 FarmBasicVO' 'data.intro 缺失' }
    }
}

$logs = Invoke-Api -Method GET -Path '/api/v1/record-logs?biz_type=FARM&biz_id=1001' -Token $ownerToken
if (Expect-Ok 'U14 修改记录' $logs) {
    if (($logs.Json.data | Measure-Object).Count -ge 1) { Write-Pass 'U14 返回数组且非空' } else { Write-Warn 'U14 返回数组且非空' '为空（检查 F10 是否写留痕）' }
}

# U05 上传：不带 X-Farm-Id，验证 F02 新建养殖场场景
$pngPath = Join-Path $env:TEMP 'zmt-smoke.png'
$png = [Convert]::FromBase64String('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Y9ZQmcAAAAASUVORK5CYII=')
[IO.File]::WriteAllBytes($pngPath, $png)
try {
    $upload = Invoke-WebRequest -Method POST -Uri "$BaseUrl/api/v1/files" -Headers @{
        'X-Client' = 'APP_ANDROID'; 'X-App-Version' = '1.0.0'
        'Authorization' = "Bearer $ownerToken"
        'Idempotency-Key' = "smoke-$([guid]::NewGuid().ToString('N'))"
    } -Form @{ file = Get-Item $pngPath; scene = 'FARM_QUAL' } -SkipHttpErrorCheck -TimeoutSec 30
    $uploadJson = $upload.Content | ConvertFrom-Json
    if ($upload.StatusCode -eq 200 -and $uploadJson.code -eq 0 -and $uploadJson.data.file_id) {
        Write-Pass 'U05 无养殖场上下文上传资质图'
        $fileId = $uploadJson.data.file_id
        Expect-Ok 'U06 文件信息' (Invoke-Api -Method GET -Path "/api/v1/files/$fileId" -Token $ownerToken) | Out-Null
        Expect-Ok 'U07 删除未引用文件' (Invoke-Api -Method DELETE -Path "/api/v1/files/$fileId" -Token $ownerToken) | Out-Null
    } else {
        Write-Fail 'U05 无养殖场上下文上传资质图' "status=$($upload.StatusCode) body=$($upload.Content)"
    }
} finally {
    Remove-Item $pngPath -ErrorAction SilentlyContinue
}

$preview = Invoke-Api -Method GET -Path '/api/v1/account/cancellation-preview' -Token $ownerToken -FarmId 1001
if (Expect-Ok 'A19 注销预览' $preview) {
    if ($preview.Json.data.checklist_version) { Write-Pass 'A19 checklist_version 有值' } else { Write-Fail 'A19 checklist_version 有值' '缺失' }
}

# U01 未注册 handler：不应 500
$form = Invoke-Api -Method GET -Path '/api/v1/change-requests/form?biz_type=FEED_RECORD&biz_id=7001' -Token $ownerToken -FarmId 1001
if ($form.Status -eq 404 -or $form.Status -eq 422) { Write-Pass "U01 无 handler 返回 $($form.Status)" }
elseif ($form.Status -eq 200) { Write-Warn 'U01 无 handler' '返回 200（可能已注册测试 handler）' }
else { Write-Fail 'U01 无 handler 不应 500' "status=$($form.Status) body=$($form.Raw)" }

# 员工视图
$staff = Login $StaffPhone $StaffPassword
$staffToken = $staff.access_token
$staffCtx = Invoke-Api -Method GET -Path '/api/v1/farms/1001/context' -Token $staffToken
Expect-Ok '员工 F02 上下文' $staffCtx | Out-Null
if ($staffCtx.Json.data.permissions.can_view_members -eq $false) { Write-Pass '员工 can_view_members=false' } else { Write-Fail '员工 can_view_members=false' '实际不是 false' }

$staffMembers = Invoke-Api -Method GET -Path '/api/v1/farms/1001/members?role=ALL&page=1&size=20' -Token $staffToken
if ($staffMembers.Status -eq 403 -and $staffMembers.Code -eq 40303) { Write-Pass '员工 F16 返回 40303' } else { Write-Fail '员工 F16 返回 40303' "status=$($staffMembers.Status) code=$($staffMembers.Code)" }

$staffForm = Invoke-Api -Method GET -Path '/api/v1/change-requests/form?biz_type=FEED_RECORD&biz_id=7001' -Token $staffToken -FarmId 1001
if (($staffForm.Status -eq 403 -and $staffForm.Code -eq 40303) -or ($staffForm.Status -eq 422 -and $staffForm.Code -eq 42207)) {
    Write-Pass "员工 U01 被拒（status=$($staffForm.Status) code=$($staffForm.Code)）"
} else {
    Write-Fail '员工 U01 被拒' "status=$($staffForm.Status) code=$($staffForm.Code)"
}

Write-Host ""
Write-Host ("== 结果：通过 {0}，失败 {1}，警告 {2} ==" -f $script:passed, $script:failed, $script:warned) -ForegroundColor Cyan
if ($script:failed -gt 0) { exit 1 }

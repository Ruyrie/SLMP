param(
    [string]$RepoRoot = '',
    [string]$Module = '',
    [switch]$FailOnMissing
)

$ErrorActionPreference = 'Stop'

if (-not $RepoRoot) {
    $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
}
$RepoRoot = (Resolve-Path $RepoRoot).Path
$ServerRoot = Join-Path $RepoRoot 'server'

function Normalize-Path([string]$Path) {
    if ([string]::IsNullOrWhiteSpace($Path)) { return '/' }
    $p = $Path.Trim()
    if (-not $p.StartsWith('/')) { $p = '/' + $p }
    $p = [regex]::Replace($p, '\{[^}]+\}', '{}')
    if ($p.Length -gt 1) { $p = $p.TrimEnd('/') }
    return $p
}

# ---------- App routes ----------
$mockRoots = @()
if ($Module) {
    $mockRoots = @(Join-Path $RepoRoot $Module)
} else {
    $mockRoots = @(Get-ChildItem -Path $RepoRoot -Directory |
        Where-Object { $_.Name -match '^(module_|lib_)' } |
        ForEach-Object { $_.FullName })
}

$app = New-Object System.Collections.Generic.List[object]
foreach ($root in $mockRoots) {
    if (-not (Test-Path $root)) { continue }
    $routes = Get-ChildItem -Path $root -Recurse -File -Filter 'routes.json' -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName -notmatch '\\build\\|\\\.claude\\|\\.worktrees\\' }
    foreach ($file in $routes) {
        try {
            $items = Get-Content -Raw $file.FullName | ConvertFrom-Json
        } catch {
            Write-Warning "跳过无法解析的 $($file.FullName): $($_.Exception.Message)"
            continue
        }
        foreach ($item in @($items)) {
            if (-not $item.method -or -not $item.path) { continue }
            $app.Add([pscustomobject]@{
                Module = (Split-Path $file.DirectoryName -Leaf)
                Method = $item.method.ToUpperInvariant()
                Path   = (Normalize-Path $item.path)
            })
        }
    }
}

# ---------- Server controllers ----------
$controllers = Get-ChildItem -Path (Join-Path $ServerRoot 'zhimu-biz\src\main\java') -Recurse -File -Filter '*Controller.java' -ErrorAction SilentlyContinue
$server = New-Object System.Collections.Generic.List[object]
foreach ($file in $controllers) {
    $text = Get-Content -Raw $file.FullName
    $baseMatch = [regex]::Match($text, '@RequestMapping\s*\(\s*(?:value\s*=\s*)?"([^"]*)"')
    $base = if ($baseMatch.Success) { $baseMatch.Groups[1].Value } else { '' }

    $methodMatches = [regex]::Matches(
        $text,
        '@(Get|Post|Put|Delete|Patch)Mapping\s*(?:\(\s*(?:value\s*=\s*)?"([^"]*)"\s*\))?')
    foreach ($m in $methodMatches) {
        $server.Add([pscustomobject]@{
            Method = $m.Groups[1].Value.ToUpperInvariant()
            Path   = (Normalize-Path ($base + $m.Groups[2].Value))
        })
    }

    # 方法级 @RequestMapping（非类级）按 ANY 记录，只用于提示
    foreach ($m in [regex]::Matches($text, '@RequestMapping\s*\(\s*(?:value\s*=\s*)?"([^"]*)"')) {
        if ($m.Groups[1].Value -and $m.Groups[1].Value -ne $base) {
            $server.Add([pscustomobject]@{
                Method = 'ANY'
                Path   = (Normalize-Path ($base + $m.Groups[1].Value))
            })
        }
    }
}

$appUnique = $app | Sort-Object Method, Path -Unique
$serverUnique = $server | Sort-Object Method, Path -Unique

$missing = @()
foreach ($a in $appUnique) {
    $hit = $serverUnique | Where-Object {
        ($_.Method -eq $a.Method -or $_.Method -eq 'ANY') -and $_.Path -eq $a.Path
    }
    if (-not $hit) { $missing += $a }
}

$coverage = if ($appUnique.Count -eq 0) { 0 } else { [math]::Round(100 * ($appUnique.Count - $missing.Count) / $appUnique.Count, 1) }

Write-Host ''
Write-Host '=== App 接口覆盖率 ===' -ForegroundColor Cyan
Write-Host ("App 唯一接口 : {0}" -f $appUnique.Count)
Write-Host ("服务端已映射 : {0}" -f ($appUnique.Count - $missing.Count))
Write-Host ("缺失         : {0}" -f $missing.Count)
Write-Host ("覆盖率       : {0}%" -f $coverage)

if ($missing.Count -gt 0) {
    Write-Host ''
    Write-Host '=== 缺失接口（按模块） ===' -ForegroundColor Yellow
    foreach ($group in ($missing | Group-Object Module | Sort-Object Name)) {
        Write-Host ''
        Write-Host ("[{0}] {1} 个" -f $group.Name, $group.Count) -ForegroundColor Yellow
        foreach ($m in ($group.Group | Sort-Object Path)) {
            Write-Host ("  {0,-6} {1}" -f $m.Method, $m.Path)
        }
    }
}

if ($FailOnMissing -and $missing.Count -gt 0) { exit 1 }
exit 0

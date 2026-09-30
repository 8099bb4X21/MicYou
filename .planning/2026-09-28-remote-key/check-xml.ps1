[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$base = 'E:/OpenCode/test/micyou-fork/composeApp/src/main/res'
$dirs = @('values','values-en','values-zh','values-zh-rTW','values-zh-rHK','values-ca','values-zh-rHD')
foreach ($d in $dirs) {
    $p = Join-Path $base "$d/strings.xml"
    $names = Select-String -Path $p -Pattern 'name="([^"]+)"' | ForEach-Object { $_.Matches[0].Groups[1].Value }
    $dup = @($names | Group-Object | Where-Object { $_.Count -gt 1 } | Select-Object -ExpandProperty Name)
    $msg = if ($dup.Count -eq 0) { 'clean' } else { 'DUP: ' + ($dup -join ',') }
    Write-Output "$d => $msg"
}
[xml]$x = Get-Content (Join-Path $base 'values/strings.xml') -Encoding UTF8
Write-Output 'XML parse OK'

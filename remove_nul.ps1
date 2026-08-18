# remove_nul.ps1
# 递归遍历当前目录及子目录，删除文件名为 "nul" 的文件
# 使用 \\?\ 前缀绕过 Windows 设备名保留字限制

$count = 0
Get-ChildItem -Path "." -Recurse -Force | Where-Object { -not $_.PSIsContainer -and $_.Name -eq "nul" } | ForEach-Object {
    $winPath = "\\?\$($_.FullName)"
    try {
        # PowerShell Remove-Item can't handle reserved device names like "nul"
        # Use cmd /c del with \\?\ prefix instead
        cmd /c del /f /q $winPath 2>&1 | Out-Null
        if ($LASTEXITCODE -eq 0) {
            Write-Host "Deleted: $($_.FullName)"
            $count++
        } else {
            Write-Host "Failed: $($_.FullName) (exit code: $LASTEXITCODE)"
        }
    } catch {
        Write-Host "Failed: $($_.FullName) - $_"
    }
}

if ($count -eq 0) {
    Write-Host "No 'nul' files found."
} else {
    Write-Host "Done. Deleted $count file(s)."
}

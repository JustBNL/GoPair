$k6Exe = "D:\Document\GoPair\k6_bin\k6-v1.7.1-windows-amd64\k6.exe"
$workDir = "d:\Document\GoPair\perf\k6"
$outputFile = "d:\Document\GoPair\perf\user_info.txt"
$userCount = 200

Write-Host "[run_bootstrap] Starting bootstrap for $userCount users..."

$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = $k6Exe
$psi.Arguments = "run bootstrap.js -e BASE_URL=http://localhost:8081 -e USER_COUNT=$userCount"
$psi.WorkingDirectory = $workDir
$psi.RedirectStandardOutput = $true
$psi.RedirectStandardError = $true
$psi.UseShellExecute = $false
$psi.CreateNoWindow = $true

$proc = [System.Diagnostics.Process]::Start($psi)

$stdoutTask = $proc.StandardOutput.ReadToEndAsync()
$stderrTask = $proc.StandardError.ReadToEndAsync()

$proc.WaitForExit()

$stdout = $stdoutTask.Result
$stderr = $stderrTask.Result

Write-Host "[run_bootstrap] k6 exit code: $($proc.ExitCode)"

$jwtLines = [System.Text.RegularExpressions.Regex]::Matches($stderr, 'msg="(eyJ[^"]*)"') |
    ForEach-Object { $_.Groups[1].Value }

$stdoutJwtLines = [System.Text.RegularExpressions.Regex]::Matches($stdout, 'msg="(eyJ[^"]*)"') |
    ForEach-Object { $_.Groups[1].Value }

$allJwtLines = $jwtLines + $stdoutJwtLines | Select-Object -Unique

Write-Host "[run_bootstrap] JWT lines extracted: $($allJwtLines.Count)"

if ($allJwtLines.Count -gt 0) {
    $allJwtLines | Set-Content $outputFile -Encoding UTF8
    Write-Host "[run_bootstrap] Written to $outputFile"
} else {
    Write-Host "[run_bootstrap] No JWT lines found in stdout/stderr. Check output above."
    Write-Host "=== STDOUT ==="
    Write-Host $stdout.Substring(0, [Math]::Min(2000, $stdout.Length))
    Write-Host "=== STDERR ==="
    Write-Host $stderr.Substring(0, [Math]::Min(2000, $stderr.Length))
}

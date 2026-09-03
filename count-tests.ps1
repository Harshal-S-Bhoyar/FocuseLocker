$root = $PSScriptRoot
$files = @()
$files += Get-ChildItem -Path (Join-Path $root 'backend\tests') -Filter '*.test.ts' -File
$files += Get-ChildItem -Path (Join-Path $root 'app\src\test') -Filter '*.kt' -File -Recurse
if (Test-Path (Join-Path $root 'app\src\androidTest')) {
  $files += Get-ChildItem -Path (Join-Path $root 'app\src\androidTest') -Filter '*.kt' -File -Recurse
}
foreach ($file in $files | Sort-Object FullName) {
  $text = Get-Content -LiteralPath $file.FullName -Raw
  if ($file.Extension -eq '.kt') {
    $count = [regex]::Matches($text, '(?m)^\s*@Test\b').Count
  }
  else {
    $count = [regex]::Matches($text, '(?m)^\s*(it|test)\s*\(').Count
  }
  Write-Output ("{0}|{1}" -f $file.FullName, $count)
}

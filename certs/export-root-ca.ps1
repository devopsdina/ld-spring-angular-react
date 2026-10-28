# Exports a root CA from the Windows certificate store into this folder as a PEM .crt file, for the
# server container to trust (see certs/README.md).
#
#   .\certs\export-root-ca.ps1 -Name "Cato"                    # writes certs\cato.crt
#   .\certs\export-root-ca.ps1 -Name "Cato" -OutName corp-root # writes certs\corp-root.crt
param(
  [Parameter(Mandatory = $true)][string]$Name,
  [string]$OutName = ($Name.ToLower() -replace '\s+', '-')
)
$ErrorActionPreference = 'Stop'

# Roots pushed by IT are usually in LocalMachine; some are per user.
$certs = Get-ChildItem Cert:\LocalMachine\Root, Cert:\CurrentUser\Root |
  Where-Object { $_.Subject -like "*$Name*" } |
  Sort-Object Thumbprint -Unique

if (-not $certs) {
  throw "No certificate matching '$Name' found in the LocalMachine or CurrentUser Trusted Root stores."
}

# Write PEM (Base64 with BEGIN/END lines): the container's certificate import needs PEM, not DER.
$pem = foreach ($cert in $certs) {
  "-----BEGIN CERTIFICATE-----"
  [Convert]::ToBase64String($cert.RawData, 'InsertLineBreaks')
  "-----END CERTIFICATE-----"
}
$out = Join-Path $PSScriptRoot "$OutName.crt"
# ASCII with LF endings keeps the file readable by Linux tools in the container.
[IO.File]::WriteAllText($out, (($pem -join "`n") + "`n").Replace("`r`n", "`n"), [Text.Encoding]::ASCII)

Write-Host "Wrote $out:"
$certs | ForEach-Object { Write-Host "  subject: $($_.Subject)  expires: $($_.NotAfter)" }

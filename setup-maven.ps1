$destDir = "$env:USERPROFILE\.m2\wrapper"
$mvnCmd = "$destDir\apache-maven-3.9.9\bin\mvn.cmd"

if (!(Test-Path $mvnCmd)) {
    Write-Host "Downloading Maven 3.9.9..."
    $zipPath = "$env:TEMP\apache-maven-3.9.9-bin.zip"
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    Invoke-WebRequest -Uri "https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.zip" -OutFile $zipPath
    if (!(Test-Path $destDir)) {
        New-Item -ItemType Directory -Path $destDir -Force | Out-Null
    }
    Write-Host "Extracting Maven..."
    Expand-Archive -Path $zipPath -DestinationPath $destDir -Force
    Remove-Item $zipPath -Force
}

Write-Host "Verifying Maven..."
& $mvnCmd -version

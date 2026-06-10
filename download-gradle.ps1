# Gradle 8.10 下载脚本
$gradleVersion = "8.10"
$downloadUrl = "https://mirrors.cloud.tencent.com/gradle/gradle-$gradleVersion-bin.zip"
$gradleUserHome = "$env:USERPROFILE\.gradle"
$distPath = "$gradleUserHome\wrapper\dists\gradle-$gradleVersion-bin"
$zipFile = "$distPath\gradle-$gradleVersion-bin.zip"

Write-Host "正在从腾讯云镜像下载 Gradle $gradleVersion..." -ForegroundColor Green
Write-Host "下载地址: $downloadUrl" -ForegroundColor Cyan

# 创建目录
if (!(Test-Path $distPath)) {
    New-Item -ItemType Directory -Force -Path $distPath | Out-Null
}

# 下载文件
try {
    $webClient = New-Object System.Net.WebClient
    $webClient.DownloadFile($downloadUrl, $zipFile)
    Write-Host "下载完成！文件位置: $zipFile" -ForegroundColor Green
    Write-Host "现在可以运行 .\gradlew bootRun 启动项目" -ForegroundColor Yellow
} catch {
    Write-Host "下载失败: $_" -ForegroundColor Red
    Write-Host "请尝试手动下载或使用其他网络" -ForegroundColor Yellow
}

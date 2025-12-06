# CashBook 密钥库生成脚本
# 请妥善保管生成的密钥文件和密码！

Write-Host "==================================" -ForegroundColor Cyan
Write-Host "  CashBook 1.0 密钥库生成工具" -ForegroundColor Cyan
Write-Host "==================================" -ForegroundColor Cyan
Write-Host ""

# 密钥配置
$keystoreFile = "cashbook-release.jks"
$keystorePassword = "CashBook2024!"
$keyAlias = "cashbook-key"
$keyPassword = "CashBook2024!"

# 开发者信息
$dname = "CN=CashBook,OU=Development,O=LocalExpense,L=Beijing,ST=Beijing,C=CN"

Write-Host "密钥库配置信息:" -ForegroundColor Yellow
Write-Host "  文件名: $keystoreFile"
Write-Host "  别名: $keyAlias"
Write-Host "  密码: $keystorePassword"
Write-Host "  有效期: 10000天 (约27年)"
Write-Host ""

# 检查是否已存在
if (Test-Path $keystoreFile) {
    Write-Host "警告: 密钥库文件已存在！" -ForegroundColor Red
    $confirm = Read-Host "是否覆盖? (yes/no)"
    if ($confirm -ne "yes") {
        Write-Host "已取消操作" -ForegroundColor Yellow
        exit
    }
    Remove-Item $keystoreFile
}

Write-Host "正在生成密钥库..." -ForegroundColor Green

# 生成密钥库
try {
    & keytool -genkeypair -v `
        -keystore $keystoreFile `
        -keyalg RSA `
        -keysize 2048 `
        -validity 10000 `
        -alias $keyAlias `
        -storepass $keystorePassword `
        -keypass $keyPassword `
        -dname $dname

    if ($LASTEXITCODE -eq 0) {
        Write-Host ""
        Write-Host "✓ 密钥库生成成功！" -ForegroundColor Green
        Write-Host ""
        Write-Host "重要提醒:" -ForegroundColor Red
        Write-Host "  1. 请立即备份 $keystoreFile 文件到安全位置"
        Write-Host "  2. 记录以下信息到密码管理器:"
        Write-Host "     - 密钥库密码: $keystorePassword"
        Write-Host "     - 密钥别名: $keyAlias"
        Write-Host "     - 密钥密码: $keyPassword"
        Write-Host "  3. 密钥丢失将无法更新应用！" -ForegroundColor Red
        Write-Host ""

        # 创建密码配置文件
        $propsContent = @"
# 密钥库配置文件
# 警告：不要将此文件提交到Git！已添加到.gitignore
storeFile=$keystoreFile
storePassword=$keystorePassword
keyAlias=$keyAlias
keyPassword=$keyPassword
"@

        $propsContent | Out-File -FilePath "keystore.properties" -Encoding UTF8
        Write-Host "✓ 已创建 keystore.properties 配置文件" -ForegroundColor Green

        # 更新 .gitignore
        $gitignorePath = ".gitignore"
        if (Test-Path $gitignorePath) {
            $gitignoreContent = Get-Content $gitignorePath
            if ($gitignoreContent -notcontains "*.jks" -and $gitignoreContent -notcontains "keystore.properties") {
                Add-Content $gitignorePath "`n# 密钥库文件（不要提交到Git）"
                Add-Content $gitignorePath "*.jks"
                Add-Content $gitignorePath "*.keystore"
                Add-Content $gitignorePath "keystore.properties"
                Write-Host "✓ 已更新 .gitignore" -ForegroundColor Green
            }
        }

        Write-Host ""
        Write-Host "下一步: 运行 gradlew assembleRelease 构建正式版APK" -ForegroundColor Cyan
    } else {
        Write-Host "✗ 密钥库生成失败" -ForegroundColor Red
        exit 1
    }
} catch {
    Write-Host "错误: $_" -ForegroundColor Red
    Write-Host ""
    Write-Host "请确保已安装Java JDK，并且keytool在PATH中" -ForegroundColor Yellow
    exit 1
}

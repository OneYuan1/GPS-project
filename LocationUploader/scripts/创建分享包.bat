@echo off
chcp 65001 >nul
echo LocationUploader 分享包创建工具
echo Version: v1.0.18
echo ================================
echo.

echo 🔧 正在构建APK...
call gradlew assembleRelease --no-daemon

if %errorlevel% neq 0 (
    echo.
    echo ✗ 构建失败！请检查错误信息
    pause
    exit /b 1
)

echo.
echo ✓ APK构建成功！

REM 创建分享包目录
set share_dir=分享包_v1.0.18_%date:~0,4%%date:~5,2%%date:~8,2%
if not exist "%share_dir%" mkdir "%share_dir%"

echo.
echo 📦 正在创建分享包：%share_dir%

REM 复制APK文件
copy "app\build\outputs\apk\release\app-release.apk" "%share_dir%\LocationUploader-v1.0.18.apk"

REM 创建使用说明
echo # LocationUploader v1.0.18 使用说明 > "%share_dir%\使用说明.md"
echo ================================ >> "%share_dir%\使用说明.md"
echo. >> "%share_dir%\使用说明.md"
echo ## 📱 应用介绍 >> "%share_dir%\使用说明.md"
echo LocationUploader v1.0.18 是一个GNSS数据上传工具，可以获取设备GPS位置并上传到TCP服务器。 >> "%share_dir%\使用说明.md"
echo. >> "%share_dir%\使用说明.md"
echo ### 🆕 新版本特性 (v1.0.18) >> "%share_dir%\使用说明.md"
echo - ✅ 连接稳定性优化 >> "%share_dir%\使用说明.md"
echo - ✅ 精确1秒间隔上传 >> "%share_dir%\使用说明.md"
echo - ✅ 完整Satellite_Info支持 >> "%share_dir%\使用说明.md"
echo - ✅ 持久TCP连接 >> "%share_dir%\使用说明.md"
echo - ✅ 多设备支持 >> "%share_dir%\使用说明.md"
echo. >> "%share_dir%\使用说明.md"
echo ## 🚀 安装步骤 >> "%share_dir%\使用说明.md"
echo 1. 下载 LocationUploader-v1.0.18.apk 文件 >> "%share_dir%\使用说明.md"
echo 2. 在手机上点击APK文件进行安装 >> "%share_dir%\使用说明.md"
echo 3. 授予位置权限 >> "%share_dir%\使用说明.md"
echo 4. 确保GPS已开启 >> "%share_dir%\使用说明.md"
echo 5. 配置服务器IP地址 (默认: 192.168.1.116:12345) >> "%share_dir%\使用说明.md"
echo 6. 点击"开始持续上传" >> "%share_dir%\使用说明.md"
echo. >> "%share_dir%\使用说明.md"
echo ## 🚨 如果遇到"安装失败(-15)"错误： >> "%share_dir%\使用说明.md"
echo. >> "%share_dir%\使用说明.md"
echo ### 解决方案： >> "%share_dir%\使用说明.md"
echo 1. 关闭安全守护：设置 → 安全 → 安全守护 → 关闭 >> "%share_dir%\使用说明.md"
echo 2. 启用未知来源安装： >> "%share_dir%\使用说明.md"
echo    Android 8.0+: 设置 → 应用 → 特殊应用访问权限 → 安装未知应用 >> "%share_dir%\使用说明.md"
echo    Android 7.0-: 设置 → 安全 → 未知来源 >> "%share_dir%\使用说明.md"
echo 3. 设备特定设置： >> "%share_dir%\使用说明.md"
echo    华为/荣耀: 设置 → 安全 → 更多安全设置 → 关闭外部来源应用检查 >> "%share_dir%\使用说明.md"
echo    小米: 设置 → 更多设置 → 开发者选项 → USB调试 >> "%share_dir%\使用说明.md"
echo    OPPO/一加: 设置 → 其他设置 → 开发者选项 → USB调试 >> "%share_dir%\使用说明.md"
echo    vivo: 设置 → 更多设置 → 开发者选项 → USB调试 >> "%share_dir%\使用说明.md"
echo. >> "%share_dir%\使用说明.md"
echo ### 💡 如果仍然无法安装，建议使用ADB安装： >> "%share_dir%\使用说明.md"
echo ```bash >> "%share_dir%\使用说明.md"
echo adb install -r LocationUploader-v1.0.18.apk >> "%share_dir%\使用说明.md"
echo ``` >> "%share_dir%\使用说明.md"
echo. >> "%share_dir%\使用说明.md"
echo ## 📞 技术支持 >> "%share_dir%\使用说明.md"
echo 如果遇到问题，请查看"安装问题解决.md"文件。 >> "%share_dir%\使用说明.md"

REM 创建安装问题解决指南
echo # LocationUploader 安装问题解决指南 > "%share_dir%\安装问题解决.md"
echo. >> "%share_dir%\安装问题解决.md"
echo ## 🚨 常见安装问题 >> "%share_dir%\安装问题解决.md"
echo. >> "%share_dir%\安装问题解决.md"
echo ### 问题1：安装失败(-15) >> "%share_dir%\安装问题解决.md"
echo **错误现象**：安装失败，提示"未开启安全守护" >> "%share_dir%\安装问题解决.md"
echo. >> "%share_dir%\安装问题解决.md"
echo **解决方案**： >> "%share_dir%\安装问题解决.md"
echo 1. 关闭安全守护：设置 → 安全 → 安全守护 → 关闭 >> "%share_dir%\安装问题解决.md"
echo 2. 启用未知来源安装 >> "%share_dir%\安装问题解决.md"
echo 3. 重新尝试安装 >> "%share_dir%\安装问题解决.md"
echo. >> "%share_dir%\安装问题解决.md"
echo ### 问题2：解析包时出错 >> "%share_dir%\安装问题解决.md"
echo **错误现象**：提示"解析包时出错" >> "%share_dir%\安装问题解决.md"
echo. >> "%share_dir%\安装问题解决.md"
echo **解决方案**： >> "%share_dir%\安装问题解决.md"
echo 1. 重新下载APK文件 >> "%share_dir%\安装问题解决.md"
echo 2. 检查设备Android版本（需要5.0+） >> "%share_dir%\安装问题解决.md"
echo 3. 确保存储空间充足 >> "%share_dir%\安装问题解决.md"
echo. >> "%share_dir%\安装问题解决.md"
echo ### 问题3：应用未安装 >> "%share_dir%\安装问题解决.md"
echo **错误现象**：安装过程完成但应用未出现 >> "%share_dir%\安装问题解决.md"
echo. >> "%share_dir%\安装问题解决.md"
echo **解决方案**： >> "%share_dir%\安装问题解决.md"
echo 1. 检查是否已卸载旧版本 >> "%share_dir%\安装问题解决.md"
echo 2. 重启设备后重新安装 >> "%share_dir%\安装问题解决.md"
echo 3. 使用ADB安装：adb install -r LocationUploader.apk >> "%share_dir%\安装问题解决.md"
echo. >> "%share_dir%\安装问题解决.md"
echo ## 🔧 设备特定解决方案 >> "%share_dir%\安装问题解决.md"
echo. >> "%share_dir%\安装问题解决.md"
echo ### 华为/荣耀设备 >> "%share_dir%\安装问题解决.md"
echo 1. 设置 → 安全 → 更多安全设置 → 关闭外部来源应用检查 >> "%share_dir%\安装问题解决.md"
echo 2. 设置 → 应用 → 应用管理 → 设置 → 启用安装外部来源应用 >> "%share_dir%\安装问题解决.md"
echo. >> "%share_dir%\安装问题解决.md"
echo ### 小米设备 >> "%share_dir%\安装问题解决.md"
echo 1. 设置 → 更多设置 → 开发者选项 → 启用USB调试 >> "%share_dir%\安装问题解决.md"
echo 2. 设置 → 更多设置 → 安全 → 启用未知来源 >> "%share_dir%\安装问题解决.md"
echo. >> "%share_dir%\安装问题解决.md"
echo ### OPPO/一加设备 >> "%share_dir%\安装问题解决.md"
echo 1. 设置 → 其他设置 → 开发者选项 → 启用USB调试 >> "%share_dir%\安装问题解决.md"
echo 2. 设置 → 安全 → 启用未知来源应用安装 >> "%share_dir%\安装问题解决.md"
echo. >> "%share_dir%\安装问题解决.md"
echo ### vivo设备 >> "%share_dir%\安装问题解决.md"
echo 1. 设置 → 更多设置 → 开发者选项 → 启用USB调试 >> "%share_dir%\安装问题解决.md"
echo 2. 设置 → 安全 → 启用未知来源 >> "%share_dir%\安装问题解决.md"

REM 创建快速安装脚本
echo @echo off > "%share_dir%\快速安装.bat"
echo chcp 65001 ^>nul >> "%share_dir%\快速安装.bat"
echo echo LocationUploader 快速安装工具 >> "%share_dir%\快速安装.bat"
echo echo ============================== >> "%share_dir%\快速安装.bat"
echo echo. >> "%share_dir%\快速安装.bat"
echo echo 正在检查设备连接... >> "%share_dir%\快速安装.bat"
echo adb devices >> "%share_dir%\快速安装.bat"
echo echo. >> "%share_dir%\快速安装.bat"
echo echo 正在安装APK... >> "%share_dir%\快速安装.bat"
echo adb install -r LocationUploader.apk >> "%share_dir%\快速安装.bat"
echo echo. >> "%share_dir%\快速安装.bat"
echo echo 安装完成！ >> "%share_dir%\快速安装.bat"
echo pause >> "%share_dir%\快速安装.bat"

REM 复制网络检测工具
if exist "network_test.py" copy "network_test.py" "%share_dir%\"
if exist "get_ip.bat" copy "get_ip.bat" "%share_dir%\"

echo.
echo ✓ 分享包创建完成！
echo.
echo 📁 分享包位置：%share_dir%
echo 📱 包含文件：
echo    - LocationUploader.apk (主程序)
echo    - 使用说明.md (使用指南)
echo    - 安装问题解决.md (问题解决方案)
echo    - 快速安装.bat (ADB安装脚本)
echo.
echo 💡 分享建议：
echo 1. 将整个文件夹压缩后分享
echo 2. 提醒用户查看"安装问题解决.md"
echo 3. 如果安装失败，建议使用ADB安装
echo.
echo 🚀 分享包已准备就绪！
pause

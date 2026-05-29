@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

echo ========================================
echo    LocationUploader APK 构建工具
echo ========================================
echo.

:: 检查Java环境
echo [1/6] 检查Java环境...
java -version >nul 2>&1
if errorlevel 1 (
    echo ❌ 错误: 未找到Java环境，请安装JDK 8或更高版本
    pause
    exit /b 1
)
echo ✅ Java环境检查通过

:: 检查Android SDK
echo [2/6] 检查Android SDK...
if not exist "%ANDROID_HOME%" (
    echo ⚠️  警告: 未设置ANDROID_HOME环境变量
    echo 请确保Android SDK已正确安装
)

:: 清理项目
echo [3/6] 清理项目...
call gradlew clean
if errorlevel 1 (
    echo ❌ 清理失败
    pause
    exit /b 1
)
echo ✅ 项目清理完成

:: 构建APK
echo [4/6] 构建APK...
call gradlew assembleDebug
if errorlevel 1 (
    echo ❌ 构建失败
    pause
    exit /b 1
)
echo ✅ APK构建完成

:: 创建发布目录
echo [5/6] 准备发布文件...
if not exist "releases" mkdir releases

:: 生成带时间戳的APK文件名
for /f "tokens=1-3 delims=/ " %%a in ('date /t') do set DATE=%%a%%b%%c
for /f "tokens=1-2 delims=: " %%a in ('time /t') do set TIME=%%a%%b
set TIMESTAMP=%DATE%_%TIME%
set TIMESTAMP=%TIMESTAMP: =0%

:: 复制APK文件
copy "app\build\outputs\apk\debug\app-debug.apk" "releases\LocationUploader-v5.0-%TIMESTAMP%.apk" >nul
if errorlevel 1 (
    echo ❌ 复制APK文件失败
    pause
    exit /b 1
)

:: 创建发布说明
echo [6/6] 生成发布说明...
(
echo # LocationUploader v5.0 发布说明
echo.
echo ## 版本信息
echo - **版本**: v5.0
echo - **构建时间**: %DATE% %TIME%
echo - **APK文件**: LocationUploader-v5.0-%TIMESTAMP%.apk
echo.
echo ## 主要功能
echo - ✅ GPS卫星定位 (3-30m精度)
echo - ✅ 网络定位 (10-100m精度)
echo - ✅ 实时精度更新
echo - ✅ GNSS状态监听
echo - ✅ TCP传输支持 (新增)
echo - ✅ HTTP/HTTPS/UDP传输
echo - 🔧 地图API定位 (可选)
echo.
echo ## 传输方式
echo - **TCP** (推荐): 端口8280，可靠连接
echo - **HTTP**: 端口28081，Web协议
echo - **HTTPS**: 端口28081，SSL加密
echo - **UDP**: 自定义端口，高速传输
echo.
echo ## 安装说明
echo 1. 下载APK文件到Android设备
echo 2. 允许安装未知来源应用
echo 3. 安装并授予必要权限
echo 4. 配置网络传输参数
echo.
echo ## 配置说明
echo 详细配置请参考 docs/ 目录下的文档：
echo - TCP传输: docs/TCP传输方式说明.md
echo - 云服务器: docs/云服务器配置说明.md
echo - 地图API: docs/地图API功能启用说明.md
echo.
echo ---
echo 构建完成时间: %DATE% %TIME%
) > "releases\LocationUploader-v5.0-%TIMESTAMP%-README.md"

echo ✅ 发布文件准备完成
echo.

:: 显示构建结果
echo ========================================
echo           构建结果
echo ========================================
echo 📱 APK文件: releases\LocationUploader-v5.0-%TIMESTAMP%.apk
echo 📄 说明文档: releases\LocationUploader-v5.0-%TIMESTAMP%-README.md
echo 📁 文档目录: docs\
echo.

:: 检查APK文件大小
for %%A in ("releases\LocationUploader-v5.0-%TIMESTAMP%.apk") do set SIZE=%%~zA
set /a SIZE_MB=%SIZE%/1024/1024
echo 📊 APK大小: %SIZE_MB% MB

:: 显示可用文档
echo.
echo 📚 可用文档:
dir /b docs\*.md | findstr /v "网络定位功能说明.md"

echo.
echo ✅ 构建完成！请查看 releases 目录
echo.
pause

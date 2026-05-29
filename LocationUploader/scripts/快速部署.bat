@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

echo ========================================
echo    LocationUploader 快速部署工具
echo ========================================
echo.

:: 检查项目结构
echo [1/5] 检查项目结构...
if not exist "app\build.gradle" (
    echo ❌ 错误: 请在LocationUploader项目根目录运行此脚本
    pause
    exit /b 1
)
echo ✅ 项目结构检查通过

:: 构建APK
echo [2/5] 构建APK...
call gradlew clean assembleDebug
if errorlevel 1 (
    echo ❌ 构建失败
    pause
    exit /b 1
)
echo ✅ APK构建完成

:: 创建发布目录
echo [3/5] 准备发布文件...
if not exist "releases" mkdir releases

:: 生成时间戳
for /f "tokens=1-3 delims=/ " %%a in ('date /t') do set DATE=%%a%%b%%c
for /f "tokens=1-2 delims=: " %%a in ('time /t') do set TIME=%%a%%b
set TIMESTAMP=%DATE%_%TIME%
set TIMESTAMP=%TIMESTAMP: =0%

:: 复制APK
copy "app\build\outputs\apk\debug\app-debug.apk" "releases\LocationUploader-v5.0-%TIMESTAMP%.apk" >nul
echo ✅ APK文件已复制到 releases 目录

:: 创建部署包
echo [4/5] 创建部署包...
if not exist "分享包" mkdir "分享包"

:: 复制必要文件到分享包
xcopy "releases\LocationUploader-v5.0-%TIMESTAMP%.apk" "分享包\" /Y >nul
xcopy "docs\TCP传输方式说明.md" "分享包\" /Y >nul
xcopy "docs\云服务器配置说明.md" "分享包\" /Y >nul
xcopy "docs\地图API功能启用说明.md" "分享包\" /Y >nul
xcopy "README.md" "分享包\" /Y >nul

:: 创建部署说明
(
echo # LocationUploader v5.0 部署包
echo.
echo ## 文件说明
echo - **LocationUploader-v5.0-%TIMESTAMP%.apk**: 主程序文件
echo - **TCP传输方式说明.md**: TCP传输配置指南
echo - **云服务器配置说明.md**: 云服务器部署指南
echo - **地图API功能启用说明.md**: 地图API配置指南
echo - **README.md**: 项目说明文档
echo.
echo ## 快速开始
echo 1. 安装APK文件到Android设备
echo 2. 授予位置和网络权限
echo 3. 配置网络传输参数
echo 4. 开始使用
echo.
echo ## 配置说明
echo - **TCP传输**: 推荐用于云服务器部署
echo - **HTTP/HTTPS**: 适用于Web服务器
echo - **UDP**: 适用于实时数据传输
echo - **地图API**: 可选功能，需配置密钥
echo.
echo ## 技术支持
echo 详细文档请查看 docs/ 目录
echo 或参考项目完整文档
echo.
echo ---
echo 部署包创建时间: %DATE% %TIME%
) > "分享包\部署说明.md"

echo ✅ 部署包创建完成

:: 显示结果
echo [5/5] 显示部署结果...
echo.
echo ========================================
echo           部署结果
echo ========================================
echo 📱 APK文件: releases\LocationUploader-v5.0-%TIMESTAMP%.apk
echo 📦 部署包: 分享包\
echo 📄 说明文档: 分享包\部署说明.md
echo.

:: 检查文件大小
for %%A in ("releases\LocationUploader-v5.0-%TIMESTAMP%.apk") do set SIZE=%%~zA
set /a SIZE_MB=%SIZE%/1024/1024
echo 📊 APK大小: %SIZE_MB% MB

:: 显示分享包内容
echo.
echo 📦 部署包内容:
dir /b "分享包\"

echo.
echo ✅ 快速部署完成！
echo 📁 请查看 分享包 目录获取部署文件
echo.
pause

@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

echo ========================================
echo    LocationUploader 项目清理工具
echo ========================================
echo.

echo 此工具将清理项目中的临时文件和旧版本文件
echo 请确认是否继续？ (Y/N)
set /p choice=
if /i not "%choice%"=="Y" (
    echo 操作已取消
    pause
    exit /b 0
)

echo.
echo [1/4] 清理构建文件...
if exist "build" (
    rmdir /s /q "build"
    echo ✅ 清理 build 目录
)
if exist "app\build" (
    rmdir /s /q "app\build"
    echo ✅ 清理 app\build 目录
)
if exist ".gradle" (
    rmdir /s /q ".gradle"
    echo ✅ 清理 .gradle 目录
)

echo [2/4] 清理临时文件...
if exist "*.tmp" del /q "*.tmp" >nul 2>&1
if exist "*.log" del /q "*.log" >nul 2>&1
if exist "*.bak" del /q "*.bak" >nul 2>&1
echo ✅ 清理临时文件

echo [3/4] 清理旧版本APK...
:: 保留最新的3个版本
set count=0
for /f "tokens=*" %%i in ('dir /b /o-d "releases\LocationUploader-*.apk" 2^>nul') do (
    set /a count+=1
    if !count! gtr 3 (
        echo 删除旧版本: %%i
        del "releases\%%i"
    )
)
echo ✅ 清理旧版本APK (保留最新3个)

echo [4/4] 清理分享包...
if exist "分享包" (
    rmdir /s /q "分享包"
    echo ✅ 清理分享包目录
)

echo.
echo ========================================
echo           清理完成
echo ========================================
echo ✅ 项目清理完成
echo 📁 已清理的目录:
echo    - build/
echo    - app/build/
echo    - .gradle/
echo    - 分享包/
echo    - 临时文件
echo    - 旧版本APK (保留最新3个)
echo.
echo 💾 清理前请确保重要文件已备份
echo.
pause

@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

:menu
cls
echo ========================================
echo    LocationUploader 项目管理工具
echo ========================================
echo.
echo 请选择要执行的操作：
echo.
echo [1] 构建APK (Debug版本)
echo [2] 构建APK (Release版本)
echo [3] 快速部署 (构建+打包)
echo [4] 项目清理
echo [5] 获取SHA1签名
echo [6] 查看项目状态
echo [7] 打开文档目录
echo [8] 打开脚本目录
echo [9] 打开发布目录
echo [0] 退出
echo.
set /p choice=请输入选项 (0-9): 

if "%choice%"=="1" goto build_debug
if "%choice%"=="2" goto build_release
if "%choice%"=="3" goto quick_deploy
if "%choice%"=="4" goto clean_project
if "%choice%"=="5" goto get_sha1
if "%choice%"=="6" goto show_status
if "%choice%"=="7" goto open_docs
if "%choice%"=="8" goto open_scripts
if "%choice%"=="9" goto open_releases
if "%choice%"=="0" goto exit
goto menu

:build_debug
cls
echo ========================================
echo        构建APK (Debug版本)
echo ========================================
echo.
echo 正在构建Debug版本APK...
echo.

:: 检查Java环境
echo [1/5] 检查Java环境...
java -version >nul 2>&1
if errorlevel 1 (
    echo ❌ 错误: 未找到Java环境，请安装JDK 8或更高版本
    pause
    goto menu
)
echo ✅ Java环境检查通过

:: 清理项目
echo [2/5] 清理项目...
call gradlew clean
if errorlevel 1 (
    echo ❌ 清理失败
    pause
    goto menu
)
echo ✅ 项目清理完成

:: 构建APK
echo [3/5] 构建APK...
call gradlew assembleDebug
if errorlevel 1 (
    echo ❌ 构建失败
    pause
    goto menu
)
echo ✅ APK构建完成

:: 创建发布目录
echo [4/5] 准备发布文件...
if not exist "releases" mkdir releases

:: 生成带时间戳的APK文件名
for /f "tokens=1-3 delims=/ " %%a in ('date /t') do set DATE=%%a%%b%%c
for /f "tokens=1-2 delims=: " %%a in ('time /t') do set TIME=%%a%%b
set TIMESTAMP=%DATE%_%TIME%
set TIMESTAMP=%TIMESTAMP: =0%

:: 复制APK文件
copy "app\build\outputs\apk\debug\app-debug.apk" "releases\LocationUploader-v5.0-Debug-%TIMESTAMP%.apk" >nul
if errorlevel 1 (
    echo ❌ 复制APK文件失败
    pause
    goto menu
)

echo [5/5] 构建完成
echo.
echo ✅ Debug版本APK构建成功！
echo 📱 文件位置: releases\LocationUploader-v5.0-Debug-%TIMESTAMP%.apk
echo.
pause
goto menu

:build_release
cls
echo ========================================
echo       构建APK (Release版本)
echo ========================================
echo.
echo 正在构建Release版本APK...
echo.

:: 检查Java环境
echo [1/5] 检查Java环境...
java -version >nul 2>&1
if errorlevel 1 (
    echo ❌ 错误: 未找到Java环境，请安装JDK 8或更高版本
    pause
    goto menu
)
echo ✅ Java环境检查通过

:: 清理项目
echo [2/5] 清理项目...
call gradlew clean
if errorlevel 1 (
    echo ❌ 清理失败
    pause
    goto menu
)
echo ✅ 项目清理完成

:: 构建APK
echo [3/5] 构建APK...
call gradlew assembleRelease
if errorlevel 1 (
    echo ❌ 构建失败
    pause
    goto menu
)
echo ✅ APK构建完成

:: 创建发布目录
echo [4/5] 准备发布文件...
if not exist "releases" mkdir releases

:: 生成带时间戳的APK文件名
for /f "tokens=1-3 delims=/ " %%a in ('date /t') do set DATE=%%a%%b%%c
for /f "tokens=1-2 delims=: " %%a in ('time /t') do set TIME=%%a%%b
set TIMESTAMP=%DATE%_%TIME%
set TIMESTAMP=%TIMESTAMP: =0%

:: 复制APK文件
copy "app\build\outputs\apk\release\app-release.apk" "releases\LocationUploader-v5.0-Release-%TIMESTAMP%.apk" >nul
if errorlevel 1 (
    echo ❌ 复制APK文件失败
    pause
    goto menu
)

echo [5/5] 构建完成
echo.
echo ✅ Release版本APK构建成功！
echo 📱 文件位置: releases\LocationUploader-v5.0-Release-%TIMESTAMP%.apk
echo.
pause
goto menu

:quick_deploy
cls
echo ========================================
echo           快速部署
echo ========================================
echo.
echo 正在执行快速部署...
echo.

:: 调用快速部署脚本
if exist "scripts\快速部署.bat" (
    call "scripts\快速部署.bat"
) else (
    echo ❌ 快速部署脚本不存在
    echo 请先运行构建APK功能
    pause
    goto menu
)

echo.
echo ✅ 快速部署完成！
pause
goto menu

:clean_project
cls
echo ========================================
echo           项目清理
echo ========================================
echo.
echo 此操作将清理项目中的临时文件和旧版本文件
echo 请确认是否继续？ (Y/N)
set /p confirm=
if /i not "%confirm%"=="Y" (
    echo 操作已取消
    pause
    goto menu
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
pause
goto menu

:get_sha1
cls
echo ========================================
echo         获取SHA1签名
echo ========================================
echo.
echo 正在获取应用SHA1签名...
echo.

:: 尝试多个可能的keystore位置
set "KEYSTORE_PATHS=C:\Users\%USERNAME%\.android\debug.keystore;C:\Users\%USERNAME%\AppData\Local\Android\Sdk\debug.keystore;%USERPROFILE%\.android\debug.keystore"

for %%i in (%KEYSTORE_PATHS%) do (
    if exist "%%i" (
        echo 找到keystore: %%i
        echo.
        keytool -list -v -keystore "%%i" -alias androiddebugkey -storepass android -keypass android
        echo.
        echo 请复制上面的SHA1值（以冒号分隔的40位十六进制字符串）
        pause
        goto menu
    )
)

echo 未找到debug.keystore文件
echo.
echo 请手动运行以下命令获取SHA1：
echo keytool -list -v -keystore "您的keystore路径" -alias androiddebugkey -storepass android -keypass android
echo.
echo 或者使用Android Studio：
echo 1. 打开Android Studio
echo 2. 点击菜单 Gradle -^> Tasks -^> android -^> signingReport
echo 3. 查看输出的SHA1值
echo.
pause
goto menu

:show_status
cls
echo ========================================
echo           项目状态
echo ========================================
echo.
echo 📊 项目概况
echo 项目名称: LocationUploader
echo 当前版本: v5.0
echo 最后更新: 2024年12月
echo.
echo 📁 文件统计
if exist "app\src\main\java" (
    dir /b "app\src\main\java\com\example\locationuploader\*.java" | find /c /v ""
) else (
    echo 0
)
echo 个Java源文件
if exist "docs" (
    dir /b "docs\*.md" | find /c /v ""
) else (
    echo 0
)
echo 个文档文件
if exist "scripts" (
    dir /b "scripts\*.bat" | find /c /v ""
) else (
    echo 0
)
echo 个脚本文件
if exist "releases" (
    dir /b "releases\*.apk" | find /c /v ""
) else (
    echo 0
)
echo 个APK文件
echo.
echo 🔧 功能状态
echo ✅ GPS卫星定位
echo ✅ 网络定位
echo ✅ 实时精度更新
echo ✅ GNSS状态监听
echo ✅ TCP传输支持
echo ✅ HTTP/HTTPS/UDP传输
echo 🔧 地图API定位 (可选)
echo.
echo 📱 最新APK文件
if exist "releases" (
    for /f "tokens=*" %%i in ('dir /b /o-d "releases\*.apk" 2^>nul') do (
        echo - %%i
        goto :break
    )
) else (
    echo - 无APK文件
)
:break
echo.
pause
goto menu

:open_docs
cls
echo ========================================
echo           打开文档目录
echo ========================================
echo.
if exist "docs" (
    explorer "docs"
    echo ✅ 已打开文档目录
) else (
    echo ❌ 文档目录不存在
)
echo.
pause
goto menu

:open_scripts
cls
echo ========================================
echo           打开脚本目录
echo ========================================
echo.
if exist "scripts" (
    explorer "scripts"
    echo ✅ 已打开脚本目录
) else (
    echo ❌ 脚本目录不存在
)
echo.
pause
goto menu

:open_releases
cls
echo ========================================
echo           打开发布目录
echo ========================================
echo.
if exist "releases" (
    explorer "releases"
    echo ✅ 已打开发布目录
) else (
    echo ❌ 发布目录不存在
)
echo.
pause
goto menu

:exit
echo.
echo 感谢使用LocationUploader项目管理工具！
echo.
exit /b 0

@echo off
echo 正在获取应用SHA1签名...
echo.

REM 尝试多个可能的keystore位置
set "KEYSTORE_PATHS=C:\Users\%USERNAME%\.android\debug.keystore;C:\Users\%USERNAME%\AppData\Local\Android\Sdk\debug.keystore;%USERPROFILE%\.android\debug.keystore"

for %%i in (%KEYSTORE_PATHS%) do (
    if exist "%%i" (
        echo 找到keystore: %%i
        echo.
        keytool -list -v -keystore "%%i" -alias androiddebugkey -storepass android -keypass android
        echo.
        echo 请复制上面的SHA1值（以冒号分隔的40位十六进制字符串）
        pause
        exit /b 0
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

@echo off
chcp 65001 >nul
echo ========================================
echo LocationUploader 数据包延迟分析工具
echo ========================================
echo.
echo 正在启动数据包延迟分析服务器...
echo 端口: 12345
echo.
echo 使用说明:
echo 1. 确保Android设备已连接并运行LocationUploader
echo 2. 在LocationUploader中设置服务器IP为当前电脑IP
echo 3. 点击"开始持续上传"
echo 4. 观察延迟分析结果
echo.
echo 按 Ctrl+C 停止服务器
echo ========================================
echo.

python 数据包延迟分析工具.py

pause

@echo off
echo ========================================
echo    LocationUploader 结构体格式服务器
echo ========================================
echo.
echo 版本: v1.0.6
echo 格式: 严格按照gnss_data.h结构体显示
echo.
echo 显示特点:
echo ✅ 移除所有emoji图标
echo ✅ 严格按照结构体格式
echo ✅ 显示完整PDOP值
echo ✅ 字段名称与.h文件一致
echo ✅ 便于数据对接
echo.
echo 数据包结构:
echo 📦 Send_Buf_Data (304字节)
echo   ├─ TCP_FrameHeader (20字节)
echo   └─ GNSS_Data (284字节)
echo.
echo 发送间隔: 1秒
echo 监听地址: 0.0.0.0:12345
echo 日志文件: server.log
echo.

python Tcp.py

echo.
echo 服务器已停止
pause

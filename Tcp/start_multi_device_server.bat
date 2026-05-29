@echo off
echo ========================================
echo    LocationUploader 多设备服务器
echo ========================================
echo.
echo 版本: v1.0.6
echo 功能: 多设备支持和管理
echo.
echo 多设备特性:
echo ✅ 设备唯一标识 (node_name)
echo ✅ 设备注册和管理
echo ✅ 数据分离存储
echo ✅ 设备状态监控
echo ✅ 数据统计汇总
echo ✅ 并发连接处理
echo.
echo 数据包结构:
echo 📦 Send_Buf_Data (304字节)
echo   ├─ TCP_FrameHeader (20字节)
echo   │   └─ node_name: 0xD******* (设备ID)
echo   └─ GNSS_Data (284字节)
echo.
echo 支持设备数量: 无限制
echo 发送间隔: 1秒/设备
echo 监听地址: 0.0.0.0:12345
echo 日志文件: multi_device_server.log
echo.

python multi_device_server.py

echo.
echo 服务器已停止
pause

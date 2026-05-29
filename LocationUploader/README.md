# LocationUploader - 智能导航位置上传应用

## 📱 应用简介

LocationUploader 是一个专为智能导航项目设计的Android应用，能够接收位置和卫星信息数据，并按照指定的二进制格式上传到服务器。

**项目状态**：✅ 已完成  
**最终版本**：v5.4 DataLogicFixed  
**APK文件**：LocationUploader-v5.4-DataLogicFixed.apk (1.7MB)

## 🎯 主要功能

### ✅ 当前启用功能
- **GPS卫星定位**: 高精度室外定位 (3-30m精度)
- **网络定位**: WiFi+基站定位 (10-100m精度)
- **实时精度更新**: 动态显示定位精度
- **GNSS状态监听**: 实时卫星信息显示
- **数据上传**: 支持TCP/UDP/HTTP/HTTPS上传
- **本地/远程配置**: 灵活的网络配置

### 🔧 可选功能 (需手动启用)
- **地图API定位**: 高德/百度地图IP定位 (500-800m精度)
- **Google定位**: WiFi+基站高精度定位 (10-100m精度)

## 📋 功能状态

| 功能 | 状态 | 说明 |
|------|------|------|
| GPS定位 | ✅ 启用 | 高精度室外定位 |
| 网络定位 | ✅ 启用 | WiFi+基站定位 |
| 地图API定位 | 🔧 可选 | 需配置API密钥 |
| 实时精度 | ✅ 启用 | 动态精度显示 |
| 数据上传 | ✅ 启用 | 多种协议支持 |

## 🚀 快速开始

### 1. 构建和安装应用

#### 方法一：使用构建脚本（推荐）
```bash
# 运行构建脚本
build_apk.bat

# 或使用快速部署脚本
scripts\快速部署.bat
```

#### 方法二：手动构建
```bash
# 清理项目
.\gradlew clean

# 构建APK
.\gradlew assembleDebug
```

### 2. 下载最终版本
**最终版本APK文件**：
- `LocationUploader-v5.4-DataLogicFixed.apk` - 完整修复版本 (1.7MB，推荐使用) ⭐
- `LocationUploader-Final-Release.apk` - 最终版本 (1.7MB，功能完整)

### 2. 基本使用
1. 启动应用
2. 点击"获取位置 (GPS+网络)"
3. 等待定位完成
4. 点击"开始上传"发送数据

### 3. 网络配置
- **本地配置**: 设置本地服务器IP和端口
- **远程配置**: 设置远程服务器地址和传输协议

#### 远程网络传输方式
- **TCP** (推荐): 可靠连接，适合二进制数据，端口8280
- **HTTP/HTTPS**: 基于Web协议，端口28081
- **UDP**: 无连接高速传输，自定义端口

#### TCP传输配置示例
```
传输方式: TCP
服务器地址: 123.456.789.123
端口号: 8280
SSL加密: 关闭
```

## 🔧 启用地图API功能

如需使用地图API定位功能，请参考：
[地图API功能启用说明](docs/地图API功能启用说明.md)

### 启用步骤
1. 申请高德/百度地图API密钥
2. 配置API密钥到代码中
3. 取消注释相关代码
4. 重新编译应用

## 📊 定位精度说明

### 当前启用功能
- **GPS定位**: 3-30m (室外高精度)
- **网络定位**: 10-100m (WiFi+基站)

### 可选功能
- **高德地图API**: 500m (IP定位)
- **百度地图API**: 800m (IP定位)
- **Google定位**: 10-100m (WiFi+基站)

## 📁 项目结构

```
LocationUploader/
├── app/src/main/java/com/example/locationuploader/
│   ├── MainActivity.java              # 主界面
│   ├── LocationService.java           # 位置服务
│   ├── GNSSStatusListener.java        # GNSS监听
│   ├── NetworkLocationHelper.java     # 网络定位 (可选)
│   ├── BinaryDataPacket.java          # 数据打包
│   ├── RemoteUploadTask.java          # 远程上传任务
│   └── GNSSData.java                  # GNSS数据
├── docs/                              # 文档目录
│   ├── 项目文档索引.md                # 文档索引
│   ├── TCP传输方式说明.md             # TCP传输说明
│   ├── 云服务器配置说明.md            # 云服务器配置
│   ├── 地图API功能启用说明.md         # API功能说明
│   ├── 真实GNSS数据功能改进报告.md    # GNSS功能
│   ├── 精度显示与定位状态优化说明.md  # 精度优化
│   ├── API密钥配置说明.md             # 密钥配置
│   └── 其他优化文档...                # 其他文档
├── scripts/                           # 脚本目录
│   ├── 快速部署.bat                   # 快速部署脚本
│   ├── 项目清理.bat                   # 项目清理脚本
│   ├── 智能构建安装.bat               # 智能构建脚本
│   └── 创建分享包.bat                 # 分享包创建脚本
├── releases/                          # 发布版本
├── 分享包/                           # 部署包
├── build_apk.bat                     # APK构建脚本
├── get_sha1.bat                      # SHA1获取脚本
└── README.md                         # 项目说明
```

## 🔍 技术特性

### 定位技术
- **GPS**: Android LocationManager GPS_PROVIDER
- **网络**: Android LocationManager NETWORK_PROVIDER
- **GNSS**: 实时卫星状态监听
- **地图API**: 第三方地图服务 (可选)

### 数据格式
- **二进制格式**: 兼容C语言结构体
- **实时打包**: 动态生成数据包
- **多协议支持**: TCP/UDP/HTTP/HTTPS

### 性能优化
- **实时精度更新**: 动态显示定位精度
- **智能定位选择**: 自动选择最佳定位方式
- **后台服务**: 前台服务保证稳定性

## 🛠️ 脚本工具

### 构建和部署脚本
- **build_apk.bat**: 主要构建脚本，生成带时间戳的APK
- **scripts\快速部署.bat**: 快速部署脚本，创建完整的部署包
- **scripts\项目清理.bat**: 清理项目临时文件和旧版本
- **scripts\智能构建安装.bat**: 智能构建和安装脚本
- **scripts\创建分享包.bat**: 创建分享包脚本

### 使用说明
```bash
# 构建APK
build_apk.bat

# 快速部署
scripts\快速部署.bat

# 清理项目
scripts\项目清理.bat

# 获取SHA1指纹
get_sha1.bat
```

## 📞 技术支持

### 常见问题
1. **定位失败**: 检查GPS和网络权限
2. **精度不准确**: 移至室外或靠近窗户
3. **上传失败**: 检查网络配置和服务器状态
4. **构建失败**: 检查Java和Android SDK环境

### 调试方法
```bash
# 查看应用日志
adb logcat | grep LocationUploader

# 查看网络定位日志 (如果启用)
adb logcat | grep NetworkLocationHelper
```

## 📄 许可证

本项目仅供学习和研究使用。

## 🔄 版本历史

- **v1.0**: 基础GPS定位功能
- **v2.0**: 添加网络定位和GNSS监听
- **v3.0**: 添加地图API定位 (可选)
- **v4.0**: 优化精度显示和用户体验
- **v5.0**: 新增TCP传输方式，支持云服务器部署

---

**当前版本**: LocationUploader-TCP-Support.apk  
**主要功能**: GPS+网络定位，实时精度更新，TCP传输支持  
**地图API**: 可选功能，需手动启用  
**传输方式**: TCP/HTTP/HTTPS/UDP

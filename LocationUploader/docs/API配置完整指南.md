# API配置完整指南

## 📋 概述

LocationUploader应用支持多种网络定位服务，需要配置相应的API密钥才能使用完整功能。

## 🔑 支持的API服务

| 服务 | 功能 | 精度 | 免费额度 | 状态 |
|------|------|------|----------|------|
| **高德地图** | IP定位 | 500m | 30万次/天 | ✅ 已配置 |
| **百度地图** | IP定位 | 800m | 30万次/天 | ✅ 已配置 |
| **Google定位** | WiFi+基站 | 10-100m | 1000次/天 | 🔧 可选 |

## 🚀 快速配置

### 当前配置状态
```java
// 高德地图API (已配置)
private static final String AMAP_KEY = "a5fadfbc5910bd32ddfd22bccb921e67";

// 百度地图API (已配置)
private static final String BAIDU_KEY = "Nat1yWckgOCUv980wTJOLqeCMRl61fUd";

// Google定位API (可选)
private static final String GOOGLE_KEY = ""; // 需要自行申请
```

## 📱 申请新API密钥

### 1. 高德地图API密钥

#### 申请步骤
1. 访问 [高德开放平台](https://lbs.amap.com/)
2. 注册并登录开发者账号
3. 进入控制台：https://console.amap.com/
4. 创建新应用
5. 选择"Android平台"
6. 填写应用信息：
   - **应用名称**: LocationUploader
   - **应用包名**: com.example.locationuploader
   - **应用签名**: 运行 `get_sha1.bat` 获取
7. 获取API Key

#### 配置代码
```java
// 在NetworkLocationHelper.java中修改
private static final String AMAP_KEY = "your_amap_key_here";
```

#### 免费额度
- **每日限额**: 30万次
- **并发限制**: 100次/秒
- **有效期**: 永久

### 2. 百度地图API密钥

#### 申请步骤
1. 访问 [百度地图开放平台](https://lbsyun.baidu.com/)
2. 注册并登录开发者账号
3. 进入控制台：https://lbsyun.baidu.com/apiconsole/key
4. 创建应用
5. 选择"Android SDK"
6. 填写应用信息：
   - **应用名称**: LocationUploader
   - **应用包名**: com.example.locationuploader
   - **应用签名**: 运行 `get_sha1.bat` 获取
7. 获取AK (Access Key)

#### 配置代码
```java
// 在NetworkLocationHelper.java中修改
private static final String BAIDU_KEY = "your_baidu_key_here";
```

#### 免费额度
- **每日限额**: 30万次
- **并发限制**: 200次/秒
- **有效期**: 永久

### 3. Google Location Services API密钥

#### 申请步骤
1. 访问 [Google Cloud Console](https://console.cloud.google.com/)
2. 创建新项目或选择现有项目
3. 启用API：
   - 进入"API和服务" > "库"
   - 搜索"Geolocation API"
   - 点击启用
4. 创建凭据：
   - 进入"API和服务" > "凭据"
   - 点击"创建凭据" > "API密钥"
5. 设置应用限制（推荐）：
   - 点击刚创建的API密钥
   - 在"应用程序限制"中选择"Android应用"
   - 添加您的应用包名和SHA-1签名

#### 配置代码
```java
// 在NetworkLocationHelper.java中修改
private static final String GOOGLE_KEY = "your_google_key_here";
```

#### 免费额度
- **每日限额**: 1000次（免费层）
- **付费**: $5.00 per 1000次
- **并发限制**: 100次/秒

## 🔧 完整配置示例

### 修改NetworkLocationHelper.java
```java
public class NetworkLocationHelper {
    // 高德地图API配置
    private static final String AMAP_IP_LOCATION_URL = "https://restapi.amap.com/v3/ip";
    private static final String AMAP_KEY = "your_amap_key_here"; // 替换为您的密钥
    
    // 百度地图API配置
    private static final String BAIDU_IP_LOCATION_URL = "https://api.map.baidu.com/location/ip";
    private static final String BAIDU_KEY = "your_baidu_key_here"; // 替换为您的密钥
    
    // Google定位API配置
    private static final String GOOGLE_LOCATION_URL = "https://www.googleapis.com/geolocation/v1/geolocate";
    private static final String GOOGLE_KEY = "your_google_key_here"; // 替换为您的密钥
}
```

## 📱 使用方法

### 在应用中测试网络定位

1. **启动应用**
2. **点击"获取位置"按钮**
3. **等待定位结果**
   - 应用会依次尝试GPS、高德、百度、Google定位
   - 自动选择最佳定位结果

### 定位优先级
```
GPS定位 → 高德地图 → 百度地图 → Google定位
```

## 📊 预期效果

### 室内环境
- ✅ 能够获取到大致位置信息
- ✅ 精度约500-800米（IP定位限制）
- ✅ 无需GPS信号

### 室外环境
- ✅ GPS定位优先（高精度）
- ✅ 网络定位作为备用
- ✅ 无缝切换

## 🔍 调试和故障排除

### 查看日志
```bash
# 查看网络定位日志
adb logcat | grep NetworkLocationHelper

# 查看API请求/响应
adb logcat | grep "HTTP响应码"
```

### 常见问题

1. **API密钥无效**
   - 检查密钥是否正确配置
   - 确认密钥是否已激活
   - 检查应用包名和签名是否匹配

2. **网络连接失败**
   - 检查网络连接
   - 确认防火墙设置
   - 检查API服务状态

3. **定位精度过低**
   - IP定位精度有限（500-800米）
   - 考虑使用Google定位服务
   - 在室外使用GPS定位

### 调试步骤
1. 确认网络连接正常
2. 查看HTTP响应码是否为200
3. 检查API响应内容格式
4. 验证API密钥权限
5. 查看详细错误日志

## ⚠️ 重要提醒

### API使用限制
- **高德地图**: 免费额度 300,000次/天
- **百度地图**: 免费额度 30,000次/天
- **Google定位**: 需要Google Play Services

### 安全建议
- 不要将API密钥分享给他人
- 定期检查API使用量
- 在生产环境中考虑使用更安全的密钥管理方式
- 设置API密钥的应用限制

### 性能优化
- 启用缓存机制减少API调用
- 合理设置超时时间
- 监控API使用量避免超出限制

## 🚀 下一步

1. **测试网络定位功能**
   - 在室内环境测试
   - 验证定位准确性

2. **监控API使用情况**
   - 检查各平台的使用量
   - 确保不超过免费额度

3. **优化定位策略**
   - 根据实际使用情况调整优先级
   - 考虑添加缓存机制

## 📞 技术支持

如果遇到定位问题：
1. 检查网络连接
2. 确认API密钥有效性
3. 查看应用日志输出
4. 联系API服务商技术支持

---

**配置完成时间**: 2024年12月
**应用版本**: LocationUploader v5.0
**维护状态**: 活跃维护中

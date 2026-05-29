# 地图API功能启用说明

## 📋 功能状态

**当前状态**: 地图API定位功能已暂时禁用
**保留内容**: 完整的地图API定位代码和文档
**启用方式**: 配置API密钥后即可启用

## 🔧 启用步骤

### 1. 申请API密钥

#### 高德地图API密钥
1. 访问 [高德开放平台](https://lbs.amap.com/)
2. 注册开发者账号
3. 创建应用，选择"Android平台"
4. 填写应用信息：
   - **PackageName**: `com.example.locationuploader`
   - **发布版SHA1**: 运行 `get_sha1.bat` 获取
   - **开发版SHA1**: 可选填写
5. 获取API密钥

#### 百度地图API密钥
1. 访问 [百度地图开放平台](https://lbsyun.baidu.com/)
2. 注册开发者账号
3. 创建应用，选择"Android平台"
4. 填写应用信息：
   - **PackageName**: `com.example.locationuploader`
   - **SHA1**: 运行 `get_sha1.bat` 获取
5. 获取API密钥

### 2. 配置API密钥

编辑文件：`app/src/main/java/com/example/locationuploader/NetworkLocationHelper.java`

```java
// 高德地图API配置
private static final String AMAP_KEY = "您的_高德地图_API密钥"; // 替换为您的密钥

// 百度地图API配置
private static final String BAIDU_KEY = "您的_百度地图_API密钥"; // 替换为您的密钥
```

### 3. 启用地图API定位

编辑文件：`app/src/main/java/com/example/locationuploader/MainActivity.java`

在 `getLocation()` 方法中，找到以下代码：

```java
// 地图API定位功能已暂时禁用
// 如需启用，请：
// 1. 在NetworkLocationHelper.java中配置API密钥
// 2. 取消注释以下代码：
// if (networkLocationHelper != null) {
//     networkLocationHelper.getNetworkLocation();
// }
```

取消注释并修改为：

```java
// 启用地图API定位
if (networkLocationHelper != null) {
    networkLocationHelper.getNetworkLocation();
}
```

### 4. 修改按钮文本

编辑文件：`app/src/main/res/layout/activity_main.xml`

将按钮文本修改为：

```xml
android:text="获取位置 (GPS+网络+地图API)"
```

### 5. 修改状态显示

编辑文件：`app/src/main/java/com/example/locationuploader/MainActivity.java`

在 `getLocation()` 方法中，修改状态文本：

```java
statusText += "✓ 地图API定位已启用\n";
```

## 📱 功能说明

### 地图API定位功能
- **高德地图API**: IP定位，精度约500m
- **百度地图API**: IP定位，精度约800m
- **Google定位**: WiFi+基站定位，精度10-100m

### 定位策略
1. **GPS优先**: 如果GPS精度≤30m，优先使用GPS结果
2. **地图API备用**: GPS失败时使用地图API结果
3. **智能选择**: 自动选择最佳定位结果

### 显示效果
```
位置信息:
纬度: XX.XXXXXX
经度: XX.XXXXXX
精度: XX.Xm (高精度/中等精度/低精度)
速度: XX.XX m/s
方向: XXX.X°
定位方式: GPS卫星定位/高德地图API定位/百度地图API定位
```

## ⚠️ 重要提醒

### API使用限制
- **高德地图**: 免费额度 300,000次/天
- **百度地图**: 免费额度 30,000次/天
- **Google定位**: 需要Google Play Services

### 安全建议
- 不要将API密钥分享给他人
- 定期检查API使用量
- 在生产环境中考虑使用更安全的密钥管理方式

### 测试建议
1. **网络连接**: 确保网络连接正常
2. **API密钥**: 验证API密钥有效性
3. **权限检查**: 确认网络权限已授予

## 🔍 故障排除

### 常见问题
1. **API密钥无效**: 检查密钥是否正确配置
2. **网络连接失败**: 检查网络连接和防火墙设置
3. **定位失败**: 查看Logcat日志，过滤标签 `NetworkLocationHelper`

### 调试方法
```bash
# 查看详细日志
adb logcat | grep NetworkLocationHelper
```

## 📞 技术支持

如果遇到问题，请检查：
1. API密钥配置是否正确
2. 网络连接是否正常
3. 应用权限是否已授予
4. Logcat日志中的错误信息

---

**文档创建时间**: 2024年12月
**功能状态**: 已禁用，可手动启用
**适用版本**: LocationUploader 所有版本

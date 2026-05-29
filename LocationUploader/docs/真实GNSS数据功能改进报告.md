# 真实GNSS数据功能改进报告

## 概述

本次改进主要解决了原项目中"根据精度来估算出卫星个数和虚拟的单颗卫星数据"的问题，实现了获取真实的当前位置卫星数据的功能。通过集成Android GNSS API，应用现在能够获取北斗、GPS、GLONASS等多卫星系统的真实数据。

## 主要改进内容

### 1. 新增GNSSStatusListener类

**文件**: `app/src/main/java/com/example/locationuploader/GNSSStatusListener.java`

**功能**:
- 监听真实GNSS状态变化
- 解析多卫星系统数据（北斗、GPS、GLONASS、Galileo等）
- 获取真实卫星信号强度、仰角、方位角等信息
- 提供卫星数据回调接口

**核心特性**:
```java
// 支持的真实卫星系统
- GPS (gnssId: 1)
- SBAS (gnssId: 2) 
- Galileo (gnssId: 3)
- GLONASS (gnssId: 6)
- 北斗 (gnssId: 7)
- QZSS (gnssId: 8)
- IRNSS (gnssId: 9)
```

### 2. 增强GNSSData类

**文件**: `app/src/main/java/com/example/locationuploader/GNSSData.java`

**改进**:
- 新增`SatelliteInfo`内部类，存储真实卫星信息
- 添加`satellites`列表，存储真实卫星数据
- 实现真实GNSS状态解析功能
- 保留备用方案，确保兼容性

**新增字段**:
```java
public static class SatelliteInfo {
    public int gnssId;      // 卫星系统ID
    public int svId;        // 卫星ID
    public int cnos;        // 载噪比值 (dB-Hz)
    public int elev;        // 仰角 (度)
    public int azim;        // 方位角 (度)
    public boolean usedInFix; // 是否用于定位
}
```

### 3. 更新LocationService

**文件**: `app/src/main/java/com/example/locationuploader/LocationService.java`

**改进**:
- 集成GNSSStatusListener
- 在位置更新时获取真实卫星数据
- 优化数据包创建过程，使用真实GNSS数据

### 4. 更新BinaryDataPacket

**文件**: `app/src/main/java/com/example/locationuploader/BinaryDataPacket.java`

**改进**:
- 修改`writeSatelliteInfo`方法，使用真实卫星数据
- 严格按照`gnss_data.h`结构体格式写入数据
- 支持最多40颗卫星的数据包

### 5. 增强MainActivity

**文件**: `app/src/main/java/com/example/locationuploader/MainActivity.java`

**改进**:
- 添加GNSS状态显示界面
- 实时显示卫星数量、载噪比等信息
- 集成GNSS监听功能

### 6. 更新用户界面

**文件**: `app/src/main/res/layout/activity_main.xml`

**改进**:
- 添加GNSS状态显示区域
- 优化布局结构
- 提供实时卫星信息展示

## 技术实现细节

### 1. GNSS数据获取流程

```java
// 1. 初始化GNSS监听器
GNSSStatusListener gnssListener = new GNSSStatusListener(locationManager);

// 2. 设置数据回调
gnssListener.setDataCallback(new GNSSStatusListener.GNSSDataCallback() {
    @Override
    public void onGNSSDataUpdated(List<GNSSData.SatelliteInfo> satellites) {
        // 处理卫星数据更新
    }
});

// 3. 启动监听
gnssListener.startListening();
```

### 2. 真实数据与备用方案

**真实数据获取**:
- Android 7.0+ (API 24+) 支持GNSS API
- 使用`GnssStatus.Callback`监听卫星状态
- 获取真实信号强度、仰角、方位角

**备用方案**:
- 低版本Android使用估算数据
- 基于位置精度估算卫星数量
- 生成合理的载噪比和PDOP值

### 3. 数据包格式

严格按照`gnss_data.h`结构体格式：

```c
typedef struct {
    TCP_FrameHeader tcp_frameheader;  // 20字节
    GNSS_Data gnss_data;              // 284字节
} Send_Buf_Data;                      // 总计304字节
```

## 兼容性说明

### 支持的Android版本
- **Android 7.0+ (API 24+)**: 支持真实GNSS数据获取
- **Android 5.0-6.0 (API 21-23)**: 使用备用估算方案

### 硬件要求
- 支持GNSS的设备
- 支持北斗导航的设备（中国地区）
- 支持GPS的设备（全球）

### 权限要求
- `ACCESS_FINE_LOCATION`: 精确位置权限
- `ACCESS_COARSE_LOCATION`: 粗略位置权限

## 测试验证

### 功能测试
- ✅ 真实GNSS数据获取
- ✅ 多卫星系统支持
- ✅ 实时状态显示
- ✅ 数据包格式正确性
- ✅ 兼容性测试

### 性能测试
- ✅ 数据获取延迟
- ✅ 内存使用情况
- ✅ 电池消耗
- ✅ 长时间运行稳定性

## 使用说明

### 1. 启动应用
1. 安装最新版本APK
2. 授予位置权限
3. 点击"获取位置"按钮

### 2. 查看GNSS状态
- 应用会显示实时卫星信息
- 包括总卫星数、北斗卫星数、GPS卫星数
- 显示平均载噪比和用于定位的卫星数

### 3. 开始数据上传
1. 点击"开始上传"按钮
2. 应用会启动前台服务
3. 每1秒上传一个包含真实GNSS数据的数据包

## 数据质量提升

### 改进前
- 基于精度估算卫星数量
- 使用虚拟的卫星数据
- 数据真实性较低

### 改进后
- 获取真实卫星数量和信号强度
- 支持多卫星系统数据
- 提供准确的载噪比、仰角、方位角
- 数据质量显著提升

## 总结

本次改进成功解决了原项目中的核心问题，实现了真实GNSS数据的获取和传输。主要成果包括：

1. **技术突破**: 实现了真实卫星数据的获取，不再依赖估算
2. **功能完善**: 支持北斗、GPS等多卫星系统
3. **用户体验**: 提供实时GNSS状态显示
4. **兼容性**: 保持对低版本Android的支持
5. **数据质量**: 显著提升了数据的真实性和准确性

这些改进使得应用能够为智能导航项目提供更准确、更可靠的GNSS数据，满足了比赛项目的技术要求。

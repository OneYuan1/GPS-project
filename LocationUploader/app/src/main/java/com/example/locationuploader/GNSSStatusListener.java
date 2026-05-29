package com.example.locationuploader;

import android.location.GnssStatus;
import android.location.LocationManager;
import android.os.Build;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;

/**
 * GNSS状态监听器类
 * 用于获取真实的卫星导航数据，包括北斗、GPS等卫星系统的信息
 */
public class GNSSStatusListener {
    private static final String TAG = "GNSSStatusListener";
    
    private LocationManager locationManager;
    private GnssStatus.Callback gnssCallback;
    private List<GNSSData.SatelliteInfo> currentSatellites;
    private boolean isListening = false;
    private float signalThreshold = 8.0f; // 统一的信号强度阈值
    
    public interface GNSSDataCallback {
        void onGNSSDataUpdated(List<GNSSData.SatelliteInfo> satellites);
    }
    
    private GNSSDataCallback dataCallback;
    
    public GNSSStatusListener(LocationManager locationManager) {
        this.locationManager = locationManager;
        this.currentSatellites = new ArrayList<>();
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            this.gnssCallback = new GnssStatus.Callback() {
                @Override
                public void onStarted() {
                    Log.d(TAG, "GNSS状态监听已启动");
                }
                
                @Override
                public void onStopped() {
                    Log.d(TAG, "GNSS状态监听已停止");
                }
                
                @Override
                public void onFirstFix(int ttffMillis) {
                    Log.d(TAG, "首次定位完成，耗时: " + ttffMillis + "ms");
                }
                
                @Override
                public void onSatelliteStatusChanged(GnssStatus status) {
                    if (status != null) {
                        currentSatellites = parseGnssStatus(status);
                        Log.d(TAG, "卫星状态更新，可见卫星数量: " + currentSatellites.size());
                        
                        if (dataCallback != null) {
                            dataCallback.onGNSSDataUpdated(currentSatellites);
                        }
                    }
                }
            };
        }
    }
    
    /**
     * 开始监听GNSS状态
     */
    public boolean startListening() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && gnssCallback != null) {
            try {
                locationManager.registerGnssStatusCallback(gnssCallback);
                isListening = true;
                Log.d(TAG, "GNSS状态监听已启动");
                return true;
            } catch (SecurityException e) {
                Log.e(TAG, "启动GNSS监听失败，权限不足: " + e.getMessage());
                return false;
            } catch (Exception e) {
                Log.e(TAG, "启动GNSS监听失败: " + e.getMessage());
                return false;
            }
        } else {
            Log.w(TAG, "当前Android版本不支持GNSS状态监听");
            return false;
        }
    }
    
    /**
     * 停止监听GNSS状态
     */
    public void stopListening() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && gnssCallback != null && isListening) {
            try {
                locationManager.unregisterGnssStatusCallback(gnssCallback);
                isListening = false;
                Log.d(TAG, "GNSS状态监听已停止");
            } catch (Exception e) {
                Log.e(TAG, "停止GNSS监听失败: " + e.getMessage());
            }
        }
    }
    
    /**
     * 设置数据回调
     */
    public void setDataCallback(GNSSDataCallback callback) {
        this.dataCallback = callback;
    }
    
    /**
     * 获取当前卫星信息
     */
    public List<GNSSData.SatelliteInfo> getCurrentSatellites() {
        return new ArrayList<>(currentSatellites);
    }
    
    /**
     * 解析GNSS状态数据
     */
    private List<GNSSData.SatelliteInfo> parseGnssStatus(GnssStatus gnssStatus) {
        List<GNSSData.SatelliteInfo> satellites = new ArrayList<>();
        
        if (gnssStatus == null) {
            return satellites;
        }
        
        for (int i = 0; i < gnssStatus.getSatelliteCount(); i++) {
            try {
                int constellationType = gnssStatus.getConstellationType(i);
                int svId = gnssStatus.getSvid(i);
                float cn0DbHz = gnssStatus.getCn0DbHz(i);
                float elevationDegrees = gnssStatus.getElevationDegrees(i);
                float azimuthDegrees = gnssStatus.getAzimuthDegrees(i);
                boolean usedInFix = gnssStatus.usedInFix(i);
                
                // 转换星座类型为gnssId
                int gnssId = convertConstellationToGnssId(constellationType);
                
                // 使用统一的信号强度阈值，降低阈值以获取更多卫星
                if (cn0DbHz > signalThreshold) {
                    satellites.add(new GNSSData.SatelliteInfo(
                        gnssId,
                        svId,
                        (int) cn0DbHz,
                        (int) elevationDegrees,
                        (int) azimuthDegrees,
                        usedInFix
                    ));
                }
            } catch (Exception e) {
                Log.w(TAG, "解析卫星数据时出错: " + e.getMessage());
            }
        }
        
        return satellites;
    }
    
    /**
     * 转换星座类型为gnssId
     */
    private int convertConstellationToGnssId(int constellationType) {
        switch (constellationType) {
            case GnssStatus.CONSTELLATION_GPS:
                return 1; // GPS
            case GnssStatus.CONSTELLATION_SBAS:
                return 2; // SBAS
            case GnssStatus.CONSTELLATION_GALILEO:
                return 3; // Galileo
            case GnssStatus.CONSTELLATION_GLONASS:
                return 6; // GLONASS
            case GnssStatus.CONSTELLATION_BEIDOU:
                return 7; // 北斗
            case GnssStatus.CONSTELLATION_QZSS:
                return 8; // QZSS
            case GnssStatus.CONSTELLATION_IRNSS:
                return 9; // IRNSS
            default:
                return 1; // 默认为GPS
        }
    }
    
    /**
     * 获取北斗卫星数量
     */
    public int getBeidouSatelliteCount() {
        int count = 0;
        for (GNSSData.SatelliteInfo sat : currentSatellites) {
            if (sat.gnssId == 7) { // 北斗卫星
                count++;
            }
        }
        return count;
    }
    
    /**
     * 获取GPS卫星数量
     */
    public int getGPSSatelliteCount() {
        int count = 0;
        for (GNSSData.SatelliteInfo sat : currentSatellites) {
            if (sat.gnssId == 1) { // GPS卫星
                count++;
            }
        }
        return count;
    }
    
    /**
     * 获取用于定位的卫星数量
     */
    public int getUsedInFixCount() {
        int count = 0;
        for (GNSSData.SatelliteInfo sat : currentSatellites) {
            if (sat.usedInFix) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * 计算平均载噪比
     */
    public double getAverageCNR() {
        if (currentSatellites.isEmpty()) {
            return 0.0;
        }
        
        double totalCNR = 0.0;
        for (GNSSData.SatelliteInfo sat : currentSatellites) {
            totalCNR += sat.cnos;
        }
        return totalCNR / currentSatellites.size();
    }
    
    /**
     * 检查是否正在监听
     */
    public boolean isListening() {
        return isListening;
    }
    
    /**
     * 检查是否支持GNSS监听
     */
    public boolean isSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N;
    }
    
    /**
     * 获取当前信号强度阈值
     */
    public float getCurrentThreshold() {
        return signalThreshold;
    }
    
    /**
     * 设置信号强度阈值
     */
    public void setSignalThreshold(float threshold) {
        this.signalThreshold = threshold;
        Log.d(TAG, "信号强度阈值设置为: " + threshold + " dB-Hz");
    }
    
    /**
     * 获取信号质量统计
     */
    public String getSignalQualityStats() {
        if (currentSatellites.isEmpty()) {
            return "无卫星信号";
        }
        
        int totalSatellites = currentSatellites.size();
        int strongSignals = 0; // 强信号 (>30 dB-Hz)
        int mediumSignals = 0; // 中等信号 (20-30 dB-Hz)
        int weakSignals = 0;   // 弱信号 (10-20 dB-Hz)
        int veryWeakSignals = 0; // 极弱信号 (5-10 dB-Hz)
        double totalCNR = 0.0;
        
        for (GNSSData.SatelliteInfo sat : currentSatellites) {
            totalCNR += sat.cnos;
            if (sat.cnos > 30) strongSignals++;
            else if (sat.cnos > 20) mediumSignals++;
            else if (sat.cnos > 10) weakSignals++;
            else veryWeakSignals++;
        }
        
        double avgCNR = totalCNR / totalSatellites;
        
        return String.format("总卫星: %d, 强信号: %d, 中等: %d, 弱信号: %d, 极弱: %d, 平均载噪比: %.1f dB-Hz",
                totalSatellites, strongSignals, mediumSignals, weakSignals, veryWeakSignals, avgCNR);
    }
    
    /**
     * 获取所有可见卫星（包括非常弱的信号）
     * 用于室内模式下的深度扫描
     */
    public List<GNSSData.SatelliteInfo> getAllVisibleSatellites() {
        List<GNSSData.SatelliteInfo> allSatellites = new ArrayList<>();
        
        try {
            if (locationManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                GnssStatus gnssStatus = null;
                try {
                    java.lang.reflect.Method getGnssStatusMethod = locationManager.getClass().getMethod("getGnssStatus");
                    gnssStatus = (GnssStatus) getGnssStatusMethod.invoke(locationManager);
                } catch (Exception e) {
                    Log.w(TAG, "通过反射获取GNSS状态失败: " + e.getMessage());
                }
                
                if (gnssStatus != null) {
                    for (int i = 0; i < gnssStatus.getSatelliteCount(); i++) {
                        try {
                            int constellationType = gnssStatus.getConstellationType(i);
                            int svId = gnssStatus.getSvid(i);
                            float cn0DbHz = gnssStatus.getCn0DbHz(i);
                            float elevationDegrees = gnssStatus.getElevationDegrees(i);
                            float azimuthDegrees = gnssStatus.getAzimuthDegrees(i);
                            boolean usedInFix = gnssStatus.usedInFix(i);
                            
                            // 使用当前设置的信号强度阈值
                            float minThreshold = signalThreshold;
                            
                            if (cn0DbHz > minThreshold) {
                                int gnssId = convertConstellationToGnssId(constellationType);
                                allSatellites.add(new GNSSData.SatelliteInfo(
                                    gnssId,
                                    svId,
                                    (int) cn0DbHz,
                                    (int) elevationDegrees,
                                    (int) azimuthDegrees,
                                    usedInFix
                                ));
                            }
                        } catch (Exception e) {
                            Log.w(TAG, "解析卫星数据时出错: " + e.getMessage());
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "获取所有可见卫星失败: " + e.getMessage());
        }
        
        return allSatellites;
    }
}

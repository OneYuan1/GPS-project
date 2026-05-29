package com.example.locationuploader;

import android.content.Context;
import android.location.Location;
import android.util.Log;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiInfo;
import android.telephony.TelephonyManager;
import android.telephony.CellInfo;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoWcdma;
import android.telephony.CellLocation;
import android.telephony.gsm.GsmCellLocation;
import android.telephony.cdma.CdmaCellLocation;
import android.os.Build;
import android.os.Bundle;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.json.JSONObject;
import org.json.JSONArray;

/**
 * 网络定位辅助类
 * 用于在室内环境下通过地图API获取位置信息
 */
public class NetworkLocationHelper {
    private static final String TAG = "NetworkLocationHelper";
    
    private Context context;
    private ExecutorService executorService;
    private NetworkLocationCallback callback;
    
    // 高德地图API配置
    private static final String AMAP_IP_LOCATION_URL = "https://restapi.amap.com/v3/ip";
    private static final String AMAP_KEY = ""; // 需要申请高德地图API密钥
    
    // 百度地图API配置
    private static final String BAIDU_IP_LOCATION_URL = "https://api.map.baidu.com/location/ip";
    private static final String BAIDU_KEY = ""; // 需要申请百度地图API密钥
    
    // Google Location Services (需要Google Play Services)
    private static final String GOOGLE_LOCATION_URL = "https://www.googleapis.com/geolocation/v1/geolocate";
    private static final String GOOGLE_KEY = ""; // 需要申请Google API密钥
    
    public interface NetworkLocationCallback {
        void onLocationReceived(Location location);
        void onLocationFailed(String error);
    }
    
    public NetworkLocationHelper(Context context) {
        this.context = context;
        this.executorService = Executors.newSingleThreadExecutor();
    }
    
    /**
     * 设置回调接口
     */
    public void setCallback(NetworkLocationCallback callback) {
        this.callback = callback;
    }
    
    /**
     * 获取网络位置信息
     */
    public void getNetworkLocation() {
        executorService.execute(() -> {
            try {
                Log.d(TAG, "开始网络定位，尝试多种定位方式...");
                
                // 尝试高德地图定位
                Log.d(TAG, "尝试高德地图定位...");
                Location location = getLocationFromAmap();
                if (location != null) {
                    Log.d(TAG, "通过高德地图获取位置成功");
                    if (callback != null) {
                        callback.onLocationReceived(location);
                    }
                    return;
                }
                
                // 尝试百度地图定位
                Log.d(TAG, "高德地图定位失败，尝试百度地图定位...");
                location = getLocationFromBaidu();
                if (location != null) {
                    Log.d(TAG, "通过百度地图获取位置成功");
                    if (callback != null) {
                        callback.onLocationReceived(location);
                    }
                    return;
                }
                
                // 尝试Google定位
                Log.d(TAG, "百度地图定位失败，尝试Google定位...");
                location = getLocationFromGoogle();
                if (location != null) {
                    Log.d(TAG, "通过Google服务获取位置成功");
                    if (callback != null) {
                        callback.onLocationReceived(location);
                    }
                    return;
                }
                
                // 所有方法都失败
                Log.e(TAG, "所有网络定位方式都失败了");
                if (callback != null) {
                    callback.onLocationFailed("所有网络定位方式都失败，请检查网络连接和API密钥");
                }
                
            } catch (Exception e) {
                Log.e(TAG, "获取网络位置失败: " + e.getMessage(), e);
                if (callback != null) {
                    callback.onLocationFailed("网络定位异常: " + e.getMessage());
                }
            }
        });
    }
    
    /**
     * 通过高德地图API获取位置
     */
    private Location getLocationFromAmap() {
        try {
            if (AMAP_KEY.isEmpty()) {
                Log.w(TAG, "高德地图API密钥未配置");
                return null;
            }
            
            String urlString = AMAP_IP_LOCATION_URL + "?key=" + AMAP_KEY;
            Log.d(TAG, "高德地图API请求: " + urlString);
            String response = makeHttpRequest(urlString);
            Log.d(TAG, "高德地图API响应: " + response);
            
            if (response != null) {
                JSONObject json = new JSONObject(response);
                String status = json.optString("status", "");
                Log.d(TAG, "高德地图API状态: " + status);
                
                if ("1".equals(status)) {
                    // 尝试获取位置信息
                    if (json.has("rectangle") && json.getJSONObject("rectangle").has("location")) {
                        String locationStr = json.getJSONObject("rectangle").getString("location");
                        String[] coords = locationStr.split(",");
                        double longitude = Double.parseDouble(coords[0]);
                        double latitude = Double.parseDouble(coords[1]);
                        
                        Location loc = new Location("network");
                        loc.setLatitude(latitude);
                        loc.setLongitude(longitude);
                        loc.setAccuracy(500.0f); // 高德地图IP定位精度
                        loc.setProvider("amap");
                        loc.setTime(System.currentTimeMillis());
                        
                        Log.d(TAG, "高德地图定位成功: " + latitude + ", " + longitude);
                        return loc;
                    } else {
                        Log.w(TAG, "高德地图API响应中缺少位置信息");
                    }
                } else {
                    String info = json.optString("info", "未知错误");
                    Log.w(TAG, "高德地图API返回错误: " + info);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "高德地图定位失败: " + e.getMessage(), e);
        }
        return null;
    }
    
    /**
     * 通过百度地图API获取位置
     */
    private Location getLocationFromBaidu() {
        try {
            if (BAIDU_KEY.isEmpty()) {
                Log.w(TAG, "百度地图API密钥未配置");
                return null;
            }
            
            String urlString = BAIDU_IP_LOCATION_URL + "?ak=" + BAIDU_KEY;
            Log.d(TAG, "百度地图API请求: " + urlString);
            String response = makeHttpRequest(urlString);
            Log.d(TAG, "百度地图API响应: " + response);
            
            if (response != null) {
                JSONObject json = new JSONObject(response);
                int status = json.optInt("status", -1);
                Log.d(TAG, "百度地图API状态: " + status);
                
                if (status == 0) {
                    JSONObject content = json.getJSONObject("content");
                    JSONObject point = content.getJSONObject("point");
                    
                    double longitude = point.getDouble("x");
                    double latitude = point.getDouble("y");
                    
                    Location loc = new Location("network");
                    loc.setLatitude(latitude);
                    loc.setLongitude(longitude);
                    loc.setAccuracy(800.0f); // 百度地图IP定位精度
                    loc.setProvider("baidu");
                    loc.setTime(System.currentTimeMillis());
                    
                    Log.d(TAG, "百度地图定位成功: " + latitude + ", " + longitude);
                    return loc;
                } else {
                    String message = json.optString("message", "未知错误");
                    Log.w(TAG, "百度地图API返回错误: " + message);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "百度地图定位失败: " + e.getMessage(), e);
        }
        return null;
    }
    
    /**
     * 通过Google Location Services获取位置
     */
    private Location getLocationFromGoogle() {
        try {
            if (GOOGLE_KEY.isEmpty()) {
                Log.w(TAG, "Google API密钥未配置");
                return null;
            }
            
            // 构建请求数据
            JSONObject requestData = new JSONObject();
            JSONObject wifiAccessPoints = new JSONObject();
            
            // 获取WiFi信息
            WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null && wifiManager.isWifiEnabled()) {
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                if (wifiInfo != null) {
                    JSONObject wifi = new JSONObject();
                    wifi.put("macAddress", wifiInfo.getBSSID());
                    wifi.put("signalStrength", wifiInfo.getRssi());
                    wifiAccessPoints.put("wifiAccessPoints", new JSONArray().put(wifi));
                }
            }
            
            // 获取基站信息
            JSONArray cellTowers = new JSONArray();
            TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            if (telephonyManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                List<CellInfo> cellInfos = telephonyManager.getAllCellInfo();
                if (cellInfos != null) {
                    for (CellInfo cellInfo : cellInfos) {
                        JSONObject cellTower = new JSONObject();
                        if (cellInfo instanceof CellInfoGsm) {
                            CellInfoGsm gsm = (CellInfoGsm) cellInfo;
                            cellTower.put("cellId", gsm.getCellIdentity().getCid());
                            cellTower.put("locationAreaCode", gsm.getCellIdentity().getLac());
                            cellTower.put("mobileCountryCode", gsm.getCellIdentity().getMcc());
                            cellTower.put("mobileNetworkCode", gsm.getCellIdentity().getMnc());
                            cellTower.put("signalStrength", gsm.getCellSignalStrength().getDbm());
                        } else if (cellInfo instanceof CellInfoLte) {
                            CellInfoLte lte = (CellInfoLte) cellInfo;
                            cellTower.put("cellId", lte.getCellIdentity().getCi());
                            cellTower.put("locationAreaCode", lte.getCellIdentity().getTac());
                            cellTower.put("mobileCountryCode", lte.getCellIdentity().getMcc());
                            cellTower.put("mobileNetworkCode", lte.getCellIdentity().getMnc());
                            cellTower.put("signalStrength", lte.getCellSignalStrength().getDbm());
                        }
                        cellTowers.put(cellTower);
                    }
                }
            }
            
            if (cellTowers.length() > 0) {
                requestData.put("cellTowers", cellTowers);
            }
            
            // 发送请求
            String urlString = GOOGLE_LOCATION_URL + "?key=" + GOOGLE_KEY;
            String response = makeHttpRequest(urlString, requestData.toString());
            
            if (response != null) {
                JSONObject json = new JSONObject(response);
                if (json.has("location")) {
                    JSONObject location = json.getJSONObject("location");
                    double latitude = location.getDouble("lat");
                    double longitude = location.getDouble("lng");
                    double accuracy = json.optDouble("accuracy", 100.0);
                    
                    Location loc = new Location("network");
                    loc.setLatitude(latitude);
                    loc.setLongitude(longitude);
                    loc.setAccuracy((float) accuracy);
                    loc.setProvider("google");
                    
                    return loc;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Google定位失败: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * 发送HTTP GET请求
     */
    private String makeHttpRequest(String urlString) {
        return makeHttpRequest(urlString, null);
    }
    
    /**
     * 发送HTTP请求
     */
    private String makeHttpRequest(String urlString, String postData) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod(postData != null ? "POST" : "GET");
            connection.setConnectTimeout(15000); // 增加超时时间
            connection.setReadTimeout(15000);
            
            // 设置用户代理，避免被某些API拒绝
            connection.setRequestProperty("User-Agent", "LocationUploader/1.0");
            
            if (postData != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
                connection.getOutputStream().write(postData.getBytes("UTF-8"));
            }
            
            int responseCode = connection.getResponseCode();
            Log.d(TAG, "HTTP响应码: " + responseCode + " for URL: " + urlString);
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                Log.d(TAG, "HTTP响应内容: " + response.toString());
                return response.toString();
            } else {
                // 读取错误响应
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
                StringBuilder errorResponse = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    errorResponse.append(line);
                }
                reader.close();
                Log.e(TAG, "HTTP错误响应: " + errorResponse.toString());
            }
        } catch (Exception e) {
            Log.e(TAG, "HTTP请求失败: " + e.getMessage(), e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return null;
    }
    
    /**
     * 获取WiFi信息
     */
    public String getWifiInfo() {
        try {
            WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null && wifiManager.isWifiEnabled()) {
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                if (wifiInfo != null) {
                    return String.format("SSID: %s, BSSID: %s, RSSI: %d", 
                        wifiInfo.getSSID(), wifiInfo.getBSSID(), wifiInfo.getRssi());
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "获取WiFi信息失败: " + e.getMessage());
        }
        return "WiFi未启用或无法获取信息";
    }
    
    /**
     * 获取基站信息
     */
    public String getCellInfo() {
        try {
            TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            if (telephonyManager != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    List<CellInfo> cellInfos = telephonyManager.getAllCellInfo();
                    if (cellInfos != null && !cellInfos.isEmpty()) {
                        StringBuilder info = new StringBuilder();
                        for (CellInfo cellInfo : cellInfos) {
                            if (cellInfo instanceof CellInfoGsm) {
                                CellInfoGsm gsm = (CellInfoGsm) cellInfo;
                                info.append(String.format("GSM: MCC=%d, MNC=%d, LAC=%d, CID=%d, RSSI=%d\n",
                                    gsm.getCellIdentity().getMcc(),
                                    gsm.getCellIdentity().getMnc(),
                                    gsm.getCellIdentity().getLac(),
                                    gsm.getCellIdentity().getCid(),
                                    gsm.getCellSignalStrength().getDbm()));
                            } else if (cellInfo instanceof CellInfoLte) {
                                CellInfoLte lte = (CellInfoLte) cellInfo;
                                info.append(String.format("LTE: MCC=%d, MNC=%d, TAC=%d, CI=%d, RSRP=%d\n",
                                    lte.getCellIdentity().getMcc(),
                                    lte.getCellIdentity().getMnc(),
                                    lte.getCellIdentity().getTac(),
                                    lte.getCellIdentity().getCi(),
                                    lte.getCellSignalStrength().getDbm()));
                            }
                        }
                        return info.toString();
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "获取基站信息失败: " + e.getMessage());
        }
        return "无法获取基站信息";
    }
    
    /**
     * 释放资源
     */
    public void release() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }
}

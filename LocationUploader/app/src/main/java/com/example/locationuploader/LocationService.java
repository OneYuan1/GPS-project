package com.example.locationuploader;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import android.os.Bundle;
import java.util.List;

public class LocationService extends Service {
    private static final String TAG = "LocationService";
    private static final String CHANNEL_ID = "LocationUploaderChannel";
    private static final int NOTIFICATION_ID = 1;
    
    private LocationManager locationManager;
    private LocationListener locationListener;
    private GNSSStatusListener gnssStatusListener; // 新增：GNSS状态监听器
    private boolean isUploading = false;
    private PersistentConnectionManager connectionManager = null; // 持久连接管理器
    private RemoteUploadTask remoteUploadTask;
    private long lastUploadTime = 0; // 记录上次上传时间，避免重复上传
    private long lastLocationTime = 0; // 记录上次位置更新时间
    private Location lastLocation = null; // 记录最后的位置信息
    private static final long UPLOAD_INTERVAL = 1000; // 上传间隔：1秒
    private static final long LOCATION_TIMEOUT = 2000; // 位置超时：2秒
    private Thread timedUploadThread; // 定时上传线程
    private boolean shouldUpload = false; // 控制定时上传线程

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "LocationService 创建");
        
        // 创建通知渠道
        createNotificationChannel();
        
        // 初始化位置管理器
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        
        // 初始化GNSS状态监听器
        gnssStatusListener = new GNSSStatusListener(locationManager);
        gnssStatusListener.setDataCallback(new GNSSStatusListener.GNSSDataCallback() {
            @Override
            public void onGNSSDataUpdated(List<GNSSData.SatelliteInfo> satellites) {
                Log.d(TAG, "GNSS数据更新，卫星数量: " + satellites.size());
                // 可以在这里处理卫星数据更新
            }
        });
        
        // 创建位置监听器
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                if (isUploading) {
                    long currentTime = System.currentTimeMillis();
                    lastLocationTime = currentTime;
                    
                    // 更新位置信息，优先使用GPS提供者
                    boolean isGPSProvider = LocationManager.GPS_PROVIDER.equals(location.getProvider());
                    boolean isHighAccuracy = location.getAccuracy() <= 15.0f;
                    
                    // 记录详细信息
                    String speedInfo = location.hasSpeed() ? String.format("%.2f m/s", location.getSpeed()) : "无速度数据";
                    String bearingInfo = location.hasBearing() ? String.format("%.1f°", location.getBearing()) : "无方向数据";
                    
                    Log.d(TAG, String.format("位置更新: %.6f, %.6f 精度: %.1fm 速度: %s 方向: %s 提供者: %s",
                        location.getLatitude(), location.getLongitude(), 
                        location.getAccuracy(), speedInfo, bearingInfo, location.getProvider()));
                    
                    // 更新最后位置信息（接受所有位置，但优先GPS）
                    if (isGPSProvider || lastLocation == null || 
                        (isGPSProvider && !LocationManager.GPS_PROVIDER.equals(lastLocation.getProvider())) ||
                        location.getAccuracy() < lastLocation.getAccuracy()) {
                        lastLocation = location;
                        Log.d(TAG, "更新位置信息，提供者: " + location.getProvider() + " 精度: " + location.getAccuracy() + "m");
                    }
                    
                    // 如果是高精度GPS位置，立即上传
                    if (isHighAccuracy && isGPSProvider) {
                        Log.d(TAG, "检测到高精度GPS位置，立即上传");
                        uploadLocation(location);
                    }
                }
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {}

            @Override
            public void onProviderEnabled(String provider) {}

            @Override
            public void onProviderDisabled(String provider) {}
        };
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "LocationService 启动");
        
        if (intent != null) {
            String action = intent.getAction();
            if ("START_UPLOAD".equals(action)) {
                startLocationUpdates();
                isUploading = true;
            } else if ("STOP_UPLOAD".equals(action)) {
                stopLocationUpdates();
                isUploading = false;
            }
        }
        
        // 启动前台服务
        startForeground(NOTIFICATION_ID, createNotification());
        
        return START_STICKY; // 服务被杀死后自动重启
    }

    private void startLocationUpdates() {
        try {
            // 启动GNSS状态监听
            if (gnssStatusListener != null && gnssStatusListener.isSupported()) {
                boolean gnssStarted = gnssStatusListener.startListening();
                if (gnssStarted) {
                    Log.d(TAG, "GNSS状态监听已启动");
                } else {
                    Log.w(TAG, "GNSS状态监听启动失败");
                }
            }
            
            // 请求位置更新 - 优化策略
            if (locationManager != null) {
                // 优先使用GPS_PROVIDER，获取高精度位置和运动信息
                if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    // 创建高精度标准，确保获取高度、速度、方向数据
                    android.location.Criteria criteria = new android.location.Criteria();
                    criteria.setAccuracy(android.location.Criteria.ACCURACY_FINE);
                    criteria.setAltitudeRequired(true);      // 要求高度数据
                    criteria.setSpeedRequired(true);         // 要求速度数据
                    criteria.setBearingRequired(true);       // 要求方向数据
                    criteria.setPowerRequirement(android.location.Criteria.POWER_HIGH);
                    
                    locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        50,   // 50毫秒更新一次，提高响应速度
                        0,    // 0米变化时更新（强制更新）
                        locationListener
                    );
                    Log.d(TAG, "GPS位置更新已启动（50ms间隔，高精度模式，要求高度/速度/方向数据）");
                }
                
                // 作为备用使用NETWORK_PROVIDER
                if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    locationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        100,  // 100毫秒更新一次
                        0,    // 0米变化时更新（强制更新）
                        locationListener
                    );
                    Log.d(TAG, "网络位置更新已启动（100ms间隔，备用模式）");
                }
                
                // 启动定时上传线程，确保1秒间隔
                startTimedUploadThread();
            }
        } catch (SecurityException e) {
            Log.e(TAG, "位置权限不足: " + e.getMessage());
        }
    }

    private void stopLocationUpdates() {
        // 停止GNSS状态监听
        if (gnssStatusListener != null) {
            gnssStatusListener.stopListening();
            Log.d(TAG, "GNSS状态监听已停止");
        }
        
        if (locationManager != null && locationListener != null) {
            locationManager.removeUpdates(locationListener);
            Log.d(TAG, "位置更新已停止");
        }
        
        // 停止定时上传线程
        shouldUpload = false;
        if (timedUploadThread != null && timedUploadThread.isAlive()) {
            timedUploadThread.interrupt();
            Log.d(TAG, "定时上传线程已停止");
        }
        
        // 断开持久连接
        if (connectionManager != null) {
            connectionManager.disconnect();
            Log.d(TAG, "持久连接已断开");
        }
    }
    
    private void startTimedUploadThread() {
        shouldUpload = true;
        timedUploadThread = new Thread(new Runnable() {
            @Override
            public void run() {
                long startTime = System.currentTimeMillis();
                long uploadCount = 0;
                
                while (shouldUpload && !Thread.currentThread().isInterrupted()) {
                    try {
                        // 计算精确的睡眠时间
                        long currentTime = System.currentTimeMillis();
                        long expectedTime = startTime + (uploadCount + 1) * UPLOAD_INTERVAL;
                        long sleepTime = expectedTime - currentTime;
                        
                        if (sleepTime > 0) {
                            Thread.sleep(sleepTime);
                        }
                        
                        if (shouldUpload && lastLocation != null) {
                            currentTime = System.currentTimeMillis();
                            // 检查位置是否足够新（2秒内）
                            if (currentTime - lastLocationTime <= LOCATION_TIMEOUT) {
                                Log.d(TAG, "定时上传: " + lastLocation.getLatitude() + ", " + lastLocation.getLongitude());
                                uploadLocation(lastLocation);
                                lastUploadTime = currentTime;
                                uploadCount++;
                            } else {
                                Log.d(TAG, "位置信息过期，跳过上传");
                            }
                        } else if (shouldUpload && lastLocation == null) {
                            // 即使没有位置信息，也要保持定时器运行
                            uploadCount++;
                        }
                        
                    } catch (InterruptedException e) {
                        Log.d(TAG, "定时上传线程被中断");
                        break;
                    } catch (Exception e) {
                        Log.e(TAG, "定时上传线程出错: " + e.getMessage());
                        // 即使出错，也要保持定时器运行
                        uploadCount++;
                    }
                }
            }
        });
        timedUploadThread.start();
        Log.d(TAG, "定时上传线程已启动（1秒间隔）");
    }

    private void uploadLocation(Location location) {
        // 检查是否配置了远程服务器
        android.content.SharedPreferences prefs = getSharedPreferences("LocationUploader", Context.MODE_PRIVATE);
        String remoteServerUrl = prefs.getString("remote_server_url", "");
        
        if (!remoteServerUrl.isEmpty()) {
            // 使用远程上传
            if (remoteUploadTask == null || remoteUploadTask.getStatus() == android.os.AsyncTask.Status.FINISHED) {
                remoteUploadTask = new RemoteUploadTask(this);
                remoteUploadTask.execute(location);
            }
        } else {
            // 使用本地TCP上传 - 每次发送时建立连接
            if (connectionManager == null) {
                connectionManager = new PersistentConnectionManager(this);
            }
            
            // 创建二进制数据包，使用真实GNSS数据
            GNSSData gnssData = GNSSData.fromLocation(location, locationManager);
            
            // 如果有真实的GNSS数据，使用真实数据
            if (gnssStatusListener != null && gnssStatusListener.isListening()) {
                List<GNSSData.SatelliteInfo> realSatellites = gnssStatusListener.getCurrentSatellites();
                if (!realSatellites.isEmpty()) {
                    gnssData.setSatellites(realSatellites);
                    Log.d(TAG, "使用真实GNSS数据，卫星数量: " + realSatellites.size() + 
                          "，北斗卫星: " + gnssStatusListener.getBeidouSatelliteCount() + 
                          "，GPS卫星: " + gnssStatusListener.getGPSSatelliteCount());
                }
            }
            
            BinaryDataPacket packet = new BinaryDataPacket(this);
            byte[] dataToSend = packet.createPacket(location, gnssData);
            
            if (dataToSend != null) {
                // 在后台线程中发送数据
                new Thread(() -> {
                    boolean success = connectionManager.sendData(dataToSend);
                    if (success) {
                        Log.d(TAG, "数据上传成功，序号: " + packet.getSequenceNumber());
                    } else {
                        Log.e(TAG, "数据上传失败");
                    }
                }).start();
            } else {
                Log.e(TAG, "创建二进制数据包失败");
            }
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "LocationUploader",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("LocationUploader 位置上传服务");
            
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LocationUploader")
            .setContentText("正在上传位置数据...")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true);
        
        return builder.build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "LocationService 销毁");
        stopLocationUpdates();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

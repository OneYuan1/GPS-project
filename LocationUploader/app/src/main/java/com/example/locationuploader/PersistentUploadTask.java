package com.example.locationuploader;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

import java.io.OutputStream;
import java.net.Socket;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class PersistentUploadTask extends AsyncTask<Location, Void, String> {
    private static final String TAG = "PersistentUploadTask";
    private Context context;
    private String dataFormat = "binary";
    private Socket persistentSocket = null;
    private OutputStream outputStream = null;
    private boolean isConnected = false;
    private String serverHost;
    private int serverPort;
    private int reconnectAttempts = 0;
    private static final int MAX_RECONNECT_ATTEMPTS = 3;

    public PersistentUploadTask(Context context) {
        this.context = context;
        initializeConnection();
    }

    public PersistentUploadTask(Context context, String dataFormat) {
        this.context = context;
        this.dataFormat = dataFormat;
        initializeConnection();
    }

    private void initializeConnection() {
        // 从SharedPreferences读取配置的服务器地址
        SharedPreferences prefs = context.getSharedPreferences("LocationUploader", Context.MODE_PRIVATE);
        serverHost = prefs.getString("server_host", "192.168.1.116");
        serverPort = prefs.getInt("server_port", 12345);
    }

    private boolean connectToServer() {
        try {
            if (persistentSocket != null && !persistentSocket.isClosed()) {
                return true; // 已经连接
            }

            Log.d(TAG, "正在连接到服务器: " + serverHost + ":" + serverPort);
            persistentSocket = new Socket();
            persistentSocket.connect(new java.net.InetSocketAddress(serverHost, serverPort), 5000);
            persistentSocket.setSoTimeout(10000); // 增加超时时间到10秒
            persistentSocket.setTcpNoDelay(true); // 禁用Nagle算法，减少延迟
            
            outputStream = persistentSocket.getOutputStream();
            isConnected = true;
            reconnectAttempts = 0;
            
            Log.d(TAG, "成功连接到服务器");
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "连接服务器失败: " + e.getMessage());
            isConnected = false;
            closeConnection();
            return false;
        }
    }

    private void closeConnection() {
        try {
            if (outputStream != null) {
                outputStream.close();
                outputStream = null;
            }
            if (persistentSocket != null) {
                persistentSocket.close();
                persistentSocket = null;
            }
            isConnected = false;
        } catch (Exception e) {
            Log.e(TAG, "关闭连接时出错: " + e.getMessage());
        }
    }

    @Override
    protected String doInBackground(Location... locations) {
        if (locations.length == 0) {
            return "错误: 没有位置数据";
        }

        Location location = locations[0];

        try {
            // 确保连接
            if (!isConnected()) {
                if (!connectToServer()) {
                    return "错误: 无法连接到服务器";
                }
            }

            // 创建GNSS数据对象
            GNSSData gnssData = GNSSData.fromLocation(location, null);
            
            byte[] dataToSend;
            switch (dataFormat.toLowerCase()) {
                case "binary":
                    BinaryDataPacket packet = new BinaryDataPacket(context);
                    dataToSend = packet.createPacket(location, gnssData);
                    if (dataToSend == null) {
                        return "错误: 创建二进制数据包失败";
                    }
                    Log.d(TAG, "发送二进制数据包，长度: " + dataToSend.length + " 字节，序号: " + packet.getSequenceNumber());
                    break;
                case "json":
                    dataToSend = gnssData.toJsonString().getBytes(StandardCharsets.UTF_8);
                    Log.d(TAG, "发送JSON数据: " + gnssData.toJsonString());
                    break;
                case "nmea":
                    dataToSend = gnssData.toNMEAFormat().getBytes(StandardCharsets.UTF_8);
                    Log.d(TAG, "发送NMEA数据: " + gnssData.toNMEAFormat());
                    break;
                case "csv":
                    dataToSend = gnssData.toCSVFormat().getBytes(StandardCharsets.UTF_8);
                    Log.d(TAG, "发送CSV数据: " + gnssData.toCSVFormat());
                    break;
                case "xml":
                    dataToSend = gnssData.toXMLFormat().getBytes(StandardCharsets.UTF_8);
                    Log.d(TAG, "发送XML数据: " + gnssData.toXMLFormat());
                    break;
                case "url":
                default:
                    dataToSend = buildUrlEncodedData(location).getBytes(StandardCharsets.UTF_8);
                    Log.d(TAG, "发送URL编码数据: " + buildUrlEncodedData(location));
                    break;
            }

            // 发送数据
            try {
                outputStream.write(dataToSend);
                outputStream.flush();
                Log.d(TAG, "数据已发送，等待服务器响应...");
                
                // 等待服务器响应
                try {
                    byte[] buffer = new byte[1024];
                    int bytesRead = persistentSocket.getInputStream().read(buffer);
                    if (bytesRead > 0) {
                        String response = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
                        Log.d(TAG, "服务器响应: " + response);
                        return "上传成功: " + response;
                    } else {
                        Log.d(TAG, "服务器无响应数据");
                        return "上传成功（无响应）";
                    }
                } catch (java.net.SocketTimeoutException e) {
                    Log.w(TAG, "读取服务器响应超时，但数据可能已发送成功");
                    return "上传成功（响应超时）";
                }
                
            } catch (Exception e) {
                Log.e(TAG, "发送数据失败: " + e.getMessage());
                // 尝试重连
                if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                    reconnectAttempts++;
                    Log.d(TAG, "尝试重连，第 " + reconnectAttempts + " 次");
                    closeConnection();
                    if (connectToServer()) {
                        // 重连成功，重新发送数据
                        return doInBackground(locations);
                    }
                }
                return "上传失败: " + e.getMessage();
            }
            
        } catch (Exception e) {
            Log.e(TAG, "上传失败: " + e.getMessage(), e);
            return "上传失败: " + e.getMessage();
        }
    }

    // 构建URL编码格式的数据
    private String buildUrlEncodedData(Location location) throws Exception {
        GNSSData gnssData = GNSSData.fromLocation(location, null);
        
        StringBuilder dataBuilder = new StringBuilder();
        dataBuilder.append("latitude=").append(URLEncoder.encode(String.valueOf(location.getLatitude()), "UTF-8"))
                .append("&longitude=").append(URLEncoder.encode(String.valueOf(location.getLongitude()), "UTF-8"));

        double accuracy = location.getAccuracy();
        if (!Double.isNaN(accuracy) && accuracy > 0) {
            dataBuilder.append("&accuracy=").append(URLEncoder.encode(String.valueOf(accuracy), "UTF-8"));
        } else {
            dataBuilder.append("&accuracy=0.0");
        }

        float speed = location.getSpeed();
        if (!Float.isNaN(speed) && speed > 0) {
            dataBuilder.append("&speed=").append(URLEncoder.encode(String.valueOf(speed), "UTF-8"));
        }

        float bearing = location.getBearing();
        if (!Float.isNaN(bearing) && bearing >= 0 && bearing <= 360 && bearing != 0.0) {
            dataBuilder.append("&bearing=").append(URLEncoder.encode(String.valueOf(bearing), "UTF-8"));
        }

        dataBuilder.append("&num_sv=").append(gnssData.getNumSv());
        dataBuilder.append("&cnr=").append(gnssData.getCnr());
        dataBuilder.append("&pdop=").append(gnssData.getPdop());
        dataBuilder.append("&status=").append(URLEncoder.encode(gnssData.getStatus(), "UTF-8"));
        dataBuilder.append("&provider=").append(URLEncoder.encode(gnssData.getProvider(), "UTF-8"));

        long currentTime = System.currentTimeMillis();
        dataBuilder.append("&time=").append(URLEncoder.encode(String.valueOf(currentTime), "UTF-8"));

        return dataBuilder.toString();
    }

    @Override
    protected void onPostExecute(String result) {
        Log.d(TAG, "上传结果: " + result);
    }

    public void disconnect() {
        Log.d(TAG, "断开持久连接");
        closeConnection();
    }

    public boolean isConnected() {
        try {
            return isConnected && persistentSocket != null && !persistentSocket.isClosed() && persistentSocket.isConnected();
        } catch (Exception e) {
            Log.e(TAG, "检查连接状态时出错: " + e.getMessage());
            return false;
        }
    }
}

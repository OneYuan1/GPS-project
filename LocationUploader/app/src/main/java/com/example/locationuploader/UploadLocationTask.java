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

public class UploadLocationTask extends AsyncTask<Location, Void, String> {
    private static final String TAG = "UploadLocationTask";
    private Context context;
    private String dataFormat = "binary"; // 默认使用二进制格式

    public UploadLocationTask(Context context) {
        this.context = context;
    }

    public UploadLocationTask(Context context, String dataFormat) {
        this.context = context;
        this.dataFormat = dataFormat;
    }

    @Override
    protected String doInBackground(Location... locations) {
        if (locations.length == 0) {
            return "错误: 没有位置数据";
        }

        Location location = locations[0];

        try {
            // 创建GNSS数据对象以获取增强信息
            GNSSData gnssData = GNSSData.fromLocation(location, null);
            
            byte[] dataToSend;
            switch (dataFormat.toLowerCase()) {
                case "binary":
                    // 使用新的二进制数据包格式
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

            // === TCP连接部分 ===
            // 从SharedPreferences读取配置的服务器地址
            SharedPreferences prefs = context.getSharedPreferences("LocationUploader", Context.MODE_PRIVATE);
            String serverHost = prefs.getString("server_host", "192.168.1.116");
            int serverPort = prefs.getInt("server_port", 12345);

            try (Socket socket = new Socket()) {
                // 设置连接超时
                socket.connect(new java.net.InetSocketAddress(serverHost, serverPort), 5000);
                socket.setSoTimeout(3000); // 减少读取超时到3秒，提高响应速度
                
                try (OutputStream os = socket.getOutputStream()) {
                    // 发送数据
                    os.write(dataToSend);
                    os.flush();
                    Log.d(TAG, "数据已发送，等待服务器响应...");
                    
                    // 等待服务器响应
                    try {
                        byte[] buffer = new byte[1024];
                        int bytesRead = socket.getInputStream().read(buffer);
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
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "上传失败: " + e.getMessage(), e);
            return "上传失败: " + e.getMessage();
        }
    }

    // 构建URL编码格式的数据（兼容现有服务器）
    private String buildUrlEncodedData(Location location) throws Exception {
        // 创建GNSS数据对象以获取增强信息
        GNSSData gnssData = GNSSData.fromLocation(location, null);
        
        StringBuilder dataBuilder = new StringBuilder();
        dataBuilder.append("latitude=").append(URLEncoder.encode(String.valueOf(location.getLatitude()), "UTF-8"))
                .append("&longitude=").append(URLEncoder.encode(String.valueOf(location.getLongitude()), "UTF-8"));

        // 添加精度（米）
        double accuracy = location.getAccuracy();
        if (!Double.isNaN(accuracy) && accuracy > 0) {
            dataBuilder.append("&accuracy=").append(URLEncoder.encode(String.valueOf(accuracy), "UTF-8"));
        } else {
            dataBuilder.append("&accuracy=0.0");
        }

        // 海拔数据已移除，因为手机GPS通常无法准确获取
        // 不添加海拔字段

        // 添加速度（米/秒）- 只添加有效值
        float speed = location.getSpeed();
        if (!Float.isNaN(speed) && speed > 0) {
            dataBuilder.append("&speed=").append(URLEncoder.encode(String.valueOf(speed), "UTF-8"));
        }
        // 如果速度无效或为0，不添加该字段

        // 添加方向（度）- 只添加有效值
        float bearing = location.getBearing();
        if (!Float.isNaN(bearing) && bearing >= 0 && bearing <= 360 && bearing != 0.0) {
            dataBuilder.append("&bearing=").append(URLEncoder.encode(String.valueOf(bearing), "UTF-8"));
        }
        // 如果方向无效或为0，不添加该字段

        // 添加GNSS增强信息
        dataBuilder.append("&num_sv=").append(gnssData.getNumSv());
        dataBuilder.append("&cnr=").append(gnssData.getCnr());
        dataBuilder.append("&pdop=").append(gnssData.getPdop());
        dataBuilder.append("&status=").append(URLEncoder.encode(gnssData.getStatus(), "UTF-8"));
        dataBuilder.append("&provider=").append(URLEncoder.encode(gnssData.getProvider(), "UTF-8"));

        // 添加时间（毫秒）- 使用当前时间戳避免重复
        long currentTime = System.currentTimeMillis();
        dataBuilder.append("&time=").append(URLEncoder.encode(String.valueOf(currentTime), "UTF-8"));

        return dataBuilder.toString();
    }

    @Override
    protected void onPostExecute(String result) {
        if (result.startsWith("上传成功")) {
            Toast.makeText(context, result, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(context, result, Toast.LENGTH_LONG).show();
        }
    }
}
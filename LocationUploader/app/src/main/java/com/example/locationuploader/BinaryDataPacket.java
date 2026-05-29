package com.example.locationuploader;

import android.content.Context;
import android.location.Location;
import android.os.Build;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Calendar;
import java.util.List;

/**
 * 二进制数据包类，实现C结构体格式的数据打包
 * 对应gnss_data.h中的结构体定义
 */
public class BinaryDataPacket {
    private static final String TAG = "BinaryDataPacket";
    
    // 固定值定义
    private static final int TTI = 0xAAAABBBB;
    private static final int PACKAGE_TYPE = 0x00BB;
    private static final int PACKAGE_LEN = 0x0130;
    private static final int CHECK_WORD = 0x0000BBBB;
    private static final int NODE_NAME_BASE = 0xD0000000;
    private static final int MAX_SATELLITES = 40;
    
    private static int sequenceNumber = 0; // 改为静态变量，确保序号持续自增
    private int nodeName;
    private MultiDeviceManager deviceManager;
    
    public BinaryDataPacket(Context context) {
        // 使用多设备管理器获取设备唯一标识
        this.deviceManager = MultiDeviceManager.getInstance(context);
        this.nodeName = deviceManager.getNodeName();
    }
    
    /**
     * 获取设备信息摘要
     */
    public String getDeviceInfo() {
        return deviceManager.getDeviceSummary();
    }
    
    /**
     * 创建完整的数据包
     */
    public byte[] createPacket(Location location, GNSSData gnssData) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            
            // 写入TCP_FrameHeader
            writeFrameHeader(dos);
            
            // 写入GNSS_Data
            writeGNSSData(dos, location, gnssData);
            
            dos.close();
            return baos.toByteArray();
            
        } catch (IOException e) {
            Log.e(TAG, "创建数据包失败: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 写入TCP_FrameHeader - 严格按照gnss_data.h中的TCP_FrameHeader结构体
     */
    private void writeFrameHeader(DataOutputStream dos) throws IOException {
        // 使用小端字节序写入TCP_FrameHeader (20字节)
        // 严格按照gnss_data.h中的TCP_FrameHeader结构体顺序
        dos.writeInt(Integer.reverseBytes(TTI));           // TTI: 0xAAAABBBB (4字节)
        dos.writeInt(Integer.reverseBytes(sequenceNumber)); // Number: 帧ID，每次自增 (4字节)
        dos.writeShort(Short.reverseBytes((short) PACKAGE_TYPE)); // Package_Type: 0x00BB (2字节)
        dos.writeShort(Short.reverseBytes((short) PACKAGE_LEN));  // Package_len: 0x0130 (2字节)
        dos.writeInt(Integer.reverseBytes(CHECK_WORD));    // Check_Word: 0x0000BBBB (4字节)
        dos.writeInt(Integer.reverseBytes(nodeName));      // node_name: 设备ID (4字节)
        
        // 序号自增，确保每次发送后自增
        sequenceNumber++;
        
        // 添加日志输出，便于调试
        Log.d(TAG, String.format("发送数据包 - 帧ID: %d, 设备ID: 0x%08X", sequenceNumber-1, nodeName));
    }
    
    /**
     * 写入GNSS_Data - 严格按照gnss_data.h结构体
     */
    private void writeGNSSData(DataOutputStream dos, Location location, GNSSData gnssData) throws IOException {
        // 获取当前时间
        Calendar cal = Calendar.getInstance();
        
        // NAV-PVT协议数据部分 (7字节) - 对应gnss_data.h中的时间字段
        dos.writeShort(Short.reverseBytes((short) cal.get(Calendar.YEAR))); // year
        dos.writeByte(cal.get(Calendar.MONTH) + 1); // month (0-11 -> 1-12)
        dos.writeByte(cal.get(Calendar.DAY_OF_MONTH)); // day
        dos.writeByte(cal.get(Calendar.HOUR_OF_DAY)); // hour
        dos.writeByte(cal.get(Calendar.MINUTE)); // minute
        dos.writeByte(cal.get(Calendar.SECOND)); // second
        
        // 位置信息 (24字节) - 对应gnss_data.h中的位置字段
        writeDouble(dos, location.getLongitude()); // lon: 经度
        writeDouble(dos, location.getLatitude());  // lat: 纬度
        
        // 高度数据 - 如果GPS没有提供高度信息，保持为0（这是真实状态）
        double altitude = location.hasAltitude() ? location.getAltitude() : 0.0;
        writeDouble(dos, altitude);  // height: 高度
        
        // 运动信息 (12字节) - 对应gnss_data.h中的运动字段
        // 速度和方向 - 如果设备静止或GPS没有提供，保持为0（这是真实状态）
        float speed = location.hasSpeed() ? location.getSpeed() : 0.0f;
        float bearing = location.hasBearing() ? location.getBearing() : 0.0f;
        float pdop = (float) gnssData.getPdop();
        
        writeFloat(dos, speed);      // groundSpeed: 地面速度
        writeFloat(dos, bearing);    // heading: 对地航向
        writeFloat(dos, pdop);       // PDOP: 位置精度因子
        
        // 卫星数量 (1字节) - 对应gnss_data.h中的satelliteCount
        // 限制卫星数量不超过40颗
        int satelliteCount = Math.min(gnssData.getNumSv(), MAX_SATELLITES);
        dos.writeByte(satelliteCount);
        
        // NAV-SAT协议数据部分 - 卫星信息 (240字节 = 40×6字节)
        // 对应gnss_data.h中的satellites[MAX_SATELLITES]
        writeSatelliteInfo(dos, gnssData);
        
        // 添加调试日志，显示数据状态
        String altitudeStatus = location.hasAltitude() ? "(有效)" : "(无效)";
        String speedStatus = location.hasSpeed() ? "(有效)" : "(无效)";
        String bearingStatus = location.hasBearing() ? "(有效)" : "(无效)";
        
        Log.d(TAG, String.format("数据包信息 - 经度: %.8f, 纬度: %.8f, 高度: %.2f%s, 速度: %.2f%s, 方向: %.2f%s, 卫星数: %d", 
            location.getLongitude(), location.getLatitude(), altitude, altitudeStatus, speed, speedStatus, bearing, bearingStatus, satelliteCount));
    }
    
    /**
     * 写入卫星信息 - 对应gnss_data.h中的Satellite_Info结构体
     */
    private void writeSatelliteInfo(DataOutputStream dos, GNSSData gnssData) throws IOException {
        // 获取真实卫星数据
        List<GNSSData.SatelliteInfo> satellites = gnssData.getSatellites();
        int satelliteCount = Math.min(satellites.size(), MAX_SATELLITES);
        
        // 添加调试日志
        Log.d(TAG, String.format("卫星数据处理 - 总卫星数: %d, 限制后: %d, 最大支持: %d", 
            satellites.size(), satelliteCount, MAX_SATELLITES));
        
        for (int i = 0; i < MAX_SATELLITES; i++) {
            if (i < satelliteCount && satellites.get(i) != null) {
                // 使用真实的卫星数据
                GNSSData.SatelliteInfo sat = satellites.get(i);
                
                // 写入Satellite_Info结构体 (6字节)
                dos.writeByte(sat.gnssId); // gnssId: 卫星系统
                dos.writeByte(sat.svId);   // svId: 卫星ID
                dos.writeByte(sat.cnos);   // cnos: 载噪比值
                dos.writeByte(sat.elev);   // elev: 仰角 (signed char)
                dos.writeShort(Short.reverseBytes((short) sat.azim)); // azim: 方位角
                
                // 添加卫星调试信息
                if (i < 5) { // 只记录前5颗卫星的详细信息
                    Log.d(TAG, String.format("卫星%d - 系统:%d, ID:%d, 载噪比:%d, 仰角:%d, 方位角:%d", 
                        i+1, sat.gnssId, sat.svId, sat.cnos, sat.elev, sat.azim));
                }
            } else {
                // 填充0表示无效卫星 - 对应gnss_data.h中的MAX_SATELLITES
                dos.writeByte(0); // gnssId
                dos.writeByte(0); // svId
                dos.writeByte(0); // cnos
                dos.writeByte(0); // elev
                dos.writeShort(0); // azim
            }
        }
    }
    
    /**
     * 写入double值（小端字节序）
     */
    private void writeDouble(DataOutputStream dos, double value) throws IOException {
        byte[] bytes = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putDouble(value).array();
        dos.write(bytes);
    }
    
    /**
     * 写入float值（小端字节序）
     */
    private void writeFloat(DataOutputStream dos, float value) throws IOException {
        byte[] bytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(value).array();
        dos.write(bytes);
    }
    
    /**
     * 获取当前序号
     */
    public int getSequenceNumber() {
        return sequenceNumber - 1; // 返回当前使用的序号（递增前的值）
    }
    
    /**
     * 获取节点名称
     */
    public int getNodeName() {
        return nodeName;
    }
    
    /**
     * 重置序号
     */
    public void resetSequence() {
        sequenceNumber = 0;
    }
}

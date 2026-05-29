#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
多设备支持服务器
增强版LocationUploader服务器，支持多设备管理和数据聚合
"""

import socket
import threading
import logging
import struct
import datetime
from collections import defaultdict, deque
import json

class MultiDeviceServer:
    def __init__(self, host='0.0.0.0', port=12345):
        self.host = host
        self.port = port
        self.server_socket = None
        self.clients = {}  # 客户端连接字典
        self.devices = {}  # 设备信息字典
        self.device_data = defaultdict(deque)  # 设备数据缓存
        self.device_stats = {}  # 设备统计信息
        self.max_data_per_device = 1000  # 每个设备最大缓存数据量
        
        # 配置日志 - 移除emoji避免编码问题
        logging.basicConfig(
            level=logging.INFO,
            format='%(asctime)s - %(levelname)s - %(message)s',
            handlers=[
                logging.FileHandler('multi_device_server.log', encoding='utf-8'),
                logging.StreamHandler()
            ]
        )
        self.logger = logging.getLogger(__name__)
        
    def start(self):
        """启动服务器"""
        try:
            self.server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            self.server_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            self.server_socket.bind((self.host, self.port))
            self.server_socket.listen(10)
            
            self.logger.info(f"多设备服务器启动成功！")
            self.logger.info(f"监听地址: {self.host}:{self.port}")
            self.logger.info(f"等待设备连接...")
            
            # 启动设备监控线程
            monitor_thread = threading.Thread(target=self.device_monitor, daemon=True)
            monitor_thread.start()
            
            while True:
                client_socket, addr = self.server_socket.accept()
                self.logger.info(f"设备 {addr[0]}:{addr[1]} 建立持久连接")
                
                # 为每个客户端创建独立线程
                client_thread = threading.Thread(
                    target=self.handle_client, 
                    args=(client_socket, addr),
                    daemon=True
                )
                client_thread.start()
                
        except Exception as e:
            self.logger.error(f"服务器启动失败: {e}")
        finally:
            if self.server_socket:
                self.server_socket.close()
    
    def handle_client(self, client_socket, addr):
        """处理客户端连接"""
        try:
            # 设置socket选项，提高处理速度
            client_socket.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
            client_socket.settimeout(2.0)  # 减少超时时间，提高响应速度
            
            self.logger.info(f"设备 {addr[0]}:{addr[1]} 建立持久连接")
            
            # 立即发送确认响应
            try:
                client_socket.send(b"OK")
            except:
                pass
            
            while True:
                try:
                    data = client_socket.recv(4096)
                    if not data:
                        # 客户端主动断开连接
                        self.logger.info(f"设备 {addr[0]}:{addr[1]} 主动断开连接")
                        break
                    
                    # 立即发送响应，避免客户端超时
                    try:
                        client_socket.send(b"OK")
                    except:
                        pass
                    
                    # 解析数据
                    parsed_data = self.parse_binary_data(data)
                    if parsed_data:
                        node_name = parsed_data.get('node_name')
                        if node_name:
                            # 注册设备
                            self.register_device(node_name, addr, parsed_data)
                            # 存储数据
                            self.store_device_data(node_name, parsed_data)
                            # 显示数据
                            self.display_device_data(node_name, parsed_data, addr)
                            
                except socket.timeout:
                    # 超时不是错误，继续等待数据
                    continue
                except Exception as e:
                    self.logger.error(f"处理客户端 {addr} 数据时出错: {e}")
                    break
                        
        except Exception as e:
            self.logger.error(f"处理客户端 {addr} 连接时出错: {e}")
        finally:
            try:
                client_socket.close()
            except:
                pass
            # 只有在真正断开时才记录日志
            self.logger.info(f"设备 {addr[0]}:{addr[1]} 连接结束")
            self.cleanup_device(addr)
    
    def parse_binary_data(self, data):
        """解析二进制数据 - 严格按照gnss_data.h结构体"""
        try:
            if len(data) < 24:
                return None
                
            # 检查标识符 (TTI = 0xAAAABBBB)
            if int.from_bytes(data[0:4], byteorder='little') != 0xAAAABBBB:
                return None
                
            # 解析TCP_FrameHeader (20字节)
            tti = int.from_bytes(data[0:4], byteorder='little')
            seq = int.from_bytes(data[4:8], byteorder='little')
            package_type = int.from_bytes(data[8:10], byteorder='little')
            package_len = int.from_bytes(data[10:12], byteorder='little')
            check_word = int.from_bytes(data[12:16], byteorder='little')
            node_name = int.from_bytes(data[16:20], byteorder='little')
            
            # 解析GNSS_Data (从第20字节开始)
            offset = 20
            
            # NAV-PVT协议数据部分 (7字节)
            year = int.from_bytes(data[offset:offset+2], byteorder='little')
            month = data[offset+2]
            day = data[offset+3]
            hour = data[offset+4]
            minute = data[offset+5]
            second = data[offset+6]
            offset += 7
            
            # 位置信息 (24字节)
            lon = struct.unpack('<d', data[offset:offset+8])[0]
            offset += 8
            lat = struct.unpack('<d', data[offset:offset+8])[0]
            offset += 8
            height = struct.unpack('<d', data[offset:offset+8])[0]
            offset += 8
            
            # 运动信息 (12字节)
            ground_speed = struct.unpack('<f', data[offset:offset+4])[0]
            offset += 4
            heading = struct.unpack('<f', data[offset:offset+4])[0]
            offset += 4
            pdop = struct.unpack('<f', data[offset:offset+4])[0]
            offset += 4
            
            # 卫星数量 (1字节)
            satellite_count = data[offset]
            offset += 1
            
            # 解析Satellite_Info数组 (最多40个卫星，每个7字节)
            satellites = []
            max_satellites = min(satellite_count, 40)  # 限制最大卫星数量
            
            for i in range(max_satellites):
                if offset + 7 <= len(data):
                    gnss_id = data[offset]
                    sv_id = data[offset + 1]
                    cnos = data[offset + 2]
                    elev = struct.unpack('<b', data[offset + 3:offset + 4])[0]
                    azim = struct.unpack('<H', data[offset + 4:offset + 6])[0]
                    
                    satellites.append({
                        'gnss_id': gnss_id,
                        'sv_id': sv_id,
                        'cnos': cnos,
                        'elev': elev,
                        'azim': azim
                    })
                    offset += 7
            
            # 构建时间戳
            try:
                dt = datetime.datetime(year, month, day, hour, minute, second)
            except:
                dt = datetime.datetime.now()
            
            # 构建节点名称字符串
            node_name_str = f"Android_Device_{node_name & 0x7FFFFFFF:07d}"
            
            return {
                'node_name': node_name_str,
                'seq': seq,
                'timestamp': dt,
                'lat': lat,
                'lon': lon,
                'alt': height,
                'speed': ground_speed,
                'bearing': heading,
                'accuracy': pdop,
                'satellites': satellite_count,
                'satellite_info': satellites,
                'tti': hex(tti),
                'package_type': hex(package_type),
                'package_len': hex(package_len),
                'check_word': hex(check_word)
            }
            
        except Exception as e:
            self.logger.error(f"解析二进制数据时出错: {e}")
            return None
    
    def register_device(self, node_name, addr, data):
        """注册新设备"""
        if node_name not in self.devices:
            self.devices[node_name] = {
                'addr': addr,
                'connect_time': datetime.datetime.now(),
                'data_count': 0,
                'last_data': None,
                'status': 'online'
            }
            self.device_stats[node_name] = {
                'total_data': 0,
                'first_seen': datetime.datetime.now(),
                'last_seen': datetime.datetime.now()
            }
            self.logger.info(f"新设备注册: {node_name} from {addr[0]}:{addr[1]}")
        
        # 更新设备状态
        self.devices[node_name]['last_data'] = data
        self.devices[node_name]['data_count'] += 1
        self.device_stats[node_name]['total_data'] += 1
        self.device_stats[node_name]['last_seen'] = datetime.datetime.now()
    
    def store_device_data(self, node_name, data):
        """存储设备数据"""
        self.device_data[node_name].append(data)
        
        # 限制缓存大小
        if len(self.device_data[node_name]) > self.max_data_per_device:
            self.device_data[node_name].popleft()
    
    def display_device_data(self, node_name, data, addr):
        """显示设备数据"""
        print("\n" + "="*80)
        print(f"Send_Buf_Data from {addr[0]}:{addr[1]} ({node_name})")
        print("="*80)
        
        print("TCP_FrameHeader {")
        print(f"    TTI: {data['tti']}")
        print(f"    Number: {data['seq']}")
        print(f"    Package_Type: {data['package_type']}")
        print(f"    Package_len: {data['package_len']}")
        print(f"    Check_Word: {data['check_word']}")
        print(f"    node_name: {data['node_name']}")
        print("}")
        print()
        
        print("GNSS_Data {")
        print("    // NAV-PVT协议数据部分")
        print(f"    year: {data['timestamp'].year}")
        print(f"    month: {data['timestamp'].month}")
        print(f"    day: {data['timestamp'].day}")
        print(f"    hour: {data['timestamp'].hour}")
        print(f"    minute: {data['timestamp'].minute}")
        print(f"    second: {data['timestamp'].second}")
        print(f"    lon: {data['lon']:.8f}")
        print(f"    lat: {data['lat']:.8f}")
        print(f"    height: {data['alt']:.3f}")
        print(f"    groundSpeed: {data['speed']:.3f}")
        print(f"    heading: {data['bearing']:.3f}")
        print(f"    PDOP: {data['accuracy']:.3f}")
        print(f"    satelliteCount: {data['satellites']}")
        print("}")
        
        # 显示卫星信息
        if 'satellite_info' in data and data['satellite_info']:
            print("\n[Satellite Information]")
            print("-" * 40)
            print("ID  GNSS  CNR  Elev  Azim")
            print("-" * 40)
            for sat in data['satellite_info']:
                print(f"{sat['sv_id']:2d}  {sat['gnss_id']:4d}  {sat['cnos']:3d}  {sat['elev']:4d}  {sat['azim']:5d}")
            print(f"Total valid satellites: {len(data['satellite_info'])}")
        
        print("="*80)
    
    def device_monitor(self):
        """设备监控线程"""
        while True:
            try:
                self.logger.info(f"设备状态监控 - 在线设备: {len(self.devices)}")
                for node_name, device_info in self.devices.items():
                    self.logger.info(f"   {node_name}: 数据包 {device_info['data_count']} 个")
                
                # 每30秒输出一次统计信息
                threading.Event().wait(30)
                
            except Exception as e:
                self.logger.error(f"设备监控出错: {e}")
    
    def cleanup_device(self, addr):
        """清理断开连接的设备"""
        for node_name, device_info in list(self.devices.items()):
            if device_info['addr'] == addr:
                self.devices[node_name]['status'] = 'offline'
                self.logger.info(f"设备离线: {node_name}")
                break
    
    def get_device_summary(self):
        """获取设备摘要信息"""
        summary = {
            'total_devices': len(self.devices),
            'online_devices': len([d for d in self.devices.values() if d['status'] == 'online']),
            'total_data_packets': sum(stats['total_data'] for stats in self.device_stats.values()),
            'devices': {}
        }
        
        for node_name, device_info in self.devices.items():
            stats = self.device_stats.get(node_name, {})
            summary['devices'][node_name] = {
                'status': device_info['status'],
                'addr': f"{device_info['addr'][0]}:{device_info['addr'][1]}",
                'data_count': device_info['data_count'],
                'total_data': stats.get('total_data', 0),
                'first_seen': stats.get('first_seen', 'Unknown'),
                'last_seen': stats.get('last_seen', 'Unknown')
            }
        
        return summary

def main():
    """主函数"""
    server = MultiDeviceServer()
    try:
        server.start()
    except KeyboardInterrupt:
        print("\n服务器正在关闭...")
        summary = server.get_device_summary()
        print(f"最终统计: {summary['total_devices']} 个设备, {summary['total_data_packets']} 个数据包")

if __name__ == "__main__":
    main()

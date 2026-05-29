#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
数据包延迟分析工具
用于分析LocationUploader数据包的接收延迟情况
"""

import socket
import threading
import time
import struct
import datetime
import logging
from collections import defaultdict, deque

class PacketDelayAnalyzer:
    def __init__(self, host='0.0.0.0', port=12345):
        self.host = host
        self.port = port
        self.server_socket = None
        self.clients = {}
        self.packet_times = defaultdict(list)  # 每个设备的数据包时间戳
        self.delays = defaultdict(list)  # 每个设备的延迟统计
        self.running = True
        
        # 配置日志
        logging.basicConfig(
            level=logging.INFO,
            format='%(asctime)s - %(levelname)s - %(message)s',
            handlers=[
                logging.FileHandler('packet_delay_analysis.log', encoding='utf-8'),
                logging.StreamHandler()
            ]
        )
        self.logger = logging.getLogger(__name__)
        
    def start(self):
        """启动分析服务器"""
        try:
            self.server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            self.server_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            self.server_socket.bind((self.host, self.port))
            self.server_socket.listen(10)
            
            self.logger.info(f"数据包延迟分析服务器启动成功！")
            self.logger.info(f"监听地址: {self.host}:{self.port}")
            self.logger.info(f"等待设备连接...")
            
            # 启动延迟分析线程
            analysis_thread = threading.Thread(target=self.delay_analysis_loop, daemon=True)
            analysis_thread.start()
            
            while self.running:
                client_socket, addr = self.server_socket.accept()
                self.logger.info(f"设备 {addr[0]}:{addr[1]} 已连接")
                
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
            
            while self.running:
                try:
                    data = client_socket.recv(4096)
                    if not data:
                        # 客户端主动断开连接
                        self.logger.info(f"设备 {addr[0]}:{addr[1]} 主动断开连接")
                        break
                    
                    # 立即发送响应
                    try:
                        client_socket.send(b"OK")
                    except:
                        pass
                    
                    # 分析数据包
                    self.analyze_packet(data, addr)
                            
                except socket.timeout:
                    # 超时不是错误，继续等待数据
                    continue
                except Exception as e:
                    self.logger.error(f"接收数据时出错: {e}")
                    break
                        
        except Exception as e:
            self.logger.error(f"处理客户端 {addr} 数据时出错: {e}")
        finally:
            try:
                client_socket.close()
            except:
                pass
            # 只有在真正断开时才记录日志
            self.logger.info(f"设备 {addr[0]}:{addr[1]} 连接结束")
    
    def analyze_packet(self, data, addr):
        """分析数据包延迟"""
        try:
            # 检查是否为二进制数据
            if len(data) >= 24 and int.from_bytes(data[0:4], byteorder='little') == 0xAAAABBBB:
                # 解析序列号
                seq = int.from_bytes(data[4:8], byteorder='little')
                node_name = int.from_bytes(data[16:20], byteorder='little')
                device_id = f"Device_{node_name & 0x7FFFFFFF:07d}"
                
                current_time = time.time()
                
                # 记录数据包时间
                self.packet_times[device_id].append((seq, current_time))
                
                # 计算延迟
                if len(self.packet_times[device_id]) > 1:
                    prev_seq, prev_time = self.packet_times[device_id][-2]
                    if seq > prev_seq:
                        delay = current_time - prev_time
                        self.delays[device_id].append(delay)
                        
                        # 实时显示延迟信息
                        self.logger.info(f"设备 {device_id} - 序列号: {seq}, 延迟: {delay:.3f}秒")
                        
                        # 如果延迟超过1.5秒，发出警告
                        if delay > 1.5:
                            self.logger.warning(f"⚠️ 设备 {device_id} 数据包延迟过高: {delay:.3f}秒")
                
                # 限制历史记录数量
                if len(self.packet_times[device_id]) > 100:
                    self.packet_times[device_id] = self.packet_times[device_id][-50:]
                if len(self.delays[device_id]) > 100:
                    self.delays[device_id] = self.delays[device_id][-50:]
                    
        except Exception as e:
            self.logger.error(f"分析数据包时出错: {e}")
    
    def delay_analysis_loop(self):
        """延迟分析循环"""
        while self.running:
            try:
                time.sleep(10)  # 每10秒分析一次
                self.print_delay_statistics()
            except Exception as e:
                self.logger.error(f"延迟分析循环出错: {e}")
    
    def print_delay_statistics(self):
        """打印延迟统计信息"""
        if not self.delays:
            return
            
        self.logger.info("=" * 60)
        self.logger.info("📊 数据包延迟统计报告")
        self.logger.info("=" * 60)
        
        for device_id, delays in self.delays.items():
            if delays:
                avg_delay = sum(delays) / len(delays)
                min_delay = min(delays)
                max_delay = max(delays)
                
                # 计算1秒内的数据包数量
                recent_delays = [d for d in delays if d <= 1.0]
                packets_per_second = len(recent_delays)
                
                self.logger.info(f"设备: {device_id}")
                self.logger.info(f"  平均延迟: {avg_delay:.3f}秒")
                self.logger.info(f"  最小延迟: {min_delay:.3f}秒")
                self.logger.info(f"  最大延迟: {max_delay:.3f}秒")
                self.logger.info(f"  1秒内数据包: {packets_per_second}个")
                
                # 延迟分布
                if max_delay > 1.0:
                    self.logger.warning(f"  ⚠️ 存在延迟超过1秒的数据包")
                
                self.logger.info("-" * 40)
        
        self.logger.info("=" * 60)
    
    def stop(self):
        """停止服务器"""
        self.running = False
        if self.server_socket:
            self.server_socket.close()

def main():
    """主函数"""
    analyzer = PacketDelayAnalyzer()
    
    try:
        analyzer.start()
    except KeyboardInterrupt:
        print("\n收到中断信号，正在关闭服务器...")
        analyzer.stop()

if __name__ == "__main__":
    main()

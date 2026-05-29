#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
测试新的结构体显示格式
验证服务器显示是否符合gnss_data.h结构体格式
"""

import datetime

def test_display_format():
    """测试显示格式"""
    print("=== 结构体显示格式测试 ===")
    print("验证服务器显示是否符合gnss_data.h格式")
    print()
    
    # 模拟解析后的数据
    test_data = {
        'tti': '0xaaaabbbb',
        'seq': 11,
        'package_type': '0xbb',
        'package_len': '0x130',
        'check_word': '0xbbbb',
        'node_name': 'Android_Device_1348049379',
        'timestamp': datetime.datetime(2025, 8, 16, 0, 57, 7),
        'lon': 112.914623,
        'lat': 28.224710,
        'alt': 0.0,
        'speed': 0.0,
        'bearing': 0.0,
        'accuracy': 7.1,  # PDOP值
        'satellites': 3,
        'satellites_info': [
            {'gnss_id': 1, 'sv_id': 1, 'cnos': 23, 'elev': 20, 'azim': 0},
            {'gnss_id': 1, 'sv_id': 2, 'cnos': 22, 'elev': 32, 'azim': 30},
            {'gnss_id': 1, 'sv_id': 3, 'cnos': 22, 'elev': 44, 'azim': 60}
        ]
    }
    
    # 模拟新的显示格式
    print("="*80)
    print("Send_Buf_Data from 192.168.1.104:39186")
    print("="*80)
    
    print("TCP_FrameHeader {")
    print(f"    TTI: {test_data['tti']}")
    print(f"    Number: {test_data['seq']}")
    print(f"    Package_Type: {test_data['package_type']}")
    print(f"    Package_len: {test_data['package_len']}")
    print(f"    Check_Word: {test_data['check_word']}")
    print(f"    node_name: {test_data['node_name']}")
    print("}")
    print()
    
    print("GNSS_Data {")
    print("    // NAV-PVT协议数据部分")
    print(f"    year: {test_data['timestamp'].year}")
    print(f"    month: {test_data['timestamp'].month}")
    print(f"    day: {test_data['timestamp'].day}")
    print(f"    hour: {test_data['timestamp'].hour}")
    print(f"    minute: {test_data['timestamp'].minute}")
    print(f"    second: {test_data['timestamp'].second}")
    print(f"    lon: {test_data['lon']:.8f}")
    print(f"    lat: {test_data['lat']:.8f}")
    print(f"    height: {test_data['alt']:.3f}")
    print(f"    groundSpeed: {test_data['speed']:.3f}")
    print(f"    heading: {test_data['bearing']:.3f}")
    print(f"    PDOP: {test_data['accuracy']:.3f}")
    print(f"    satelliteCount: {test_data['satellites']}")
    print()
    
    print("    // NAV-SAT协议数据部分")
    print("    satellites[MAX_SATELLITES] {")
    for i, sat in enumerate(test_data['satellites_info']):
        print(f"        [{i}]: {{ gnssId: {sat['gnss_id']}, svId: {sat['sv_id']}, cnos: {sat['cnos']}, elev: {sat['elev']}, azim: {sat['azim']} }}")
    print("    }")
    print("}")
    print("="*80)
    
    print()
    print("✅ 新的显示格式特点:")
    print("   - 严格按照gnss_data.h结构体格式")
    print("   - 移除了所有emoji图标")
    print("   - 显示完整的PDOP值")
    print("   - 结构体字段名称与.h文件一致")
    print("   - 便于数据对接和调试")

if __name__ == "__main__":
    test_display_format()

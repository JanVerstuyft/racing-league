import struct

def analyze_kendrix_lap2(filepath):
    print(f"Analyzing Kendrix Lap 2 packets in {filepath}...")
    with open(filepath, 'rb') as f:
        packet_count = 0
        samples = []
        
        while True:
            length_bytes = f.read(2)
            if not length_bytes:
                break
            
            length = (length_bytes[0] << 8) | length_bytes[1]
            data = f.read(length)
            if not data:
                break
            
            packet_count += 1
            packet_id = data[6]
            session_time = struct.unpack('<f', data[15:19])[0]
            
            if packet_id == 2: # PacketLapData
                format_val = struct.unpack('<H', data[0:2])[0]
                max_cars = 24 if format_val == 2026 else 22
                offset = 29
                lap_data_size = 57
                
                # Kendrix is car index 11
                car_offset = offset + 11 * lap_data_size
                if car_offset + lap_data_size <= len(data):
                    car_lap_bytes = data[car_offset:car_offset+lap_data_size]
                    unpacked = struct.unpack('<IIHBHBHBHBfffBBBBBBBBBBBBBBBHHBfB', car_lap_bytes)
                    
                    last_lap_time = unpacked[0]
                    current_lap_time = unpacked[1]
                    lap_distance = unpacked[10]
                    current_lap_num = unpacked[14]
                    current_lap_invalid = unpacked[18]
                    driver_status = unpacked[25]
                    result_status = unpacked[26]
                    
                    if current_lap_num == 2:
                        samples.append((packet_count, session_time, driver_status, result_status, current_lap_time, lap_distance))
                        
        print(f"Total packets on Lap 2 for Kendrix: {len(samples)}")
        
        # Count driver_status occurrences
        status_counts = {}
        for s in samples:
            status = s[2]
            status_counts[status] = status_counts.get(status, 0) + 1
        print("Driver status distribution on Lap 2:")
        for status, count in status_counts.items():
            print(f"  Status {status}: {count} packets")
            
        # Print status transitions
        last_status = None
        for idx, s in enumerate(samples):
            # s: (packet_count, session_time, driver_status, result_status, current_lap_time, lap_distance)
            if last_status is None or s[2] != last_status:
                print(f"Index {idx} (Packet {s[0]}, st={s[1]:.2f}s): Status {s[2]}, lapTime={s[4]}ms, dist={s[5]:.2f}m")
                last_status = s[2]
                
        if samples:
            last = samples[-1]
            print(f"Final Lap 2 Packet (Packet {last[0]}, st={last[1]:.2f}s): Status {last[2]}, lapTime={last[4]}ms, dist={last[5]:.2f}m")

analyze_kendrix_lap2("data/exa_austria.bin")

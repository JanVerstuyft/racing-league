import struct
import os

def analyze_raw_packets(filepath):
    print(f"Analyzing raw packets in {filepath}...")
    with open(filepath, 'rb') as f:
        packet_count = 0
        
        # We want to track car 2 (Dion) and car 11 (Kendrix)
        dion_data = []
        kendrix_data = []
        
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
                # Check format
                format_val = struct.unpack('<H', data[0:2])[0]
                max_cars = 24 if format_val == 2026 else 22
                
                # Header size is 29
                offset = 29
                lap_data_size = 57
                
                for car_idx in range(max_cars):
                    car_offset = offset + car_idx * lap_data_size
                    if car_offset + lap_data_size > len(data):
                        break
                    
                    car_lap_bytes = data[car_offset:car_offset+lap_data_size]
                    # Unpack LapData
                    unpacked = struct.unpack('<IIHBHBHBHBfffBBBBBBBBBBBBBBBHHBfB', car_lap_bytes)
                    
                    last_lap_time = unpacked[0]
                    current_lap_time = unpacked[1]
                    lap_distance = unpacked[10]
                    current_lap_num = unpacked[14]
                    current_lap_invalid = unpacked[18]
                    driver_status = unpacked[25]
                    result_status = unpacked[26]
                    
                    # Store if Dion or Kendrix
                    if car_idx == 2:
                        dion_data.append((session_time, current_lap_num, driver_status, result_status, current_lap_time, lap_distance, current_lap_invalid, last_lap_time))
                    elif car_idx == 11:
                        kendrix_data.append((session_time, current_lap_num, driver_status, result_status, current_lap_time, lap_distance, current_lap_invalid, last_lap_time))
                        
        print(f"Total packets parsed: {packet_count}")
        
        def print_driver_summary(name, log_data):
            print(f"\n--- {name} Telemetry Stream Summary ---")
            print(f"Total Lap Data packets: {len(log_data)}")
            
            # Print transition points (where lap number, driver status, or result status changes)
            last_entry = None
            for idx, entry in enumerate(log_data):
                # entry: (session_time, current_lap_num, driver_status, result_status, current_lap_time, lap_distance, current_lap_invalid, last_lap_time)
                if last_entry is None or (entry[1] != last_entry[1] or entry[2] != last_entry[2] or entry[3] != last_entry[3] or entry[6] != last_entry[6]):
                    print(f"Packet {idx:4d} (st={entry[0]:.2f}s): Lap={entry[1]}, Status={entry[2]}, Result={entry[3]}, lapTime={entry[4]}ms, dist={entry[5]:.2f}m, invalid={entry[6]}, lastLap={entry[7]}ms")
                last_entry = entry
                
            # Let's print the very last entry
            if log_data:
                entry = log_data[-1]
                print(f"Final Packet (st={entry[0]:.2f}s): Lap={entry[1]}, Status={entry[2]}, Result={entry[3]}, lapTime={entry[4]}ms, dist={entry[5]:.2f}m, invalid={entry[6]}, lastLap={entry[7]}ms")

        print_driver_summary("DION (Car 2)", dion_data)
        print_driver_summary("KENDRIX (Car 11)", kendrix_data)

analyze_raw_packets("data/exa_austria.bin")

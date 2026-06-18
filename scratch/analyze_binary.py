import os
import struct

def parse_header(data):
    # F1 25 header format: 29 bytes
    header = struct.unpack("<HBBBBBQfIIBB", data[:29])
    return {
        "packetFormat": header[0],
        "packetId": header[5],
        "sessionUID": header[6],
        "sessionTime": header[7],
        "frameIdentifier": header[8],
        "playerCarIndex": header[10]
    }

def parse_lap_data(data, car_idx):
    # Offset of LapData array in PacketLapData is 29 (header)
    # LapData size is 48 bytes
    offset = 29 + car_idx * 48
    
    # Extract fields:
    # lastLapTimeInMS (uint32) @ 0
    # currentLapTimeInMS (uint32) @ 4
    # sector1TimeMSPart (uint16) @ 8
    # sector1TimeMinutesPart (uint8) @ 10
    # ...
    # lapDistance (float) @ 20
    # currentLapNum (uint8) @ 33
    # driverStatus (uint8) @ 44
    # resultStatus (uint8) @ 45
    # Total unpack format for 48 bytes:
    # < I I H B H B H B H B f f f B B B B B B B B B B B B B B B H H B f B
    # Wait, let's just unpack the specific fields we need by offset:
    last_lap_time = struct.unpack("<I", data[offset:offset+4])[0]
    curr_lap_time = struct.unpack("<I", data[offset+4:offset+8])[0]
    lap_dist = struct.unpack("<f", data[offset+20:offset+24])[0]
    curr_lap_num = struct.unpack("<B", data[offset+33:offset+34])[0]
    driver_status = struct.unpack("<B", data[offset+44:offset+45])[0]
    result_status = struct.unpack("<B", data[offset+45:offset+46])[0]
    
    return {
        "lastLapTime": last_lap_time,
        "currentLapTime": curr_lap_time,
        "lapDistance": lap_dist,
        "currentLapNum": curr_lap_num,
        "driverStatus": driver_status,
        "resultStatus": result_status
    }

def analyze():
    file_path = "data/exa_austria.bin"
    if not os.path.exists(file_path):
        print("File not found.")
        return

    print("Analyzing packets in binary file...")
    
    dion_laps = []
    kendrix_laps = []
    
    with open(file_path, "rb") as f:
        packet_count = 0
        while True:
            len_bytes = f.read(2)
            if not len_bytes:
                break
            length = (len_bytes[0] << 8) | len_bytes[1]
            data = f.read(length)
            if not data:
                break
            
            header = parse_header(data)
            packet_id = header["packetId"]
            
            if packet_id == 2: # Lap Data
                # Dion = car index 2, Kendrix = car index 11
                if len(data) >= 29 + 22 * 48:
                    dion = parse_lap_data(data, 2)
                    kendrix = parse_lap_data(data, 11)
                    
                    dion_laps.append((header["sessionTime"], dion))
                    kendrix_laps.append((header["sessionTime"], kendrix))
            
            packet_count += 1

    # Print summary for Dion
    print("\n--- Dion (Car 2) Lap Data ---")
    last_lap = -1
    for t, lap in dion_laps:
        if lap["currentLapNum"] != last_lap:
            print(f"Time: {t:.2f}s | Lap: {lap['currentLapNum']} | LastLapTime: {lap['lastLapTime']}ms | CurrLapTime: {lap['currentLapTime']}ms | Status: {lap['driverStatus']} | ResultStatus: {lap['resultStatus']}")
            last_lap = lap["currentLapNum"]
            
    # Print summary for Kendrix
    print("\n--- Kendrix (Car 11) Lap Data ---")
    last_lap = -1
    for t, lap in kendrix_laps:
        if lap["currentLapNum"] != last_lap:
            print(f"Time: {t:.2f}s | Lap: {lap['currentLapNum']} | LastLapTime: {lap['lastLapTime']}ms | CurrLapTime: {lap['currentLapTime']}ms | Status: {lap['driverStatus']} | ResultStatus: {lap['resultStatus']}")
            last_lap = lap["currentLapNum"]

if __name__ == "__main__":
    analyze()

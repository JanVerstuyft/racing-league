import struct
import os

def analyze_recording(file_path):
    if not os.path.exists(file_path):
        print(f"File {file_path} not found.")
        return

    print(f"Analyzing {file_path}...")
    
    with open(file_path, 'rb') as f:
        packet_count = 0
        current_session_uid = None
        current_session_type = None
        
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
            session_uid = struct.unpack('<Q', data[7:15])[0]
            
            if packet_id == 1: # Session Packet
                session_type = data[35]
                if session_type != current_session_type or session_uid != current_session_uid:
                    print(f"Packet {packet_count}: Session Change! UID: {session_uid}, Type: {session_type}")
                    current_session_type = session_type
                    current_session_uid = session_uid
            
            if packet_id == 3: # Event Packet
                event_code = data[29:33].decode('ascii', errors='ignore')
                if event_code in ["SSTA", "SEND", "CHQF"]:
                    print(f"Packet {packet_count}: EVENT {event_code} for UID: {session_uid}, Type: {current_session_type}")
            
            if packet_id == 8: # Final Classification
                print(f"Packet {packet_count}: FINAL CLASSIFICATION (Packet 8) for UID: {session_uid}, Type: {current_session_type}")

        print(f"Last packet was {packet_count}. ID: {packet_id}, UID: {session_uid}")

if __name__ == "__main__":
    analyze_recording("data/exa_austria.bin")

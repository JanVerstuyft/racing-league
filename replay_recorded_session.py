import time
import os
import urllib.request

def replay_session(file_path, target_url):
    if not os.path.exists(file_path):
        print(f"Error: File {file_path} not found.")
        return

    print(f"Starting replay of {file_path} to {target_url}...")
    
    with open(file_path, 'rb') as f:
        packet_count = 0
        try:
            while True:
                # Read 2-byte length prefix
                length_bytes = f.read(2)
                if not length_bytes:
                    break
                
                length = (length_bytes[0] << 8) | length_bytes[1]
                data = f.read(length)
                
                if not data:
                    break

                # Forward to cloud server using urllib
                try:
                    req = urllib.request.Request(
                        target_url, 
                        data=data, 
                        headers={'Content-Type': 'application/octet-stream'},
                        method='POST'
                    )
                    with urllib.request.urlopen(req) as response:
                        if response.status != 200:
                            print(f"Error forwarding packet {packet_count}: {response.status}")
                except Exception as e:
                    print(f"Request failed at packet {packet_count}: {e}")
                    # If the server is down or erroring, we might want to stop
                    if packet_count > 0 and "Connection refused" in str(e):
                        break

                packet_count += 1
                if packet_count % 100 == 0:
                    print(f"Sent {packet_count} packets...")
                
                # No delay for maximum speed
                # time.sleep(0.0001)
                
        except KeyboardInterrupt:
            print("\nReplay interrupted by user.")
        
    print(f"Finished! Sent total of {packet_count} packets.")

if __name__ == "__main__":
    RECORDING_FILE = "data/recorded_race_test.bin"
    CLOUD_URL = "http://localhost:8080/api/telemetry"
    replay_session(RECORDING_FILE, CLOUD_URL)

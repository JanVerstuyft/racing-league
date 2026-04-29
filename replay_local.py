import time
import os
import http.client
import urllib.parse

def replay_session(file_path, target_url):
    if not os.path.exists(file_path):
        print(f"Error: File {file_path} not found.")
        return

    parsed_url = urllib.parse.urlparse(target_url)
    host = parsed_url.netloc
    path = parsed_url.path
    
    print(f"Starting replay of {file_path} to {target_url}...")
    
    # Use persistent connection
    if parsed_url.scheme == 'https':
        conn = http.client.HTTPSConnection(host)
    else:
        conn = http.client.HTTPConnection(host)

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

                # Forward to cloud server using persistent connection
                try:
                    conn.request('POST', path, body=data, headers={'Content-Type': 'application/octet-stream'})
                    response = conn.getresponse()
                    response.read() # Must read the full response to reuse connection
                    if response.status != 200:
                        print(f"Error forwarding packet {packet_count}: {response.status}")
                except Exception as e:
                    print(f"Request failed at packet {packet_count}: {e}")
                    # Reconnect on error
                    if parsed_url.scheme == 'https':
                        conn = http.client.HTTPSConnection(host)
                    else:
                        conn = http.client.HTTPConnection(host)

                packet_count += 1
                if packet_count % 100 == 0:
                    print(f"Sent {packet_count} packets...")
                
                # Small delay to prevent overwhelming the server, but much faster than before
                # time.sleep(0.001)
                
        except KeyboardInterrupt:
            print("\nReplay interrupted by user.")
        finally:
            conn.close()
        
    print(f"Finished! Sent total of {packet_count} packets.")

if __name__ == "__main__":
    RECORDING_FILE = "data/local_race.bin"
    CLOUD_URL = "https://racingleague.jabapage.be/api/telemetry/26c5b212-ebd6-49b2-a134-d7fbf1d4f77e"
    replay_session(RECORDING_FILE, CLOUD_URL)

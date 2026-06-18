import subprocess
import json

def get_telemetry():
    cmd = [
        "docker", "exec", "-t", "racingleague-postgres",
        "psql", "-U", "postgres", "-d", "racingleague", "-t", "-P", "pager=off", "-c",
        "SELECT dr.driver_name, lr.lap_number, lt.telemetry_data FROM driver_result dr JOIN lap_result lr ON lr.driver_result_id = dr.id JOIN lap_telemetry lt ON lt.lap_result_id = lr.id WHERE dr.session_result_id = 219 AND dr.driver_name IN ('Dion', 'Kendrix');"
    ]
    result = subprocess.run(cmd, capture_output=True, text=True)
    lines = result.stdout.split('\n')
    for line in lines:
        if not line.strip():
            continue
        parts = line.split('|')
        if len(parts) < 3:
            continue
        driver = parts[0].strip()
        lap_num = parts[1].strip()
        telemetry_str = parts[2].strip()
        
        try:
            telemetry = json.loads(telemetry_str)
            t = telemetry.get('t', [])
            d = telemetry.get('d', [])
            x = telemetry.get('x', [])
            z = telemetry.get('z', [])
            
            num = len(t)
            print(f"Driver: {driver}, Lap: {lap_num}, Samples: {num}")
            if num > 0:
                print("  First 3:")
                for i in range(min(3, num)):
                    print(f"    t={t[i]}ms, d={d[i]:.2f}m, x={x[i]:.2f}, z={z[i]:.2f}")
                print("  Last 3:")
                for i in range(max(0, num-3), num):
                    print(f"    t={t[i]}ms, d={d[i]:.2f}m, x={x[i]:.2f}, z={z[i]:.2f}")
        except Exception as e:
            print(f"Failed to parse for {driver} Lap {lap_num}: {e}")

if __name__ == "__main__":
    get_telemetry()

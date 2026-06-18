import json

def analyze_file(name, filepath):
    print(f"=== Analyzing {name} ({filepath}) ===")
    with open(filepath, 'r') as f:
        data = json.load(f)
    
    t = data.get('t', [])
    d = data.get('d', [])
    x = data.get('x', [])
    z = data.get('z', [])
    spd = data.get('spd', [])
    thr = data.get('thr', [])
    brk = data.get('brk', [])
    gear = data.get('gear', [])
    
    print(f"Number of samples: {len(t)}")
    if len(t) > 0:
        print(f"Time: {t[0]} to {t[-1]} ms (diff: {t[-1] - t[0]} ms)")
        print(f"Distance: {d[0]} to {d[-1]} m (min: {min(d)}, max: {max(d)})")
        print(f"Speed: {spd[0]} to {spd[-1]} km/h (min: {min(spd)}, max: {max(spd)})")
        print(f"Throttle: {thr[0]} to {thr[-1]} (min: {min(thr)}, max: {max(thr)})")
        print(f"Brake: {brk[0]} to {brk[-1]} (min: {min(brk)}, max: {max(brk)})")
        print(f"Gear: {gear[0]} to {gear[-1]} (min: {min(gear)}, max: {max(gear)})")
        
        # Let's print samples where distance changes or jumps
        # Check if there are negative or descending distances
        jumps = []
        for i in range(1, len(d)):
            diff = d[i] - d[i-1]
            if diff < 0:
                jumps.append((i, d[i-1], d[i], diff))
        if jumps:
            print(f"Detected {len(jumps)} reverse distance transitions (drops):")
            for j in jumps[:5]:
                print(f"  Sample {j[0]}: {j[1]} -> {j[2]} (diff: {j[3]})")
        else:
            print("No distance drops detected (monotonically increasing or flat).")
            
        # Let's print some intermediate samples
        print("Sample time progression (every 10% of array):")
        step = max(1, len(t) // 10)
        for idx in range(0, len(t), step):
            print(f"  Sample {idx}: t={t[idx]}ms, d={d[idx]}m, spd={spd[idx]}km/h, gear={gear[idx]}, x={x[idx]}, z={z[idx]}")
    print()

analyze_file("Dion", "scratch/dion_telemetry.json")
analyze_file("Kendrix", "scratch/kendrix_telemetry.json")

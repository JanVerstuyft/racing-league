import socket
import struct
import time
import math
import random

PORT = 20777
IP = "127.0.0.1"
TRACK_LENGTH = 5000.0
NUM_CARS = 20

# Team ID to Name mapping (from TelemetryProcessingService.java)
# 0: Mercedes, 1: Ferrari, 2: Red Bull Racing, 3: Williams,
# 4: Aston Martin, 5: Alpine, 6: RB, 7: Haas,
# 8: McLaren, 9: Sauber
DRIVERS_CONFIG = [
    ("Max Verstappen", 2), ("Sergio Perez", 2),
    ("Lewis Hamilton", 0), ("George Russell", 0),
    ("Charles Leclerc", 1), ("Carlos Sainz", 1),
    ("Lando Norris", 8), ("Oscar Piastri", 8),
    ("Fernando Alonso", 4), ("Lance Stroll", 4),
    ("Pierre Gasly", 5), ("Esteban Ocon", 5),
    ("Alexander Albon", 3), ("Logan Sargeant", 3),
    ("Daniel Ricciardo", 6), ("Yuki Tsunoda", 6),
    ("Valtteri Bottas", 9), ("Guanyu Zhou", 9),
    ("Nico Hulkenberg", 7), ("Kevin Magnussen", 7)
]

class CarState:
    def __init__(self, index, name, team_id):
        self.index = index
        self.name = name
        self.team_id = team_id
        self.lap_distance = random.uniform(0, TRACK_LENGTH)
        self.total_distance = self.lap_distance
        self.current_lap = 1
        self.speed_kmh = 0.0
        self.gear = 0
        self.rpm = 1000
        self.throttle = 0.0
        self.brake = 0.0
        self.steer = 0.0
        self.tyre_wear = [0.0] * 4
        self.tyre_damage = [0] * 4
        self.brake_temp = [100] * 4
        self.tyre_temp_surface = [90] * 4
        self.tyre_temp_inner = [85] * 4
        self.fuel = 10.0
        self.position = index + 1
        self.session_time = 0.0
        self.last_lap_time = 0
        self.best_lap_time = 0
        self.pit_stops = 0

    def update(self, dt):
        self.session_time += dt
        dist = self.lap_distance
        target_speed = 320.0 - (self.index * 2) # Slight variation
        
        if (800 < dist < 1200) or (2500 < dist < 3000) or (4500 < dist < 5000):
            target_speed = 100.0
            self.throttle = 0.0
            self.brake = 0.8
            self.steer = 0.4 if (dist % 500 > 250) else -0.4
        else:
            self.throttle = 1.0
            self.brake = 0.0
            self.steer = 0.0

        if self.speed_kmh < target_speed:
            self.speed_kmh += (40.0 + random.uniform(-2, 2)) * dt
        elif self.speed_kmh > target_speed:
            self.speed_kmh -= 80.0 * dt
            
        speed_ms = self.speed_kmh / 3.6
        self.lap_distance += speed_ms * dt
        self.total_distance += speed_ms * dt
        
        if self.speed_kmh < 40: self.gear = 1
        elif self.speed_kmh < 80: self.gear = 2
        elif self.speed_kmh < 120: self.gear = 3
        elif self.speed_kmh < 160: self.gear = 4
        elif self.speed_kmh < 200: self.gear = 5
        elif self.speed_kmh < 240: self.gear = 6
        elif self.speed_kmh < 280: self.gear = 7
        else: self.gear = 8
        
        self.rpm = int(4000 + (self.speed_kmh % 40) / 40 * 8000)
        
        for i in range(4):
            if self.brake > 0: self.brake_temp[i] = min(800, self.brake_temp[i] + 50 * dt)
            else: self.brake_temp[i] = max(100, self.brake_temp[i] - 10 * dt)
            self.tyre_wear[i] = min(100.0, self.tyre_wear[i] + 0.05 * dt)

        if self.lap_distance >= TRACK_LENGTH:
            self.lap_distance -= TRACK_LENGTH
            self.current_lap += 1
            self.last_lap_time = int(self.session_time * 1000)

def create_header(packet_id, session_uid=12345678, frame_id=1, session_time=0.0, car_index=0):
    # uint16, uint8 x 6, uint64, float, uint32, uint32, uint8, uint8
    return struct.pack("<HBBBBBQfIIBB", 2025, 25, 1, 0, 1, packet_id, session_uid, session_time, frame_id, frame_id, car_index, 255)

def pack_participants(cars):
    header = create_header(4, car_index=0)
    data = header + struct.pack("<B", len(cars))
    for i in range(22):
        if i < len(cars):
            car = cars[i]
            name = car.name.encode("utf-8").ljust(32, b"\x00")
            # ParticipantData: uint8 x 7, 32s, uint8 x 2, uint16, uint8 x 2, LiveryColour[4](uint8 x 3)
            # LiveryColour x 4 = 12 bytes
            data += struct.pack("<BBBBBBB32sBBHBB 12B", 
                                0, i, i, car.team_id, 0, car.index+1, 1, name, 1, 1, 100, 1, 4, 
                                *([255, 0, 0] * 4)) # 4 colors
        else:
            data += struct.pack("<BBBBBBB32sBBHBB 12B", 
                                0, 255, 255, 255, 0, 0, 0, b"".ljust(32, b"\x00"), 0, 0, 0, 0, 0, 
                                *([0, 0, 0] * 4))
    return data

def pack_session(cars):
    header = create_header(1, session_time=cars[0].session_time)
    # PacketSessionData: uint8, int8, int8, uint8, uint16, uint8, int8, uint8, uint16, uint16, uint8, uint8, uint8, uint8, uint8, uint8
    # trackId is the 7th field (index 6 in pack)
    data = header + struct.pack("<BbbBHBbBHHBBBBBB", 0, 30, 25, 50, int(TRACK_LENGTH), 10, 17, 0, 1800, 3600, 80, 0, 0, 0, 0, 0)
    # Skip MarshalZones (105 bytes)
    data += b"\x00" * 105
    # uint8, uint8, uint8
    data += struct.pack("<BBB", 0, 0, 0)
    # Skip WeatherForecastSamples (512 bytes)
    data += b"\x00" * 512
    # uint8, uint8, uint32, uint32, uint32, uint8, uint8, uint8, uint8 x 11, uint32, uint8 x 35, uint8, 12B, float, float
    # We'll just pad the rest to keep it simple but correct enough for buffer size if needed
    data += struct.pack("<BBIII BBB BBBBBBBBBBB I B BBBBB BBBBB BBBBB BBBBB BBBBB BBBBB BBBBB B 12B ff",
                        0, 50, 123, 456, 789, 0, 0, 0, 
                        1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 
                        1000, 0, 1, 1, 1, 1, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0,
                        *([0]*12), 2000.0, 4000.0)
    return data

def pack_lap_data(cars, frame_id):
    header = create_header(2, frame_id=frame_id, session_time=cars[0].session_time)
    data = header
    
    # Sort cars by total distance for positions
    sorted_cars = sorted(cars, key=lambda c: c.total_distance, reverse=True)
    for i, car in enumerate(sorted_cars):
        car.position = i + 1

    for i in range(22):
        if i < len(cars):
            c = cars[i]
            # LapData: uint32 x 2, (uint16, uint8) x 4, float x 3, uint8 x 14, uint16 x 2, uint8, float, uint8
            # Total lap times/deltas split into MS part and Minute part
            data += struct.pack("<II HBHB HBHB fff BBBBBBBBBBBBBB HH B f B", 
                                c.last_lap_time % 60000, c.last_lap_time // 60000, # Incorrect but filler
                                0, 0, 0, 0, 0, 0, 0, 0, 
                                c.lap_distance, c.total_distance, 0.0, 
                                c.position, c.current_lap, 0, c.pit_stops, 1, 0, 0, 0, 0, 0, 0, c.position, 4, 3, 
                                0, 0, 0, 0.0, 0)
        else:
            data += struct.pack("<II HBHB HBHB fff BBBBBBBBBBBBBB HH B f B", 
                                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.0, 0.0, 0.0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.0, 0)
    data += struct.pack("<BB", 255, 255)
    return data

def pack_car_telemetry(cars, frame_id):
    header = create_header(6, frame_id=frame_id, session_time=cars[0].session_time)
    data = header
    for i in range(22):
        if i < len(cars):
            c = cars[i]
            # CarTelemetryData: uint16, float x 3, uint8, int8, uint16, uint8, uint8, uint16, uint16 x 4, uint8 x 4, uint8 x 4, uint16, float x 4, uint8 x 4
            data += struct.pack("<HfffBbHBBH 4H 4B 4B H 4f 4B",
                                int(c.speed_kmh), c.throttle, c.steer, c.brake, 0, c.gear, c.rpm, 1, 80, 0,
                                *c.brake_temp, *c.tyre_temp_surface, *c.tyre_temp_inner, 105, 
                                2.1, 2.1, 2.1, 2.1, 0, 0, 0, 0)
        else:
            data += struct.pack("<HfffBbHBBH 4H 4B 4B H 4f 4B", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
    data += struct.pack("<BBb", 255, 255, 0)
    return data

def pack_car_damage(cars, frame_id):
    header = create_header(10, frame_id=frame_id, session_time=cars[0].session_time)
    data = header
    for i in range(22):
        if i < len(cars):
            c = cars[i]
            # CarDamageData: float x 4, uint8 x 4, uint8 x 4, uint8 x 4, uint8 x 18
            data += struct.pack("<4f 4B 4B 4B 18B",
                                *c.tyre_wear, *c.tyre_damage, 0, 0, 0, 0, 0, 0, 0, 0,
                                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        else:
            data += struct.pack("<4f 4B 4B 4B 18B", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
    return data

def pack_car_status(cars, frame_id):
    header = create_header(7, frame_id=frame_id, session_time=cars[0].session_time)
    data = header
    for i in range(22):
        if i < len(cars):
            c = cars[i]
            # CarStatusData: uint8 x 5, float x 3, uint16 x 2, uint8 x 2, uint16, uint8 x 3, int8, float x 2, float, uint8, float x 4, uint8
            # visualTyreCompound 16=Soft, 17=Medium, 18=Hard
            tyre = 16 + (c.index % 3)
            data += struct.pack("<BBBBBfffHHBBHBBBbff f B ffff B",
                                1, 1, 1, 58, 0, c.fuel, 10.0, 5.0, 15000, 4000, 8, 1, 0, tyre+1, tyre, 2, 0, 
                                600000.0, 200000.0, # enginePowerICE, MGUK
                                4000000.0, 1, 0.0, 0.0, 0.0, 0)
        else:
            data += struct.pack("<BBBBBfffHHBBHBBBbff f B ffff B", 0, 0, 0, 0, 0, 0.0, 0.0, 0.0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.0, 0.0, 0.0, 0, 0.0, 0.0, 0.0, 0)
    return data

def main():
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    cars = [CarState(i, DRIVERS_CONFIG[i][0], DRIVERS_CONFIG[i][1]) for i in range(NUM_CARS)]
    
    print(f"Starting simulation with {NUM_CARS} drivers. Sending data to {IP}:{PORT}...")
    
    dt = 0.5
    frame_id = 0
    
    # Run loop
    try:
        while True:
            for car in cars:
                car.update(dt)
            
            # Send all required packets for leaderboard and dashboard
            sock.sendto(pack_participants(cars), (IP, PORT))
            sock.sendto(pack_session(cars), (IP, PORT))
            sock.sendto(pack_lap_data(cars, frame_id), (IP, PORT))
            sock.sendto(pack_car_status(cars, frame_id), (IP, PORT))
            sock.sendto(pack_car_telemetry(cars, frame_id), (IP, PORT))
            sock.sendto(pack_car_damage(cars, frame_id), (IP, PORT))
            
            if frame_id % 10 == 0:
                p1 = sorted(cars, key=lambda c: c.total_distance, reverse=True)[0]
                print(f"Frame {frame_id}: Leader {p1.name} at {p1.total_distance:.1f}m. Player Speed: {cars[0].speed_kmh:.1f} km/h")
                
            frame_id += 1
            time.sleep(dt)
    except KeyboardInterrupt:
        print("Simulation stopped.")

if __name__ == "__main__":
    main()

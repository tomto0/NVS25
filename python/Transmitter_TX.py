import socket
import hashlib
import struct
import time

# Konfiguration
DEST_IP = "127.0.0.1" # localhost als Ziel-IP
DEST_PORT = 5005
PACKET_SIZE = 1024 # Maximale Paketgröße in Bytes
TX_ID = 1 # Transmitter ID zur Identifikation des Senders
FILENAME = "/home/tomas/IdeaProjects/NVS25/test2.txt"

def send_file():
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM) # UDP-Socket erstellen

    with open(FILENAME, "rb") as f:
        file_data = f.read()

    # Berechnung der Nutzdaten pro Paket (ohne Header, also ohne TX ID und SeqNr)
    data_per_packet = PACKET_SIZE - 6  # 2 bytes TX ID + 4 bytes SeqNr
    num_data_packets = (len(file_data) + data_per_packet - 1) // data_per_packet
    max_seq_nr = num_data_packets + 2  # für First und Last Packet

    file_name_bytes = FILENAME.encode()
    file_name_len = len(file_name_bytes)

    # Startzeit
    start_time = time.time()

    # First Packet (SeqNr = 0)
    # Aufbau: [TX_ID (2B)][SeqNr (4B)=0][max_seq_nr (4B)][filename_len (4B)][filename (nB)]
    first_packet = struct.pack("!HI", TX_ID, 0)
    first_packet += struct.pack("!I", max_seq_nr)
    first_packet += struct.pack("!I", file_name_len)
    first_packet += file_name_bytes
    sock.sendto(first_packet, (DEST_IP, DEST_PORT))

    # === Data Packets (SeqNr 1 .. n) ===
    seq_nr = 1
    for i in range(0, len(file_data), data_per_packet):
        chunk = file_data[i:i + data_per_packet]
        # Aufbau: [TX_ID (2B)][SeqNr (4B)][Daten]
        packet = struct.pack("!HI", TX_ID, seq_nr) + chunk
        sock.sendto(packet, (DEST_IP, DEST_PORT))
        seq_nr += 1
        time.sleep(0.0005)  # kurz warten, um Überlastung zu vermeiden

    # === Last Packet ===
    # MD5-Hash der Originaldatei, damit der Empfänger die Integrität überprüfen kann
    md5_hash = hashlib.md5(file_data).digest()
    # Aufbau: [TX_ID (2B)][SeqNr (4B)][MD5 (16B)]
    last_packet = struct.pack("!HI", TX_ID, seq_nr) + md5_hash
    sock.sendto(last_packet, (DEST_IP, DEST_PORT))

    end_time = time.time()
    duration = end_time - start_time
    size_kb = len(file_data) / 1024
    throughput = size_kb / duration

    print(f"Datei gesendet in {duration:.2f} Sekunden")
    print(f"Durchsatz: {throughput:.2f} kB/s")
    print(f"Gesendete Pakete: {seq_nr + 1} (inkl. First & Last)")

    # Socket schließen
    sock.close()

if __name__ == "__main__":
    send_file()

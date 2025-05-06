#!/usr/bin/env python3
import socket
import sys
import math
import hashlib
import os

def main():
    if len(sys.argv) != 5:
        print("Verwendung: python3 Transmitter_TX.py <EmpfängerIP> <SendungsID> <DatenGroesseBytes> <MaxDatenBytesProPaket>")
        sys.exit(1)

    empfaenger_ip       = sys.argv[1]
    sendungs_id         = int(sys.argv[2])
    daten_groesse       = int(sys.argv[3])
    paket_groesse       = int(sys.argv[4])

    # --- 0) Daten generieren ---
    # Erzeuge ein Byte-Array zufälliger Daten in der gewünschten Länge
    daten = os.urandom(daten_groesse)

    # MD5-Prüfsumme über alle Daten
    md5_pruefsumme = hashlib.md5(daten).digest()

    # Berechne Anzahl der Datenpakete sowie Gesamtsequenz
    anzahl_daten_pakete = math.ceil(len(daten) / paket_groesse)
    max_seq             = anzahl_daten_pakete + 2  # +1 Header +1 Footer

    # UDP-Socket anlegen
    sockel = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)

    # --- 1) Erstes Paket (Seq=0) ---
    # Erzeuge virtuellen Dateinamen für den Receiver
    dateiname   = f"gen_{daten_groesse}B.bin"
    name_bytes  = dateiname.encode('utf-8')

    header = (
            sendungs_id.to_bytes(2, 'big') +
            (0).to_bytes(4, 'big') +
            max_seq.to_bytes(4, 'big') +
            len(name_bytes).to_bytes(4, 'big') +
            name_bytes
    )
    sockel.sendto(header, (empfaenger_ip, 5005))
    print(f"[TX] Erstes Paket gesendet (virtuelle Datei: {dateiname})")

    # --- 2) Datenpakete Seq = 1 … max_seq-2 ---
    versatz = 0
    for seq in range(1, max_seq - 1):
        teil = daten[versatz:versatz + paket_groesse]
        paket = (
                sendungs_id.to_bytes(2, 'big') +
                seq.to_bytes(4, 'big') +
                teil
        )
        sockel.sendto(paket, (empfaenger_ip, 5005))
        print(f"[TX] Datenpaket {seq} gesendet ({len(teil)} B)")
        versatz += paket_groesse

    # --- 3) Letztes Paket (Seq = max_seq-1) mit MD5 ---
    footer = (
            sendungs_id.to_bytes(2, 'big') +
            (max_seq - 1).to_bytes(4, 'big') +
            md5_pruefsumme
    )
    sockel.sendto(footer, (empfaenger_ip, 5005))
    print("[TX] Letztes Paket (MD5) gesendet.")

    sockel.close()

if __name__ == "__main__":
    main()

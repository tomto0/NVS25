import socket
import hashlib
import os

PORT = 5005
BUFFER_SIZE = 65536

def main():
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.bind(('', PORT))
    packets = {}
    expected_txid = None
    max_seq = None
    filename = None
    expected_md5 = None

    print("Receiver ready...")
    start = None

    while True:
        data, addr = sock.recvfrom(BUFFER_SIZE)
        if start is None:
            start = os.times()

        if len(data) < 6:
            continue
        txid = int.from_bytes(data[0:2], 'big')
        seq  = int.from_bytes(data[2:6], 'big')

        # First Packet
        if seq == 0:
            expected_txid = txid
            max_seq = int.from_bytes(data[6:10], 'big')
            name_len = int.from_bytes(data[10:14], 'big')
            filename = data[14:14+name_len].decode()
            print(f"Receiving '{filename}', maxSeq={max_seq}")

        # Last Packet
        elif seq == max_seq-1:
            expected_md5 = data[6:22]
            print(f"Last packet received (seq {seq})")

        # Data-Packet
        else:
            if txid == expected_txid:
                packets[seq] = data[6:]
                print(f"Packet {seq} stored ({len(data)-6} B)")

                # ACK send
                ack = txid.to_bytes(2, 'big') + seq.to_bytes(4, 'big')
                sock.sendto(ack, addr)

        # Check complete
        if expected_md5 and len(packets) == max_seq-2:
            # reconstruct
            hasher = hashlib.md5()
            with open(f"recv_{filename}", 'wb') as f:
                for s in range(1, max_seq-1):
                    chunk = packets[s]
                    f.write(chunk)
                    hasher.update(chunk)
            comp = hasher.digest()
            if comp == expected_md5:
                print("File received correctly (MD5 ok).")
            else:
                print("MD5 mismatch — Übertragung fehlerhaft.")
            break

    sock.close()

if __name__ == "__main__":
    main()

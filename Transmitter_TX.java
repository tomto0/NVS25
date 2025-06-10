import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

public class Transmitter_TX {
    private static final int PORT = 5005;

    public static void main(String[] args) throws Exception {
        if (args.length != 4) {
            System.err.println("Verwendung: java Transmitter_TX <EmpfängerIP> <SendungsID> <DatenGroesseBytes> <MaxDatenBytesProPaket>");
            System.exit(1);
        }
        InetAddress empfaenger = InetAddress.getByName(args[0]);
        int sendungsId    = Integer.parseInt(args[1]);
        int datenGroesse  = Integer.parseInt(args[2]);
        int paketGroesse  = Integer.parseInt(args[3]);

        // --- 0) Daten generieren ---
        byte[] daten = new byte[datenGroesse];
        new SecureRandom().nextBytes(daten);
        MessageDigest md5 = MessageDigest.getInstance("MD5");
        byte[] md5sum = md5.digest(daten);

        // Anzahl Pakete: Header (0), Daten 1…n, Footer (n+1)
        int anzahlDatenPakete = (int)Math.ceil((double)datenGroesse / paketGroesse);
        int maxSeq = anzahlDatenPakete + 2;

        DatagramSocket socket = new DatagramSocket();
        socket.setSoTimeout(1000); // 1 Sekunde Timeout

        // --- 1) Erstes Paket (Seq=0) ---
        String dateiname = "gen_" + datenGroesse + "B.bin";
        byte[] nameBytes = dateiname.getBytes("UTF-8");
        // 2 Bytes SendungsID, 4 Bytes Seq, 4 Bytes maxSeq, 4 Bytes NameLänge, NameBytes
        int headerLen = 2 + 4 + 4 + 4 + nameBytes.length;
        byte[] header = new byte[headerLen];
        int pos = 0;
        header[pos++] = (byte)(sendungsId >> 8);
        header[pos++] = (byte)(sendungsId);
        // Seq = 0
        for (int i = 3; i >= 0; i--) header[pos++] = (byte)((0 >> (8*i)) & 0xFF);
        // maxSeq
        for (int i = 3; i >= 0; i--) header[pos++] = (byte)((maxSeq >> (8*i)) & 0xFF);
        // Name-Länge
        for (int i = 3; i >= 0; i--) header[pos++] = (byte)((nameBytes.length >> (8*i)) & 0xFF);
        // Name-Bytes
        System.arraycopy(nameBytes, 0, header, pos, nameBytes.length);

        socket.send(new DatagramPacket(header, header.length, empfaenger, PORT));
        System.out.println("[TX] Erstes Paket gesendet (virtuelle Datei: " + dateiname + ")");

        // --- 2) Datenpakete Seq=1…maxSeq-2 ---
        int offset = 0;
        for (int seq = 1; seq < maxSeq - 1; seq++) {
            int len = Math.min(paketGroesse, datenGroesse - offset);
            byte[] pkt = new byte[2 + 4 + len];
            pos = 0;
            pkt[pos++] = (byte)(sendungsId >> 8);
            pkt[pos++] = (byte)(sendungsId);
            // Seq
            for (int i = 3; i >= 0; i--) pkt[pos++] = (byte)((seq >> (8*i)) & 0xFF);
            // Daten
            System.arraycopy(daten, offset, pkt, pos, len);

            boolean acked = false;
            while (!acked) {
            socket.send(new DatagramPacket(pkt, pkt.length, empfaenger, PORT));
            System.out.printf("[TX] Datenpaket %d gesendet (%d B)%n", seq, len);

            // Warten auf ACK, bis ACK empfangen oder Timeout
                try {
                    byte[] ackBuf = new byte[6];
                    DatagramPacket ackPkt = new DatagramPacket(ackBuf, ackBuf.length);
                    socket.receive(ackPkt);

                    int ackId = ((ackBuf[0] & 0xFF) << 8) | (ackBuf[1] & 0xFF);
                    int ackSeq = ((ackBuf[2] & 0xFF) << 24) | ((ackBuf[3] & 0xFF) << 16) |
                            ((ackBuf[4] & 0xFF) << 8) | (ackBuf[5] & 0xFF);

                    System.out.printf("[TX] Empf. ACK: ID=%d, SEQ=%d (erwartet: ID=%d, SEQ=%d)%n",
                            ackId, ackSeq, sendungsId, seq);

                    if (ackId == sendungsId && ackSeq == seq) {
                        acked = true;
            offset += len;
                    }
                } catch (java.net.SocketTimeoutException e) {
                    System.out.printf("[TX] Timeout bei Seq %d, wiederhole...%n", seq);
                }
            }
        }

        // --- 3) Letztes Paket (Seq=maxSeq-1) mit MD5 ---
        int lastSeq = maxSeq - 1;
        byte[] footer = new byte[2 + 4 + md5sum.length];
        pos = 0;
        footer[pos++] = (byte)(sendungsId >> 8);
        footer[pos++] = (byte)(sendungsId);
        for (int i = 3; i >= 0; i--) footer[pos++] = (byte)((lastSeq >> (8*i)) & 0xFF);
        System.arraycopy(md5sum, 0, footer, pos, md5sum.length);

        socket.send(new DatagramPacket(footer, footer.length, empfaenger, PORT));
        System.out.println("[TX] Letztes Paket (MD5) gesendet.");

        socket.close();
    }
}

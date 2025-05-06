import java.io.FileOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

public class Receiver_RX {
    private static final int PORT = 5005;
    private static final int PUF_SIZE = 65536;

    public static void main(String[] args) throws Exception {
        DatagramSocket socket = new DatagramSocket(PORT);
        Map<Integer, byte[]> speicher = new HashMap<>();
        Integer erwarteteId = null, maxSeq = null;
        String dateiname = null;
        byte[] erwartetesMd5 = null;

        System.out.println("Receiver bereit...");

        while (true) {
            byte[] buf = new byte[PUF_SIZE];
            DatagramPacket pkt = new DatagramPacket(buf, buf.length);
            socket.receive(pkt);
            byte[] data = pkt.getData();
            int len = pkt.getLength();

            // TX_ID
            int txId = ((data[0]&0xFF)<<8) | (data[1]&0xFF);
            // Seq
            int seq  = ((data[2]&0xFF)<<24)|((data[3]&0xFF)<<16)|((data[4]&0xFF)<<8)|(data[5]&0xFF);

            // Erstes Paket
            if (seq == 0) {
                erwarteteId = txId;
                maxSeq = ((data[6]&0xFF)<<24)|((data[7]&0xFF)<<16)|((data[8]&0xFF)<<8)|(data[9]&0xFF);
                int nameLen = ((data[10]&0xFF)<<24)|((data[11]&0xFF)<<16)|((data[12]&0xFF)<<8)|(data[13]&0xFF);
                dateiname = new String(data, 14, nameLen, "UTF-8");
                System.out.printf("[RX] Empfang '%s', erwarte %d Pakete%n", dateiname, maxSeq);
            }
            // Letztes Paket
            else if (maxSeq != null && seq == maxSeq - 1) {
                erwartetesMd5 = new byte[16];
                System.arraycopy(data, 6, erwartetesMd5, 0, 16);
                System.out.println("[RX] Letztes Paket empfangen.");
            }
            // Datenpaket
            else if (txId == erwarteteId) {
                int payloadLen = len - 6;
                byte[] chunk = new byte[payloadLen];
                System.arraycopy(data, 6, chunk, 0, payloadLen);
                speicher.put(seq, chunk);
                System.out.printf("[RX] Paket %d (%d B) zwischengespeichert%n", seq, payloadLen);
            }

            // Fertig, wenn alle Datenpakete + Footer da sind
            if (erwartetesMd5 != null && speicher.size() == maxSeq - 2) {
                // Datei schreiben & MD5 prüfen
                MessageDigest md5 = MessageDigest.getInstance("MD5");
                try (FileOutputStream fos = new FileOutputStream("recv_" + dateiname)) {
                    for (int s = 1; s < maxSeq - 1; s++) {
                        byte[] chunk = speicher.get(s);
                        fos.write(chunk);
                        md5.update(chunk);
                    }
                }
                byte[] actual = md5.digest();
                if (MessageDigest.isEqual(actual, erwartetesMd5)) {
                    System.out.println("[RX] Datei korrekt empfangen (MD5 OK).");
                } else {
                    System.out.println("[RX] MD5 mismatch – Übertragung fehlerhaft.");
                }
                break;
            }
        }
        socket.close();
    }
}

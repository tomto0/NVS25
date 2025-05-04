import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashMap;
import java.io.FileOutputStream;
import java.io.File;

public class Receiver_RX {

    private static final int PORT = 5005;
    private static final int BUFFER_SIZE = 1024; // maximale Paketgröße in Bytes

    public static void main(String[] args) throws Exception {
        // UDP-Socket erstellen
        DatagramSocket socket = new DatagramSocket(PORT);
        byte[] buffer = new byte[BUFFER_SIZE];

        HashMap<Integer, byte[]> packets = new HashMap<>(); // Zwischenspeicher für empfangene Pakete (key: SeqNr, value: Daten)
        int maxSeqNr = -1;
        String fileName = "received_output.bin";
        int expectedTxID = -1;
        int receivedLastSeqNr = -1;
        byte[] expectedMD5 = null;
        int receivedDataPackets = 0;

        System.out.println("Empfänger bereit...");

        long startTime = System.nanoTime();

        while (true) {
            // Paket empfangen
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            socket.receive(packet);

            // Daten aus dem Paket extrahieren
            byte[] data = Arrays.copyOf(packet.getData(), packet.getLength());

            if (data.length < 6) {
                System.err.println("Paket zu kurz, wird ignoriert.");
                continue;
            }

            // TX_ID (2 Bytes) und SeqNr (4 Bytes) extrahieren
            int txID = ((data[0] & 0xFF) << 8) | (data[1] & 0xFF);
            int seqNr = ((data[2] & 0xFF) << 24) | ((data[3] & 0xFF) << 16) |
                    ((data[4] & 0xFF) << 8) | (data[5] & 0xFF);

            // === First Packet (SeqNr = 0) ===
            if (seqNr == 0) {
                if (data.length < 14) {
                    System.err.println("Fehler: First Packet zu kurz!");
                    continue;
                }

                expectedTxID = txID;

                // maxSeqNr und Dateinamenslänge extrahieren
                maxSeqNr = ((data[6] & 0xFF) << 24) | ((data[7] & 0xFF) << 16) |
                        ((data[8] & 0xFF) << 8) | (data[9] & 0xFF);
                int nameLength = ((data[10] & 0xFF) << 24) | ((data[11] & 0xFF) << 16) |
                        ((data[12] & 0xFF) << 8) | (data[13] & 0xFF);

                if (nameLength < 8 || nameLength > 2048 || data.length < 14 + nameLength) {
                    System.err.println("Fehler: Ungültiger Dateiname im First Packet!");
                    continue;
                }

                String rawFileName = new String(data, 14, nameLength);
                fileName = new File(rawFileName).getName();
                System.out.println("Empfange Datei: " + fileName + " (max SeqNr = " + maxSeqNr + ")");

            } else {
                // Pakete mit falscher TX_ID werden ignoriert
                if (expectedTxID != -1 && txID != expectedTxID) {
                    System.out.println("Falsche TX_ID (" + txID + "), Paket ignoriert.");
                    continue;
                }

                // === Last Paket (enthält MD5) ===
                if (seqNr == maxSeqNr - 1 && data.length >= 22) {
                    expectedMD5 = Arrays.copyOfRange(data, 6, 22);
                    receivedLastSeqNr = seqNr;
                    System.out.println("Letztes Paket erhalten (SeqNr " + seqNr + ")");

                    // === Datenpakete ===
                } else {
                    if (!packets.containsKey(seqNr)) {
                        receivedDataPackets++;
                        System.out.println("Paket empfangen: SeqNr " + seqNr + " (insgesamt: " + receivedDataPackets + ")");
                    }
                    packets.put(seqNr, Arrays.copyOfRange(data, 6, data.length));
                }
            }

            // === Prüfen, ob alle Pakete vorhanden sind ===
            if (expectedMD5 != null && packets.size() == (receivedLastSeqNr - 1)) {
                System.out.println("Alle Datenpakete empfangen. Rekonstruiere Datei...");

                // Datei wiederherstellen und MD5 berechnen
                MessageDigest md5 = MessageDigest.getInstance("MD5");
                FileOutputStream fos = new FileOutputStream("recv_" + fileName);

                for (int i = 1; i < receivedLastSeqNr; i++) {
                    byte[] content = packets.get(i);
                    if (content != null) {
                        fos.write(content);
                        md5.update(content);
                    } else {
                        System.err.println("Fehlendes Paket mit SeqNr " + i);
                        fos.close();
                        socket.close();
                        return;
                    }
                }

                fos.close();

                // MD5-Hash vergleichen
                byte[] computedMD5 = md5.digest();
                if (Arrays.equals(computedMD5, expectedMD5)) {
                    System.out.println("Datei korrekt empfangen (MD5 passt).");
                } else {
                    System.out.println("MD5 stimmt NICHT überein. Übertragung fehlerhaft!");
                }

                long endTime = System.nanoTime();
                double durationSec = (endTime - startTime) / 1_000_000_000.0;
                double sizeKB = new File("recv_" + fileName).length() / 1024.0;
                double throughput = sizeKB / durationSec;

                System.out.printf("Dauer: %.2f Sekunden\n", durationSec);
                System.out.printf("Durchsatz: %.2f kB/s\n", throughput);

                break;
            }
        }
        // Socket schließen
        socket.close();
    }
}

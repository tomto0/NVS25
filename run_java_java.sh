#!/usr/bin/env bash
# run_java_java.sh — Java → Java
RECEIVER_CMD="java -cp . Receiver_RX"
TRANSMITTER_CMD="java -cp . Transmitter_TX"
OUTFILE="results_java_java_ack.txt"

RECEIVER_IP="127.0.0.1"
TX_ID=1
DATA_SIZE=1048576
PKT_SIZE=1024
RUNS=10

rm -f "$OUTFILE"
echo "# Lauf  Zeit_s  Durchsatz_kB/s" >> "$OUTFILE"

for i in $(seq 1 $RUNS); do
  printf "Starte Java->Java Lauf %02d ...\n" "$i"
  $RECEIVER_CMD > /dev/null 2>&1 &
  RPID=$!
  sleep 1

  REAL_TIME=$(
    { time -p $TRANSMITTER_CMD "$RECEIVER_IP" $TX_ID $DATA_SIZE $PKT_SIZE; } \
      2>&1 | awk '/^real/ {print $2}'
  )

  kill -9 $RPID > /dev/null 2>&1 || true
  wait $RPID 2>/dev/null || true

  THROUGHPUT=$(awk -v sz="$DATA_SIZE" -v t="$REAL_TIME" \
    'BEGIN { printf "%.2f", (sz/1024)/t }'
  )

  printf "%2d   %6s   %8s\n" "$i" "$REAL_TIME" "$THROUGHPUT" >> "$OUTFILE"
done

AVG_TIME=$(awk 'NR>1{sum+=$2}END{printf "%.3f",sum/(NR-1)}' "$OUTFILE")
AVG_TP=$(awk 'NR>1{sum+=$3}END{printf "%.2f",sum/(NR-1)}' "$OUTFILE")

echo "" >> "$OUTFILE"
echo "# Durchschnitt" >> "$OUTFILE"
echo "Zeit_s:          $AVG_TIME" >> "$OUTFILE"
echo "Durchsatz_kB/s:  $AVG_TP" >> "$OUTFILE"

echo "→ Fertig! Siehe $OUTFILE"


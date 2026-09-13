# Kakathic
set -o pipefail

num1="$TMP/url_payload_extract.log";
[ -f "$num1" ] && rm -f "$num1"

payload_extract -o "$PTSD" -X "$1" -i "$(glog url_text_payload)" 2>&1 | tee "$TMP/url_payload_extract.log" || killtree "Download network error, Please delete the file and download it again." &
PIDK=$!
while kill -0 $PIDK 2>/dev/null; do
if [ -f "$num1" ]; then
  num3=$(tr '\r' '\n' < "$num1" | grep -o '[0-9]\+%' | tail -n1 | tr -d '%')
  if [ "${num3:-0}" != 0 ]; then
  [ "${num3:-0}" == "${num4:-1}" ] || progress "$num3/100"
  num4="$num3"
  fi
fi
sleep 1
done

progress -1/0

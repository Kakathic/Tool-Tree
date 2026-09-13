#!/data/data/com.tool.tree/files/home/bin/bash
# Kakathic

home() {
menuadd "$MPAT"
echo '
[[group]]
title = "'$google_text'"

  [[action]]
  shell = "hidden"
  reload = "true"
  title = "'$payload_text_1'"
  summary = "'$payload_text_2' http://...'$(glog url_text_payload | tail -c 25)'"
  script = "slog url_text_payload \"$url_text_payload\""

    [[action.params]]
    name = "url_text_payload"
    placeholder = "https://web.com/rom-payload-ota.zip"
    value-sh = "glog url_text_payload"
    type = "text"
    required = "required"

[[group]]

  [[action]]
  visible = "echo '$checkdjhrh'"
  reload = "true"
  title = "'$payload_text_3'"
  summary = "'$payload_text_4' '$PTSD'"
  script = """
  echo "Downloading..."
  echo
  for vv in $partition; do
      '$MPAT'/payload.bash $vv
  done
  echo
  echo "'$payload_text_4' $PTSD"
  echo
  checktime
  """

    [[action.params]]
    name = "partition"
    desc = "'$payload_text_5'"
    multiple = "multiple"
    options-sh = "cat '$MPAT'/list_payload"
    required = "required"
'
}

# Xử lý payload
if [ -n "$(glog url_text_payload)" ]; then
    checkdjhrh=1
    if [ "$(glog url_text_payload | checksum)" != "$(glog url_text_payload_md5)" ]; then
    listpayload "$(glog url_text_payload)" | awk '{print $1"|"$1" "$2}' > $MPAT/list_payload
    slog url_text_payload_md5 "$(glog url_text_payload | checksum)"
    fi
else
    checkdjhrh=0
fi

# Ngôn ngữ & Google dịch
source langadd "${0%/*}"
"$@"

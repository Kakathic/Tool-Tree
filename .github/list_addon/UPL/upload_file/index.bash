#!/data/data/com.tool.tree/files/home/bin/bash
# kakathic

# home
home() {
menuadd "$MPAT"
echo '[[group]]
title = "'$google_text'"

  [[action]]
  title = "Upload Gofile"
  script = """
  set -o pipefail
  if [ -f "$FILE" ]; then
      urls="$(xem https://api.gofile.io/servers | jq -r .data.servers[0].name)"
      [ -z "$urls" ] && urls="upload.gofile.io" || urls="$urls.gofile.io"
      echo "'$gofile_text_1' $urls"
      echo
      curl -L -H "$WEBS" -F "file=@$FILE" "https://$urls/contents/uploadfile" > $TMP/Uploadgo.log || killtree "'$gofile_text_2'"
      jq -r . $TMP/Uploadgo.log
      echo
      checktime
  else
      echo "'$gofile_text_5'"
  fi
  """

    [[action.params]]
    name = "FILE"
    type = "file"
    required = "true"
    desc = "https://gofile.io"

  [[action]]
  title = "Upload Pixeldrain"
  script = """
  set -o pipefail
  [ "$TEXT" ] || echo "'$gofile_text_6'"
  slog tocken_key_upload_free "$TEXT"
  if [ -f "$FILE" ]; then
      echo "'$gofile_text_1' pixeldrain.com"
      echo
      curl -T "$FILE" -u ":$TEXT" https://pixeldrain.com/api/file | jq -r .id | awk '"'{print \"https://pixeldrain.com/u/\"\$1}'"' | tee $TMP/Upload.log || killtree "'$gofile_text_2'"
      echo
      echo "'$gofile_text_3' $(cat "$TMP/Upload.log")"
      echo
      checktime
  else
      echo "'$gofile_text_5'"
  fi
  """

    [[action.params]]
    name = "FILE"
    type = "file"
    required = "true"
    desc = "https://pixeldrain.com"

    [[action.params]]
    name = "TEXT"
    label = "Token"
    desc = "Token: xxx-xxx-xxx-xxx-xxx"
    value-sh = "glog tocken_key_upload_free"
    type = "text"
'
}

# Ngôn ngữ & Google dịch
source langadd "${0%/*}"
"$@"

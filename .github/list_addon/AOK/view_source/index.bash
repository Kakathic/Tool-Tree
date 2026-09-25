#!/data/data/com.tool.tree/files/home/bin/bash
# kakathic

# home
home() {
menuadd "$MPAT"
echo '
  [[group]]
  title = "'$google_text'"
  
  [[menu]]
  [[menu.items]]
  title = "'$view_source_text_16'"
  reload = true
  silent = true
  script = "rm -fr $BIN/jadx"
    
  [[download]]
  title = "'$view_source_text_14' jadx"
  desc = "'$view_source_text_15'"
  reload = true
  support = "[ -f $BIN/jadx ] || echo 1"
  url-sh = "'$MPAT'/index.bash jadx_link"
  script = "'$MPAT'/index.bash jadx_install $state"

  [[group]]
  [[action]]
  title = "Jadx"
  desc = "'$view_source_text_11'"
  warn = "'$view_source_text_10'"
  support = "[ -f $BIN/jadx ] && echo 1"
  script = """
  [ "$no_res" == 1 ] && no_res_v="-r"
  [ "$no_dex" == 1 ] && no_dex_v="-s"
  jadx $no_res_v $no_dex_v -j 4 -d "${FILE%.*}_jadx" "$FILE" || killtree "'$view_source_text_13'"
  echo
  echo "'$view_source_text_12': ${FILE%.*}_jadx"
  echo
  checktime
  """
    
    [[action.params]]
    name = "no_res"
    title = "'$view_source_text_9'"
    label = "'$view_source_text_8'"
    type = "switch"

    [[action.params]]
    name = "no_dex"
    label = "'$view_source_text_7'"
    type = "switch"
    
    [[action.params]]
    name = "FILE"
    title = "'$view_source_text_6'"
    desc = "'$view_source_text_5': apk, dex, jar, class, smali, zip, aar, arsc, aab, xapk, apkm"
    type = "file"
    required = true
    editable = true
  '
}

jadx_link(){
xem "https://api.github.com/repos/skylot/jadx/releases/latest" | jq -r ".assets[0].browser_download_url // empty"
}

jadx_install(){
if [ -f "$1" ]; then
unzip -oqj "$1" bin/jadx -d "$BIN"
unzip -oqj "$1" lib/jadx*.jar -d "$LIB"
sed -i "s|#!/usr/bin/env sh|#!/data/data/com.tool.tree/files/home/bin/bash|" "$BIN/jadx"
chmod 755 "$BIN/jadx"
rm -rf "$1"
fi
}

# Ngôn ngữ & Google dịch
source langadd "${0%/*}"
"$@"

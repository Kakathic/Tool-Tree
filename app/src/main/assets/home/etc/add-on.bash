#!/data/data/com.tool.tree/files/home/bin/bash

# icon load
if [ "$(glog Ticon)" != 1 ]; then
urlicon="$ETC/icon"
fi

# Ngôn ngữ
source language 2>/dev/null

echo '
  [[group]]
  [[menu]]
  [[menu.items]]
  title = "'$customize_text'"
  get = "glog show_setting_add 1"
  reload = true
  silent = true
  type = "checkbox"
  script = """
  if [ "$(glog show_setting_add)" == 1 ]; then
  slog show_setting_add 0
  else
  slog show_setting_add 1
  fi
  """
  
  [[group]]
  [[fab]]
  handler = """
  [ "$menu_id" == "file" ] && installadd "$file" "'$1'"
  """
  
  [[fab.items]]
  key = "file"
  type = "file"
  title = "'$input_add_text'"
  suffix = "add,zip,7z"
  reload = true
'

Download() {
  if [ "$url" ]; then
  echo '[[group]]
  [[download]]
  '$croot_add'
  icon = "'$icon_vb'"
  title = "'$name'"
  summary = "'$sumstxt'"
  reload = true
  url = "'$url'"
  script = """
  installadd "$state" "'${dirvad%/*}'"
  """'
  
    if [ "$(glog show_setting_add)" == 1 ]; then
      echo '
      [[download.rows]]
      toggle = "checkbox"
      line = true
      align="right"
      text = "'$hide_add_text'"
      get = "[ -f '$dirvad'/hide ] && echo 1"
      set = """
      if [ -f '$dirvad'/hide ]; then
      rm '$dirvad'/hide
      else
      touch '$dirvad'/hide
      fi
      """
      '
    else
      echo '
      [[download.rows]]
      text = "'$description'"
      margin-top = 4
      line = true
      '
    fi
  fi
}

Homeadd() {

  # Load index
  if [ -f "$dirvad/index.bash" ]; then
  pagesh='config-sh = "'$dirvad'/index.bash home"'
  elif [ -f "$dirvad/index.toml" ]; then
  pagesh='config = "'$dirvad'/index.toml"'
  else
  pagesh='config = "'$ETC'/error.toml"'
  fi

  if [ -f "$dirvad/before-load.bash" ]; then
  beforesh='before-load = "'$dirvad'/before-load.bash '$dirvad'"'
  fi

  if [ "$(glog show_setting_add)" == 1 ]; then
    hinde_add='[[page.rows]]
    toggle = "checkbox"
    text = "'$hide_add_text'"
    get = "[ -f '$dirvad'/hide ] && echo 1"
    align="right"
    line = true
    set = """
    if [ -f '$dirvad'/hide ]; then
    rm '$dirvad'/hide
    else
    touch '$dirvad'/hide
    fi
    """
    '
    
    [ -f "$dirvad/nodelete" ] || delete_add='[[page.rows]]
    toggle = "switch"
    toast = true
    text = "'$deleted_text'"
    get = "[ -f '$dirvad'/delete ] && echo 1"
    set = """
    if [ -f '$dirvad'/delete ]; then
    rm '$dirvad'/delete
    else
    touch '$dirvad'/delete
    echo "'$addon_text_2'"
    fi
    """
    '
  else
    text_line='[[page.rows]]
    text = "'$description'"
    margin-top = 4
    line = true'
  fi
  
  # Danh sách Add-on
  echo '
    [[group]]
    [[page]]
    title = "'$name'"
    summary = "'$sumstxt'"
    icon = "'$icon_vb'"
    process = "'$process'"
    '$croot_add'
    '$shortcut_text'
    '$pagesh'
    '$beforesh'
    '"$text_line"'
    '"$hinde_add"'
    '"$delete_add"'
  '
}

Vips() {
  
  # Xoá giá trị cũ
  id= root= shortcut= description= url= name=
  beforesh= croot_add= process= sumstxt=
  hinde_add= shortcut_text= delete_add=
  
  # Nạp string
  source "$vadd" 2>/dev/null
  [ "$id" ] || continue
  
  # Phát hiện root
  if [ "$root" == "true" ]; then
  [ "$ROT" == 1 ] sumstxt="$version $author" || sumstxt="$text_root"
  croot_add='lock = "'$LOT'|'$root_warning_text'"'
  else
  sumstxt="$version $author"
  fi

  # Phát hiện tính năng
  if [ "$shortcut" == "true" ]; then
  shortcut_text='key = "'$id'" '
  fi
  
  if [ "$(glog Ticon)" != 1 ]; then
    if [ -f "$dirvad/icon.png" ]; then
    icon_vb="$dirvad/icon.png"
    else
    icon_vb="$urlicon/icon.png"
    fi
  fi

  # Load trang danh sách
  if [ -f "$dirvad/delete" ]; then
    # Xoá Add-on
    [ -f "$dirvad/uninstall.bash" ] && $dirvad/uninstall.bash
    find "$dirvad" -maxdepth 1 ! -path "$dirvad" \
    ! -name 'download.bash' ! -exec rm -rf {} +
    else
    if [[ -f "$dirvad/index.bash" || -f "$dirvad/index.toml" ]]; then
      if [[ ! -f "$dirvad/hide" || "$(glog show_setting_add)" == 1 ]]; then
      Homeadd
      fi
    elif [ -f "$dirvad/download.bash" ]; then
      if [[ ! -f "$dirvad/hide" || "$(glog show_setting_add)" == 1 ]]; then
      Download
      fi
    fi
  fi

}

# Load trang add-on có pin trước
for vadd in $1/*/Add-on.bash; do
  [ -f "$vadd" ] || continue
  dirvad="${vadd%/*}"
  [ -f "$dirvad/pin" ] || continue
  if [[ -f "$dirvad/index.bash" || -f "$dirvad/index.toml" ]]; then
  Vips
  fi
done

# Load trang không có pin
for vadd in $1/*/Add-on.bash; do
  [ -f "$vadd" ] || continue
  dirvad="${vadd%/*}"
  [ -f "$dirvad/pin" ] && continue
  if [[ -f "$dirvad/index.bash" || -f "$dirvad/index.toml" ]]; then
  Vips
  fi
done

# Load trang tải xuống ở dưới cùng
for vadd in $1/*/download.bash; do
  [ -f "$vadd" ] || continue
  dirvad="${vadd%/*}"
  if [[ -f "$dirvad/index.bash" || -f "$dirvad/index.toml" ]]; then
  continue
  fi
  Vips
done

#!/data/data/com.tool.tree/files/home/bin/bash
# Kakathic

show_sett() {
  echo '
  [[group]]
  [[action]]
  shell = "hidden"
  reload = true
  menu = true
  title = "'$input_folder_text'"
  desc = "'$path_text': '$PTSD'"
  script = """
  if [ ! -d "$SDC/$Name" ] || [ ! -d "$SDH/$Name" ]; then
    slog PTSD "$SDC/$Name"
    slog PTSH "$Name"
    mkdir -p "$SDH/$Name" "$SDC/$Name/out"
  elif [ -d "$SDH/$Name" ]; then
    slog PTSH "$Name"
    slog PTSD "$SDC/$Name"
  fi
  """

  [[action.params]]
  name = "Name"
  desc = "'$config_text_1'"
  label = "'$option_text'"
  title = "'$projects_text'"
  options-sh = "findfile for $SDH"
  value-sh = "glog PTSH"
  editable = true
'
}

show_apkset() {
  echo '
  [[group]]
  [[action]]
  shell = "hidden"
  reload = true
  menu = true
  title = "'$input_folder_text'"
  desc = "'$path_text': '$PTAD'"
  script = """
  if [ ! -d "$SDC/$Name" ] || [ ! -d "$APK/$Name" ]; then
    slog PTAD "$SDC/$Name"
    slog PTAH "$Name"
    mkdir -p "$APK/$Name" "$SDC/$Name/out"
  elif [ -d "$APK/$Name" ]; then
    slog PTAH "$Name"
    slog PTAD "$SDC/$Name"
  fi
  """

  [[action.params]]
  name = "Name"
  desc = "'$config_text_1'"
  label = "'$option_text'"
  title = "'$projects_text'"
  options-sh = "findfile for $APK"
  value-sh = "glog PTAH"
  editable = true
  '
}

shell_bash() {
  echo '[[group]]
  [[editor]]
  title = "'$home_text_5'"
  desc = "'$more_text_9'"
  file = "home/usr/run_'$1'.bash"
  need-input = "true"
  placeholder = "#!/data/data/com.tool.tree/files/home/bin/bash"
  icon = "'$urlicon'/shell.png" '
}

inforkk() {
echo '
  [[group]]
  [[text.rows]]
  text = "'$infor_text' × '$system_text'"
  size = 16
  bold = true
  alpha = 1
  line-height = 1.3
  margin-top = 6
  
  [[text.rows]]
  size = 13
  text = "'$root_text':"
  bold = true
  icon = "'$urlicon'/1shield.png"
  line-height = 1.3
  break = true
  
  [[text.rows]]
  bold = true
  size = 13
  color = "#0dbda2"
  text = "'${ROOT^}'"
  
  [[text.rows]]
  text = "'$device_text':"
  break = true
  size = 13
  bold = true
  line-height = 1.3
  icon = "'$urlicon'/1smart.png"
  
  [[text.rows]]
  bold = true
  size = 13
  color = "#0dbda2"
  text = "'$ANDROID_BRAND' - '$ANDROID_DEVICE'"
  
  [[text.rows]]
  text = "'$operating_system':"
  break = true
  size = 13
  bold = true
  line-height = 1.3
  icon = "'$urlicon'/1android.png"
  
  [[text.rows]]
  bold = true
  size = 13
  color = "#0dbda2"
  text = "Android '$ANDROID_RELEASE' - SDK '$API'"
  
  [[text.rows]]
  text = "'$microprocessors':"
  break = true
  size = 13
  bold = true
  line-height = 1.3
  icon = "'$urlicon'/1cpu.png"
  
  [[text.rows]]
  bold = true
  size = 13
  color = "#0dbda2"
  text = "'${CPU_ABI^}'"
  margin-bottom = 6
  '
}

(
  # Tạo thư mục
  [ -d $PTAD/out ] && mkdir -p $PTAD/out &>/dev/null
  [ -d $PTSD/out ] && mkdir -p $PTSD/out &>/dev/null
  # Dịch ngôn ngữ
  if [[ "$1" == "Home" || "$1" == "More" ]]; then
  auto_trans &>/dev/null
  fi
) &

# Ngôn ngữ
source language 2>/dev/null

# icon load
if [ "$(glog Ticon)" != 1 ]; then
urlicon="$ETC/icon"
fi

# Văn bản
Home() {
  inforkk
  
  echo '
  [[group]]
  [[page]]
  title = "'$setting_text'"
  desc = "'$home_text_1'"
  icon = "'$urlicon'/settings.png"
  config-sh = "'$ETC'/tool-tree.bash Info"

  [[group]]
  [[page]]
  title = "'$editor_rom'"
  desc = "'$home_text_2'"
  icon = "'$urlicon'/utilities.png"
  config-sh = "'$ETC'/tool-tree.bash Utilities"

  [[group]]
  [[page]]
  title = "'$tools_text'"
  desc = "'$home_text_3'"
  icon = "'$urlicon'/tools.png"
  config-sh = "'$ETC'/tool-tree.bash Root"

  [[group]]
  [[page]]
  title = "'$addon_text'"
  desc = "'$home_text_4'"
  icon = "'$urlicon'/addon.png"
  process = true
  config-sh = "PATHADD=\"$AON\" '$ETC'/tool-tree.bash Addon"
  '

  [ "$(glog shellc)" == 1 ] && shell_bash shellc
}

More() {
  inforkk
  
  echo '
  [[group]]
  [[page]]
  title = "'$setting_text'"
  desc = "'$home_text_1'"
  icon = "'$urlicon'/settings.png"
  config-sh = "'$ETC'/tool-tree.bash Info"

  [[group]]
  [[page]]
  title = "'$editor_apk'"
  desc = "'$more_text_6'"
  icon = "'$urlicon'/apk_utility.png"
  config-sh = "'$ETC'/tool-tree.bash Utiliapk"
  lock = "[ -f $HOME/check_unpack ] && echo \"'$boot_text_1'\" || echo 0"

  [[group]]
  [[page]]
  title = "'$utilities_text'"
  desc = "'$more_text_7'"
  icon = "'$urlicon'/tool_apk.png"
  config-sh = "'$ETC'/tool-tree.bash Troot"

  [[group]]
  [[page]]
  title = "'$plugin_text'"
  desc = "'$more_text_8'"
  icon = "'$urlicon'/apk_addon.png"
  process = true
  config-sh = "PATHADD=\"$AOK\" '$ETC'/tool-tree.bash Addon"
  '

  [ "$(glog shellc)" == 1 ] && shell_bash shells
}

Info() {
  echo '
  
  [[group]]
  [[menu]]
    handler = """
    if [ "$menu_id" == "v1" ]; then
    echo "am:[start -a android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS -d package:com.tool.tree]"
    elif [ "$menu_id" == "v2" ]; then
    echo "am:[start -a android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION -d package:com.tool.tree]"
    elif [ "$menu_id" == "v3" ]; then
    echo "am:[start -a android.settings.APPLICATION_DETAILS_SETTINGS -d package:com.tool.tree]"
    fi
  """
  
    [[menu.items]]
    key = "v1"
    title = "'$permis_text_1'"
    silent = true

    [[menu.items]]
    key = "v2"
    title = "'$permis_text_4'"
    silent = true

    [[menu.items]]
    key = "v3"
    title = "'$setting_text_5'"
    silent = true
  
  [[group]]
  [[page]]
  title = "'$setting_text_1'"
  desc = "'$setting_text_2'"
  icon = "'$urlicon'/info.png"
  config-sh = "'$ETC'/tool-tree.bash Update"
  process = true
  
  [[group]]
  [[page]]
  title = "'$setting_text_3'"
  desc = "'$setting_text_4'"
  icon = "'$urlicon'/project.png"
  config-sh = "'$ETC'/tool-tree.bash Project"
    
  [[group]]
  [[page]]
  title = "'$setting_text_7'"
  desc = "'$setting_text_8'"
  icon = "'$urlicon'/feature.png"
  config-sh = "'$ETC'/tool-tree.bash Feature"
  
  [[group]]
  [[picker]]
  title = "'$permis_text_2'"
  desc = "'$permis_text_5'"
  icon = "'$urlicon'/language.png"
  option-sh = """
  echo -e "|'$default_text'\nai|Gemini\nen|English\nvi|Việt nam\nru|Русский\nhu|Hungarian\nid|Indonesia\nes|Spanish"
  """
  get = "glog language_kkts"
  set = """
    if [ "$state" == "ai" ]; then
      if transai -c; then
        slog language_kkts "$state"
        auto_trans
        echo "exit:[restart]"
        exit
      fi
    else
      slog language_kkts "$state"
      [ -f $ETC/lang/auto.sh ] && rm -fr $ETC/lang/auto.sh
      slog language "$state"
      echo "exit:[restart]"
      exit
    fi
  """

  [[group]]
  [[editor]]
  id = "shella"
  title = "'$home_text_5'"
  desc = "'$home_text_6'"
  icon = "'$urlicon'/shella.png"
  file = "home/usr/run_shella.bash"
  need-input = true
  placeholder = "#!/data/data/com.tool.tree/files/home/bin/bash"
  '
}

Update() {
  {
  if [ ! -f $TMP/update ]; then
  check_update
  fi
  if [ -f $TMP/update ]; then
  url_dowload="$(cat $TMP/update)"
  show_update=1
  desc_xx="$sizes_text: $(cat $TMP/size)"
  title_xx="$update_text"
  elif [ "$(glog gg_beta)" == 1 ]; then
  url_dowload="https://github.com/Kakathic/Tool-Tree/releases/download/beta/Tool-Tree-beta.apk"
  title_xx="$download_text beta"
  show_update=1
  fi
  if [ "$(glog gg_trans_ver)" == 1 ]; then
    link_vers="version_transai.txt"
    shum_vers="$(checksum $TEMP/version.txt)"
    if [[ "$shum_vers" != "$(glog shum_vers)" ]]; then
      transai "$(cat $TEMP/version.txt)" > $TEMP/$link_vers
      if [ -n "$(cat $TEMP/$link_vers)" ]; then
      slog shum_vers "$shum_vers"
      fi
    fi
  fi
  if [ -z "$link_vers" ]; then
  link_vers="version.txt"
  fi
  } &>/dev/null
  echo '
  [[group]]
  [[menu]]
  handler = """
  if [ "$menu_id" == "share" ]; then
    echo "am:[start -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT https://Kakathic.github.io/Tool-Tree]"
  elif [ "$menu_id" == "data" ]; then
    slog boot_ver_code 1
    slog sum_onl_plugin 1
    slog sum_moduls 1
  fi
  """
    
    [[menu.items]]
    title = "'$download_text' beta"
    get = "glog gg_beta"
    reload = true
    silent = true
    type = "checkbox"
    script = """
    if [ "$(glog gg_beta)" == 1 ]; then
    slog gg_beta 0
    else
    slog gg_beta 1
    fi
    """
    
    [[menu.items]]
    title = "Gemini"
    get = "glog gg_trans_ver"
    reload = true
    silent = true
    type = "checkbox"
    script = """
    if [ "$(glog gg_trans_ver)" == 1 ]; then
    slog gg_trans_ver 0
    else
    slog gg_trans_ver 1
    fi
    """
    
    [[menu.items]]
    key = "share"
    title = "'$share_text'"
    silent = true
    
    [[menu.items]]
    key = "data"
    auto-kill = true
    silent = true
    title = "'$reset_data_text'"
    
  [[group]]
  [[fab]]
  [[fab.items]]
  type = "refresh"
  icon = "'$ETC'/icon/Loading.png"
  
  [[group]]
  [[page]]
  title = "'$author_text'"
  icon = "'$urlicon'/like.png"
  html = "https://Kakathic.github.io/Tool-Tree/website/Information.html"

  [[group]]
  [[page]]
  title = "'$update_text_5'"
  icon = "'$urlicon'/website.png"
  html = "https://Kakathic.github.io/Tool-Tree"

  [[group]]
  [[page]]
  title = "Telegram"
  icon = "'$urlicon'/telegram.png"
  html = "https://t.me/tooltree"

  [[group]]
  [[download]]
  title = "'$title_xx'"
  desc = "'$desc_xx'"
  icon = "'$urlicon'/update.png"
  support = "'$show_update'"
  url = "'$url_dowload'"
  script = """
  openfile "$state"
  slog -d gg_beta
  """

  [[text]]
  desc = """'"$(cat $TEMP/$link_vers 2>/dev/null)"'"""
  [[text.rows]]
  photo = "'$ETC'/icon/tool-tree.jpg"
  '

}

Project() {

  echo '
  [[group]]
  [[menu]]
  [[menu.items]]
  type = "refresh"
  title = "'$refresh_text'"
  
  [[group]]
  [[action]]
  shell = "hidden"
  reload = true
  icon = "'$urlicon'/folder_rom.png"
  title = "'$folder_text'"
  desc = "'$path_text': '$SDC'"
  script = """
  if [ ! -d "$FOLDER" ]; then
  slog SDC "$FOLDER"
  mkdir -p "$FOLDER"
  fi
  """
  
  [[action.params]]
  name = "FOLDER"
  label = "'$folder_text'"
  desc = "'$config_text_2'"
  value-sh = "glog SDC"
  type = "folder"
  required = true
    
  [[action]]
  title = "'$project_text_3'"
  icon = "'$urlicon'/cleanup.png"
  warn = "'$project_text_4'"
  auto-off = true
  script = """
    for vl in $dels; do
    echo "Deleting the folder: $vl"
    rm -fr "$vl"
    done
  """
  
    [[action.params]]
    name = "dels"
    label = "'$option_text'"
    options-sh = "findfile folders $SDH/$PTSH; findfile folders $APK/$PTAH"
    multiple = true

  [[group]]
  [[action]]
  title = "'$customize_tools_text'"
  icon = "'$urlicon'/list_tool.png"
  shell = "hidden"
  reload = true
  script = """
    slog un_tool_ext4 "$un_tool_ext4"
    slog un_tool_erofs "$un_tool_erofs"
    slog un_tool_f2fs "$un_tool_f2fs"
    slog re_tool_ext4 "$re_tool_ext4"
    slog re_tool_erofs "$re_tool_erofs"
    slog re_tool_f2fs "$re_tool_f2fs"
  """
  
    [[action.params]]
    name = "un_tool_ext4"
    title = "'$tool_unpack_text' ext4"
    label = "'$option_text'"
    value-sh = "glog un_tool_ext4 0"
    options-sh = "echo -e \"0|imgextractor\n1|imgkit_scuti\""

    [[action.params]]
    name = "un_tool_erofs"
    title = "'$tool_unpack_text' erofs"
    label = "'$option_text'"
    value-sh = "glog un_tool_erofs"
    options-sh = "echo -e \"0|extract.erofs\n1|imgkit_scuti\""

    [[action.params]]
    name = "un_tool_f2fs"
    title = "'$tool_unpack_text' f2fs"
    label = "'$option_text'"
    value-sh = "glog un_tool_f2fs 0"
    options-sh = "echo -e \"0|extract.f2fs\n1|imgkit_scuti\""

    [[action.params]]
    name = "re_tool_ext4"
    title = "'$tool_repack_text' ext4"
    label = "'$option_text'"
    value-sh = "glog re_tool_ext4 1"
    options-sh = "echo -e \"0|make_ext4fs\n1|mke2fs+e2fsdroid\n2|imgkit_scuti\""

    [[action.params]]
    name = "re_tool_erofs"
    title = "'$tool_repack_text' erofs"
    label = "'$option_text'"
    value-sh = "glog re_tool_erofs"
    options-sh = "echo -e \"0|mkfs.erofs\n1|imgkit_scuti\""

    [[action.params]]
    name = "re_tool_f2fs"
    title = "'$tool_repack_text' f2fs"
    label = "'$option_text'"
    value-sh = "glog re_tool_f2fs 0"
    options-sh = "echo -e \"0|sload_f2fs\""
  '

  for vvsskk in $SDH/$PTSH/*; do
  [[ -d "$vvsskk" ]] && namept="${vvsskk##*/}" || continue
  [[ "$namept" == *config* || "$namept" == *raw* ]] && continue
  sload+='if [[ -z "$name_'$namept'" || "$name_'$namept'" == 0 ]]; then
  rm -rf $SDH/$PTSH/config/'$namept'_size.txt
  else
  echo "$name_'$namept'" > $SDH/$PTSH/config/'$namept'_size.txt
  fi
  '
  vbload+='
  [[action.params]]
  name = "name_'$namept'"
  type = "number"
  placeholder = "0"
  label = "'$namept'"
  value-sh = "cat $SDH/$PTSH/config/'$namept'_size.txt 2>/dev/null"
  '
  done
  
  if [ -n "$(ls -1d "$SDH/$PTSH"/* 2>/dev/null | grep -vE '/(raw|config)')" ]; then
  echo '
  [[group]]
  [[action]]
  title = "'$custom_size'"
  warn = "'$custom_size_desc'"
  icon = "'$urlicon'/size_icon.png"
  shell = "hidden"
  reload = true
  script = """
  '"$sload"'
  """
  '"$vbload"'
  '
  fi
  
}

Feature() {
  echo '
  [[group]]
  [[menu]]
  [[menu.items]]
  link = "https://aistudio.google.com/api-keys"
  title = "'$generate_text' Gemini API"
  silent = true
  
  [[group]]
  [[switch]]
  title = "'$project_text_5'"
  icon = "'$urlicon'/set_home.png"
  shell = "hidden"
  auto-restart = true
  get = "glog Tset"
  set = "slog Tset $state"

  [[switch]]
  title = "'$project_text_7'"
  icon = "'$urlicon'/icon_off.png"
  shell = "hidden"
  auto-restart = true
  get = "glog Ticon"
  set = "slog Ticon $state"

  [[switch]]
  title = "'$project_text_6'"
  icon = "'$urlicon'/shell_off.png"
  shell = "hidden"
  auto-restart = true
  get = "glog shellc"
  set = "slog shellc $state"

  [[group]]
  [[action]]
  title = "'$project_text_10'"
  icon = "'$urlicon'/java.png"
  warn = "'$project_text_9'"
  shell = "hidden"
  script = "slog ramoccupied \"$ramoccupied\""

    [[action.params]]
    name = "ramoccupied"
    label = "'$option_text'"
    value-sh = "glog ramoccupied 4096"
    options-sh = "echo -e \"512\n1024\n2048\n3072\n4096\n5120\n6144\n7168\n8192\""

  [[group]]
  [[action]]
  title = "'$project_text_12'"
  icon = "'$urlicon'/cpu.png"
  warn = "'$project_text_13'"
  shell = "hidden"
  support = "command -v taskset &>/dev/null && echo 1"
  script = "slog use_cpu \"$use_cpus\""
  
    [[action.params]]
    name = "use_cpus"
    label = "'$option_text'"
    value-sh = "glog use_cpu"
    options-sh = "seq 1 $(nproc --all)"

  [[group]]
  [[action]]
  title = "'$project_text_14'"
  icon = "'$urlicon'/background.png"
  warn = "'$project_text_15'"
  shell = "hidden"
  auto-restart = true
  script = """
  slog dissblur "$dissblur"
  slog directbg "$directbg"
  slog uri_change_background "$uri_change_background"
  [ -f "$uri_change_background" ] && cp -f "$uri_change_background" "$ETC/wallpaper.jpg"
  [ -z "$uri_change_background" ] && rm -f "$ETC/wallpaper.jpg"
  set_permis "$ETC/wallpaper.jpg" &>/dev/null
  """

    [[action.params]]
    name = "dissblur"
    label = "'$dissblur_text'"
    type = "switch"
    value-sh = "glog dissblur"

    [[action.params]]
    name = "directbg"
    label = "'$directbg_text'"
    type = "switch"
    value-sh = "glog directbg"

    [[action.params]]
    name = "uri_change_background"
    type = "file"
    suffix = "jpg"
    editable = true
    value-sh = "glog uri_change_background"
  
  [[group]]
  [[action]]
  title = "'$api_key_text'"
  icon = "'$urlicon'/apikey.png"
  shell = "hidden"
  script = """
    [ -z "$models_genmini" ] && slog -d models_genmini || slog models_genmini "$models_genmini"
    [ -z "$api_genmini" ] || slog api_genmini "$(tokenenc "$api_genmini")"
    transai -c || slog -d api_genmini
  """
  
  [[action.params-rows]]
  text = "'$note_genmini_text':"
  line = true
  
  [[action.params-rows]]
  text = "'$generate_text' Gemini API"
  link = "https://aistudio.google.com/api-keys"
  underline = true
  
    [[action.params]]
    name = "api_genmini"
    title = "Gemini API"
    placeholder = "*******************"
    type = "text"
    desc-sh = "transai -c 2>&1"
    
    [[action.params]]
    name = "models_genmini"
    placeholder = "gemini-3.1-flash-lite"
    title = "Models Gemini"
    editable = true
    label = "Models"
    items = ["gemini-3.5-flash-lite", "gemini-3.1-flash-lite"]
    value-sh = "glog models_genmini \"gemini-3.1-flash-lite\""
    

  '
}

Root() {
  echo '
  [[group]]
  [[action]]
  title = "'$mount_text_1'"
  summary = "'$show_root_text'"
  icon = "'$urlicon'/mount.png"
  lock = "'$LOT'|'$root_warning_text'"
  interruptible = false
  script = """
    for kkh in $IMG_NAME; do
    if [ "$(ls $SDH/raw/${kkh%.*} 2>/dev/null)" ]; then
        su -mm -c umount -l $SDH/raw/${kkh%.*}
    fi
    mkdir -p $SDH/raw/${kkh%.*}
    if [ "$(checktype $PTSD/$kkh)" == "erofs" ]; then
    su -mm -c mount -r -t erofs $PTSD/$kkh $SDH/raw/${kkh%.*}
    else
    su -mm -c mount -w $PTSD/$kkh $SDH/raw/${kkh%.*}
    fi
    done
    echo "'$save_text' $SDH/raw"
  """

    [[action.params]]
    name = "IMG_NAME"
    desc = "'$mount_text_2'"
    options-sh = "findfile 3 $PTSD | grep -E \"(f2fs)|(ext)|(erofs)\""
    required = true
    multiple = true

  [[action]]
  title = "'$umount_text_1'"
  summary = "'$show_root_text'"
  icon = "'$urlicon'/umount.png"
  lock = "'$LOT'|'$root_warning_text'"
  interruptible = false
  script = """
    for kkh in $IMG_NAME; do
    su -mm -c umount -l $SDH/raw/$kkh
    rm -fr $SDH/raw/$kkh
    if [ "$(checktype $PTSD/${kkh}.img)" == "ext" ]; then
    [ -f $PTSD/${kkh}.img ] && e2fsck -yf $PTSD/${kkh}.img
    elif [ "$(checktype $PTSD/${kkh}.img)" == "f2fs" ]; then
    [ -f $PTSD/${kkh}.img ] && fsck.f2fs -yf $PTSD/${kkh}.img
    fi
    done
    echo "'$umount_text_2'"
  """

    [[action.params]]
    name = "IMG_NAME"
    desc = "'$umount_text_3'"
    options-sh = "findfile 4 $SDH/raw"
    required = true
    multiple = true

  [[group]]
  [[action]]
  title = "'$backup_text_1'"
  summary = "'$show_root_text'"
  icon = "'$urlicon'/backup.png"
  lock = "'$LOT'|'$root_warning_text'"
  interruptible = false
  script = """
    Extract=$PTSD/backup
    [[ ! -d "$Extract" ]] && mkdir -p "$Extract"
    for i in $IMG; do
    e=${i##*/}
    File="$Extract/${e}.img"
    if [[ ! -L $i ]]; then
    echo "'$backup_text_3' $e" >&2
    else
    echo "'$backup_text_4' $e"
    echo
    dd if="$i" of="$File" 2>&1
    echo
    fi
    done
    echo "'$save_text' $Extract"
  """

    [[action.params]]
    name = "IMG"
    label = "IMAGE"
    desc = "'$backup_text_2 $PTSD'"
    options-sh = "search_image"
    required = true
    multiple = true

  [[action]]
  title = "'$flash_text_1'"
  summary = "'$show_root_text'"
  icon = "'$urlicon'/flash.png"
  lock = "'$LOT'|'$root_warning_text'"
  script = """
    e=${IMG##*/}
    if [ "$e" = "vendor" ] || [ "$e" = "system" ] || [ "$e" = "super" ]; then
    killtree "($e) '$flash_text_8'"
    fi
    echo "'$more_text_4' $Brush_in"
    echo
    if [ "$(checktype "$Brush_in")" == "space" ]; then
    simg2img "$Brush_in"
    fi
    if [[ -f "$Brush_in" ]]; then
    echo "Flash (${Brush_in##*/}) ➠ ($e)"
    echo
    dd if="$Brush_in" of="$IMG" 2>&1
    if [[ $CQ1 = 1 ]]; then
    echo
    echo "'$flash_text_9'..."
    for i in $(seq 4 -1 1); do
    echo $i
    sleep 1
    done
    reboot recovery
    fi
    if [[ $CQ = 1 ]]; then
    echo
    echo "'$flash_text_10'..."
    for i in $(seq 4 -1 1); do
    echo $i
    sleep 1
    done
    reboot
    fi
    else
    killtree "! ($Brush_in) '$flash_text_11' ($e)"
    fi
    echo
    echo "'$flash_text_12'"
  """

    [[action.params]]
    name = "CQ"
    label = "'$flash_text_2'"
    type = "checkbox"
    depend-on = "CQ1"
    depend-value = "1"
    depend-mode = "hide"
    depend-cascade = false
    depend-readonly = true

    [[action.params]]
    name = "CQ1"
    label = "'$flash_text_3'"
    type = "checkbox"
    depend-on = "CQ"
    depend-value = "1"
    depend-mode = "hide"
    depend-cascade = false
    depend-readonly = true

    [[action.params]]
    name = "IMG"
    title = "'$flash_text_4'"
    desc = "'$flash_text_5'"
    label = "IMAGE"
    options-sh = "search_image"
    required = true

    [[action.params]]
    name = "Brush_in"
    title = "'$flash_text_6'"
    desc = "'$flash_text_7'"
    type = "file"
    suffix = "img"
    editable = true
    required = true
  '
}

Troot() {
  echo '
  [[group]]
  [[action]]
  title = "'$dexopt_app_text'"
  summary = "'$show_root_text'"
  icon = "'$urlicon'/dexopt_app.png"
  lock = "'$LOT'|'$root_warning_text'"
  script = """
    if [ "$bools" == 1 ]; then
    pm compile -v -a -m $name_dex_list
    echo
    checktime
    else
    for vv in $apps; do
    pm compile -v -f -m $name_dex_list $vv
    echo
    done
    checktime
    fi
  """

    [[action.params]]
    name = "name_dex_list"
    label = "'$option_text'"
    value = "speed-profile"
    options-sh = "echo -e \"everything\nspeed\nspeed-profile\nverify\""

    [[action.params]]
    name = "bools"
    label = "'$dexopt_app_text_2'"
    desc = "'$dexopt_app_text_3'"
    type = "checkbox"

    [[action.params]]
    name = "apps"
    desc = "'$dexopt_app_text_1'"
    type = "app"
    options-sh = "pm list package -3 | cut -f2 -d:"
    multiple = true

  [[group]]
  [[action]]
  title = "'$backups_text_2'"
  icon = "'$urlicon'/backup_apk.png"
  warn = "'$backups_text_1'"
  interruptible = false
  script = """
    for v in $Sapp; do
    patk="$(pm path $v | cut -f2 -d:)"
    patk22="$(pm path "$v" | cut -f2 -d: | head -n1)"
    pathvv="${patk22%/*}"
    hcdf="$(echo "$patk" | grep -c ".apk"$)"
    paptn="$(echo "$patk" | grep "base.apk"$)"
    if [[ -n "$paptn" ]]; then
    infor="$(apkeditor info -i "$paptn")"
    nameapk="$(echo "$infor" | grep -m1 "AppName" | cut -d\" -f2)"
    else
    nameapk="${pathvv##*/}"
    fi
    if [ "$hcdf" -ge 2 ]; then
    zip -j -r "$PTAD/${nameapk}.apks" $patk
    echo "'$save_text' $PTAD/${nameapk}.apks"
    else
    cp -rf "$patk" "$PTAD/${nameapk}.apk"
    echo "'$save_text' $PTAD/${nameapk}.apk"
    fi
    done
  """

    [[action.params]]
    name = "Sapp"
    label = "'$backups_text_3'"
    type = "app"
    multiple = true
  '
}

Generate() {

  show_sett
  echo '
  [[group]]
  [[action]]
  title = "'$generate_text' Payload"
  icon = "'$urlicon'/build_payload.png"
  script = """
  slog sign_payload "$sign_payload"
  slog payload_switch "$payload_switch"
  slog payload_super_size "$payload_super_size"
  slog payload_super_group "$payload_super_group"
  payload_repack -m "$IMAGES" -i "$PTSD" -s "$sign_payload" -w "$payload_switch" -e "$payload_super_size" -g "$payload_super_group"
  echo
  checktime
  """

    [[action.params]]
    name = "payload_switch"
    label = "'$payload_text_3'"
    type = "switch"
    value-sh = "glog payload_switch"

    [[action.params]]
    name = "payload_super_size"
    label = "'$sizes_text'"
    desc = "'$default_text': 11GB, '$payload_text_4'"
    type = "number"
    value-sh = "glog payload_super_size 11"
    required = true
    depend-on = "payload_switch"
    depend-value = "0"
    depend-mode = "hide"
    depend-readonly = true

    [[action.params]]
    name = "payload_super_group"
    label = "'$super_text_5'"
    desc = "'$super_text_6', '$payload_text_4'"
    value-sh = "glog payload_super_group qti_dynamic_partitions"
    required = true
    depend-on = "payload_switch"
    depend-value = "0"
    depend-mode = "hide"
    depend-readonly = true

    [[action.params]]
    name = "sign_payload"
    label = "'$sign_text'"
    value-sh = "glog sign_payload testkey"
    options-sh = "findfile file $ETC/key/2048 .pem | sed \"s|.pem||\""

    [[action.params]]
    name = "IMAGES"
    desc = "'$payload_text_2'"
    options-sh = "findfile 11 $PTSD"
    required = true
    multiple = true

  [[group]]
  [[action]]
  title = "'$generate_text' Amlogic"
  icon = "'$urlicon'/build_amlogic.png"
  script = """
    slog amlogic_boolbox "$amlogic_boolbox"
    slog amlogic_ver "$amlogic_ver"
    echo "'$apkb_text_1' $PTSD/$FOLDER"
    echo
    if [ "$(checktype "$PTSD/$FOLDER/super.img")" == "super" ]; then
    echo "'$unpack_text_0' super(raw) ➠ super(sparse)..."
    img2simg "$PTSD/$FOLDER/super.img"
    echo
    fi
    if [ -n "$(ls -1d $PTSD/$FOLDER/*.img 2>/dev/null)" ]; then
    for vv in $PTSD/$FOLDER/*.img; do
    echo "mv: ${vv##*/} ➠ $(echo "${vv##*/}" | sed "s|\\.img$|.PARTITION|")"
    mv "$vv" "${vv%.*}.PARTITION"
    done
    echo
    fi
    ampack pack --verify --out-ver $amlogic_ver --out-align $amlogic_align "$PTSD/$FOLDER" "$PTSD/out/$FOLDER.img" | tee $TMP/amlogic_pack.log
    [ "$?" == 1 ] && bug_rom=1 || bug_rom=0
    for vv in $(ls -1d $PTSD/$FOLDER/*.PARTITION 2>/dev/null); do
    mv "$vv" "${vv%.*}.img"
    done
    [ "$bug_rom" == 1 ] && killtree "Log check error: $TMP/amlogic_pack.log"
    [ "$amlogic_boolbox" == 1 ] && rm -fr "$PTSD/$FOLDER"
    echo "'$unpack_text_2' $TMP/amlogic_pack.log"
    echo
    echo "'$save_text' $PTSD/out/$FOLDER.img"
    echo
    checktime
  """

    [[action.params]]
    name = "amlogic_boolbox"
    label = "'$deleted_project_text'"
    type = "checkbox"
    value-sh = "glog amlogic_boolbox"

    [[action.params]]
    name = "amlogic_ver"
    label = "'$version_text'"
    value-sh = "glog amlogic_ver v2"
    options-sh = "echo -e \"v2\nv1\""

    [[action.params]]
    name = "amlogic_align"
    label = "'$alignment_text'"
    value-sh = "glog amlogic_align 8"
    options-sh = "echo -e \"4|4\n8|8 (Android 11+)\""

    [[action.params]]
    name = "FOLDER"
    desc = "'$builds_text_1'"
    options-sh = "findfile file $PTSD platform.conf | sed \"s|/platform.conf||\""
    required = true
    multiple = true
  '
}

Utilities() {

  mkdir -p $PTSD/out &>/dev/null &
  time_riviu="$(date -d "@`glog build_times 1230768000`")"
  desc_rom="$path_text: ${PTSD/$SDCARD_PATH/\/sdcard}"
  desc_rom1="$projects_text: $PTSH"
  patchrom="$AON/patch_rom/Add-on.bash"
  
  if [ -f "$patchrom" ]; then
  vdbfbfsn='
  [[menu.items]]
  title = "Patch ROM"
  icon-path = "'${patchrom%/*}'/patch.png"
  config-sh = "'${patchrom%/*}'/index.bash home"
  '
  fi
  
  show_sett
  
  echo '
  [[group]]
  [[menu]]
  handler = """
  if [ "$menu_id" == "v1" ]; then
  echo "am:[start -a android.intent.action.SEND -t */* -d content://'$PACKAGE_NAME'.provider/external_files${PTSD#$SDCARD_PATH}]"
  elif [ "$menu_id" == "v2" ]; then
  echo "am:[start -a android.intent.action.SEND -t */* -d content://'$PACKAGE_NAME'.provider/root$SDH/$PTSH]"
  fi
  """
  
    [[menu.items]]
    title = "'$setting_text' - '$setting_text_3'"
    config-sh = "'$ETC'/tool-tree.bash Project"
    '"$vdbfbfsn"'
    
    [[menu.items]]
    key = "v1"
    title = "'$open_activity_text' ROM"
    silent = true
    
    [[menu.items]]
    key = "v2"
    title = "'$open_activity_text' (data-root)"
    silent = true
  
  [[group]]
  [[action]]
  title = "'$convert_text'"
  icon = "'$urlicon'/convert_file.png"
  script = """
    slog format_img "$format_img"
    slog nen_br "$nen_br"
    slog cboxksbhd "$cboxk"
    for vinput in $IMAGES; do
    cover_img -i "$PTSD/$vinput" -o "$PTSD/out" -c $format_img -l $nen_br -d $cboxk
    done
    echo "'$save_text' $PTSD/out"
    echo
    checktime
  """

    [[action.params]]
    name = "cboxk"
    label = "'$deleted_file_text'"
    type = "checkbox"
    value-sh = "glog cboxksbhd"

    [[action.params]]
    name = "format_img"
    label = "'$option_text'"
    value-sh = "glog format_img raw"
    required = true
    options-sh = """
    echo -e "raw|File.img (raw)\nsparse|File.img (sparse)\ndat|File.new.dat\nbr|File.new.dat.br\nzstd|File.img.zstd\nzst|File.img.zst\nlzma|File.img.lzma\nlz4|File.img.lz4\nxz|File.img.xz\ngz|File.img.gz"
    """

    [[action.params]]
    name = "nen_br"
    label = "'$builds_text_4'"
    desc = "'$convert_text_2'"
    type = "seekbar"
    min = 0
    max = 22
    value-sh = "glog nen_br 4"
    required = true
    depend-on = "format_img"
    depend-value = "raw,sparse,File.new.dat"
    depend-mode = "hide"
    depend-readonly = true

    [[action.params]]
    name = "IMAGES"
    desc = "'$input_file_text': br, dat, zstd, img"
    options-sh = "findfile 1 $PTSD"
    required = true
    multiple = true

  [[group]]
  [[action]]
  title = "'$decompile_text'"
  desc = "'$desc_rom'"
  icon = "'$urlicon'/decom.png"
  script = """
  slog vavbbgdf "$vavb"
  slog xoa_oat_boot "$xoa_oat_boot"
  slog dkjdj "$nounpak"
  slog pcvbmeta "$pcvbmeta"
  slog dkhdh "$cboxk"
  slog text_oat_boot "$text_oat_boot"
  for vkl in $IMAGES; do
  if [ -f "$PTSD/${vkl#*=}" ]; then
  unpack_img -i "$PTSD/${vkl#*=}" -p "${vkl%%=*}" -o "$SDH/$PTSH" -n $nounpak -d $cboxk -r $xoa_oat_boot -a $vavb -m $pcvbmeta
  else
  unpack_img -i "$PTSD/$vkl" -o "$SDH/$PTSH" -n $nounpak -d $cboxk -r $xoa_oat_boot -a $vavb -m $pcvbmeta
  fi
  done
  checktime
  """
  
    [[action.params]]
    name = "cboxk"
    label = "'$deleted_file_text'"
    type = "checkbox"
    value-sh = "glog dkhdh"
    
    [[action.params]]
    name = "nounpak"
    label = "'$decode_text_1'"
    type = "switch"
    value-sh = "glog dkjdj"
    
    [[action.params]]
    name = "xoa_oat_boot"
    label = "'$xoaoat_text_1'"
    type = "switch"
    value-sh = "glog xoa_oat_boot"
    depend-on = "nounpak"
    depend-value = "1"
    depend-mode = "hide"
    depend-readonly = true
    
    [[action.params]]
    name = "text_oat_boot"
    type = "text"
    value-sh = "glog text_oat_boot \"fsv_meta,oat,vdex,odex,prof,bprof\""
    depend-on = "xoa_oat_boot"
    depend-value = "1"
    depend-mode = "show"
    depend-default = "hide"
    
    [[action.params]]
    name = "vavb"
    label = "'$builds_text_8'"
    type = "switch"
    depend-on = "nounpak"
    depend-value = "1"
    depend-mode = "hide"
    depend-readonly = true
    
    [[action.params]]
    name = "pcvbmeta"
    label = "'$patch_text' vbmeta"
    value-sh = "glog pcvbmeta 0"
    options-sh = "echo -e \"0|'$default_text'\n1|'$disable_text' dm-verity\n2|'$disable_text' Verification\n3|'$disable_text' dm-verity + Verification\""
    depend-on = "nounpak"
    depend-value = "1"
    depend-mode = "hide"
    depend-readonly = true
    
    [[action.params]]
    name = "IMAGES"
    desc = "'$input_file_text': br, dat, img, zst, zstd, bin, zip"
    options-sh = "findfile 2 $PTSD"
    required = true
    multiple = true
  
  [[action]]
  title = "'$build_text'"
  desc = "'$desc_rom1'"
  icon = "'$urlicon'/build.png"
  script = """
  slog dang_nen "$dang_nen"
  slog on_f2fs_nen "$on_f2fs_nen"
  slog format_imgs "$format_imgs"
  slog boolboxdjh "$boolbox"
  slog dinh_dang "$dinh_dang"
  slog build_size "$build_size"
  slog offfscontex "$offfscontex"
  slog muc_nen "$muc_nen"
  slog nen_br "$nen_br"
  slog build_times "$build_times"
  for vkl in $IMAGES; do
  repack_img -i "$SDH/$PTSH/$vkl" -o "$PTSD/out" -n "$dang_nen" -l "$muc_nen" -k "$dinh_dang" -s "$build_size" -d "$boolbox" -c "$format_imgs" -p "$offfscontex"
  done
  echo "'$save_text' $PTSD/out"
  echo
  checktime
  """
  
    [[action.params]]
    name = "boolbox"
    label = "'$deleted_project_text'"
    type = "checkbox"
    value-sh = "glog boolboxdjh"

    [[action.params]]
    name = "IMAGES"
    title = "'$list_partition_text'"
    desc = "'$builds_text_1'"
    options-sh = "findfile 0 $SDH/$PTSH"
    required = true
    multiple = true

    [[action.params]]
    name = "dinh_dang"
    label = "'$build_text'"
    desc = "'$builds_text_2'"
    value-sh = "glog dinh_dang 0"
    options-sh = "echo -e \"0|'$default_text'\n1|RO (EROFS)\n2|RW (EXT4)\n3|RO (F2FS)\n4|RW (F2FS)\""
    depend-on = "IMAGES"
    depend-value = "(erofs),(ext),(f2fs)"
    depend-mode = "show"
    depend-default = "hide"

    [[action.params]]
    name = "on_f2fs_nen"
    label = "'$lall_nen_f2fs_text'"
    desc = "'$nen_f2fs_text'"
    type = "switch"
    value-sh = "glog on_f2fs_nen 0"
    depend-on = "dinh_dang"
    depend-value = "(F2FS)"
    depend-mode = "show"
    depend-default = "hide"

    [[action.params]]
    name = "dang_nen"
    label = "'$option_text'"
    desc = "'$builds_text_3'"
    value-sh = "glog dang_nen lz4hc"
    options-sh = "echo -e \"lz4hc\nlz4\nlzma\ndeflate\nzstd\""
    depend-on = "dinh_dang|dinh_dang|IMAGES"
    depend-value = "EROFS|EXT4,F2FS|(erofs)"
    depend-mode = "show|hide|show"
    depend-logic = "priority"
    depend-default = "hide"

    [[action.params]]
    name = "muc_nen"
    label = "'$builds_text_4'"
    desc = "'$builds_text_6': lz4: 0, lz4hc: 0-12, deflate,lzma: 0-9, zstd: 0-22"
    type = "seekbar"
    min = 0
    max = 22
    value-sh = "glog muc_nen 8"
    depend-on = "dang_nen"
    depend-value = "lz4"
    depend-mode = "hide"
    depend-logic = "priority"

    [[action.params]]
    name = "format_imgs"
    label = "'$convert_text'"
    desc = "'$convert_img_text'"
    value-sh = "glog format_imgs raw"
    depend-on = "IMAGES"
    depend-value = "(erofs),(ext),(f2fs)"
    depend-mode = "show"
    depend-readonly = true
    options-sh = """
    echo -e "raw|File.img (raw)\nsparse|File.img (sparse)\nzstd|File.img.zstd\nzst|File.img.zst\ndat|File.new.dat\nbr|File.new.dat.br"
    """

    [[action.params]]
    name = "nen_br"
    label = "'$builds_text_4'"
    desc = "'$convert_text_2'"
    type = "seekbar"
    min = 0
    max = 22
    value-sh = "glog nen_br 4"
    required = true
    depend-on = "format_imgs"
    depend-value = "raw,sparse,File.new.dat"
    depend-mode = "hide"

    [[action.params]]
    name = "build_times"
    label = "'$time_text'"
    desc = "'$build_time_text_1': '$time_riviu'"
    type = "number"
    value-sh = "glog build_times"
    required = true
    depend-on = "IMAGES"
    depend-value = "(erofs),(ext),(f2fs)"
    depend-mode = "show"
    depend-readonly = true

    [[action.params]]
    name = "offfscontex"
    label = "'$patch_text_fscontex'"
    desc = "'$patch_text_fsdesc'"
    type = "switch"
    value-sh = "glog offfscontex 1"
    depend-on = "IMAGES"
    depend-value = "(erofs),(ext),(f2fs)"
    depend-mode = "show"
    depend-readonly = true

    [[action.params]]
    name = "build_size"
    label = "'$sizes_text'"
    desc = "'$builds_text_7'"
    type = "number"
    value-sh = "glog build_size 0"
    required = true
    depend-on = "dinh_dang|dinh_dang|IMAGES"
    depend-value = "EROFS|EXT4,F2FS|(ext),(f2fs)"
    depend-mode = "hide|show|show"
    depend-logic = "priority"
    depend-default = "hide"
  
  [[group]]
  [[action]]
  title = "'$build_text' Super"
  icon = "'$urlicon'/build_super.png"
  script = """
    slog typeheh "$type"
    slog fromdjfh "$from"
    slog super_sizedj "$super_size"
    slog super_group "$super_group"
    repack_super -m "$IMAGES" -g "$super_group" -s "$super_size" -f "$from" -t "$type" -i "$PTSD"
    echo
    checktime
  """

    [[action.params]]
    name = "type"
    label = "'$super_text_2'"
    value-sh = "glog typeheh VAB"
    options-sh = "echo -e \"A|a_only\nAB|ab\nVAB|virtual_ab\""

    [[action.params]]
    name = "from"
    label = "'$super_text_3'"
    value-sh = "glog fromdjfh raw"
    options-sh = "echo -e \"raw\nsparse\""

    [[action.params]]
    name = "super_size"
    label = "'$sizes_text'"
    desc = "'$default_text': 8.5GB"
    type = "number"
    placeholder = "8.5"
    value-sh = "glog super_sizedj 8.5"
    required = true

    [[action.params]]
    name = "super_group"
    label = "'$super_text_5'"
    desc = "'$super_text_6'"
    value-sh = "glog super_group qti_dynamic_partitions"
    placeholder = "qti_dynamic_partitions"
    required = true

    [[action.params]]
    name = "IMAGES"
    desc = "'$super_text_7'"
    options-sh = "findfile 3 $PTSD"
    required = true
    multiple = true
    
  [[group]]
  [[action]]
  title = "'$super_split_text_1'"
  icon = "'$urlicon'/super_split.png"
  script = """
    slog cboxkshg "$cboxk"
    slog slipdhhe "$slipdhhe"
    slog khoi_dau_dem "$khoi_dau_dem"
    echo "'$super_split_text_4' ${IMAGES}..."
    echo
    if [ $(checktype "$PTSD/$IMAGES") == "sparse" ]; then
    simg2img "$PTSD/$IMAGES"
    fi
    size_super_mb=$(($(stat -c %s "$PTSD/$IMAGES") / 1048576))
    chunk_size_mb=$((size_super_mb / slipdhhe))
    echo "${size_super_mb}M ÷ $slipdhhe = ${chunk_size_mb}M"
    echo
    chunk_split -s .cache.%02d -B 4K -C "$chunk_size_mb"M "$PTSD/$IMAGES"
    [ "$khoi_dau_dem" == 1 ] && sonum=1 || sonum=0
    cd "$PTSD"
    for vcd in ${IMAGES}.cache.*; do
    if [ -f "$vcd" ]; then
    echo "$vcd ➠ ${IMAGES}.$sonum"
    mv "$vcd" "out/${IMAGES}.$sonum"
    sonum=$((sonum + 1))
    fi
    done
    echo
    [ "$cboxk" == 0 ] || rm -fr "$PTSD/$IMAGES"
    echo "'$save_text' $PTSD/out"
    echo
    checktime
  """

    [[action.params]]
    name = "cboxk"
    label = "'$deleted_file_text'"
    type = "checkbox"
    value-sh = "glog cboxkshg"

    [[action.params]]
    name = "slipdhhe"
    label = "'$number_text'"
    title = "'$split_number_desc'"
    type = "number"
    min = 2
    max = 50
    value-sh = "glog slipdhhe 9"
    required = true

    [[action.params]]
    name = "khoi_dau_dem"
    label = "'$split_number_label'"
    type = "switch"
    value-sh = "glog khoi_dau_dem 0"

    [[action.params]]
    name = "IMAGES"
    label = "'$option_text'"
    title = "'$super_split_text_3'"
    options-sh = "findfile 7 $PTSD"
    required = true
  
  [[action]]
  title = "'$super_merge_text_1'"
  icon = "'$urlicon'/super_merge.png"
  script = """
    slog silence $silence
    echo "'$super_merge_text_2'..."
    simg2img $MERGE "$PTSD/super.img" || killtree "Error" "$PTSD/super.img"
    [ "$silence" == 0 ] || rm -fr $MERGE
    echo
    echo "'$save_text' $PTSD/super.img"
    echo
    checktime
  """

    [[action.params]]
    name = "silence"
    label = "'$deleted_file_text'"
    type = "checkbox"
    value-sh = "glog silence 1"

    [[action.params]]
    name = "MERGE"
    desc = "'$super_merge_text_3'"
    options-sh = "findfile 5 $PTSD | sort -n -t . -k 3"
    required = true
    multiple = true
  
  [[group]]
  [[page]]
  title = "'$synthetic_text'"
  icon = "'$urlicon'/generate.png"
  config-sh = "'$ETC'/tool-tree.bash Generate"
  '
  
}

Apex() {

  desc_apkd="$path_text: ${PTAD/$SDCARD_PATH/\/sdcard}"
  desc_apkd1="$projects_text: $PTAH"
  show_apkset

  echo '
  [[group]]
  [[action]]
  title = "'$decompile_text'"
  desc = "'$desc_apkd'"
  icon = "'$urlicon'/decom.png"
  script = """
    IFS=$'"'\n'"'
    for vv in $FILE; do
        apexeditor d -i "$PTAD/$vv" -o "$APK/$PTAH"
        echo
    done
    echo "'$save_text' $APK/$PTAH"
    echo
    checktime
  """

    [[action.params]]
    name = "FILE"
    desc = "'$apex_text_2'"
    options-sh = "findfile 12 $PTAD"
    required = true
    multiple = true

  [[action]]
  title = "'$build_text'"
  desc = "'$desc_apkd1'"
  icon = "'$urlicon'/build.png"
  script = """
    slog gobo_apex "$gobo_apex"
    slog nen_apex "$nen_apex"
    slog payload_type "$payload_type"
    slog signs_apex "$SIGNS"
    IFS=$'"'\n'"'
    for vv in $FILE; do
        apexeditor b -d "$gobo_apex" -k "$SIGNS" -c "$nen_apex" -i "$APK/$PTAH/$vv" -o "$PTAD/out"
        echo
    done
    checktime
  """

    [[action.params]]
    name = "gobo_apex"
    label = "'$deleted_project_text'"
    type = "checkbox"
    value-sh = "glog gobo_apex"

    [[action.params]]
    title = "'$apex_text_1'"
    name = "nen_apex"
    label = "'$option_text'"
    value-sh = "glog nen_apex auto"
    options-sh = "echo -e \"auto|'$default_text'\n0|'$off_text'\n1|'$on_text'\""

    [[action.params]]
    name = "SIGNS"
    label = "'$sign_text'"
    value-sh = "glog signs_apex testkey"
    options-sh = "findfile file $ETC/key/4096 .pem | sed \"s|.pem||\""

    [[action.params]]
    name = "FILE"
    desc = "'$builds_text_1'"
    options-sh = "findfile forapex $APK/$PTAH"
    required = true
    multiple = true
  '
}

Utiliapk() {

  mkdir -p $PTAD/out &>/dev/null &
  desc_apks="$path_text: ${PTAD/$SDCARD_PATH/\/sdcard}"
  desc_apks1="$projects_text: $PTAH"
  show_apkset
  
  echo '
  [[group]]
  [[menu]]
  handler = """
    if [ "$menu_id" == "b2" ]; then
    echo "Looking for system apk..."
    for mm in $(pm list package -s | cut -f2 -d:); do
    cggdccg="$(pm path $mm | cut -f2 -d:)"
    [ -f "$cggdccg" ] && apktool if "$cggdccg" 2>/dev/null | sed "/127.apk/d"
    done
    rm -fr $HOME/.local/share/apktool/framework/1.apk
    echo
    checktime
    elif [ "$menu_id" == "b4" ]; then
    echo "'$more_text_4' $file"
    echo
    apktool if "$file"
    elif [ "$menu_id" == "v2" ]; then
    echo "'$more_text_4' $file"
    echo
    [ "$(unzip -ql "$file" | grep -cm1 ".x509.pem")" == 1 ] || killtree "'$more_text_5' .x509.pem"
    [ "$(unzip -ql "$file" | grep -cm1 ".pk8")" == 1 ] || killtree "'$more_text_5' .pk8"
    unzip -oj "$file" *.x509.pem *.pk8 -d "$ETC/key"
    elif [ "$menu_id" == "v4" ]; then
    echo "am:[start -a android.intent.action.SEND -t */* -d content://'$PACKAGE_NAME'.provider/external_files${PTAD#$SDCARD_PATH}]"
    elif [ "$menu_id" == "v5" ]; then
    echo "am:[start -a android.intent.action.SEND -t */* -d content://'$PACKAGE_NAME'.provider/root$APK/$PTAH]"
    fi
  """

    [[menu.items]]
    key = "b2"
    type = "default"
    title = "'$framework_auto_text'"

    [[menu.items]]
    key = "v2"
    type = "file"
    title = "'$more_text_3'"
    suffix = "zip"
    auto-off = true

    [[menu.items]]
    key = "b4"
    type = "file"
    title = "'$more_text_10' framework"
    suffix = "apk"

    [[menu.items]]
    key = "v4"
    type = "default"
    title = "'$open_activity_text' APK"
    silent = true

    [[menu.items]]
    key = "v5"
    type = "default"
    title = "'$open_activity_text' (data-root)"
    silent = true
    
  [[group]]
  [[page]]
  title = "'$apex_text'"
  icon = "'$urlicon'/apex.png"
  config-sh = "'$ETC'/tool-tree.bash Apex"
  
  [[group]]
  [[action]]
  title = "'$decompile_text'"
  desc = "'$desc_apks'"
  icon = "'$urlicon'/decom.png"
  warn = "'$decom_apk_text_15'"
  script = """
    slog dexlib "$dexlib"
    slog tooldecom "$tooldecom"
    slog xoa_debug_info "$xoa_debug_info"
    slog type_apk "$type_apk"
    slog dexlibk "$dexlibk"
    slog mutiresk "$mutiresk"
    slog redivdd "$redivdd"
    IFS=$'"'\n'"'
    for vapk in $FILE; do
    if [ "$tooldecom" == "apkeditor" ]; then
    apkeditor_d -i "$PTAD/$vapk" -t "$type_apk" -b "$xoa_debug_info" -d "$dexlib" -o "$APK/$PTAH" -s "$redivdd"
    else
    apktool_d -i "$PTAD/$vapk" -r "$mutiresk" -b "$xoa_debug_info" -d "$dexlibk" -o "$APK/$PTAH" -s "$redivdd"
    fi
    echo
    done
    echo "'$save_text' $APK/$PTAH"
    echo
    checktime
  """

    [[action.params]]
    name = "tooldecom"
    title = "'$customize_tools_text'"
    label = "'$tools_text'"
    value-sh = "glog tooldecom apkeditor"
    options-sh = "echo -e \"apkeditor|Apkeditor\napktool|Apktool\""

    [[action.params]]
    name = "mutiresk"
    title = "'$decom_apk_text_11'"
    label = "'$option_text'"
    value-sh = "glog mutiresk 1"
    options-sh = "echo -e \"0|'$decom_apk_text_3'\n1|'$default_text'\n2|'$decom_apk_text_5'\""
    depend-on = "tooldecom"
    depend-value = "apkeditor"
    depend-mode = "hide"

    [[action.params]]
    name = "type_apk"
    title = "'$decom_apk_text_11'"
    label = "'$option_text'"
    value-sh = "glog type_apk xml"
    depend-on = "tooldecom"
    depend-value = "apktool"
    depend-mode = "hide"
    options-sh = "echo -e \"raw|'$decom_apk_text_3'\nxml|'$default_text'\nreso|'$decom_apk_text_10'\""

    [[action.params]]
    name = "dexlibk"
    title = "'$decom_apk_text_12'"
    label = "'$option_text'"
    value-sh = "glog dexlibk 2"
    options-sh = "echo -e \"0|'$decom_apk_text_3'\n1|'$default_text'\n2|Baksmali 3.0.9\""
    depend-on = "tooldecom"
    depend-value = "apkeditor"
    depend-mode = "hide"

    [[action.params]]
    name = "dexlib"
    title = "'$decom_apk_text_12'"
    label = "'$option_text'"
    value-sh = "glog dexlib smali"
    options-sh = "echo -e \"nodex|'$decom_apk_text_3'\ninternal|'$default_text'\nsmali|Baksmali 3.0.9\""
    depend-on = "tooldecom"
    depend-value = "apktool"
    depend-mode = "hide"

    [[action.params]]
    name = "xoa_debug_info"
    label = "'$decom_apk_text_7'"
    type = "switch"
    value-sh = "glog xoa_debug_info 1"
    depend-on = "dexlib|dexlibk"
    depend-value = "nodex|0"
    depend-mode = "hide|hide"
    depend-cascade = false
    depend-readonly = true

    [[action.params]]
    name = "redivdd"
    label = "'$decom_apk_text_14'"
    type = "switch"
    value-sh = "glog redivdd 0"
    depend-on = "dexlib|dexlibk"
    depend-value = "internal,jf|1"
    depend-mode = "hide|hide"
    depend-cascade = false
    depend-readonly = true

    [[action.params]]
    name = "dex_methods"
    label = "'$number_text'"
    type = "number"
    min = 40000
    max = 65535
    value-sh = "glog dex_methods 64000"
    depend-on = "redivdd"
    depend-value = "1"
    depend-mode = "show"
    depend-default = "hide"

    [[action.params]]
    name = "FILE"
    title = "'$decom_apk_text_9'"
    desc = "'$input_file_text': apk, apks, apkm, xapk, jar, zip"
    options-sh = "findfile 9 $PTAD"
    required = true
    multiple = true

  [[action]]
  title = "'$build_text'"
  desc = "'$desc_apks1'"
  icon = "'$urlicon'/build.png"
  warn = "'$build_apk_text_2'"
  script = """
    slog sign "$sign"
    slog comlib "$comlib"
    slog sstring "$sstring"
    slog xoatm "$xoatm"
    slog copysign "$copysign"
    IFS=$'"'\n'"'
    for vbapk in $FOLDER; do
      if [ -f "$APK/$PTAH/$vbapk/archive-info.json" ]; then
        apkeditor_b -i "$APK/$PTAH/$vbapk" -o "$PTAD/out" -s "$sign" -n "$sstring" -d "$xoatm" -x "$comlib"
      else
        apktool_b -i "$APK/$PTAH/$vbapk" -o "$PTAD/out" -c "$copysign" -s "$sign" -n "$sstring" -d "$xoatm" -x "$comlib"
      fi
      echo
    done
    echo "'$save_text' $PTAD/out"
    echo
    checktime
  """

    [[action.params]]
    name = "xoatm"
    label = "'$deleted_project_text'"
    type = "bool"
    value-sh = "glog xoatm 0"

    [[action.params]]
    name = "sign"
    label = "'$sign_text'"
    value-sh = "glog sign default"
    options-sh = "findfile file $ETC/key .pk8 | sed \"s|.pk8||\""

    [[action.params]]
    name = "sstring"
    label = "'$build_apk_text_1'"
    type = "switch"
    value-sh = "glog sstring 1"

    [[action.params]]
    name = "copysign"
    label = "'$decom_apk_text_13'"
    type = "switch"
    value-sh = "glog copysign"
    depend-on = "FOLDER"
    depend-value = "(apktool)"
    depend-mode = "show"
    depend-default = "hide"

    [[action.params]]
    name = "comlib"
    label = "'$addlang_text_2'"
    desc = "'$addlang_text_3'"
    value-sh = "glog comlib manifest"
    options-sh = "echo -e \"manifest|'$default_text'\ntrue|'$on_text'\nfalse|'$off_text'\""

    [[action.params]]
    name = "FOLDER"
    desc = "'$builds_text_1'"
    options-sh = "findfile forapk $APK/$PTAH"
    required = true
    multiple = true

  [[group]]
  [[action]]
  title = "'$apk_mager_text_2'"
  icon = "'$urlicon'/merge_apk.png"
  warn = "'$apk_mager_text_1'"
  script = """
    IFS=$'"'\n'"'
    for v in $FILE; do
        echo "'$more_text_4' $FILE"
        echo
        apkeditor m -f -i "$PTAD/$v" -o "$PTAD/out/$v" 2>&1 | sed -u -e "1,/__/d"
        echo
    done
    checktime
  """

    [[action.params]]
    name = "FILE"
    options-sh = "findfile 9 $PTAD | grep -E \"(apks)|(apkm)|(xapk)\""
    desc = "'$desc_apks'"
    required = true
    multiple = true
    
  [[group]]
  [[action]]
  title = "'$distur_apk_text_2'"
  icon = "'$urlicon'/apk_distur.png"
  warn = "'$distur_apk_text_1'"
  script = """
    IFS=$'"'\n'"'
    for v in $FILE; do
        echo "'$more_text_4' $FILE"
        echo
        apkeditor p -skip-manifest -f -i "$PTAD/$v" -o "$PTAD/out/$v" 2>&1 | sed -u -e "1,/__/d"
        echo
    done
    checktime
  """

    [[action.params]]
    name = "FILE"
    options-sh = "findfile 10 $PTAD"
    desc = "'$desc_apks'"
    required = true
    multiple = true

  [[action]]
  title = "'$apk_restore_text_2'"
  icon = "'$urlicon'/apk_restore.png"
  warn = "'$apk_restore_text_1'"
  script = """
    IFS=$'"'\n'"'
    for v in $FILE; do
        echo "'$more_text_4' $FILE"
        echo
        apkeditor x -fix-types -f -i "$PTAD/$v" -o "$PTAD/out/$v" 2>&1 | sed -u -e "1,/__/d"
        echo
    done
    checktime
  """

    [[action.params]]
    name = "FILE"
    options-sh = "findfile 10 $PTAD"
    desc = "'$desc_apks'"
    required = true
    multiple = true

  [[group]]
  [[action]]
  title = "'$restore_apk_text_3'"
  icon = "'$urlicon'/restore_sign.png"
  warn = "'$desc_apks'"
  script = """
    slog apk_restore_sign "$FILE"
    slog apk_restore_sign2 "$FILE2"
    echo "'$more_text_4' $FILE"
    echo
    apkeditor d -f -t sig -i "$PTAD/$FILE" -sig "$TMP/signatures_dir" 2>&1
    echo
    apkeditor b -f -t sig -i "$PTAD/$FILE2" -sig "$TMP/signatures_dir" -o "$PTAD/out/$FILE2" 2>&1
    rm -fr "$TMP/signatures_dir"
  """

    [[action.params]]
    name = "FILE"
    title = "'$restore_apk_text_1'"
    value-sh = "glog apk_restore_sign"
    options-sh = "findfile 10 $PTAD"
    required = true

    [[action.params]]
    name = "FILE2"
    title = "'$restore_apk_text_2'"
    value-sh = "glog apk_restore_sign2"
    options-sh = "findfile 10 $PTAD"
    required = true
  '
}

Addon() {

  if [[ "$PATHADD" == "$AON" ]]; then
  linkweb="Addon.html"
  else
  linkweb="Apkon.html"
  fi
  
  echo '
    [[group]]
    [[menu]]
    [[menu.items]]
    title = "'$download_text'"
    link = "https://Kakathic.github.io/Tool-Tree/website/'$linkweb'"
    silent = true
    
    [[menu.items]]
    title = "'$customize_text'"
    get = "glog show_setting_add"
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
    [ "$menu_id" == "file" ] && installadd "$file" "'$PATHADD'"
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
    warn = "'$use_network_text'"
    icon = "'$icon_vb'"
    title = "'$name'"
    desc = "'$sum_vb'"
    reload = true
    url = "'$url'"
    script = """
    installadd "$state" "'${dirvad%/*}'"
    """'
      if [ "$(glog show_setting_add)" == 1 ]; then
        echo '
        [[download.rows]]
        toggle = "checkbox"
        text = "'$hide_add_text'"
        get = "[ -f '$dirvad'/hide ] && echo 1"
        line = true
        align="opposite"
        set = """
        if [ -f '$dirvad'/hide ]; then
        rm '$dirvad'/hide
        else
        touch '$dirvad'/hide
        fi
        """
        '
      fi
    fi
  }

  Homeadd() {
  
    # Load index
    if [ -f "$dirvad/index.bash" ]; then
    pagesh='config-sh = "MPAT='$dirvad' '$dirvad'/index.bash home"'
    elif [ -f "$dirvad/index.toml" ]; then
    pagesh='config = "'$dirvad'/index.toml"'
    else
    pagesh='config = "'$ETC'/error.toml"'
    fi
  
    if [ -f "$dirvad/before-load.bash" ]; then
    beforesh='before-load = "MPAT='$dirvad' '$dirvad'/before-load.bash"'
    fi
  
    if [ "$(glog show_setting_add)" == 1 ]; then
      hinde_add='[[page.rows]]
      toggle = "checkbox"
      text = "'$hide_add_text'"
      get = "[ -f '$dirvad'/hide ] && echo 1"
      line = true
      align="opposite"
      set = """
      if [ -f '$dirvad'/hide ]; then
      rm '$dirvad'/hide
      else
      touch '$dirvad'/hide
      fi
      """
      '
      
      [ -f "$dirvad/nodelete" ] || delete_add='
      [[page.rows]]
      toggle = "switch"
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
    fi
    
      # Danh sách Add-on
      echo '
      [[group]]
      [[page]]
      title = "'$name'"
      desc = "'$sum_vb'"
      icon = "'$icon_vb'"
      process = "'$process'"
      '$croot_add'
      '$shortcut_text'
      '$pagesh'
      '$beforesh'
      '"$hinde_add"'
      '"$delete_add"'
      '
  }

  Vips() {
    
    # Xoá giá trị cũ
    id= root= shortcut= description= google_text= url= name=
    google_trans= code_option= beforesh= croot_add= process=
    description_text= sum_vb= hinde_add= shortcut_text= delete_add=
    
    # Nạp string
    source "$vadd" 2>/dev/null
    [ "$id" ] || continue
    
    # Phát hiện root
    if [ "$root" == "true" ]; then
    croot_add='lock = "'$LOT'|'$root_warning_text'"'
    fi
  
    # Phát hiện tính năng
    if [ "$shortcut" == "true" ]; then
    shortcut_text='key = "'$id'" '
    fi
    
    if [ "$description" ]; then
    description_text=" | $description"
    fi
    
    sum_vb="$version $author$description_text"
    
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
  for vadd in $PATHADD/*/Add-on.bash; do
    [ -f "$vadd" ] || continue
    dirvad="${vadd%/*}"
    [ -f "$dirvad/pin" ] || continue
    if [[ -f "$dirvad/index.bash" || -f "$dirvad/index.toml" ]]; then
    Vips
    fi
  done

  # Load trang không có pin
  for vadd in $PATHADD/*/Add-on.bash; do
    [ -f "$vadd" ] || continue
    dirvad="${vadd%/*}"
    [ -f "$dirvad/pin" ] && continue
    if [[ -f "$dirvad/index.bash" || -f "$dirvad/index.toml" ]]; then
    Vips
    fi
  done

  # Load trang tải xuống ở dưới cùng
  for vadd in $PATHADD/*/download.bash; do
    [ -f "$vadd" ] || continue
    dirvad="${vadd%/*}"
    if [[ -f "$dirvad/index.bash" || -f "$dirvad/index.toml" ]]; then
    continue
    fi
    Vips
  done

}

# Điều hướng chính
"$@"
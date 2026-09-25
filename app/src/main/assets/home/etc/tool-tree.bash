#!/data/data/com.tool.tree/files/home/bin/bash

Account(){
echo '
  [[group]]
  [[action]]
  icon = "'$urlicon'"
  title = "Phòng thí nghiệm"
  desc = "Tạo tài khoản, đăng nhập, quên mật khẩu"
  warn = "Đăng ký tài khoản để có thể tải lên add-on, và cập nhật add-on"
  script = ""
  
  [[action.params]]
  name = "NAME"
  label = "Lựa chọn"
  desc = ""
  options-sh = """
  echo -e "1|Tạo tài khoản\n2|Đăng nhập\n3|Quên mật khẩu"
  """
  
  [[action.params]]
  name = "NAME2"
  label = "Tài khoản"
  placeholder = "test"
  type = "text"
  required = true
  
  [[action.params]]
  name = "NAME3"
  label = "Mật khẩu"
  placeholder = "123456"
  type = "text"
  required = true
  
  [[action.params]]
  name = "NAME4"
  label = "Mật khẩu mới"
  placeholder = "654321"
  type = "text"
  required = true
  
  [[action.params]]
  name = "NAME5"
  placeholder = "name_022098"
  label = "Văn bản"
  desc = "Mục này dùng để khôi phục mật khẩu, hãy lưu lại tên tài khoản, và mục này để khi quên mật khẩu có thể lấy lại"
  type = "text"
  required = true
  '
}

Addss(){

echo '
  [[group]]
  [[page]]
  title = "Phòng thí nghiệm"
  desc = "Tạo add-on cơ bản và upload lên"
  icon = "'$urlicon'/add_user.png"
  config-sh = "'$ETC'/tool-tree.bash Account"

  [[page]]
  title = "Demo tính năng"
  desc = "Hướng dẫn dành cho nhà phát triển"
  icon = "'$urlicon'/add_list.png"
  config-sh = "'$ETC'/tool-tree.bash"
  
  [[group]]
  [[page]]
  title = "Add-on có sẵn"
  desc = "Đây là các add-on do tác giả tạo ra"
  icon = "'$urlicon'/add_off.png"
  process = true
  config-sh = "'$ETC'/tool-tree.bash Addon $UPL"

  [[group]]
  [[page]]
  title = "Add-on online"
  desc = "Đây là các add-on do người dùng tải lên"
  icon = "'$urlicon'/add_online.png"
  process = true
  config-sh = "'$ETC'/tool-tree.bash Addon $UZE"
  '
}

show_sett() {
  echo '
  [[group]]
  [[action]]
  shell = "hidden"
  reload = true
  menu = true
  title = "'$projects_text'"
  desc = "'$path_text': '$PTSD'"
  script = """
  slog SDH "$SDH"
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
  name = "SDH"
  title = "'$output_folder_text'"
  desc = "'$path_text': '$SDH'"
  type = "folder"
  value-sh = "glog SDH"
  path-home = "'$HOME'"
  editable = true
  
  [[action.params]]
  name = "Name"
  title = "'$setting_text_3'"
  desc = "'$config_text_1'"
  label = "'$option_text'"
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
  title = "'$projects_text'"
  desc = "'$path_text': '$PTAD'"
  script = """
  slog APK "$APK"
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
  name = "APK"
  title = "'$output_folder_text'"
  desc = "'$path_text': '$APK'"
  type = "folder"
  value-sh = "glog APK"
  path-home = "'$HOME'"
  editable = true

  [[action.params]]
  name = "Name"
  title = "'$setting_text_3'"
  desc = "'$config_text_1'"
  label = "'$option_text'"
  options-sh = "findfile for $APK"
  value-sh = "glog PTAH"
  editable = true
  '
}

inforkk() {
echo '
  [[group]]
  [[text.rows]]
  size = 13
  text = "'$root_text': {'${ROOT^}'}(#0dbda2)"
  bold = true
  icon = "'$urlicon'/1shield.png"
  line-height = 1.3
  break = true
  markdown = true
  
  [[text.rows]]
  text = "'$device_text': {'$ANDROID_BRAND' - '$ANDROID_DEVICE'}(#0dbda2)"
  break = true
  size = 13
  bold = true
  line-height = 1.3
  icon = "'$urlicon'/1smart.png"
  markdown = true
  
  [[text.rows]]
  text = "'$operating_system': {Android '$ANDROID_RELEASE' - SDK '$API'}(#0dbda2)"
  break = true
  size = 13
  bold = true
  line-height = 1.3
  icon = "'$urlicon'/1android.png"
  markdown = true
  
  [[text.rows]]
  text = "'$microprocessors': {'${CPU_ABI^}'}(#0dbda2)"
  break = true
  size = 13
  bold = true
  line-height = 1.3
  icon = "'$urlicon'/1cpu.png"
  markdown = true
  '
}

inforka() {
echo '
  [[group]]
  [[text.rows]]
  size = 13
  text = "Selinux: {'$SELINUX'}(#0dbda2)"
  bold = true
  icon = "'$urlicon'/1shield.png"
  line-height = 1.3
  break = true
  markdown = true
  
  [[text.rows]]
  text = "Kernel: {'$KERNEL_VERSION'}(#0dbda2)"
  break = true
  size = 13
  bold = true
  line-height = 1.3
  icon = "'$urlicon'/1smart.png"
  markdown = true
  
  [[text.rows]]
  text = "App UID: {'$APP_UID'}(#0dbda2)"
  break = true
  size = 13
  bold = true
  line-height = 1.3
  icon = "'$urlicon'/1android.png"
  markdown = true
  
  [[text.rows]]
  text = "Bootloader: {'${BOOTLOADER_STATE^}' - '$VERIFIED_BOOT_STATE'}(#0dbda2)"
  break = true
  size = 13
  bold = true
  line-height = 1.3
  icon = "'$urlicon'/1cpu.png"
  markdown = true
  '
}

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
  title = "'$editor_rom'"
  desc = "'$setting_text_4'"
  icon = "'$urlicon'/project.png"
  config-sh = "'$ETC'/tool-tree.bash Project 1"

  [[group]]
  [[page]]
  title = "'$customize_text'"
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
  title = "'$plugin_text'"
  desc = "'$home_text_4'"
  icon = "'$urlicon'/addon.png"
  process = true
  config-sh = "'$ETC'/tool-tree.bash Addon $AON"
  '
}

More() {
  inforka
  
  echo '
  [[group]]
  [[page]]
  title = "'$editor_apk'"
  desc = "'$setting_text_4'"
  icon = "'$urlicon'/project2.png"
  config-sh = "'$ETC'/tool-tree.bash Project 2"

  [[group]]
  [[page]]
  title = "'$customize_text'"
  desc = "'$more_text_6'"
  icon = "'$urlicon'/apk_utility.png"
  config-sh = "'$ETC'/tool-tree.bash Utiliapk"
  lock = "[ -f $HOME/check_unpack ] && echo \"'$boot_text_1'\" || echo 0"

  [[group]]
  [[page]]
  title = "'$tools_text'"
  desc = "'$more_text_7'"
  icon = "'$urlicon'/tool_apk.png"
  config-sh = "'$ETC'/tool-tree.bash Troot"

  [[group]]
  [[page]]
  title = "'$plugin_text'"
  desc = "'$more_text_8'"
  icon = "'$urlicon'/apk_addon.png"
  process = true
  config-sh = "'$ETC'/tool-tree.bash Addon $AOK"
  '
}

Info() {
  echo '
  [[group]]
  [[page]]
  title = "'$setting_text_1'"
  desc = "'$setting_text_2'"
  icon = "'$urlicon'/info.png"
  config-sh = "'$ETC'/tool-tree.bash Update"
    
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
  echo -e "|'$default_text'\nai|Gemini Ai\nen|English\nvi|Việt nam\nru|Русский\nhu|Hungarian\nid|Indonesia\nes|Spanish"
  """
  get = "glog language_kkts"
  set = """
    if [ "$state" == "ai" ]; then
      if transai -c; then
        slog language_kkts "$state"
        auto_trans
        echo "exit:[restart]"
        exit
      else
      echo
      echo "'$warn_gemini_text'" >&2
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
  [[action]]
  id = "chatgemini"
  title = "Chat Gemini"
  desc = "'$chat_gemini_text'"
  icon = "'$urlicon'/chatai.png"
  lock-sh = "[ -z $(glog api_genmini) ] && echo \"'$warn_gemini_text'\" || echo 0"
  script = """
  [ "$models_thinking" -gt 0 ] && thingkk="-t $models_thinking"
  transai -m "$chatai" -l "$chatai_lang" $thingkk
  """
  
  [[action.params]]
  name = "models_genmini"
  placeholder = "gemini-3.1-flash-lite"
  title = "Models Gemini"
  editable = true
  label = "Models"
  items = [ "gemini-3.5-flash-lite", "gemini-3.1-flash-lite" ]
  value-sh = "glog models_genmini gemini-3.1-flash-lite"
  
  [[action.params]]
  name = "models_thinking"
  title = "'$chatai_think_text'"
  label = "'$option_text'"
  items = [ "0|'$default_text'", "1024", "2048", "4096" ]
  value = "0"
  
  [[action.params]]
  name = "chatai_lang"
  title = "'$permis_text_5'"
  label = "'$permis_text_2'"
  placeholder = "en-US"
  type = "text"
  value = "'$LANGUAGE'-'$COUNTRY'"
  
  [[action.params]]
  name = "chatai_nextrule"
  title = "'$chatai_rule_text'"
  placeholder = "- Always return text in uppercase"
  type = "text"
  
  [[action.params]]
  name = "chatai"
  title = "'$doc_text'"
  placeholder = "Hello Gemini"
  type = "text"
  required = true

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
    auto-kill = true
    silent = true
    title = "'$reset_data_text'"
    script = """
    slog -d boot_ver_code
    slog -d sum_onl_plugin
    slog -d sum_moduls
    slog -d sum_ver_boot
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
  [[text]]
  [[text.rows]]
  html-file = "'$ETC'/html/tool-tree.html"
  html-height = 100
  
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
  title = "'$download_text' stable"
  icon = "'$urlicon'/update.png"
  url-sh = "linkgit https://api.github.com/repos/Kakathic/Tool-Tree/releases/latest Tool-Tree"
  script = "openfile $state"
  
  [[group]]
  [[download]]
  title = "'$download_text' beta"
  icon = "'$urlicon'/update2.png"
  url = "https://github.com/Kakathic/Tool-Tree/releases/download/beta/Tool-Tree-beta.apk"
  script = "openfile $state"
  '
}

Project() {

  if [ "$1" == 1 ]; then
  clear_shell='findfile folders $SDH/$PTSH'
  else
  clear_shell='findfile folders $APK/$PTAH'
  fi

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

  [[group]]
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
    options-sh = "'$clear_shell'"
    multiple = true
'

if [ "$1" == 1 ]; then
  echo '
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
    items = [ "0|imgextractor", "1|imgkit_scuti" ]

    [[action.params]]
    name = "un_tool_erofs"
    title = "'$tool_unpack_text' erofs"
    label = "'$option_text'"
    value-sh = "glog un_tool_erofs"
    items = [ "0|extract.erofs", "1|imgkit_scuti" ]

    [[action.params]]
    name = "un_tool_f2fs"
    title = "'$tool_unpack_text' f2fs"
    label = "'$option_text'"
    value-sh = "glog un_tool_f2fs 0"
    items = [ "0|extract.f2fs", "1|imgkit_scuti" ]

    [[action.params]]
    name = "re_tool_ext4"
    title = "'$tool_repack_text' ext4"
    label = "'$option_text'"
    value-sh = "glog re_tool_ext4 1"
    items = [ "0|make_ext4fs", "1|mke2fs+e2fsdroid", "2|imgkit_scuti" ]

    [[action.params]]
    name = "re_tool_erofs"
    title = "'$tool_repack_text' erofs"
    label = "'$option_text'"
    value-sh = "glog re_tool_erofs"
    items = [ "0|mkfs.erofs", "1|imgkit_scuti" ]

    [[action.params]]
    name = "re_tool_f2fs"
    title = "'$tool_repack_text' f2fs"
    label = "'$option_text'"
    value-sh = "glog re_tool_f2fs 0"
    items = [ "0|sload_f2fs" ]
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
    items = [ "512", "1024", "2048", "3072", "4096", "5120", "6144", "7168", "8192" ]

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
  slog models_genmini "$models_genmini"
  transai -c
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
    items = [ "gemini-3.5-flash-lite", "gemini-3.1-flash-lite" ]
    value-sh = "glog models_genmini gemini-3.1-flash-lite"
  '
}

Root() {
  echo '
  [[group]]
  [[action]]
  title = "'$mount_text_1'"
  summary = "'$text_root'"
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
  summary = "'$text_root'"
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
  summary = "'$text_root'"
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

  [[group]]
  [[action]]
  title = "'$flash_text_1'"
  summary = "'$text_root'"
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
  summary = "'$text_root'"
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
    items = [ "everything", "speed", "speed-profile", "verify" ]

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

    [[action.params]]
    name = "payload_super_size"
    label = "'$sizes_text'"
    desc = "'$default_text': 11GB, '$payload_text_4'"
    type = "number"
    value = "11"
    required = true
    depend-on = "payload_switch"
    depend-value = "0"
    depend-mode = "hide"
    depend-readonly = true

    [[action.params]]
    name = "payload_super_group"
    label = "'$super_text_5'"
    desc = "'$super_text_6', '$payload_text_4'"
    value = "qti_dynamic_partitions"
    required = true
    depend-on = "payload_switch"
    depend-value = "0"
    depend-mode = "hide"
    depend-readonly = true

    [[action.params]]
    name = "sign_payload"
    label = "'$sign_text'"
    value = "testkey"
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

    [[action.params]]
    name = "amlogic_ver"
    label = "'$version_text'"
    value = "v2"
    items = [ "v2", "v1" ]

    [[action.params]]
    name = "amlogic_align"
    label = "'$alignment_text'"
    value = "8"
    items = [ "4|4", "8|8 (Android 11+)" ]

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
    title = "'$apex_text'"
    config-sh = "'$ETC'/tool-tree.bash Apex"
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
    slog nen_br "$nen_br"
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

    [[action.params]]
    name = "format_img"
    label = "'$option_text'"
    value = "raw"
    required = true
    items = [ "raw|File.img (raw)", "sparse|File.img (sparse)", "dat|File.new.dat", "br|File.new.dat.br", "zstd|File.img.zstd", "zst|File.img.zst", "lzma|File.img.lzma", "lz4|File.img.lz4", "xz|File.img.xz", "gz|File.img.gz" ]

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
    
    [[action.params]]
    name = "nounpak"
    label = "'$decode_text_1'"
    type = "switch"
    
    [[action.params]]
    name = "xoa_oat_boot"
    label = "'$xoaoat_text_1'"
    type = "switch"
    depend-on = "nounpak"
    depend-value = "1"
    depend-mode = "hide"
    depend-readonly = true
    
    [[action.params]]
    name = "text_oat_boot"
    type = "text"
    value = "fsv_meta,oat,vdex,odex,prof,bprof"
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
    name = "box_btn"
    value = [ "avb", "enc", "recovery" ]
    multiple = true
    label = "'$option_text'"
    items = [ "avb|'$builds_text_81'", "enc|'$builds_text_82'", "recovery|'$builds_text_83'" ]
    depend-on = "vavb"
    depend-value = "1"
    depend-mode = "show"
    depend-default = "hide"
    
    [[action.params]]
    name = "pcvbmeta"
    label = "'$patch_text' vbmeta"
  items = [ "1|'$disable_text' dm-verity (hex 020)", "2|'$disable_text' Verification", "3|'$disable_text' dm-verity (hex 020) + Verification", "4|'$disable_text' dm-verity (hex 010)", "5|'$disable_text' dm-verity + Verification (hex 030)" ]
    depend-on = "nounpak"
    depend-value = "1"
    depend-mode = "hide"
    depend-readonly = true
    no-select = true
    
    [[action.params]]
    name = "IMAGES"
    desc = "'$input_file_text': br, dat, img, zst, zstd, bin, zip"
    options-sh = "findfile 2 $PTSD"
    required = true
    multiple = true
    remember = false
  
  [[action]]
  title = "'$build_text'"
  desc = "'$desc_rom1'"
  icon = "'$urlicon'/build.png"
  script = """
  slog nen_br "$nen_br"
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

    [[action.params]]
    name = "IMAGES"
    title = "'$list_partition_text'"
    desc = "'$builds_text_1'"
    options-sh = "findfile 0 $SDH/$PTSH"
    required = true
    multiple = true
    remember = false

    [[action.params]]
    name = "dinh_dang"
    label = "'$build_text'"
    desc = "'$builds_text_2'"
    items = [ "0|'$default_text'", "1|RO (EROFS)", "2|RW (EXT4)", "3|RO (F2FS)", "4|RW (F2FS)" ]
    depend-on = "IMAGES"
    depend-value = "(erofs),(ext),(f2fs)"
    depend-mode = "show"
    depend-default = "hide"

    [[action.params]]
    name = "on_f2fs_nen"
    label = "'$lall_nen_f2fs_text'"
    desc = "'$nen_f2fs_text'"
    type = "switch"
    depend-on = "dinh_dang"
    depend-value = "(F2FS)"
    depend-mode = "show"
    depend-default = "hide"

    [[action.params]]
    name = "dang_nen"
    label = "'$option_text'"
    desc = "'$builds_text_3'"
    value = "lz4hc"
    items = [ "lz4hc", "lz4", "lzma", "deflate", "zstd" ]
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
    value = "8"
    depend-on = "dang_nen"
    depend-value = "lz4"
    depend-mode = "hide"
    depend-logic = "priority"

    [[action.params]]
    name = "format_imgs"
    label = "'$convert_text'"
    desc = "'$convert_img_text'"
    value = "raw"
    depend-on = "IMAGES"
    depend-value = "(erofs),(ext),(f2fs)"
    depend-mode = "show"
    depend-readonly = true
    items = [ "raw|File.img (raw)", "sparse|File.img (sparse)", "zstd|File.img.zstd", "zst|File.img.zst", "dat|File.new.dat", "br|File.new.dat.br" ]

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
    name = "time_build"
    label = "'$time_text'"
    desc = "'$build_time_text_1': '$time_riviu'"
    type = "number"
    required = true
    value-sh = "glog build_times"
    depend-on = "IMAGES"
    depend-value = "(erofs),(ext),(f2fs)"
    depend-mode = "show"
    depend-readonly = true

    [[action.params]]
    name = "offfscontex"
    label = "'$patch_text_fscontex'"
    desc = "'$patch_text_fsdesc'"
    type = "switch"
    value = "1"
    depend-on = "IMAGES"
    depend-value = "(erofs),(ext),(f2fs)"
    depend-mode = "show"
    depend-readonly = true

    [[action.params]]
    name = "build_size"
    label = "'$sizes_text'"
    desc = "'$builds_text_7'"
    type = "number"
    value = "0"
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
    repack_super -m "$IMAGES" -g "$super_group" -s "$super_size" -f "$from" -t "$type" -i "$PTSD"
    echo
    checktime
  """

    [[action.params]]
    name = "type"
    label = "'$super_text_2'"
    value = "VAB"
    items = [ "A|a_only", "AB|ab", "VAB|virtual_ab" ]

    [[action.params]]
    name = "from"
    label = "'$super_text_3'"
    value = "raw"
    items = [ "raw", "sparse" ]

    [[action.params]]
    name = "super_size"
    label = "'$sizes_text'"
    desc = "'$default_text': 8.5GB"
    type = "number"
    placeholder = "8.5"
    value = "8.5"
    required = true

    [[action.params]]
    name = "super_group"
    label = "'$super_text_5'"
    desc = "'$super_text_6'"
    value = "qti_dynamic_partitions"
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

    [[action.params]]
    name = "slipdhhe"
    label = "'$number_text'"
    title = "'$split_number_desc'"
    type = "number"
    min = 2
    max = 98
    value = "9"
    required = true

    [[action.params]]
    name = "khoi_dau_dem"
    label = "'$split_number_label'"
    type = "switch"
    value = "0"

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
    value = "1"

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

    [[action.params]]
    title = "'$apex_text_1'"
    name = "nen_apex"
    label = "'$option_text'"
    value = "auto"
    items = [ "auto|'$default_text'", "0|'$off_text'", "1|'$on_text'" ]

    [[action.params]]
    name = "SIGNS"
    label = "'$sign_text'"
    value = "testkey"
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
  title = "'$decompile_text'"
  desc = "'$desc_apks'"
  icon = "'$urlicon'/decom.png"
  warn = "'$decom_apk_text_15'"
  script = """
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
    value = "apkeditor"
    items = [ "apkeditor|Apkeditor", "apktool|Apktool" ]

    [[action.params]]
    name = "mutiresk"
    title = "'$decom_apk_text_11'"
    label = "'$option_text'"
    value = "1"
    items = [ "0|'$decom_apk_text_3'", "1|'$default_text'", "2|'$decom_apk_text_5'" ]
    depend-on = "tooldecom"
    depend-value = "apkeditor"
    depend-mode = "hide"

    [[action.params]]
    name = "type_apk"
    title = "'$decom_apk_text_11'"
    label = "'$option_text'"
    value = "xml"
    depend-on = "tooldecom"
    depend-value = "apktool"
    depend-mode = "hide"
    items = [ "raw|'$decom_apk_text_3'", "xml|'$default_text'", "reso|'$decom_apk_text_10'" ]

    [[action.params]]
    name = "dexlibk"
    title = "'$decom_apk_text_12'"
    label = "'$option_text'"
    value = "2"
    items = [ "0|'$decom_apk_text_3'", "1|'$default_text'", "2|Baksmali 3.0.9" ]
    depend-on = "tooldecom"
    depend-value = "apkeditor"
    depend-mode = "hide"

    [[action.params]]
    name = "dexlib"
    title = "'$decom_apk_text_12'"
    label = "'$option_text'"
    value = "smali"
    items = [ "nodex|'$decom_apk_text_3'", "internal|'$default_text'", "smali|Baksmali 3.0.9" ]
    depend-on = "tooldecom"
    depend-value = "apktool"
    depend-mode = "hide"

    [[action.params]]
    name = "xoa_debug_info"
    label = "'$decom_apk_text_7'"
    type = "switch"
    value = "1"
    depend-on = "dexlib|dexlibk"
    depend-value = "nodex|0"
    depend-mode = "hide|hide"
    depend-cascade = false
    depend-readonly = true

    [[action.params]]
    name = "redivdd"
    label = "'$decom_apk_text_14'"
    type = "switch"
    value = "0"
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
    value = "64000"
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
    value = "0"

    [[action.params]]
    name = "sign"
    label = "'$sign_text'"
    value = "default"
    options-sh = "findfile file $ETC/key .pk8 | sed \"s|.pk8||\""

    [[action.params]]
    name = "sstring"
    label = "'$build_apk_text_1'"
    type = "switch"
    value = "1"

    [[action.params]]
    name = "copysign"
    label = "'$decom_apk_text_13'"
    type = "switch"
    depend-on = "FOLDER"
    depend-value = "(apktool)"
    depend-mode = "show"
    depend-default = "hide"

    [[action.params]]
    name = "comlib"
    label = "'$addlang_text_2'"
    desc = "'$addlang_text_3'"
    value = "manifest"
    items = [ "manifest|'$default_text'", "true|'$on_text'", "false|'$off_text'" ]

    [[action.params]]
    name = "FOLDER"
    desc = "'$builds_text_1'"
    options-sh = "findfile forapk $APK/$PTAH"
    required = true
    multiple = true

  [[group]]
  [[page]]
  title = "'$apex_text'"
  icon = "'$urlicon'/apex.png"
  config-sh = "'$ETC'/tool-tree.bash Apex"
    
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
    options-sh = "findfile 10 $PTAD"
    required = true

    [[action.params]]
    name = "FILE2"
    title = "'$restore_apk_text_2'"
    options-sh = "findfile 10 $PTAD"
    required = true
  '
}

Addon() {

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
    summary = "'$sumstxt$version' '$author'"
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
      
      [ -f "$dirvad/nodelete" ] || delete_add='
      [[page.rows]]
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
      summary = "'$sumstxt$version' '$author'"
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
    [ "$ROT" == 1 ] || sumstxt="$text_root | "
    croot_add='lock = "'$LOT'|'$root_warning_text'"'
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

}

# Điều hướng chính
"$@"
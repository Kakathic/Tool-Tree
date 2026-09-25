#!/data/data/com.tool.tree/files/home/bin/bash
# Kakathic

# home
home() {
menuadd "$MPAT"
echo '
  [[group]]
  title = "'$google_text'"
  
  [[fab]]
  [[fab.items]]
  type = "refresh"
  style = "fab"
  icon = "'$ETC'/icon/Loading.png"
  
  [[action]]
  warn = "'$lang_action_warn'"
  title = "'$lang_title'"
  summary = "'$lang_summary_path''$path_modun'"
  reload = "true"
  script = """
  
  slog uri_adb_moduls "$uri_adb_moduls"
  slog uri_file_modun "$uri_file_modun"
  [ -f '$path_modun'/remove ] && rm -fr '$path_modun'/remove
  mkdir -p '$path_modun' '$path_modun2'
  
  if [ -f "$uri_file_modun" ] && [ -n "$uri_adb_moduls" ]; then
    mkdir -p "'$path_modun2'${uri_adb_moduls%/*}"
    cp -rf "$uri_file_modun" "'$path_modun2'$uri_adb_moduls"
    echo "'$lang_save_at''$path_modun2'$uri_adb_moduls"
    echo
  else
    echo "'$lang_searching'"
    echo
    for vc in $uri_file_modun; do
      link_find_file="$(find -L /system -name "${vc##*/}" -type f -print -quit)"
      if [ "$link_find_file" ]; then
        mkdir -p "'$path_modun2'${link_find_file%/*}"
        cp -rf "$vc" "'$path_modun2'$link_find_file"
        echo "'$lang_save_at''$path_modun2'$link_find_file"
        echo
      else
        echo "'$lang_not_found'${vc##*/}'$lang_input_notice'"
      fi
    done
  fi
  
  echo -e "id=Tool-Tree\nname=Tool-Tree Module\nversion=1.0\nversionCode=100\nauthor=Kakathic\ndescription=Modified system files" | tee '$path_modun'/module.prop
  touch '$path_modun'/update
  cp -rf '$MPAT'/service.sh '$path_modun2'/service.sh
  [ -f '$MPAT'/system.prop ] && cp -rf '$MPAT'/system.prop '$path_modun2'/system.prop
  set_permis -R -o 0:0 -c u:object_r:system_file:s0 '$path_modun2'/system
  """
    [[action.params]]
    name = "uri_file_modun"
    value-sh = "glog uri_file_modun"
    path-home = "'$PTAD'"
    multiple = true
    required = "required"
    type="file"

    [[action.params]]
    name = "uri_adb_moduls"
    desc = "'$lang_desc_adb'"
    placeholder = "/system_ext/priv-app/Settings/Settings.apk"
    value-sh = "glog uri_adb_moduls"
    type = "text"

  [[group]]
  [[editor]]
  title = "'$lang_desc_prop'"
  file = "'$MPAT'/system.prop"
  value = "ro.control_privapp_permissions=log"
  placeholder = "ro.control_privapp_permissions=log"
  visible = "'$visisj'"

  [[group]]
  [[editor]]
  title = "'$lang_desc_service'"
  file = "'$MPAT'/service.sh"
  visible = "'$visisj'"
  
  [[group]]
  [[action]]
  title = "'$lang_del_tile'"
  visible = "'$visisj'"
  reload = "true"
  script = """
  for vcx in $del_file_modun; do
      echo "Delete file: $vcx"
      [ -f "$vcx" ] && rm -fr "$vcx"
  done
  """

    [[action.params]]
    name = "del_file_modun"
    desc = "'$lang_del_desc2' '$path_modun2'"
    value-sh = "glog del_file_modun"
    label = "@string/options_text"
    options-sh = "[ -d '$path_modun2' ] && find '$path_modun2'/system -type f -printf '"'%p|%f\\n'"'"
    required = "required"
    multiple = "true"

  [[group]]
  [[switch]]
  title = "@string/remove_text Module"
  get = "[ -f '$path_modun'/remove ] && echo 1"
  reload = "true"
  shell = "hidden"
  set = """
  if [ "$state" == 1 ]; then
  [ -d '$path_modun' ] && touch '$path_modun'/remove
  [ -f '$path_modun'/update ] && rm -f '$path_modun'/update
  [ -d '$path_modun2' ] && rm -fr '$path_modun2'
  else
  rm -fr '$path_modun'/remove
  fi
  """
  confirm = true
  visible = "'$visisj'"
  '
  [ "$visisj" == 1 ] && echo '
  [[text]]
  desc = "'$list_modul'"
  summary-sh = """
  [ -d '$path_modun' ] && tree '$path_modun';
  echo;
  [ -d '$path_modun2' ] && tree '$path_modun2'
  """
  '
}

# Đường dẫn mô-đun
path_modun="/data/adb/modules/Tool-Tree"
path_modun2="/data/adb/modules_update/Tool-Tree"
[ -d $path_modun ] && visisj=1 || visisj=0

# Ngôn ngữ & Google dịch
source langadd "${0%/*}"
"$@"

#!/data/data/com.tool.tree/files/home/bin/bash
# Kakathic

home() {
menuadd "$MPAT"
echo '
[[group]]
title = "'$google_text'"
  [[action]]
  title = "'$mage_name_text'"
  desc = "'$merge_partition_text'"
  script = """
    slog silencekd "$silence"
    slog dang_filehd "$dang_file"
    '$MPAT'/bin/combine_img $silence $dang_file "$MUTIIMG" "$IMAGE"
    checktime
  """
  [[action.params]]
    name = "silence"
    label = "'$delete_text'"
    type = "checkbox"
    value-sh = "glog silencekd"
  [[action.params]]
    name = "dang_file"
    title = "'$merge_partition_1'"
    label = "'$select_text'"
    value-sh = "glog dang_filehd"
    options-sh = """
    echo -e "0|'$default_text'\n1|erofs\n2|ext4\n3|'$pack_img_text'"
    """
  [[action.params]]
    name = "MUTIIMG"
    desc = "'$merge_partition_3'"
    label = "'$select_text'"
    options-sh = "findfile 6 $PTSD"
    required = "true"
  [[action.params]]
    name = "IMAGE"
    desc = "'$merge_partition_5'"
    label = "'$select_text'"
    multiple = "true"
    options-sh = """
    findfile 3 $PTSD | sed "/^system\\./d"
    """
[[group]]
  [[action]]
  warn = "'$oat_text_1'"
  title = "'$oat_text_3'"
  desc = "'$oat_text_4'"
  script = """
    slog features_oat "$features_oat"
    slog apps_apk_oat "$apps_apk_oat"
    slog secontex "$secontex"
    slog services_switch "$services_switch"
    slog framework_switch "$framework_switch"
    '$MPAT'/bin/dex2oat
  """
  [[action.params]]
    name = "PTSH"
    title = "'$config_text_1'"
    label = "'$setting_text_3'"
    options-sh = "findfile for $SDH | grep -v raw"
    value-sh = "glog PTSH"
  [[action.params]]
    name = "framework_switch"
    label = "'$oat_text_5'"
    type = "switch"
    value-sh = "glog framework_switch 1"
  [[action.params]]
    name = "services_switch"
    label = "'$oat_text_6'"
    type = "switch"
    value-sh = "glog services_switch 1"
  [[action.params]]
    name = "features_oat"
    label = "'$oat_text_7'"
    placeholder = "default"
    type = "text"
    value-sh = "glog features_oat default"
  [[action.params]]
    name = "apps_apk_oat"
    desc = "'$oat_text_10'"
    label = "'$oat_text_8'"
    placeholder = "default"
    type = "text"
    options-sh = "'$MPAT'/bin/listapk"
    value-sh = "glog apps_apk_oat"
    multiple = "true"
  [[action.params]]
    name = "secontex"
    desc = "'$oat_text_9'"
    placeholder = "PCL[]"
    type = "text"
    value-sh = "glog secontex"
[[group]]
  [[action]]
  title = "Sign boot"
  desc = "Sign AVB 1.0 boot, vendor_boot"
  script = """
    slog name_boot_key "$NAME"
    slog sign_boot_key "$SIGN"
    mkdir -p $PTSD/out
    cp -rf "$PTSD/$FILE" "$PTSD/out/$FILE"
    magiskboot sign "$PTSD/out/$FILE" "/$NAME" "$ETC/key/$SIGN.x509.pem" "$ETC/key/$SIGN.pk8" &>/dev/null
    magiskboot verify "$PTSD/out/$FILE" "$ETC/key/$SIGN.x509.pem" 2>&1 || abort "failed to sign"
    echo
    echo "'$save_text': $PTSD/out/$FILE"
  """
  [[action.params]]
    name = "NAME"
    label = "'$name_text'"
    value-sh = "glog name_boot_key boot"
    type = "text"
    placeholder = "boot"
  [[action.params]]
    name = "SIGN"
    value-sh = "glog sign_boot_key testkey"
    label = "'$sign_text'"
    options-sh = "findfile file $ETC/key x509.pem | sed \"s|.x509.pem||\""
  [[action.params]]
    name = "FILE"
    desc = "'$input_text' .img, '$folder_text' '$PTSD'"
    options-sh = "cd $PTSD; ls *.img | grep boot"
    label = "'$select_text'"
    required = "true"
[[group]]
  [[action]]
  title = "Protoc"
  desc = "'$protoc_text'"
  script = """
  for vvc in $FILE; do
  if [ $(file $PTSD/$vvc | grep -cm1 "data") == 1 ]; then
    if [ "$LIST" == "Xml" ]; then
    protoc_pb.py -d "$PTSD/$vvc" > "$PTSD/${vvc%.*}.xml"
    echo "'$save_text': $PTSD/${vvc%.*}.xml"
    else
    protoc_pb.py --json -d "$PTSD/$vvc" > "$PTSD/${vvc%.*}.json"
    echo "'$save_text': $PTSD/${vvc%.*}.json"
    fi
  elif [ $(file $PTSD/$vvc | grep -cm1 "text") == 1 ]; then
    protoc_pb.py -e "$PTSD/$vvc" -o "$PTSD/${vvc%.*}_new.pb"
    [ -f "$PTSD/${vvc%.*}_new.pb" ] && echo "'$save_text': $PTSD/${vvc%.*}_new.pb"
  else
    echo "'$error_text' $vvc" >&2
  fi
  done
  """
  [[action.params]]
    name = "LIST"
    label = "'$select_text'"
    options-sh = "echo -e \"Xml\nJson\""
  [[action.params]]
    name = "FILE"
    desc = "'$input_text' .pb .json .xml, '$folder_text' '$PTSD'"
    multiple = "true"
    options-sh = "findfile file $PTSD \".pb|.json|.xml\""
    required = "true"
[[group]]
  [[action]]
  title = "Mi Thermal"
  desc = "'$mi_thermal_text'"
  script = """
  for vvc in $FILE; do
  if [ $(file $PTSD/$vvc | grep -cm1 "data") == 1 ]; then
  thermal-crypt.py -i "$PTSD/$vvc" -o "$PTSD/${vvc%.*}.txt"
  elif [ $(file $PTSD/$vvc | grep -cm1 "text") == 1 ]; then
  thermal-crypt.py -e -i "$PTSD/$vvc" -o "$PTSD/${vvc%.*}_new.conf"
  else
  echo "'$error_text' $vvc" >&2
  fi
  done
  """
  [[action.params]]
    name = "FILE"
    desc = "'$input_text' .conf, .txt '$folder_text' '$PTSD'"
    multiple = "true"
    options-sh = "findfile file $PTSD \".conf|.txt\""
    required = "true"
[[group]]
  [[action]]
  title = "Unpack splitapp"
  desc = "'$splitapp_desc_text'"
  script = """
  if [ -f "$FILE" ]; then
  splitapp.py -f "$FILE" -o $PTSD/out
  echo
  echo "'$save_text': $PTSD/out"
  else
  echo "'$error_text' $FILE" >&2
  fi
  """
  [[action.params]]
    name = "FILE"
    label = "'$select_text'"
    desc = "'$input_text' .APP, '$folder_text' '$PTSD'"
    options-sh = "findfile file $PTSD \".app|.APP\""
    required = "true"
  [[action]]
  title = "Unpack pac"
  desc = "'$pac_desc_text'"
  script = """
  if [ -f "$FILE" ]; then
  unpac.py extract -d $PTSD/out "$FILE"
  echo
  echo "'$save_text': $PTSD/out"
  else
  echo "'$error_text' $FILE" >&2
  fi
  """
  [[action.params]]
    name = "FILE"
    label = "'$select_text'"
    desc = "'$input_text' .pac, '$folder_text' '$PTSD'"
    options-sh = "findfile file $PTSD .pac"
    required = "true"
'
}

# Ngôn ngữ & Google dịch
source langadd "${0%/*}"
"$@"

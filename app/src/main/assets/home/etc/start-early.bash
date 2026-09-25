#!/data/data/com.tool.tree/files/home/bin/bash
# Kakathic

am(){ /system/bin/am "$@"; }
dumpsys(){ /system/bin/dumpsys "$@"; }

# Dọn dẹp tmp
find "$TMPDIR" -maxdepth 1 ! -path "$TMPDIR" ! -name '*.log' -exec rm -rf {} +
rm -fr $TEMP/documents $TEMP/kr_download_* $START_DIR/icons/*

[ -z "$(glog api_genmini)" ] && transai -c &

(
# Tự động cập nhật add-on
urlgitv1="https://github.com/Kakathic/Tool-Tree/releases/download/V1"
aokshum="$(get_shum 'V1' "AOK.zip")"
if [[ -n "$aokshum" ]] && [[ "$aokshum" != "$(glog aok_shum "$aokshum")" ]]; then
  taive -s "$urlgitv1/AOK.zip" "$TMP/AOK.zip" && unzip -o "$TMP/AOK.zip" -d "$AOK"
  slog aok_shum "$aokshum"
fi
# add-on 2
aonshum="$(get_shum 'V1' "AON.zip")"
if [[ -n "$aonshum" ]] && [[ "$aonshum" != "$(glog aon_shum "$aonshum")" ]]; then
  taive -s "$urlgitv1/AON.zip" "$TMP/AON.zip" && unzip -o "$TMP/AON.zip" -d "$AON"
  slog aon_shum "$aonshum"
fi
# add-on 3
uplshum="$(get_shum 'V1' "UPL.zip")"
if [[ -n "$uplshum" ]] && [[ "$uplshum" != "$(glog upl_shum "$uplshum")" ]]; then
  taive -s "$urlgitv1/UPL.zip" "$TMP/UPL.zip" && unzip -o "$TMP/UPL.zip" -d "$UPL"
  slog upl_shum "$uplshum"
fi
) &

{

# Cấp quyền tự động nếu đã root
chown -R 0:0 $HOME/.cache

# Tạo link home
if [ ! -e /data/local/TOOL ]; then
  set_permis -R /data/local/TOOL
  ln -sf $APK /data/local/TOOL
fi

if [ ! -e /data/local/TREE ]; then
  set_permis -R /data/local/TREE
  ln -sf $SDH /data/local/TREE
fi

# Thêm không giới hạn tiết kiệm pin
dumpsys deviceidle whitelist +$PACKAGE_NAME

# Giữ nền
am set-inactive --user 0 $PACKAGE_NAME false
am set-standby-bucket $PACKAGE_NAME active
am set-bg-restriction-level --user 0 $PACKAGE_NAME unrestricted
am unfreeze --sticky $PACKAGE_NAME

# Chạy nền
cmd appops set $PACKAGE_NAME RUN_IN_BACKGROUND allow
cmd appops set $PACKAGE_NAME RUN_ANY_IN_BACKGROUND allow
cmd appops set $PACKAGE_NAME WAKE_LOCK allow

# Cấp quyền ở MIUI, HyperOS
cmd appops set $PACKAGE_NAME 10022 allow
cmd appops set $PACKAGE_NAME GET_USAGE_STATS allow
cmd appops set $PACKAGE_NAME QUERY_ALL_PACKAGES allow

# Phím tắt màn hình chính
cmd appops set $PACKAGE_NAME 10017 allow

# Loaded sẵn danh sách img
search_image &>/dev/null

} &

# Dọn bộ đếm
rm -fr $AON/*/zcheck $AOK/*/zcheck &

# Cấp quyền 755 tự động
set_permis $AON/*/* $AOK/*/* &>/dev/null

# Khởi động các file shell ở add-on
for vadd in $AON/* $AOK/*; do
  if [ -f "$vadd/early_start.bash" ]; then
  echo "Run shell: $vadd/early_start.bash"
  $vadd/early_start.bash &
  fi
done

#!/data/data/com.tool.tree/files/home/bin/bash
# Kakathic

# Dọn dẹp tmp
find "$TMPDIR" -maxdepth 1 ! -path "$TMPDIR" ! -name '*.log' -exec rm -rf {} +
rm -fr $TEMP/documents $TEMP/kr_download_* $START_DIR/icons/*

check_update boot &

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
am set-inactive --user 0 $PACKAGE_NAME false
am set-standby-bucket $PACKAGE_NAME active
am set-bg-restriction-level --user 0 $PACKAGE_NAME unrestricted
am unfreeze --sticky $PACKAGE_NAME
cmd appops set $PACKAGE_NAME RUN_IN_BACKGROUND allow
cmd appops set $PACKAGE_NAME RUN_ANY_IN_BACKGROUND allow
cmd appops set $PACKAGE_NAME WAKE_LOCK allow
# Cấp quyền ở MIUI, HyperOS
cmd appops set $PACKAGE_NAME 10022 allow
cmd appops set $PACKAGE_NAME GET_USAGE_STATS allow
[ "$API" -ge 30 ] && cmd appops set $PACKAGE_NAME QUERY_ALL_PACKAGES allow
# Phím tắt màn hình chính
cmd appops set $PACKAGE_NAME 10017 allow
# Loaded sẵn danh sách img
search_image &>/dev/null
} &

{
  # Dọn bộ đếm
  rm -fr $AON/*/zcheck $AOK/*/zcheck
  # Cấp quyền 755 tự động
  set_permis $AON/*/* $AOK/*/* &>/dev/null
  # Khởi động các file shell ở add-on
  for vadd in $AON/* $AOK/*; do
  if [ -f "$vadd/early_start.bash" ]; then
  echo "Run shell: $vadd/early_start.bash"
  $vadd/early_start.bash &
  elif [ -f "$vadd/early_start.sh" ]; then
  echo "Run shell: $vadd/early_start.sh"
  $vadd/early_start.sh &
  fi
  done
}

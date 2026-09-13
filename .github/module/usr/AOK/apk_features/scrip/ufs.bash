#!/data/data/com.tool.tree/files/home/bin/bash
# kakathic

# Ngôn ngữ mặc định
source langadd "${0%/*}"

bDeviceLifeTimeEstA=""

# --- Life Time Estimation A ---
if [ -f /sys/devices/platform/soc/1d84000.ufshc/health_descriptor/life_time_estimation_a ]; then
    bDeviceLifeTimeEstA=$(cat /sys/devices/platform/soc/1d84000.ufshc/health_descriptor/life_time_estimation_a)

elif [ -f /sys/devices/virtual/mi_memory/mi_memory_device/ufshcd0/dump_health_desc ]; then
    bDeviceLifeTimeEstA=$(grep bDeviceLifeTimeEstA \
        /sys/devices/virtual/mi_memory/mi_memory_device/ufshcd0/dump_health_desc \
        | cut -d '=' -f2 | awk '{print $1}')

else
    bDeviceLifeTimeEstA=$(grep bDeviceLifeTimeEstA \
        /sys/kernel/debug/*.ufshc/dump_health_desc 2>/dev/null \
        | cut -d '=' -f2 | awk '{print $1}')
fi

dump_files=$(find /sys -name "dump_*_desc" 2>/dev/null | grep ufshc)

if [ -z "$bDeviceLifeTimeEstA" ]; then
    for line in $dump_files; do
        str=$(grep bDeviceLifeTimeEstA "$line" | cut -d '=' -f2 | awk '{print $1}')
        [ -n "$str" ] && bDeviceLifeTimeEstA="$str" && break
    done
fi

if [ -z "$bDeviceLifeTimeEstA" ]; then
    for line in $(find /sys -name "life_time_estimation_a" 2>/dev/null | grep ufshc); do
        str=$(cat "$line")
        [ -n "$str" ] && bDeviceLifeTimeEstA="$str" && break
    done
fi

case "$bDeviceLifeTimeEstA" in
    0x00|0x0) echo "$ufs_text_3 $ufs_text_2" ;;
    0x01|0x1) echo "$ufs_text_3 0% ~ 10%" ;;
    0x02|0x2) echo "$ufs_text_3 10% ~ 20%" ;;
    0x03|0x3) echo "$ufs_text_3 20% ~ 30%" ;;
    0x04|0x4) echo "$ufs_text_3 30% ~ 40%" ;;
    0x05|0x5) echo "$ufs_text_3 40% ~ 50%" ;;
    0x06|0x6) echo "$ufs_text_3 50% ~ 60%" ;;
    0x07|0x7) echo "$ufs_text_3 60% ~ 70%" ;;
    0x08|0x8) echo "$ufs_text_3 70% ~ 80%" ;;
    0x09|0x9) echo "$ufs_text_3 80% ~ 90%" ;;
    0x0A|0xA) echo "$ufs_text_3 90% ~ 100%" ;;
    0x0B|0xB) echo "$ufs_text_3 $ufs_text_4" ;;
    *)        echo "$ufs_text_3 $ufs_text_2" ;;
esac
echo

# --- Pre EOL Info ---
bPreEOLInfo=""

if [ -f /sys/devices/platform/soc/1d84000.ufshc/health_descriptor/eol_info ]; then
    bPreEOLInfo=$(cat /sys/devices/platform/soc/1d84000.ufshc/health_descriptor/eol_info)

elif [ -f /sys/devices/virtual/mi_memory/mi_memory_device/ufshcd0/dump_health_desc ]; then
    bPreEOLInfo=$(grep bPreEOLInfo \
        /sys/devices/virtual/mi_memory/mi_memory_device/ufshcd0/dump_health_desc \
        | cut -d '=' -f2 | awk '{print $1}')

else
    bPreEOLInfo=$(grep bPreEOLInfo \
        /sys/kernel/debug/*.ufshc/dump_health_desc 2>/dev/null \
        | cut -d '=' -f2 | awk '{print $1}')
fi

if [ -z "$bPreEOLInfo" ]; then
    for line in $dump_files; do
        str=$(grep bPreEOLInfo "$line" | cut -d '=' -f2 | awk '{print $1}')
        [ -n "$str" ] && bPreEOLInfo="$str" && break
    done
fi

if [ -z "$bPreEOLInfo" ]; then
    for line in $(find /sys -name "eol_info" 2>/dev/null | grep ufshc); do
        str=$(cat "$line")
        [ -n "$str" ] && bPreEOLInfo="$str" && break
    done
fi

case "$bPreEOLInfo" in
    0x00|0x0) echo "$ufs_text_1 $ufs_text_2" ;;
    0x01|0x1) echo "$ufs_text_1 $ufs_text_6" ;;
    0x02|0x2) echo "$ufs_text_1 $ufs_text_5" ;;
    0x03|0x3) echo "$ufs_text_1 $ufs_text_4" ;;
    *)        echo "$ufs_text_1 $ufs_text_2" ;;
esac

echo
echo "$check_ufs_text_2"
echo
dd if=/dev/zero of="${0%/*}/test.bin" bs=4M count=256 conv=fsync

echo
echo "$check_ufs_text_1"
echo
dd if="${0%/*}/test.bin" of=/dev/null bs=4M
rm -fr "${0%/*}/test.bin"
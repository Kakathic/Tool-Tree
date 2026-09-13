#!/system/bin/sh
# Kakathic

MKD="${0%/*}"

# không dùng cơ chế này nếu có mô-đun overlayfs
if [ ! -d /data/adb/modules/overlayfs ]; then
  # Anti-bootloop feature using volume keys with LED lights
  until [ "$(getprop sys.boot_completed)" = 1 ]; do
    get_key="$(getevent -qlc1)"
    if echo "$get_key" | grep -q KEY_VOLUMEDOWN; then
    # Turn off the current module.
      check_up=0
      check_dow=$((check_dow + 1))
      echo 255 > /sys/class/leds/flashlight/brightness
      sleep 0.3
      echo 0 > /sys/class/leds/flashlight/brightness
      if [ "$check_dow" -ge 5 ]; then
        touch $MKD/disable
        reboot
      fi
    elif echo "$get_key" | grep -q KEY_VOLUMEUP; then
    # Turn off all modules
      check_dow=0
      check_up=$((check_up + 1))
      echo 255 > /sys/class/leds/flashlight/brightness
      sleep 0.3
      echo 0 > /sys/class/leds/flashlight/brightness
      if [ "$check_up" -ge 5 ]; then
        for vv in /data/adb/modules/*; do
        [ -d "$vv" ] && touch "$vv/disable"
        done
        reboot
      fi
    elif echo "$get_key" | grep -q KEY_POWER; then
    # Turn the lights on and off
      check_pow=$((check_pow + 1))
        if [ "$check_pow" == 1 ]; then
          echo 255 > /sys/class/leds/flashlight/brightness
        else
          echo 0 > /sys/class/leds/flashlight/brightness
          check_pow=0
        fi
      sleep 0.3
    fi
  done
  # Ép tắt đèn pin
  echo 0 > /sys/class/leds/flashlight/brightness
fi

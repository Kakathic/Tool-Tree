#!/data/data/com.tool.tree/files/home/bin/bash
# kakathic

# home
home() {
menuadd "$MPAT"
echo '
[[group]]
  title = "'$google_text'"

  [[action]]
  title = "'$home_text_1'"
  desc = "'$home_text_2'"
  script = """
    slog overlay_folder "$overlay_folder"
    '$MPAT'/overlay.bash
  """

    [[action.params]]
    name = "overlay_folder"
    desc = "'$home_text_7'"
    type = "folder"
    value-sh = "glog overlay_folder"
    required = true
    editable = true

  [[action]]
  title = "'$home_text_3'"
  desc = "'$home_text_4'"
  script = """
    slog extract_folder_lang "$extract_folder_lang"
    slog extract_folder_lang_text "$extract_folder_lang_text"
    '$MPAT'/extract.bash
  """

    [[action.params]]
    name = "extract_folder_lang"
    desc = "'$home_text_8'"
    type = "folder"
    value-sh = "glog extract_folder_lang"
    required = true
    editable = true

    [[action.params]]
    name = "extract_folder_lang_text"
    label = "'$home_text_5'"
    desc = "'$home_text_6'"
    placeholder = "values-vi,values-zh-rCN"
    type = "text"
    value-sh = "glog extract_folder_lang_text"
  '
}

# Ngôn ngữ & Google dịch
source langadd "${0%/*}"
"$@"

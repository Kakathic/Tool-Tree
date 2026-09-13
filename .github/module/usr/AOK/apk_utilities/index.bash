#!/data/data/com.tool.tree/files/home/bin/bash
# kakathic

search_pro() {
    if [ -e "$APK/$PTAH" ]; then
        cd "$APK/$PTAH"
        ls -1d */apktool.yml */archive-info.json 2>/dev/null | sed -e 's|/apktool.yml||' -e 's|/archive-info.json||'
    fi
}

search_values() {
    if [ -e "$APK/$PTAH/$path_clean" ]; then
        cd "$APK/$PTAH/$path_clean"
        ls -1d resources/package_1/res/values-*/strings.xml res/values-*/strings.xml 2>/dev/null | sed -e 's|resources/package_1/||' -e 's|res/||' -e 's|/strings.xml||'
    fi
}

search_array() {
    if [ -e "$APK/$PTAH/$path_clean" ]; then
        cd "$APK/$PTAH/$path_clean"
        ls -1d resources/package_1/res/values-*/arrays.xml res/values-*/arrays.xml 2>/dev/null | sed -e 's|resources/package_1/||' -e 's|res/||' -e 's|/arrays.xml||'
    fi
}

search_plurals() {
    if [ -e "$APK/$PTAH/$path_clean" ]; then
        cd "$APK/$PTAH/$path_clean"
        ls -1d resources/package_1/res/values-*/plurals.xml res/values-*/plurals.xml 2>/dev/null | sed -e 's|resources/package_1/||' -e 's|res/||' -e 's|/plurals.xml||'
    fi
}

# home
home() {
menuadd "$MPAT"
echo '[[group]]

  [[group]]
  title = "'$google_text'"

    [[picker]]
    shell = "hidden"
    reload = "true"
    options-sh = "'$MPAT'/index.bash search_pro"
    title = "'$clean_text_1'"
    summary = "'$clean_text_2' '"$path_clean"'"
    get = "glog project_apk_clean"
    set = """
    [ "$state" ] && slog project_apk_clean "$state"
    """

  [[group]]

    [[action]]
    reload = "true"
    visible = "echo '$show_clean'"
    title = "'$clean_text_3'"
    script = """
    IFS=$'"'\n'"'
    for vv in $LIST $LIST2 $LIST3; do
      echo "'$clean_text_5' $vv"
      if [ -d "$APK/$PTAH/'$path_clean'/resources/package_1/res" ]; then
      rm -fr "$APK/$PTAH/'$path_clean'/resources/package_1/res/$vv"
      elif [ -d "$APK/$PTAH/'$path_clean'/res" ]; then
      rm -fr "$APK/$PTAH/'$path_clean'/res/$vv"
      fi
    done
    """

      [[action.params]]
      name = "LIST"
      title = "strings"
      desc = "'$clean_text_4'"
      multiple = "multiple"
      options-sh = "'$MPAT'/index.bash search_values"

      [[action.params]]
      name = "LIST2"
      title = "arrays"
      desc = "'$clean_text_4'"
      multiple = "multiple"
      options-sh = "'$MPAT'/index.bash search_array"

      [[action.params]]
      name = "LIST3"
      title = "plurals"
      desc = "'$clean_text_4'"
      multiple = "multiple"
      options-sh = "'$MPAT'/index.bash search_plurals"
'
}

# Thư mục hiện tại
path_clean="$(glog project_apk_clean)"
[ "$(glog project_apk_clean)" ] && show_clean=1

# Ngôn ngữ & Google dịch
source langadd "${0%/*}"
"$@"

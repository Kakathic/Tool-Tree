# Kakathic

mkdir -p Up Add
sumcek="$(curl -s -G 'https://api.github.com/repos/Kakathic/Tool-Tree/releases/tags/V1' | jq -r --arg name "list_onl.zip" '.assets[] | select(.name == $name and .digest != null) | .digest // empty' | cut -d: -f2)"
curl -L "https://github.com/Kakathic/Tool-Tree/releases/download/V1/list_onl.zip" -o "list_onl.zip"
curl -s -L "https://github.com/Kakathic/Tool-Tree/releases/download/V1/addon.log" -o "addon.log"
unzip -o list_onl.zip -d Up

if [[ "$(sha256sum "list_onl.zip" | awk '{print $1}')" != "$sumcek" ]]; then
echo "E: sha256sum list_onl.zip"
gh run cancel $GITHUB_RUN_ID
sleep 5
exit 0
exit 1
fi
ls Up/*
if [ -z "$(ls Up/*)" ]; then
echo "E: Download failed list_onl.zip"
gh run cancel $GITHUB_RUN_ID
sleep 5
exit 0
exit 1
fi

getp(){ grep -m1 "$1=" | cut -d= -f2 | sed 's|"||g'; }
list_file="$(curl -sS -u ":$UP_TOKEN" https://pixeldrain.com/api/user/files | jq -r '.files[] | .id')"

# Xử lý từng file
for vc in $list_file; do
(
  # Lấy thông tin file
  infor="$(curl -sS -u ":$UP_TOKEN" "https://pixeldrain.com/api/file/$vc/info")"
  file_name="$(echo "$infor" | jq -r .name)"
  file_size="$(echo "$infor" | jq -r .size)"
  # Check tên
  if [[ "$file_name" == addon_* && "$file_size" -lt 10485760 ]]; then
    # Tải về
    curl -L -u ":$UP_TOKEN" -o "$file_name" "https://pixeldrain.com/api/file/$vc"
    # Lấy thông tin add-on
    if [[ "$file_name" != *.add ]]; then
      loadmd="$(7z x "$file_name" Add-on.bash -so 2>/dev/null)"
      id_add="$(echo "$loadmd" | getp id)"
      author_add="$(echo "$loadmd" | getp author)"
      vcode_add="$(echo "$loadmd" | getp versionCode)"
      version_add="$(echo "$loadmd" | getp version)"
      name_add="$(echo "$loadmd" | getp name)"
      description_add="$(echo "$loadmd" | getp description)"
      root_add="$(echo "$loadmd" | getp root)"
      mv "$file_name" "Add/${id_add}_$vcode_add.add"
      if [ -d Up/$id_add ]; then
        if [ "$author_add" == "$(cat Up/$id_add/download.bash | getp author)" ]; then
        echo "$(date) $id_add: Add-on updated: APPROVED" | tee -a addon.log
        echo -e "id=$id_add\nauthor=\"$author_add\"\nname=\"$name_add\"\ndescription=\"$description_add\"\nversion=\"$version_add\"\nversionCode=$vcode_add\nroot=$root_add\nurl=https://github.com/Kakathic/Tool-Tree/releases/download/V1.0.2/${id_add}_\$versionCode.add" > Up/$id_add/download.bash
        else
        echo "$(date) $id_add: This add-on already exists: NO APPROVAL" | tee -a addon.log
        fi
      else
        echo "$(date) $id_add: Add-on created: APPROVED" | tee -a addon.log
        mkdir -p Up/$id_add
        echo -e "id=$id_add\nauthor=\"$author_add\"\nname=\"$name_add\"\ndescription=\"$description_add\"\nversion=\"$version_add\"\nversionCode=$vcode_add\nroot=$root_add\nurl=https://github.com/Kakathic/Tool-Tree/releases/download/V1.0.2/${id_add}_\$versionCode.add" > Up/$id_add/download.bash
      fi
    elif [[ "$file_name" != *.bash ]]; then
      # Lấy thông tin file add-on
      id_add="$(cat "$file_name" | getp id)"
      author_add="$(cat "$file_name" | getp author)"
      vcode_add="$(cat "$file_name" | getp versionCode)"
      version_add="$(cat "$file_name" | getp version)"
      name_add="$(cat "$file_name" | getp name)"
      description_add="$(cat "$file_name" | getp description)"
      root_add="$(cat "$file_name" | getp root)"
      if [ -d Up/$id_add ]; then
        if [ "$author_add" == "$(cat Up/$id_add/download.bash | getp author)" ]; then
        echo "$(date) $id_add: Add-on updated: APPROVED" | tee -a addon.log
        echo -e "id=$id_add\nauthor=\"$author_add\"\nname=\"$name_add\"\ndescription=\"$description_add\"\nversion=\"$version_add\"\nversionCode=$vcode_add\nroot=$root_add\nurl=https://github.com/Kakathic/Tool-Tree/releases/download/V1.0.2/${id_add}_\$versionCode.add" > Up/$id_add/download.bash
        else
        echo "$(date) $id_add: This add-on already exists: NO APPROVAL" | tee -a addon.log
        fi
      else
        echo "$(date) $id_add: Add-on created: APPROVED" | tee -a addon.log
        mkdir -p Up/$id_add
        echo -e "id=$id_add\nauthor=\"$author_add\"\nname=\"$name_add\"\ndescription=\"$description_add\"\nversion=\"$version_add\"\nversionCode=$vcode_add\nroot=$root_add\nurl=https://github.com/Kakathic/Tool-Tree/releases/download/V1.0.2/${id_add}_\$versionCode.add" > Up/$id_add/download.bash
      fi
    fi
  fi
  # Xoá file
  curl -sS -X DELETE -u ":$UP_TOKEN" "https://pixeldrain.com/api/file/$vc"
)
done

(
cd Up
zip -r list_onl.zip *
)

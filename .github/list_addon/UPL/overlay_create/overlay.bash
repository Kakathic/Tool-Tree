#!/data/data/com.tool.tree/files/home/bin/bash
# kakathic

# Ngôn ngữ & Google dịch
source langadd "${0%/*}"

[ -d "$overlay_folder" ] || killtree "$overlay_text_1"

if [ "$(ls "$overlay_folder")" ]; then
    if [ ! -f "$overlay_folder/1out/1list_overlay.prop" ]; then
        mkdir -p "$overlay_folder/1out"
        echo "$overlay_text_2 $overlay_folder/1out/1list_overlay.prop" >&2
        echo "#Test.apk=com.test" > "$overlay_folder/1out/1list_overlay.prop"
        cd "$overlay_folder"
        ls -1d * | sed '/1out/d' | awk '{print $0"="}' >> "$overlay_folder/1out/1list_overlay.prop"
        exit 1
    fi
else
    killtree "$overlay_text_3"
fi

echo "$overlay_text_4"
echo

# Danh sách
IFS=$'\n'
for vv in "$overlay_folder"/*; do
    if [ -d "$vv" ]; then
        [ "${vv##*/}" == "1out" ] && continue

        calssname="$(grep -m1 "${vv##*/}=" "$overlay_folder/1out/1list_overlay.prop" | cut -d= -f2)"
        demso=$((demso + 1))

        if [ -z "$calssname" ]; then
            echo -e "\033[91m$demso: ${vv##*/}: $overlay_text_7"
            continue
        fi

        if [ -f "$overlay_folder/1out/${vv##*/}" ]; then
            echo -e "\033[0;32m$demso: ${vv##*/}: $overlay_text_6"
            continue
        fi

        # apktool.yml
        file_apktool="version: 3.0.0
apkFileName: ${vv##*/}
usesFramework:
  ids:
  - 1
sdkInfo:
  minSdkVersion: 24
  targetSdkVersion: $API
versionInfo:
  versionCode: $API
  versionName: $ANDROID_RELEASE
resourcesInfo:
  packageId: 127
doNotCompress:
- arsc"

        file_manifest='<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="z.'$calssname'">
    <overlay
        android:targetPackage="'$calssname'"
        android:targetName="z.'${vv##*/}'"
        android:isStatic="true"
        android:priority="1000"/>
    <uses-sdk
        android:minSdkVersion="24"
        android:targetSdkVersion="'$API'" />
    <application
        android:label="z.'${vv##*/}'"
        android:hasCode="false"
        android:extractNativeLibs="false"/>
</manifest>'

        echo -ne "\033[0;33m$demso: ${vv##*/}: $overlay_text_5"
        [ -f "$vv/apktool.yml" ] || echo "$file_apktool" > "$vv/apktool.yml"
        [ -f "$vv/AndroidManifest.xml" ] || echo "$file_manifest" > "$vv/AndroidManifest.xml"

        mkdir -p "$vv/res/values"
        [ -f "$vv/res/values/public.xml" ] || echo '<?xml version="1.0" encoding="utf-8"?>
<resources>
  <public type="array" name="test" id="0x7f010000" />
  <public type="plurals" name="test2" id="0x7f020000" />
  <public type="string" name="test3" id="0x7f030000" />
  <public type="bool" name="test4" id="0x7f040000" />
  <public type="integer" name="test5" id="0x7f050000" />
  <public type="dimen" name="test6" id="0x7f060000" />
</resources>' > "$vv/res/values/public.xml"

        for vc in $(find "$vv/res" -type f | sed "/\/values\//d"); do
            if [ "$(echo "$vc" | grep -c -e "values\-" -e "\.xml")" -ge 1 ]; then
                filter_xml.py "$vc" >/dev/null
                update_id.py --use-file "$vv/res/values/public.xml" "$vc" >/dev/null
            fi
        done

        sed -i "/name=\"test/d" "$vv/res/values/public.xml"
        find "$vv" -type f -name "*.bak" -delete >/dev/null

        apktool b -f -o "$TMP/${vv##*/}" "$vv" &>"$overlay_folder/1out/1build.log" || killtree "$overlay_text_8\n\n$(cat "$overlay_folder/1out/1build.log")" "$TMP/${vv##*/}"

        sign -i "$TMP/${vv##*/}" -o "$overlay_folder/1out/${vv##*/}"
        rm -f "$TMP/${vv##*/}"
        echo -ne "\r\033[0;32m$demso: ${vv##*/}: $overlay_text_6\n"
    fi
done

echo
echo "$overlay_text_9 $overlay_folder/1out"
echo

checktime
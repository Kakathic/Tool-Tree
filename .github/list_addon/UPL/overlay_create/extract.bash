#!/data/data/com.tool.tree/files/home/bin/bash
# kakathic

# Ngôn ngữ & Google dịch
source langadd "${0%/*}"

[ -d "$extract_folder_lang" ] || killtree "$overlay_text_1"
[ "$extract_folder_lang_text" ] && text_filters="$(echo "$extract_folder_lang_text" | tr ',' ' ')"

# Nhập framework
if [[ ! -f $HOME/.local/share/apktool/framework/18.apk ]]; then
    echo "$overlay_text_11"
    echo
    unset IFS
    for vcx in com.miui.hybrid com.google.android.ext.shared com.miui.rom com.miui.system com.xiaomi.micloud.sdk; do
        pathapkz="$(pm path $vcx | cut -d ':' -f2)"
        [ -f "$pathapkz" ] && apktool if "$pathapkz"
    done
fi

echo "$overlay_text_4"
echo

IFS=$'\n'
for vv in "$extract_folder_lang"/*.apk; do
    if [ -f "$vv" ]; then
        demso=$((demso + 1))

        if [ -f "$extract_folder_lang/1out/${vv##*/}" ]; then
            echo -e "\033[0;32m$demso: ${vv##*/}: $overlay_text_6"
            continue
        fi

        if [ ! -d "${vv%.*}" ]; then
            mkdir -p "$extract_folder_lang/1out"
            apktool d -f -s "$vv" -o "$TMP/${vv##*/}" &>"$extract_folder_lang/1out/1build.log" || killtree "$overlay_text_10\n\n$(cat "$extract_folder_lang/1out/1build.log")"

            if [ -z "$text_filters" ]; then
                text_filter="$(cd "$TMP/${vv##*/}"/res; find values-* -type f \( -name "plurals.xml" -o -name "arrays.xml" -o -name "strings.xml" \))"
            else
                text_filter="$(cd "$TMP/${vv##*/}"/res; find $text_filters -type f \( -name "plurals.xml" -o -name "arrays.xml" -o -name "strings.xml" \))"
            fi

            for vc in $text_filter; do
                mkdir -p "${vv%.*}/res/${vc%/*}"
                cp -rf "$TMP/${vv##*/}/res/$vc" "${vv%.*}/res/${vc%/*}"
            done

            # Lọc package
            calssname="$(grep "package=" "$TMP/${vv##*/}/AndroidManifest.xml" | tr ' ' '\n' | grep -m1 "package=" | cut -d'"' -f2)"
            rm -fr "$TMP/${vv##*/}"

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

            [ -f "${vv%.*}/apktool.yml" ] || echo "$file_apktool" > "${vv%.*}/apktool.yml"
            [ -f "${vv%.*}/AndroidManifest.xml" ] || echo "$file_manifest" > "${vv%.*}/AndroidManifest.xml"

            mkdir -p "${vv%.*}/res/values"
            [ -f "${vv%.*}/res/values/public.xml" ] || echo '<?xml version="1.0" encoding="utf-8"?>
<resources>
  <public type="array" name="test" id="0x7f010000" />
  <public type="plurals" name="test2" id="0x7f020000" />
  <public type="string" name="test3" id="0x7f030000" />
  <public type="bool" name="test4" id="0x7f040000" />
  <public type="integer" name="test5" id="0x7f050000" />
  <public type="dimen" name="test6" id="0x7f060000" />
</resources>' > "${vv%.*}/res/values/public.xml"
        fi

        echo -ne "\033[0;33m$demso: ${vv##*/}: $overlay_text_5"
        for vc in $(find "${vv%.*}/res" -type f | sed "/\/values\//d"); do
            if [ "$(echo "$vc" | grep -c -e "values\-" -e "\.xml")" -ge 1 ]; then
                filter_xml.py "$vc" >/dev/null
                delxml.py "$vc" >/dev/null
                update_id.py --use-file "${vv%.*}/res/values/public.xml" "$vc" >/dev/null
            fi
        done

        sed -i "/name=\"test/d" "${vv%.*}/res/values/public.xml"
        find "${vv%.*}" -type f -name "*.bak" -delete >/dev/null

        apktool b -f -o "$TMP/${vv##*/}" "${vv%.*}" &>"$extract_folder_lang/1out/1build.log" || killtree "$overlay_text_8:\n\n$(cat "$extract_folder_lang/1out/1build.log")" "$TMP/${vv##*/}"

        sign -i "$TMP/${vv##*/}" -o "$extract_folder_lang/1out/${vv##*/}"
        rm -fr "$TMP/${vv##*/}" "${vv%.*}"
        echo -ne "\r\033[0;32m$demso: ${vv##*/}: $overlay_text_6\n"
    fi
done

echo
echo "$overlay_text_9 $extract_folder_lang/1out"
echo

checktime
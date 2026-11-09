# Kakathic

HOME="$GITHUB_WORKSPACE"

(
cd .github/module
for vmk in $(find lib .local -type f \( -name "*.jar" -o -name "*.apk" \)); do
mkdir -p "${vmk%.*}®${vmk##*.}_7zv2"
7z x "$vmk" -o"${vmk%.*}®${vmk##*.}_7zv2" -y >/dev/null
rm -fr "$vmk"
done
for vnk in $(find lib/*®jar_7zv2/frameworks/android/*apk lib/apktool®jar_7zv2/prebuilt/*.jar -type f); do
mkdir -p "${vnk%.*}®${vnk##*.}_7zv1"
7z x "$vnk" -o"${vnk%.*}®${vnk##*.}_7zv1" -y >/dev/null
rm -fr "$vnk"
done

mkdir -p root tmp TREE/ROM TOOL/APK
find . -exec touch -d "2026-01-01 00:00:00" {} +
# Nén dữ liệu
7z a -t7z -mx=9 -mmt=off -mtc=off -mta=off -y ../module.7z .
)

[ "$UPAPK" == "true" ] || echo 1 > app/src/main/assets/beta
gradle assembleRelease -x lint -x test

VER=$(grep -m1 'versionName ' app/build.gradle | cut -d'"' -f2)

echo "Tool-Tree $VER"
echo "VER=$VER" >> "$GITHUB_ENV"

BUILD_TOOLS=$(find "$ANDROID_HOME/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -1)

mkdir -p Up Beta assets
if [ "$UPAPK" == "true" ]; then
cp -f app/build/outputs/apk/*/*.apk "Up/Tool-Tree.$VER.apk"
cp -f app/build/outputs/apk/*/*.apk "Tool-Tree.$VER-offline.apk"
(
mv .github/module.7z assets/module
zip -q -u "Tool-Tree.$VER-offline.apk" assets/module
"$BUILD_TOOLS/zipalign" -f 4 "Tool-Tree.$VER-offline.apk" "Tool-Tree-off.apk"
"$BUILD_TOOLS/apksigner" sign --ks app/Kakathic.jks --ks-pass pass:"$KEY_PASSWORD" --key-pass pass:"$KEY_PASSWORD" --out "Tool-Tree.$VER-offline.apk" "Tool-Tree-off.apk"
)
else
cp -f app/build/outputs/apk/*/*.apk "Beta/Tool-Tree-beta.apk"
cp -f app/build/outputs/apk/*/*.apk "Tool-Tree-offline-beta.apk"
(
mv .github/module.7z assets/module
zip -q -u "Tool-Tree-offline-beta.apk" assets/module
"$BUILD_TOOLS/zipalign" -f 4 "Tool-Tree-offline-beta.apk" "Tool-Tree-beta-off.apk"
"$BUILD_TOOLS/apksigner" sign --ks app/Kakathic.jks --ks-pass pass:"$KEY_PASSWORD" --key-pass pass:"$KEY_PASSWORD" --out "Beta/Tool-Tree-offline-beta.apk" "Tool-Tree-beta-off.apk"
)
fi
ls -l Up Beta
echo -e "- ![Downloads](https://img.shields.io/github/downloads/Kakathic/Tool-Tree/V$VER/total?label=Downloads&color=%230072F4)\n- See version notes: [Changelog](https://Kakathic.github.io/Tool-Tree/Version.html)" > change.txt

if [ "$UPTG" == "true" ]; then
echo "Telegram"
chat_tg="🎉 <b>Update new version Tool-Tree</b>

• Application has many tools, ROM and APK editing, Many add-on utilities

• 🎋 <b>Version:</b> $VER
• 🪴 <b>Author:</b> Kakathic
• 📱 <b>Device:</b> Arm64

• 🧾 <b>Notes:</b> <a href=\"https://github.com/Kakathic/Tool-Tree/blob/main/Version.md\">Changelog</a>
• 📚 <b>Instruct:</b> Root | <a href=\"https://Kakathic.github.io/Tool-Tree/website/Guide.html\">No-Root</a>
• 📥 <b><a href=\"https://github.com/Kakathic/Tool-Tree/releases/tag/V$VER\">Download</a></b>

🌐 <b>Language:</b> Help <a href=\"https://github.com/Kakathic/Tool-Tree#-language\">translation</a>

#release@tool_tree"

curl -sS -X POST "https://api.telegram.org/bot$TG_TOKEN/sendMessage" \
-d chat_id="$CHAT_ID" \
-d parse_mode="HTML" \
--data-urlencode text="$chat_tg"
fi

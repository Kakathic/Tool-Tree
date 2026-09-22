# Kakathic
VER=$(grep -m1 'versionName ' app/build.gradle | cut -d'"' -f2)
cd .github/module
find . -exec touch -d "2026-01-01 00:00:00" {} +

for vmk in $(find lib/*.jar .local/share/apktool/framework/*.apk -type f); do
mkdir -p "${vmk%.*}®${vmk##*.}_7zv2"
7z x "$vmk" -o"${vmk%.*}®${vmk##*.}_7zv2" -y >/dev/null
rm -fr "$vmk"
done

for vnk in $(find lib/*®jar_7zv2/frameworks/android/*apk lib/apktool®jar_7zv2/prebuilt/*.jar -type f); do
mkdir -p "${vnk%.*}®${vnk##*.}_7zv1"
7z x "$vnk" -o"${vnk%.*}®${vnk##*.}_7zv1" -y >/dev/null
rm -fr "$vnk"
done

mkdir -p lib root tmp TREE/ROM TOOL/APK usr/log

# -snh -snl
7z a -t7z -mx=9 -mmt=off -mtc=off -mta=off -y ../module.7z
mv ../module.7z ../module_$VER.so
ls -lh ../module_$VER.so

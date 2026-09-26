# Kakathic
cd .github/module

for vmk in $(find lib .local -type f \( -name "*.jar" -o -name "*.apk" \)); do
mkdir -p "${vmk%.*}®${vmk##*.}_7zv2"
7z x "$vmk" -o"${vmk%.*}®${vmk##*.}_7zv2" -y >/dev/null
rm -fr "$vmk"
done

for vnk in $(find lib/*®jar_7zv2/frameworks/android/*apk lib/apktool®jar_7zv2/prebuilt/*.jar -type f 2>/dev/null); do
mkdir -p "${vnk%.*}®${vnk##*.}_7zv1"
7z x "$vnk" -o"${vnk%.*}®${vnk##*.}_7zv1" -y >/dev/null
rm -fr "$vnk"
done

mkdir -p root tmp TREE/ROM TOOL/APK

# Đồng bộ ngày giờ cho TẤT CẢ file/thư mục trước khi nén
find . -exec touch -d "2026-01-01 00:00:00" {} +

# Nén dữ liệu
7z a -t7z -mx=9 -mmt=off -mtc=off -mta=off -y ../termux.7z -x!lib -x!bin -x!.local -x!etc
7z a -t7z -mx=9 -mmt=off -mtc=off -mta=off -y ../bin.7z bin
7z a -t7z -mx=9 -mmt=off -mtc=off -mta=off -y ../lib.7z lib
7z a -t7z -mx=9 -mmt=off -mtc=off -mta=off -y ../local.7z .local
7z a -t7z -mx=9 -mmt=off -mtc=off -mta=off -y ../etc.7z etc

ls -lh ../*.7z

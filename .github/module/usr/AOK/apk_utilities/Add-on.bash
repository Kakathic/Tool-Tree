# Add-on
id=apk_utilities
name="Apk patch"
author=Kakathic
description="Multiple APK patches"
version=1.0
versionCode=100
root=false

# default
google_text="Currently using a translation tool"
clean_text_1="Project"
clean_text_2="Present:"
clean_text_3="Remove language"
clean_text_4="Select the languages you want to delete"
clean_text_5="Deleted:"

# other languages
case "$LANGUAGE-$COUNTRY" in
  vi*)
    name="Bản vá apk"
    description="Nhiều bản vá APK"
    clean_text_1="Dự án"
    clean_text_2="Hiện tại:"
    clean_text_3="Xóa ngôn ngữ"
    clean_text_4="Chọn các ngôn ngữ bạn muốn xóa"
    clean_text_5="Đã xóa:"
    ;;
  hu*)
    description="Több APK javítás"
    clean_text_1="Projekt"
    clean_text_2="Jelenlegi:"
    clean_text_3="Nyelv törlése"
    clean_text_4="Válassza ki a törölni kívánt nyelveket"
    clean_text_5="Törölve:"
    ;;
  es*)
    description="Múltiples parches de APK"
    clean_text_1="Proyecto"
    clean_text_2="Actual:"
    clean_text_3="Eliminar idioma"
    clean_text_4="Selecciona los idiomas que quieres eliminar"
    clean_text_5="Eliminado:"
    ;;
  *)
    ;;
esac

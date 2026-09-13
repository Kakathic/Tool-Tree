# Add-on

id=payload_online
name="Payload dumper online"
author=Kakathic
description="Extract payload online"
version=1.0
versionCode=100
root=false

# default
google_text="Currently using a translation tool"
payload_text_1="Import url path"
payload_text_2="Link:"
payload_text_3="Partition list"
payload_text_4="Save in:"
payload_text_5="Select the partition to download"

# other languages
case "$LANGUAGE-$COUNTRY" in
  vi*)
    description="Trích xuất payload online"
    payload_text_1="Nhập đường dẫn URL"
    payload_text_2="Liên kết:"
    payload_text_3="Danh sách phân vùng"
    payload_text_4="Lưu tại:"
    payload_text_5="Chọn phân vùng muốn tải về"
    ;;
  hu*)
    description="Payload kibontása online"
    payload_text_1="Adja meg az URL-t"
    payload_text_2="Link:"
    payload_text_3="Partíciólista"
    payload_text_4="Mentés ide:"
    payload_text_5="Válassza ki a letölteni kívánt partíciót"
    ;;
  es*)
    description="Extraer payload en línea"
    payload_text_1="Introduce la URL"
    payload_text_2="Enlace:"
    payload_text_3="Lista de particiones"
    payload_text_4="Guardar en:"
    payload_text_5="Selecciona la partición que quieres descargar"
    ;;
  *)
    ;;
esac

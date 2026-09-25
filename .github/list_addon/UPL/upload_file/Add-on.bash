# Add-on
id=upload_file
name="Upload file online"
author=Kakathic
description="Upload files to web"
version=1.0
versionCode=100
root=false

# default
google_text="Currently using a translation tool"
gofile_text_1="Uploading to server:"
gofile_text_2="Upload file error !"
gofile_text_3="Link download:"
gofile_text_5="The file does not exist!"
gofile_text_6="You need to register an account and create a token in the API section: pixeldrain.com"

# other languages
case "$LANGUAGE-$COUNTRY" in
  vi*)
    description="Tải tập tin lên web"
    gofile_text_1="Đang tải tệp tin lên máy chủ:"
    gofile_text_2="Lỗi tải tệp tin lên !"
    gofile_text_3="Liên kết tải về:"
    gofile_text_5="Tệp tin không tồn tại!"
    gofile_text_6="Bạn cần đăng ký tài khoản và tạo một token trong mục API tại: pixeldrain.com"
    ;;
  hu*)
    description="Fájl feltöltése a webre"
    gofile_text_1="Fájl feltöltése a szerverre:"
    gofile_text_2="Fájlfeltöltési hiba!"
    gofile_text_3="Letöltési link:"
    gofile_text_5="A fájl nem létezik!"
    gofile_text_6="Regisztrálnod kell egy fiókot, és létre kell hoznod egy tokent az API-szekcióban: pixeldrain.com"
    ;;
  es*)
    description="Subir archivo a la web"
    gofile_text_1="Subiendo archivo al servidor:"
    gofile_text_2="¡Error al subir el archivo!"
    gofile_text_3="Enlace de descarga:"
    gofile_text_5="¡El archivo no existe!"
    gofile_text_6="Debes registrar una cuenta y crear un token en la sección de API en: pixeldrain.com"
    ;;
  *)
    ;;
esac

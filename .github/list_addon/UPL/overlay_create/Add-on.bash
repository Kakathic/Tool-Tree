# Add-on
id=overlay_create
name="Create overlay"
author=Kakathic
description="Create overlay language"
version=1.0
versionCode=100
root=false

# default
google_text="Currently using a translation tool"
home_text_1="Create overlay"
home_text_2="Create an overlay from the language directory"
home_text_3="Extract language"
home_text_4="Extract languages from the APK file to create an overlay"
home_text_5="Language"
home_text_6="Extract only the languages already present, if left blank, it will retrieve all languages: values-vi,values-zh-rCN"
home_text_7="The folder must contain a language directory, for example: App/res/values-vi/strings.xml"
home_text_8="The folder must contain an APK file"
overlay_text_1="Folder not found"
overlay_text_2="Created overlay list. Please add the package to the list:"
overlay_text_3="Folder is empty"
overlay_text_4="Starting to create overlay..."
overlay_text_5="Creating"
overlay_text_6="Created"
overlay_text_7="Skipped"
overlay_text_8="Overlay build error, check log:"
overlay_text_9="Saved at:"
overlay_text_10="Decode APK errors, view logs:"
overlay_text_11="Importing framework..."

# other languages
case "$LANGUAGE-$COUNTRY" in
  vi*)
    name="Tạo Overlay"
    description="Tạo ngôn ngữ lớp phủ"
    home_text_1="Tạo overlay"
    home_text_2="Tạo một overlay từ thư mục ngôn ngữ"
    home_text_3="Trích xuất ngôn ngữ"
    home_text_4="Trích xuất các ngôn ngữ từ tệp tin APK để tạo một overlay"
    home_text_5="Ngôn ngữ"
    home_text_6="Chỉ trích xuất các ngôn ngữ đã có sẵn, nếu bỏ trống sẽ lấy tất cả các ngôn ngữ: values-vi,values-zh-rCN"
    home_text_7="Thư mục phải chứa cấu trúc thư mục ngôn ngữ, ví dụ: App/res/values-vi/strings.xml"
    home_text_8="Thư mục phải chứa một tệp tin APK"

    overlay_text_1="Không tìm thấy thư mục"
    overlay_text_2="Đã tạo danh sách overlay. Vui lòng thêm package vào danh sách:"
    overlay_text_3="Thư mục trống"
    overlay_text_4="Bắt đầu tạo overlay..."
    overlay_text_5="Đang tạo"
    overlay_text_6="Đã tạo"
    overlay_text_7="Đã bỏ qua"
    overlay_text_8="Lỗi xây dựng overlay, kiểm tra log:"
    overlay_text_9="Lưu tại:"
    overlay_text_10="Lỗi giải mã (decode) APK, xem log:"
    overlay_text_11="Đang nhập framework..."
    ;;
  hu*)
    description="Overlay nyelv létrehozása"
    home_text_1="Hozz létre egy átfedést (overlay)"
    home_text_2="H hozzon létre overlay-t a nyelvi könyvtárból"
    home_text_3="Nyelvkinyerés"
    home_text_4="Nyelvi fájlok kinyerése APK-fájlból overlay létrehozásához"
    home_text_5="Nyelv"
    home_text_6="Csak a megadott nyelveket bontsa ki; ha üresen hagyja, az összes nyelv benne lesz: values-vi, values-zh-rCN"
    home_text_7="A könyvtárnak tartalmaznia kell egy nyelvi könyvtárstruktúrát, például: App/res/values-hu/strings.xml"
    home_text_8="A könyvtárnak tartalmaznia kell egy APK-fájlt"

    overlay_text_1="A könyvtár nem található"
    overlay_text_2="Overlay-lista létrehozva. Kérjük, adja hozzá a csomagot a listához:"
    overlay_text_3="Üres mappa"
    overlay_text_4="Átfedés létrehozása folyamatban..."
    overlay_text_5="Létrehozás"
    overlay_text_6="Létrehozva"
    overlay_text_7="Kihagyva"
    overlay_text_8="Hiba az overlay összeállítása során, ellenőrizze a naplót:"
    overlay_text_9="Mentve ide:"
    overlay_text_10="APK-dekódolási hiba, ellenőrizze a naplót:"
    overlay_text_11="framework betöltése..."
    ;;
  es*)
    description="Crear idioma de overlay"
    home_text_1="Crear overlay"
    home_text_2="Crea un overlay a partir de la carpeta de idioma"
    home_text_3="Extraer idioma"
    home_text_4="Extrae los idiomas de un archivo APK para crear un overlay"
    home_text_5="Idioma"
    home_text_6="Extrae solo los idiomas indicados; si se deja en blanco, se tomarán todos los idiomas: values-vi,values-zh-rCN"
    home_text_7="La carpeta debe contener la estructura de carpetas de idioma, por ejemplo: App/res/values-es/strings.xml"
    home_text_8="La carpeta debe contener un archivo APK"

    overlay_text_1="No se ha encontrado la carpeta"
    overlay_text_2="Lista de overlay creada. Añade el paquete a la lista:"
    overlay_text_3="Carpeta vacía"
    overlay_text_4="Iniciando creación del overlay..."
    overlay_text_5="Creando"
    overlay_text_6="Creado"
    overlay_text_7="Omitido"
    overlay_text_8="Error al compilar el overlay, revisa el registro:"
    overlay_text_9="Guardado en:"
    overlay_text_10="Error al decodificar el APK, revisa el registro:"
    overlay_text_11="Importando framework..."
    ;;
  *)
    ;;
esac

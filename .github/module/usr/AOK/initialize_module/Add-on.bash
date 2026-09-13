# Add-on

id=initialize_module
name="Initialize module"
author=Kakathic
description="Customize module"
version=1.0
versionCode=100
root=true
process=true

# default
google_text="Currently using a translation tool"
lang_action_warn="This add-on copies the selected files into Magisk, KSU, APatch modules, etc., to create a new module. After rebooting, it will be applied to the system just like any other module."
lang_title="Import files"
lang_summary_path="Path: "
lang_desc_adb="Enter the file path. If left blank, it will automatically search for a filename that matches the system"
lang_save_at="Saved at: "
lang_searching="Searching for path..."
lang_not_found="File not found: "
lang_input_notice=", please enter a path to create a new one"
lang_del_tile="Delete files quickly"
lang_del_desc="Select any file within the module to delete"
lang_del_desc2="Delete any files in:"
list_modul="List of applicable files"
lang_desc_prop="Edit system.prop"
lang_desc_service="Edit service.sh"

# other languages
case "$LANGUAGE-$COUNTRY" in
  vi*)
    name="Khởi tạo mô-đun"
    description="Tùy chỉnh mô-đun"
    lang_action_warn="Add-on này có tác dụng sao chép các tệp tin đã chọn vào module magisk, ksu, apatch v.v, để tạo ra module, sau khi khởi động lại nó sẽ được áp dụng vào hệ thống giống như các module khác."
    lang_title="Nhập tệp tin"
    lang_summary_path="Đường dẫn: "
    lang_desc_adb="Điền vị trí đường dẫn, nếu bỏ trống sẽ tự tìm kiếm tên tệp tin khớp với tên trong hệ thống"
    lang_desc_prop="Chỉnh sửa system.prop"
    lang_desc_service="Chỉnh sửa service.sh"
    lang_save_at="Lưu ở: "
    lang_searching="Tìm kiếm đường dẫn..."
    lang_not_found="Không tìm thấy tệp tin: "
    lang_input_notice=", hãy nhập đường dẫn để tạo mới"
    lang_del_tile="Xoá tệp nhanh"
    lang_del_desc="Lựa chọn tệp tin bất kỳ trong module để xóa bỏ"
    lang_del_desc2="Xoá tệp tin bất kỳ trong:"
    list_modul="Danh sách tệp được áp dụng"
    ;;
  hu*)
    description="Modul testreszabása"
    lang_action_warn="Ez a kiegészítő a kijelölt fájlokat egy Magisk-, KSU- vagy APatch-modulba másolja; újraindítás után a modul ugyanúgy aktiválódik a rendszerben, mint bármely más modul."
    lang_title="Fájl importálása"
    lang_summary_path="Link: "
    lang_desc_adb="Adja meg a fájl elérési útját; ha üresen hagyja, a rendszer automatikusan megkeresi a rendszerben lévővel megegyező fájlnevet"
    lang_desc_prop="system.prop szerkesztése"
    lang_desc_service="service.sh szerkesztése"
    lang_save_at="Mentés ide: "
    lang_searching="Útvonal keresése..."
    lang_not_found="A fájl nem található: "
    lang_input_notice=", adja meg az elérési utat egy új létrehozásához"
    lang_del_tile="Fájlok gyors törlése"
    lang_del_desc="Válassza ki a törölni kívánt fájlt a modulon belül"
    lang_del_desc2="Töröljön bármilyen fájlt a következő helyről:"
    list_modul="Alkalmazott fájlok listája"
    ;;
  es*)
    description="Personalizar módulo"
    lang_action_warn="Este complemento copia los archivos seleccionados a un módulo de Magisk, KSU, APatch, etc., para crear el módulo; tras reiniciar, se aplicará al sistema igual que cualquier otro módulo."
    lang_title="Importar archivo"
    lang_summary_path="Ruta: "
    lang_desc_adb="Introduce la ruta; si se deja en blanco, se buscará automáticamente un nombre de archivo que coincida con el del sistema"
    lang_desc_prop="Editar system.prop"
    lang_desc_service="Editar service.sh"
    lang_save_at="Guardar en: "
    lang_searching="Buscando ruta..."
    lang_not_found="Archivo no encontrado: "
    lang_input_notice=", introduce la ruta para crear uno nuevo"
    lang_del_tile="Eliminación rápida de archivos"
    lang_del_desc="Selecciona cualquier archivo del módulo para eliminarlo"
    lang_del_desc2="Eliminar cualquier archivo en:"
    list_modul="Lista de archivos aplicados"
    ;;
  *)
    ;;
esac

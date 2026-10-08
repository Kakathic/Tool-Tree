#!/data/data/com.tool.tree/files/home/termux/bin/python
import xml.etree.ElementTree as ET
import sys

def get_primary_dex_classes(xml_path):
    # Namespace mặc định của Android
    ns = {'android': 'http://schemas.android.com/apk/res/android'}
    
    try:
        tree = ET.parse(xml_path)
    except Exception as e:
        print(f"File reading error: {e}")
        return []

    root = tree.getroot()
    package = root.attrib.get('package', '')
    class_list = set()

    def resolve_name(name):
        if not name: 
            return None
        if name.startswith('.'):
            return f"{package}{name}"
        elif '.' not in name:
            return f"{package}.{name}"
        return name

    app = root.find('application')
    if app is not None:
        # 1. Application Class & Component Factory
        app_name = app.attrib.get(f'{{{ns["android"]}}}name')
        factory = app.attrib.get(f'{{{ns["android"]}}}appComponentFactory')
        
        if app_name: 
            class_list.add(resolve_name(app_name))
        if factory: 
            class_list.add(resolve_name(factory))

        # 2. Toàn bộ ContentProvider (bắt buộc ở DEX 1)
        for provider in app.findall('provider'):
            p_name = provider.attrib.get(f'{{{ns["android"]}}}name')
            if p_name: 
                class_list.add(resolve_name(p_name))

        # 3. Toàn bộ BroadcastReceiver (các thành phần lắng nghe sự kiện)
        for receiver in app.findall('receiver'):
            r_name = receiver.attrib.get(f'{{{ns["android"]}}}name')
            if r_name: 
                class_list.add(resolve_name(r_name))

    return sorted(list(class_list))

if __name__ == '__main__':
    file_path = sys.argv[1] if len(sys.argv) > 1 else 'AndroidManifest.xml'
    classes = get_primary_dex_classes(file_path)

    # In ra danh sách từng dòng
    for cls in classes:
        print(cls)

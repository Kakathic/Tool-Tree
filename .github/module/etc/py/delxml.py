#!/data/data/com.tool.tree/files/home/termux/bin/python
import sys
import xml.etree.ElementTree as ET

def clean_xml_file(file_path):
    try:
        # Đọc file với parser giữ nguyên comment/ký tự đặc biệt
        parser = ET.XMLParser(target=ET.TreeBuilder(insert_comments=True))
        tree = ET.parse(file_path, parser=parser)
        root = tree.getroot()
        
        # Cập nhật danh sách thẻ: Nhận diện cả 'string-array' và 'array'
        target_tags = {'string', 'array', 'string-array', 'plurals'}
        nodes_to_remove = []
        
        for child in root:
            if child.tag in target_tags:
                has_at_sign = False
                
                # Kiểm tra text của chính thẻ đó
                if child.text and '@' in child.text:
                    has_at_sign = True
                else:
                    # Quét tất cả các thẻ <item> bên trong <string-array> hoặc <plurals>
                    for item in child.findall('.//item'):
                        if item.text and '@' in item.text:
                            has_at_sign = True
                            break
                
                if has_at_sign:
                    nodes_to_remove.append(child)
        
        # Tiến hành xóa
        for node in nodes_to_remove:
            root.remove(node)
            print(f" Deleted: <{node.tag} name=\"{node.get('name')}\">")
            
        # Ghi đè lại file gốc
        tree.write(file_path, encoding='utf-8', xml_declaration=True)
        print(f"\n File processing completed: {file_path}")
        
    except Exception as e:
        print(f"Error: {e}")

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python delxml.py file.xml")
    else:
        clean_xml_file(sys.argv[1])

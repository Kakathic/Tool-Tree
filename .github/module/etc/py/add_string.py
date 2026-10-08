#!/data/data/com.tool.tree/files/home/termux/bin/python
import sys
import os
import xml.etree.ElementTree as ET

def update_xml_from_cli(xml_file_path, raw_xml_text):
    """
    Handles adding or updating any Android XML tags from the command line.
    Supports: string, string-array, plurals, dimen, integer, bool, color, etc.
    """
    # 1. Check or initialize the root XML file structure (<resources>)
    if os.path.exists(xml_file_path) and os.path.getsize(xml_file_path) > 0:
        try:
            tree = ET.parse(xml_file_path)
            root = tree.getroot()
            if root.tag != 'resources':
                # If the file exists but root is not <resources>, force Android standard
                root = ET.Element('resources')
                tree = ET.ElementTree(root)
        except ET.ParseError:
            print(f"Warning: File '{xml_file_path}' has a structural error or is empty. Auto-initializing.")
            root = ET.Element('resources')
            tree = ET.ElementTree(root)
    else:
        # If the file does not exist, create a new Android standard structure
        root = ET.Element('resources')
        tree = ET.ElementTree(root)

    # 2. Wrap the input string with a temporary <wrapper> tag to avoid "junk after document element" error
    wrapped_input = f"<wrapper>{raw_xml_text.strip()}</wrapper>"
    try:
        wrapper_node = ET.fromstring(wrapped_input)
    except ET.ParseError as e:
        print(f"Error: Input text is not in a valid XML format.")
        print(f"Error details: {e}")
        return

    # If the input string is empty and contains no tags
    if len(wrapper_node) == 0:
        print("Warning: No valid XML tags found in the input text.")
        return

    # 3. Iterate through each child tag passed from the command line
    for input_node in list(wrapper_node):
        tag_type = input_node.tag  # e.g., 'string', 'string-array', 'dimen', 'plurals'...
        name_attr = input_node.get('name')  # Get the value of the name="..." attribute

        if not name_attr:
            print(f"Skipped <{tag_type}> tag: Missing name=\"...\" attribute.")
            continue

        # Check if a tag with the same type and name already exists in the original file
        existing_node = root.find(f"./{tag_type}[@name='{name_attr}']")

        if existing_node is not None:
            # CASE 1: ALREADY EXISTS -> Replace
            index = list(root).index(existing_node)
            root.remove(existing_node)
            root.insert(index, input_node)
            print(f" ✓ REPLACED: <{tag_type} name=\"{name_attr}\">")
        else:
            # CASE 2: DOES NOT EXIST -> Add new
            root.append(input_node)
            print(f" + ADDED NEW: <{tag_type} name=\"{name_attr}\">")

    # 4. Format the indentation (Pretty-print) and save the file
    # ET.indent requires Python 3.9+ to auto-indent the XML file with 4 spaces properly
    if sys.version_info >= (3, 9):
        ET.indent(tree, space="    ", level=0)
        
    try:
        tree.write(xml_file_path, encoding='utf-8', xml_declaration=True)
        print(f"Script execution completed on file: {xml_file_path}")
    except IOError as e:
        print(f"Error writing to file: {e}")

if __name__ == "__main__":
    # Check if the user has provided enough arguments (File name and XML text)
    if len(sys.argv) < 3:
        print("INVALID SYNTAX!")
        print("-" * 50)
        print("Correct Usage:")
        print('  python test.py <xml_file_path> "<xml_text_to_add>"')
        print("-" * 50)
        print("Example:")
        print('  python test.py dimens.xml "<dimen name=\\"text_size_large\\">22sp</dimen>"')
        sys.exit(1)

    # Get arguments from command line
    target_file = sys.argv[1]
    input_text = sys.argv[2]

    update_xml_from_cli(target_file, input_text)

import os
import re

# Path to search for values folders
BASE_PATH = "app/src/main/res"


def convert_xliff_to_format_specifier(xml_content):
    """
    Converts XLIFF <xliff:g> tags in Android string resources to format specifiers (%n$s).
    Resets numbering per <string> tag, keeping msgid intact.
    """

    def process_string(match):
        """Handles each <string> tag separately, resetting numbering per string."""
        string_content = match.group(3)  # Extract text inside the <string> tag
        # Extract msgid attribute (if present)
        msgid = match.group(2) if match.group(2) else ""
        counter = 0  # Reset parameter numbering for each string

        def replace_xliff_tag(xliff_match):
            nonlocal counter
            counter += 1
            return f"%{counter}$s"  # Replace <xliff:g> with format specifiers

        # Check if the original string was surrounded by quotes (we assume it's inside quotes if it starts and ends with them)
        was_surrounded_by_quotes = string_content.startswith(
            '"') and string_content.endswith('"')

        # If it was surrounded by quotes, strip the quotes for processing
        if was_surrounded_by_quotes:
            # Remove surrounding quotes temporarily
            string_content = string_content[1:-1]

        # Replace all <xliff:g> tags within the current <string>
        updated_content = re.sub(
            r'<xliff:g[^>]*>(.*?)</xliff:g>', replace_xliff_tag, string_content)

        # Re-add quotes around the string content if it was originally surrounded by quotes
        if was_surrounded_by_quotes:
            updated_content = f'"{updated_content}"'

        # Rebuild <string> tag with msgid and updated content
        if msgid:
            return f'<string name="{match.group(1)}" msgid="{msgid}">{updated_content}</string>'
        else:
            return f'<string name="{match.group(1)}">{updated_content}</string>'

    # Regex to find <string> tags and process them separately
    updated_content = re.sub(
        r'<string name="([^"]+)"(?: msgid="([^"]+)")?>(.*?)</string>', process_string, xml_content, flags=re.DOTALL)

    return updated_content


def process_strings_file(file_path):
    """Reads, converts, and overwrites a strings.xml file."""
    try:
        with open(file_path, "r", encoding="utf-8") as file:
            xml_data = file.read()

        converted_xml = convert_xliff_to_format_specifier(xml_data)

        with open(file_path, "w", encoding="utf-8") as file:
            file.write(converted_xml)

        print(f"✅ Processed: {file_path}")
    except Exception as e:
        print(f"❌ Error processing {file_path}: {e}")


def find_and_process_strings():
    """Finds all 'strings.xml' files inside values and values-* folders and processes them."""
    # Walk through all directories and subdirectories
    for root, dirs, files in os.walk(BASE_PATH):
        folder_name = os.path.basename(root)

        # Match folders starting with 'values' (including 'values-*')
        if folder_name.startswith("values"):
            for file in files:
                if file == "strings.xml":
                    file_path = os.path.join(root, file)
                    process_strings_file(file_path)


if __name__ == "__main__":
    find_and_process_strings()
    print("🎉 All strings.xml files have been updated!")

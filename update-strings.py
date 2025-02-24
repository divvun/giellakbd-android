import os
import re

# Path to search for values folders
BASE_PATH = "app/src/main/res"


def convert_xliff_to_format_specifier(xml_content):
    """
    Converts XLIFF <xliff:g> tags in Android string resources to format specifiers (%n$s).
    Resets numbering per <string> tag.
    """

    def process_string(match):
        """Handles each <string> tag separately, resetting numbering per string."""
        string_content = match.group(2)  # Extract text inside the <string> tag
        counter = 0  # Reset parameter numbering for each string

        def replace_xliff_tag(xliff_match):
            nonlocal counter
            counter += 1
            return f"%{counter}$s"

        # Replace all <xliff:g> tags within the current <string>
        updated_content = re.sub(
            r'<xliff:g[^>]*>(.*?)</xliff:g>', replace_xliff_tag, string_content)

        # Remove unnecessary surrounding quotes (if present)
        updated_content = re.sub(r'^(["\'])(.*)\1$', r'\2', updated_content)

        return f'<string name="{match.group(1)}">{updated_content}</string>'

    # Regex to find <string> tags and process them separately
    updated_content = re.sub(
        r'<string name="([^"]+)">(.*?)</string>', process_string, xml_content, flags=re.DOTALL)

    return updated_content


def process_strings_file(file_path):
    """Reads, converts, and overwrites a strings.xml file."""
    with open(file_path, "r", encoding="utf-8") as file:
        xml_data = file.read()

    converted_xml = convert_xliff_to_format_specifier(xml_data)

    with open(file_path, "w", encoding="utf-8") as file:
        file.write(converted_xml)

    print(f"✅ Processed: {file_path}")


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

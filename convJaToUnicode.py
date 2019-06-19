import os
import sys


def main():
    reverse = False

    if len(sys.argv) > 1 and sys.argv[1] == '--reverse':
        reverse = True

    from_encoding = "utf-8" if reverse else "unicode_escape"
    to_encoding = "unicode_escape" if reverse else "utf-8"

    for root, dirs, files in os.walk("."):
        for file in files:
            if file == "Bundle_ja.properties":
                with open(root + "/" + file, "r", encoding=from_encoding) as fin:
                    file_text = fin.read()
                    if len(file_text) > 0 and file_text != "\\n" and file_text != "\n":
                        if reverse:
                            with open(root + "/" + file, "w") as fout:
                                file_lines = file_text.splitlines()
                                for line in file_lines:
                                    encoded_line = line.encode(to_encoding).decode("ascii") + "\n"
                                    fout.write(encoded_line)
                        else: 
                            with open(root + "/" + file, "w", encoding=to_encoding) as fout:
                                fout.write(file_text)


if __name__ == '__main__':
    main()

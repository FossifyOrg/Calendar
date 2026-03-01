import re
import os

# arguments
filename_in = "app/src/main/res/layout/horizontal_1.xml"
limit = 14 # split the day into 15 minute segments

# constants
filename_regex = r'(.*)_1(.*)'
content_regex = r'(.*)layout_weight="1"(.*)'

# variables
rid_base = os.path.splitext(os.path.basename(filename_in))[0]
rids = "R.layout." + rid_base + ","

# read first file's content
with open(filename_in, "r") as file:
    content = file.read()
    for i in range(2, limit + 1):
        filename_out = re.sub(filename_regex, r"\g<1>_" + str(i) + r"\g<2>", filename_in)
        rid = re.sub(filename_regex, r"\g<1>_" + str(i) + r"\g<2>", rid_base)
        content_out = re.sub(content_regex, r'\g<1>layout_weight="' + str(i) + r'"\g<2>', content)
        with open(filename_out, "w") as f:
            f.write(content_out)
        rids += "\nR.layout." + rid + ","

# print R.layouts so they can easily be pasted into code
print(rids)


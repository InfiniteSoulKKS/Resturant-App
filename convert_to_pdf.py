#!/usr/bin/env python3
"""Convert INTERVIEW_PREP.md to a styled PDF."""

import markdown
from xhtml2pdf import pisa
import io

# Read the markdown
with open("INTERVIEW_PREP.md", "r") as f:
    md_content = f.read()

# Convert markdown to HTML
html_body = markdown.markdown(
    md_content,
    extensions=["tables", "fenced_code", "codehilite", "toc"],
    output_format="html5",
)

CSS = """
@page {
  size: A4;
  margin: 1.8cm 1.5cm 2cm 1.5cm;
}

body {
  font-family: Helvetica, Arial, sans-serif;
  font-size: 10pt;
  line-height: 1.55;
  color: #1a1a1a;
}

h1 {
  font-size: 22pt;
  color: #1a56db;
  border-bottom: 3px solid #1a56db;
  padding-bottom: 8px;
  margin-top: 0;
}

h2 {
  font-size: 16pt;
  color: #1e3a5f;
  border-bottom: 1.5px solid #ccc;
  padding-bottom: 5px;
  margin-top: 28px;
}

h3 {
  font-size: 13pt;
  color: #2c5282;
  margin-top: 18px;
}

h4 {
  font-size: 11pt;
  color: #2d3748;
  margin-top: 12px;
}

p {
  margin: 6px 0;
}

blockquote {
  border-left: 3px solid #1a56db;
  background: #f0f4ff;
  padding: 8px 12px;
  margin: 10px 0;
  font-style: italic;
  color: #333;
}

table {
  width: 100%;
  border-collapse: collapse;
  margin: 10px 0;
  font-size: 9pt;
}

th {
  background: #1e3a5f;
  color: white;
  padding: 6px 8px;
  text-align: left;
  font-weight: bold;
  border: 1px solid #1e3a5f;
}

td {
  padding: 5px 8px;
  border: 1px solid #ddd;
  vertical-align: top;
}

tr:nth-child(even) td {
  background: #f7f9fc;
}

code {
  font-family: Courier New, Courier, monospace;
  font-size: 9pt;
  background: #f1f5f9;
  padding: 1px 4px;
  color: #c7254e;
}

pre {
  background: #1e293b;
  color: #e2e8f0;
  padding: 12px;
  border-radius: 6px;
  font-size: 8.5pt;
  line-height: 1.4;
  white-space: pre-wrap;
  word-wrap: break-word;
}

pre code {
  background: none;
  color: #e2e8f0;
  padding: 0;
}

ul, ol {
  margin: 6px 0;
  padding-left: 22px;
}

li {
  margin: 3px 0;
}

hr {
  border: none;
  border-top: 1.5px solid #ccc;
  margin: 20px 0;
}

strong {
  color: #1a1a1a;
}
"""

html = f"""<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<style>{CSS}</style>
</head>
<body>
{html_body}
</body>
</html>"""

# Convert to PDF
output_path = "SavoryStay_Interview_Prep.pdf"
with open(output_path, "wb") as f:
    status = pisa.CreatePDF(
        io.StringIO(html),
        dest=f,
        encoding="utf-8",
    )

if status.err:
    print(f"Error converting to PDF: {status.err}")
else:
    print(f"PDF created: {output_path}")

import os
size_kb = os.path.getsize(output_path) / 1024
print(f"   Size: {size_kb:.0f} KB")

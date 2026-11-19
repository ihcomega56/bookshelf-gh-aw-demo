#!/usr/bin/env python3
"""Convert a JaCoCo XML report to Cobertura XML format."""

import argparse
import os
import sys
import xml.etree.ElementTree as ET
from xml.dom import minidom


def parse_args():
    parser = argparse.ArgumentParser(description="Convert JaCoCo XML to Cobertura XML")
    parser.add_argument("input", help="Path to JaCoCo XML report")
    parser.add_argument("output", help="Path to write Cobertura XML report")
    parser.add_argument("--source-root", default="src/main/java",
                        help="Source root directory (default: src/main/java)")
    return parser.parse_args()


def counter_value(element, counter_type, attr):
    for counter in element.findall("counter"):
        if counter.get("type") == counter_type:
            return int(counter.get(attr, 0))
    return 0


def rate(covered, missed):
    total = covered + missed
    return covered / total if total > 0 else 0.0


def convert(input_path, output_path, source_root):
    tree = ET.parse(input_path)
    root = tree.getroot()

    cob_root = ET.Element("coverage")
    cob_root.set("version", "jacoco-to-cobertura")

    sources = ET.SubElement(cob_root, "sources")
    ET.SubElement(sources, "source").text = source_root

    packages_el = ET.SubElement(cob_root, "packages")

    total_lines_covered = 0
    total_lines_missed = 0
    total_branches_covered = 0
    total_branches_missed = 0

    for pkg in root.findall("package"):
        pkg_name = pkg.get("name", "").replace("/", ".")
        pkg_el = ET.SubElement(packages_el, "package")
        pkg_el.set("name", pkg_name)

        classes_el = ET.SubElement(pkg_el, "classes")

        for src_file in pkg.findall("sourcefile"):
            src_name = src_file.get("name", "")
            class_el = ET.SubElement(classes_el, "class")
            class_el.set("name", pkg_name + "." + src_name.replace(".java", ""))
            class_el.set("filename", os.path.join(
                source_root, pkg.get("name", ""), src_name))

            lines_el = ET.SubElement(class_el, "lines")

            file_lines_covered = 0
            file_lines_missed = 0
            file_branches_covered = 0
            file_branches_missed = 0

            for line in src_file.findall("line"):
                nr = line.get("nr")
                mi = int(line.get("mi", 0))
                ci = int(line.get("ci", 0))
                mb = int(line.get("mb", 0))
                cb = int(line.get("cb", 0))

                line_el = ET.SubElement(lines_el, "line")
                line_el.set("number", nr)
                line_el.set("hits", "0" if ci == 0 else "1")

                if mb + cb > 0:
                    total_conditions = mb + cb
                    covered_conditions = cb
                    line_el.set("branch", "true")
                    line_el.set("condition-coverage",
                                f"{int(100 * covered_conditions / total_conditions)}% "
                                f"({covered_conditions}/{total_conditions})")
                    file_branches_covered += cb
                    file_branches_missed += mb
                else:
                    line_el.set("branch", "false")

                if ci > 0:
                    file_lines_covered += 1
                else:
                    file_lines_missed += 1

            class_el.set("line-rate", str(rate(file_lines_covered, file_lines_missed)))
            class_el.set("branch-rate", str(rate(file_branches_covered, file_branches_missed)))

            total_lines_covered += file_lines_covered
            total_lines_missed += file_lines_missed
            total_branches_covered += file_branches_covered
            total_branches_missed += file_branches_missed

        pkg_lc = counter_value(pkg, "LINE", "covered")
        pkg_lm = counter_value(pkg, "LINE", "missed")
        pkg_bc = counter_value(pkg, "BRANCH", "covered")
        pkg_bm = counter_value(pkg, "BRANCH", "missed")
        pkg_el.set("line-rate", str(rate(pkg_lc, pkg_lm)))
        pkg_el.set("branch-rate", str(rate(pkg_bc, pkg_bm)))

    cob_root.set("line-rate", str(rate(total_lines_covered, total_lines_missed)))
    cob_root.set("branch-rate", str(rate(total_branches_covered, total_branches_missed)))
    cob_root.set("lines-covered", str(total_lines_covered))
    cob_root.set("lines-valid", str(total_lines_covered + total_lines_missed))
    cob_root.set("branches-covered", str(total_branches_covered))
    cob_root.set("branches-valid", str(total_branches_covered + total_branches_missed))

    xml_str = minidom.parseString(
        ET.tostring(cob_root, encoding="unicode")
    ).toprettyxml(indent="  ")

    with open(output_path, "w", encoding="utf-8") as f:
        f.write(xml_str)

    print(f"Cobertura report written to {output_path}")
    print(f"Lines:    {total_lines_covered}/{total_lines_covered + total_lines_missed} "
          f"({rate(total_lines_covered, total_lines_missed):.1%})")
    print(f"Branches: {total_branches_covered}/{total_branches_covered + total_branches_missed} "
          f"({rate(total_branches_covered, total_branches_missed):.1%})")


def main():
    args = parse_args()
    if not os.path.exists(args.input):
        print(f"Error: input file not found: {args.input}", file=sys.stderr)
        sys.exit(1)
    convert(args.input, args.output, args.source_root)


if __name__ == "__main__":
    main()

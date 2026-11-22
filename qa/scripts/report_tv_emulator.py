#!/usr/bin/env python3
"""Summarize real Android TV UIAutomator focus bounds captured by QA runner."""
from pathlib import Path
import json
import re
import sys
import xml.etree.ElementTree as ET
folder = Path(sys.argv[1] if len(sys.argv) > 1 else "shots")
rows = []
for shot in sorted(folder.glob("*.png")):
    item = {"screen": shot.name, "png_bytes": shot.stat().st_size}
    hierarchy = shot.with_suffix(".xml")
    if hierarchy.exists():
        try:
            tree = ET.parse(hierarchy)
            focus = [n for n in tree.iter("node") if n.attrib.get("focused") == "true"]
            item["focus"] = [
                {"class": n.attrib.get("class", ""), "text": n.attrib.get("text", ""),
                 "content_description": n.attrib.get("content-desc", ""),
                 "bounds": n.attrib.get("bounds", "")}
                for n in focus]
            item["qa_poster_count"] = sum(
                "QA " in n.attrib.get("content-desc", "")
                for n in tree.iter("node"))
            near_edge = False
            for n in focus:
                b = re.findall(r"\d+", n.attrib.get("bounds", ""))
                if len(b) == 4:
                    x1,y1,x2,y2 = map(int,b)
                    if "QA " in n.attrib.get("content-desc",""):
                        near_edge |= x1 < 5 or y1 < 5 or x2 > 1275 or y2 > 715
            item["focused_poster_touches_screen_edge"] = near_edge
        except Exception as error:
            item["ui_error"] = str(error)
    else:
        item["ui_error"] = "No UIAutomator hierarchy collected"
    rows.append(item)
(folder / "focus-report.json").write_text(json.dumps(rows, indent=2, ensure_ascii=False))
with (folder / "focus-report.txt").open("w") as out:
    print("Capturas tomadas por un emulador Android TV: " + str(len(rows)), file=out)
    print("Los archivos qa-* tienen títulos e imágenes sintéticas"
          " renderizados por la UI real mediante un debug build temporal.", file=out)
    print("La ausencia de clipping de píxeles requiere inspección visual."
          " El XML solo informa del foco y los límites declarados.", file=out)
    for row in rows:
        print("\n" + row["screen"] + ": " + str(row.get("png_bytes", 0)) + " bytes", file=out)
        for focus in row.get("focus", []):
            print("  foco: " + focus.get("content_description", "")
                  + " " + focus.get("bounds", ""), file=out)
        if row.get("focused_poster_touches_screen_edge"):
            print("  AVISO: poster focalizado toca un borde de la pantalla.", file=out)
print("Recorded", len(rows), "emulator screenshots in", folder)

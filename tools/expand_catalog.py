#!/usr/bin/env python3
"""Merge the curated 2024–2026 device supplement into the offline catalog.

The script is idempotent: existing user models are preserved and duplicates are
removed case-insensitively. Sources for the 2026 refresh are recorded in JSON.
"""
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / "app/src/main/assets/catalog.json"

ADDITIONS = {
    "phone": {
        "Apple": ["iPhone 17e", "iPhone 18 Pro", "iPhone 18 Pro Max", "iPhone Duo"],
        "Samsung": [
            "Galaxy A06 5G", "Galaxy A07", "Galaxy A08", "Galaxy A17", "Galaxy A17 5G", "Galaxy A18",
            "Galaxy A27 5G", "Galaxy A37 5G", "Galaxy A57", "Galaxy F05", "Galaxy F06 5G", "Galaxy F14",
            "Galaxy F15", "Galaxy F16", "Galaxy F17", "Galaxy M05", "Galaxy M06", "Galaxy M16", "Galaxy M17",
            "Galaxy M47 5G", "Galaxy S25 FE", "Galaxy S26", "Galaxy S26+", "Galaxy S26 Ultra", "Galaxy S26 FE",
            "Galaxy Z Flip 7 FE", "Galaxy Z Flip 8", "Galaxy Z Fold 8", "Galaxy Z Fold 8 Ultra", "Galaxy Z TriFold",
            "Galaxy XCover 7", "Galaxy XCover 7 Pro",
        ],
        "Xiaomi": [
            "Xiaomi 14 Civi", "Xiaomi 15 Pro", "Xiaomi 15 Civi", "Xiaomi 17", "Xiaomi 17 Max", "Xiaomi 17 Pro",
            "Xiaomi 17 Pro Max", "Xiaomi 17 Ultra", "Xiaomi 17T", "Xiaomi 17T Pro", "Xiaomi 18", "Xiaomi 18 Pro",
            "Xiaomi 18 Pro Max", "Xiaomi 18 Fold", "Xiaomi Civi 5 Pro", "Xiaomi Mix Fold 5",
        ],
        "Redmi": [
            "Redmi 15", "Redmi 15 5G", "Redmi 15C", "Redmi A3x", "Redmi A3 Pro", "Redmi Note 14 5G",
            "Redmi Note 15", "Redmi Note 15 Pro", "Redmi Note 15 Pro+", "Redmi Turbo 4 Pro", "Redmi Turbo 5",
            "Redmi Turbo 5 Max", "Redmi K80 Ultra", "Redmi K90", "Redmi K90 Pro Max",
        ],
        "Poco": [
            "Poco C71", "Poco C75 5G", "Poco C85", "Poco M7", "Poco M7 Plus", "Poco X8 Pro", "Poco X8 Pro Max",
            "Poco F9", "Poco F9 Pro", "Poco F9 Ultra", "Poco M8", "Poco M8 Pro",
        ],
        "Huawei": [
            "Pura 70 Pro+", "Pura 80 Pro+", "Pura X", "Mate 70 Pro+", "Mate 70 RS", "Mate 80", "Mate 80 Pro",
            "Mate 80 Pro Max", "Mate 80 RS", "Mate XT", "Mate XTs", "Mate X7", "Nova 12s", "Nova 12i", "Nova 12 SE",
            "Nova 14", "Nova 14 Pro", "Nova 14 Ultra", "Enjoy 70X", "Enjoy 80",
        ],
        "Honor": [
            "Honor 300", "Honor 300 Pro", "Honor 300 Ultra", "Honor 400 Smart", "Honor 500", "Honor 500 Pro",
            "Honor 700", "Honor 700S", "Honor 700 Pro", "Honor Magic 6 Lite", "Honor Magic 7 Lite", "Honor Magic 8",
            "Honor Magic 8 Pro", "Honor Magic8 Lite", "Honor Magic V6", "Honor GT", "Honor GT Pro", "Honor GT 2",
            "Honor X6c", "Honor X7c", "Honor X8c", "Honor X9d", "Honor X70", "Honor X70i", "Honor Power",
            "Honor Power 2", "Honor Robot Phone",
        ],
        "Motorola": [
            "Moto E14", "Moto E15", "Moto G05", "Moto G06", "Moto G15", "Moto G15 Power", "Moto G35", "Moto G45",
            "Moto G56", "Moto G57", "Moto G75", "Moto G86", "Moto G (2026)", "Edge 50s", "Edge 60 Stylus",
            "Edge 60 Neo", "Edge 70", "Edge 70 Neo", "Razr 50s", "Razr 60s", "Razr 70", "Razr 70 Ultra",
            "Razr Fold", "Motorola Signature 27", "Moto S50", "Moto S50 Neo",
        ],
        "Google": ["Pixel 10a", "Pixel 11", "Pixel 11 Pro", "Pixel 11 Pro XL", "Pixel 11 Pro Fold"],
        "OnePlus": [
            "OnePlus 13T", "OnePlus 13s", "OnePlus Nord 5", "OnePlus Nord CE 5", "OnePlus Nord CE 5 Lite",
            "OnePlus Ace 5 Ultra", "OnePlus Ace 5 Racing", "OnePlus Ace 6", "OnePlus Ace 6T", "OnePlus 15T",
        ],
        "Oppo": [
            "Find X8s", "Find X8s+", "Find X9", "Find X9 Pro", "Find X9 Ultra", "Find X10 Pro Max", "Find N6",
            "Reno 15", "Reno 15 Pro", "Reno 15 Pro Max", "Oppo A5", "Oppo A5 Pro", "Oppo A5i", "Oppo A5x",
            "Oppo A6", "Oppo A6 Pro", "Oppo A6x", "Oppo K13", "Oppo K13 Turbo", "Oppo K13 Turbo Pro",
        ],
        "Realme": [
            "Realme 14T", "Realme 14x", "Realme 14 Pro Lite", "Realme 15", "Realme 15 Pro", "Realme 15T",
            "Realme GT 7T", "Realme GT 8", "Realme GT 8 Pro", "Realme GT Neo6", "Realme GT Neo6 SE", "Realme Neo7",
            "Realme Neo7x", "Realme Neo7 SE", "Realme Neo7 Turbo", "Realme P3", "Realme P3 Pro", "Realme P3x",
            "Realme P4", "Realme P4 Pro", "Realme C71", "Realme C75", "Realme C75x", "Realme C85", "Realme Note 70",
        ],
        "Vivo": [
            "Vivo X200 Pro mini", "Vivo X200s", "Vivo X200 FE", "Vivo X300", "Vivo X300 Pro", "Vivo X300 Ultra",
            "Vivo X Fold5", "Vivo V50e", "Vivo V60", "Vivo V60e", "Vivo Y19s", "Vivo Y29", "Vivo Y39", "Vivo Y300",
            "Vivo Y300i", "Vivo Y300 Pro", "Vivo Y300 Pro+", "Vivo Y400", "Vivo Y400 Pro", "Vivo Y31d", "Vivo T4",
            "Vivo T4x", "Vivo T4R", "Vivo T4 Lite",
        ],
        "iQOO": [
            "iQOO 15", "iQOO Neo11", "iQOO Z10R", "iQOO Z10x", "iQOO Z10 Turbo Pro", "iQOO Z11 Turbo",
            "iQOO Neo9S Pro", "iQOO Neo9S Pro+", "iQOO Neo10R", "iQOO Z9s", "iQOO Z9s Pro", "iQOO Z9 Turbo+",
        ],
        "Nothing": ["Nothing Phone 4a", "Nothing Phone 4a Pro", "Nothing Phone 4b"],
        "Sony": ["Xperia 1 VIII", "Xperia 10 VIII"],
        "ZTE": [
            "Blade A35", "Blade A35e", "Blade A36", "Blade A56", "Blade A75", "Blade A76", "Blade V70", "Blade V80",
            "Nubia Music 2", "Nubia Focus 2 Ultra",
        ],
        "Nubia": ["Nubia Z80 Ultra", "Nubia Flip 3", "Nubia Neo 3 GT", "Nubia Neo 4", "Nubia Air"],
        "RedMagic": ["RedMagic 10 Air", "RedMagic 11 Pro", "RedMagic 11S Pro", "RedMagic 11 Air", "RedMagic 12 Pro"],
        "Infinix": [
            "Hot 50 Pro+", "Hot 60", "Hot 60i", "Hot 60 Pro", "Hot 60 Pro+", "Note 50 Pro+", "Note 50s", "Note 60",
            "Note 60 Pro", "Note 60 Ultra", "Smart 10", "Smart 10 Plus", "Smart 11", "Zero 50", "Zero 50 Pro", "GT 30",
        ],
        "Tecno": [
            "Spark 40", "Spark 40C", "Spark 40 Pro", "Spark 40 Pro+", "Spark 50", "Spark 50C", "Camon 40 Premier",
            "Camon 50", "Camon 50 Pro", "Pova 7 Ultra", "Pova Curve", "Pova Slim", "Phantom V Flip 2", "Spark Go 1",
        ],
        "Itel": ["A50C", "A60s", "A90", "A100", "A100 Pro", "S25 Ultra", "S26", "S26 Ultra", "P65", "P70", "P80", "City 100"],
        "Nokia": ["Nokia 235 4G", "Nokia 3210 2024", "Nokia 110 Power", "Nokia 105 2024"],
        "HMD": ["HMD Aura2", "HMD Key", "HMD Luma", "HMD 100", "HMD Touch 4G", "HMD Barbie Phone"],
        "Meizu": ["Meizu 22", "Meizu 22 Pro", "Meizu Note 22", "Meizu Note 22 Pro", "Meizu 21 Note 5G"],
        "Blackview": ["BL9000", "BL9000 Pro", "BL7000", "BL8000", "BV7300", "BV8200", "BV9300 Pro", "Hero 10", "Shark 8", "Shark 9"],
        "Doogee": ["S200X", "S200 Ultra", "S Punk", "S Cyber", "V40", "V40 Pro", "V Max Plus", "Blade 10", "Blade 10 Pro", "Note 59"],
        "Ulefone": ["Armor 28 Pro", "Armor 29 Pro", "Armor 30 Pro", "Armor 33 Pro", "Armor 34 Pro", "Armor X16", "Armor X16 Pro", "Note 19", "Note 20 Pro"],
        "Oukitel": ["WP100 Titan", "WP200 Pro", "WP210", "WP300", "WP55", "WP55 Pro", "C59", "C61", "C63"],
        "Cubot": ["KingKong X", "KingKong X Pro", "KingKong Power 5", "KingKong Star 3", "KingKong Star 5", "Note 60", "P90", "X90"],
        "TCL": ["TCL 60", "TCL 60 NxtPaper", "TCL 60 XE", "TCL 60 SE", "TCL 50 Pro", "TCL 50 XE NxtPaper"],
        "Alcatel": ["Alcatel A62"],
    },
    "tablet": {
        "Apple": ["iPad Air 11 M4", "iPad Air 13 M4", "iPad Pro 11 M5", "iPad Pro 13 M5"],
        "Samsung": [
            "Galaxy Tab A11", "Galaxy Tab A11+", "Galaxy Tab S10 Lite", "Galaxy Tab S11", "Galaxy Tab S11 Ultra",
            "Galaxy Tab S12", "Galaxy Tab S12+", "Galaxy Tab S12 Ultra", "Galaxy Tab Active 5 Pro",
        ],
        "Lenovo": [
            "Idea Tab", "Idea Tab Plus", "Idea Tab Pro", "Idea Tab Pro Gen 2", "Tab K9", "Tab One", "Legion Tab Gen 3",
            "Legion Tab Gen 4", "Legion Y700 Gen 4", "Legion Y700 Gen 5", "Yoga Tab", "Yoga Tab Plus", "Legion Y900",
        ],
        "Huawei": ["MatePad 12 X", "MatePad Pro 12.2 2025", "MatePad Pro 13.2 2025", "MatePad Mini", "MatePad Edge", "MatePad 11.5 2025"],
        "Honor": ["Honor Pad V9", "Honor Pad X9a", "Honor Pad X10", "Honor Pad GT", "Honor Pad GT Pro", "Honor MagicPad 3", "Honor MagicPad3 Pro 13.3", "Honor MagicPad 4"],
        "Xiaomi": ["Xiaomi Pad 7S Pro", "Xiaomi Pad 8", "Xiaomi Pad 8 Pro"],
        "Redmi": ["Redmi Pad 2 Pro 5G", "Redmi Pad 3"],
        "Poco": ["Poco Pad 2"],
        "OnePlus": ["OnePlus Pad 4", "OnePlus Pad Go 2"],
        "Oppo": ["Oppo Pad 5", "Oppo Pad 5 Pro"],
        "Realme": ["Realme Pad 3", "Realme Pad 3 Pro"],
        "Vivo": ["Vivo Pad6", "Vivo Pad6 Pro"],
        "iQOO": ["iQOO Pad6", "iQOO Pad6 Pro"],
        "Microsoft": ["Surface Pro 12"],
        "Amazon": ["Fire HD 10 2025"],
        "Blackview": ["Tab 60 WiFi", "Tab 90 WiFi", "Active 12 Pro"],
        "Teclast": ["P50AI Pro", "T60 Pro", "T70", "M70", "P40HD 2024"],
        "Alldocube": ["iPlay 60 mini Turbo", "iPlay 70 mini Ultra", "iPlay 80", "iPlay 80 Pro"],
    },
}

RENAMES = {
    "phone": {
        "Apple": {"iPhone 17 Air": "iPhone Air"},
    },
}

SOURCES = [
    "https://en.wikipedia.org/wiki/Category:Mobile_phones_introduced_in_2024",
    "https://en.wikipedia.org/wiki/Category:Mobile_phones_introduced_in_2025",
    "https://en.wikipedia.org/wiki/Category:Mobile_phones_introduced_in_2026",
    "https://www.cnet.com/tech/mobile/comparing-the-2026-phone-lineups-from-apple-samsung-google-and-everyone-else/",
    "https://www.gsmarena.com/best_phones_2026_gsmarena_awards-news-75526.php",
    "https://benchmarks.ul.com/compare/best-tablets",
]


def unique(values: list[str]) -> list[str]:
    result: dict[str, str] = {}
    for value in values:
        value = value.strip()
        if value:
            result.setdefault(value.casefold(), value)
    return sorted(result.values(), key=str.casefold)


def main() -> None:
    root = json.loads(CATALOG.read_text(encoding="utf-8"))
    for category, brands in RENAMES.items():
        for entry in root[category]:
            replacements = brands.get(entry["brand"], {})
            entry["models"] = [replacements.get(model, model) for model in entry["models"]]

    for category, brands in ADDITIONS.items():
        by_brand = {entry["brand"].casefold(): entry for entry in root[category]}
        for brand, models in brands.items():
            entry = by_brand.get(brand.casefold())
            if entry is None:
                entry = {"brand": brand, "models": []}
                if category == "pc_component":
                    raise ValueError("New component brands require a componentType")
                root[category].append(entry)
                by_brand[brand.casefold()] = entry
            entry["models"] = unique(entry["models"] + models)

    for category in ("phone", "tablet", "pc_component"):
        for entry in root[category]:
            entry["models"] = unique(entry["models"])

    root["version"] = 3
    root["updated"] = "2026-11-24"
    root["sources"] = SOURCES

    lines = ["{"]
    lines.append(f'  "version": {root["version"]},')
    lines.append(f'  "updated": {json.dumps(root["updated"])},')
    lines.append(f'  "sources": {json.dumps(root["sources"], ensure_ascii=False)},')
    categories = ("phone", "tablet", "pc_component")
    for category_index, category in enumerate(categories):
        lines.append(f'  "{category}": [')
        entries = root[category]
        for index, entry in enumerate(entries):
            suffix = "," if index < len(entries) - 1 else ""
            lines.append("    " + json.dumps(entry, ensure_ascii=False, separators=(",", ":")) + suffix)
        lines.append("  ]" + ("," if category_index < len(categories) - 1 else ""))
    lines.append("}")
    CATALOG.write_text("\n".join(lines) + "\n", encoding="utf-8")

    counts = {
        category: sum(len(entry["models"]) for entry in root[category])
        for category in categories
    }
    print(json.dumps({"version": root["version"], "models": counts, "total": sum(counts.values())}, ensure_ascii=False))


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Build curriculum.json for the EE Timeline Android app.

Scrapes the Everything Everywhere AP World History curriculum page, joins every
listed episode against the podcast RSS feed, applies the manual corrections
below, and writes ``app/src/main/assets/curriculum.json``.

Usage:
    python3 tools/dataset/build_dataset.py             # regenerate the asset
    python3 tools/dataset/build_dataset.py --verify    # also probe every audio URL
    python3 tools/dataset/build_dataset.py --out /tmp/curriculum.json

Only the Python standard library is used.
"""

from __future__ import annotations

import argparse
import html
import json
import re
import sys
import urllib.request
import xml.etree.ElementTree as ET
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
from html.parser import HTMLParser
from pathlib import Path

PAGE_URL = "https://everything-everywhere.com/everything-everywhere-ap-world-history-curriculum/"
FEED_URL = "https://feeds.megaphone.fm/ADV3162807280"
SITE = "https://everything-everywhere.com/"
DEFAULT_OUT = Path(__file__).resolve().parents[2] / "app/src/main/assets/curriculum.json"

ITUNES = "{http://www.itunes.com/dtds/podcast-1.0.dtd}"
USER_AGENT = "ee-timeline-dataset/1.0 (+https://everything-everywhere.com/)"

# ---------------------------------------------------------------------------
# MANUAL CORRECTIONS (page errors)
# ---------------------------------------------------------------------------
# The curriculum page has a handful of broken/misaligned episode links. Keyed
# by (topic id, slug found on the page) -> slug that the entry should point at.
# The display title always comes from the RSS item, so corrected entries pick
# up the real episode title automatically.
FIX_LINKS: dict[tuple[str, str], str] = {
    # 7.7 "Ramages Rampage" pointed at Hildegard of Bingen (1.6).
    ("7.7", "hildegard-of-bingen"): "ramages-rampage",
    # 7.7 "The Siege of Tobruk" pointed at The Vikings (1.6).
    ("7.7", "the-vikings"): "the-siege-of-tobruk",
    # 7.7 "Project Habakkuk" pointed at The Northwest Passage (4.2).
    ("7.7", "the-northwest-passage"): "project-habakkuk",
}

# 8.2's second "Berlin Wall" bullet points at the Berlin Airlift page and 8.3's
# "Chinese Cultural Revolution" bullet points at the Chinese Communist
# Revolution page. Both targets are real, distinct episodes, so only the title
# (taken from RSS) needs fixing. Recorded for the issues list.
RETITLED: list[tuple[str, str, str]] = [
    ("8.2", "the-berlin-airlift", "second 'Berlin Wall' bullet relabelled 'The Berlin Airlift'"),
    (
        "8.3",
        "the-chinese-communist-revolution",
        "'Chinese Cultural Revolution' bullet points at The Chinese Communist Revolution; retitled",
    ),
]

# Non-episode page defects worth surfacing in the issues list.
STATIC_ISSUES = [
    "8.3: empty bullet in the 'Effects of the Cold War (Proxy wars)' list removed",
    "Older feed items (pre-2022) carry no episode page link; those were matched by normalized title",
    "Republished episodes use the most recent feed item; the ' (Encore)' suffix is removed from titles",
]

# Old feed items predate per-episode page links, so URL-slug matching fails for
# them. These are matched by episode title instead (most recent item wins).
TITLE_MATCH: dict[str, str] = {
    "al-andalus-moorish-spain": "Al Andalus",
    "the-age-of-enlightenment": "The Age of Enlightenment",
    "hanns-scharff-master-interrogator-of-the-luftwaffe": "Hanns Scharff: Master Interrogator of the Luftwaffe",
    "operation-sealion": "Operation Sealion",
    "post-wwii-german-expulsions": "Post WWII German Expulsions",
    "the-b-52-stratofortress": "The B-52 Stratofortress",
}

# ---------------------------------------------------------------------------
# MANUAL TIMELINE DATA: slug -> (approxYear, yearLabel)
# approxYear is the point used for the dot; negative = BCE.
# ---------------------------------------------------------------------------
YEARS: dict[str, tuple[int, str]] = {
    # Unit 1
    "chinas-imperial-dynasties": (-221, "221 BCE–1912"),
    "the-voyages-of-admiral-zheng-he": (1405, "1405–1433"),
    "the-song-dynasty": (1100, "960–1279"),
    "yellow-river-the-cradle-of-chinese-civilization": (-3000, "c. 3000 BCE"),
    "the-islamic-caliphates": (750, "632–1258"),
    "al-andalus-moorish-spain": (750, "711–1492"),
    "the-siege-of-baghdad": (1258, "1258"),
    "the-house-of-wisdom": (820, "c. 800–1258"),
    "the-khmer-empire": (1150, "802–1431"),
    "the-srivijaya-empire": (800, "c. 650–1377"),
    "the-maya-civilization": (600, "c. 250–900"),
    "the-rise-and-fall-of-the-aztec-empire": (1428, "1428–1521"),
    "who-was-the-richest-person-in-history": (1324, "c. 1280–1337"),
    "great-zimbabwe": (1200, "c. 1100–1450"),
    "joan-of-arc": (1429, "1412–1431"),
    "the-medici-family-and-renaissance-florence": (1450, "c. 1434–1737"),
    "hildegard-of-bingen": (1150, "1098–1179"),
    "the-knights-templar": (1120, "1119–1312"),
    "the-vikings": (850, "793–1066"),
    "the-war-of-the-roses": (1455, "1455–1487"),
    "the-magna-carta": (1215, "1215"),
    "michelangelo-the-greatest-artist-of-the-renaissance": (1512, "1475–1564"),
    "the-battle-of-bannockburn": (1314, "1314"),
    "a-history-of-the-crusades": (1096, "1096–1291"),
    "the-cortes-of-leon-of-1188": (1188, "1188"),
    # Unit 2
    "zoroastrianism": (-1000, "c. 1000 BCE"),
    "the-silk-road": (100, "c. 130 BCE–1450"),
    "how-the-secret-to-silk-was-smuggled-out-of-china": (550, "c. 550"),
    "a-brief-history-of-the-mongol-empire": (1206, "1206–1368"),
    "genghis-khan-the-man-who-built-the-mongol-empire": (1206, "c. 1162–1227"),
    "the-mongol-invasions-of-japan": (1274, "1274 & 1281"),
    "marco-polo": (1271, "1271–1295"),
    "the-legend-of-prester-john": (1165, "c. 1165"),
    "the-indian-ocean-trade": (1000, "c. 800–1500"),
    "the-travels-of-ibn-battuta": (1325, "1325–1354"),
    "the-black-death": (1347, "1347–1351"),
    "the-little-ice-age": (1650, "c. 1300–1850"),
    # Unit 3
    "the-empire-that-never-existed-remake": (1000, "330–1453"),
    "a-brief-overview-of-the-ottoman-empire": (1453, "1299–1922"),
    "cannons-and-artillery": (1453, "c. 1350–1500"),
    "the-history-of-the-taj-mahal": (1632, "1632–1653"),
    "the-hagia-sophia": (537, "537"),
    "timur-and-the-timurid-empire": (1370, "1370–1507"),
    "miyamoto-musashi": (1600, "c. 1584–1645"),
    "the-icelandic-althing-the-worlds-oldest-parliament": (930, "930"),
    "the-defenestrations-of-prague": (1618, "1419 & 1618"),
    "the-thirty-years-war": (1618, "1618–1648"),
    "the-peace-of-westphalia-and-westphalian-sovereignty": (1648, "1648"),
    "isaac-newton-the-worlds-most-famous-alchemist": (1687, "1643–1727"),
    "did-gutenburg-really-invent-the-printing-press": (1450, "c. 1450"),
    "the-trial-of-galileo-galilei": (1633, "1633"),
    # Unit 4
    "the-fra-mauro-medieval-world-map": (1459, "c. 1450"),
    "ocean-currents-and-gyres": (1855, "1855"),
    "gil-eanes-and-the-volta-do-mar": (1434, "1434"),
    "the-northwest-passage": (1906, "1497–1906"),
    "the-hanseatic-league": (1350, "c. 1350–1669"),
    "the-spanish-reconquista": (1492, "718–1492"),
    "the-columbian-exchange": (1492, "1492–1600"),
    "the-great-dying-of-the-americas": (1520, "c. 1492–1600"),
    "the-rise-fall-and-possible-rise-of-maslin-agriculture": (-8000, "c. 8000 BCE"),
    "the-republic-of-pirates": (1700, "c. 1650–1730"),
    "the-battle-of-cajamarca": (1532, "1532"),
    "the-east-african-slave-trade": (1750, "c. 1500–1900"),
    "the-tensho-embassy": (1582, "1582–1590"),
    "the-spice-trade": (1600, "c. 1500–1800"),
    "the-darien-scheme": (1698, "1698–1700"),
    "world-war-zero-aka-the-seven-years-war": (1756, "1756–1763"),
    "the-spanish-inquisition": (1478, "1478–1834"),
    # Unit 5
    "the-age-of-enlightenment": (1750, "c. 1685–1815"),
    "the-wealth-of-nations": (1776, "1776"),
    "the-haitian-revolution": (1791, "1791–1804"),
    "catherine-the-great": (1762, "1762–1796"),
    "the-revolutions-of-1848": (1848, "1848"),
    "the-desecration-at-st-denis": (1793, "1793"),
    "napoleons-100-days": (1815, "1815"),
    "the-life-of-napoleon-bonaparte": (1804, "1769–1821"),
    "the-storming-of-the-bastille": (1789, "1789"),
    "the-cult-of-reason": (1793, "1793–1794"),
    "madame-la-guillotine": (1793, "1793–1794"),
    "napoleons-invasion-of-russia": (1812, "1812"),
    "the-battle-of-trafalgar": (1805, "1805"),
    "energy-work-and-power": (1847, "1840s"),
    "the-war-of-the-currents": (1890, "1880s–1890s"),
    "the-germ-theory-of-disease": (1876, "1860s–1880s"),
    "the-transatlantic-cable": (1858, "1858–1866"),
    "steel-the-metal-that-made-the-modern-world": (1856, "1856"),
    "all-about-the-internal-combustion-engine": (1876, "1876"),
    "the-great-stink-how-a-horrific-smell-changed-london-forever": (1858, "1858"),
    "the-meiji-restoration": (1868, "1868"),
    "the-development-of-the-steam-engine": (1769, "1712–1800"),
    "the-chartist-movement": (1838, "1838–1857"),
    "the-luddites": (1811, "1811–1816"),
    # Unit 6
    "the-scramble-for-africa": (1884, "1884–1914"),
    "king-leopold-and-the-congo-free-state": (1885, "1885–1908"),
    "the-voyages-of-captain-cook": (1768, "1768–1779"),
    "the-sinking-of-the-mignonette-and-justified-cannibalism": (1884, "1884"),
    "the-crimean-war": (1853, "1853–1856"),
    "imperial-leftovers-the-remnants-of-the-british-empire": (1947, "1947–1997"),
    "the-boxer-rebellion": (1899, "1899–1901"),
    "the-paraguayan-war": (1864, "1864–1870"),
    "the-taiping-rebellion": (1850, "1850–1864"),
    "the-opium-wars": (1839, "1839–1860"),
    "the-maori-settlement-of-new-zealand": (1250, "c. 1250–1300"),
    "the-indian-rebellion-of-1857": (1857, "1857"),
    "the-colonial-history-of-south-africa": (1652, "1652–1910"),
    "queen-ranavalona-i-of-madagascar": (1828, "1828–1861"),
    "the-irish-potato-famine": (1845, "1845–1852"),
    "soccer-in-south-america": (1890, "c. 1880–1930"),
    "the-globalization-of-baseball": (1880, "c. 1870–1930"),
    "all-about-curling": (1560, "c. 1560"),
    # Unit 7
    "the-great-arab-revolt": (1916, "1916–1918"),
    "the-russian-revolution": (1917, "1917"),
    "the-mexican-revolution": (1910, "1910–1920"),
    "the-sinking-of-the-lusitania": (1915, "1915"),
    "the-assassination-of-archduke-franz-ferdinand": (1914, "1914"),
    "the-eastern-front-of-world-war-i": (1914, "1914–1918"),
    "the-schlieffen-plan": (1914, "1905–1914"),
    "the-1917-french-army-mutinies": (1917, "1917"),
    "hanns-scharff-master-interrogator-of-the-luftwaffe": (1944, "1944"),
    "the-maginot-line": (1939, "1930–1940"),
    "the-first-battle-of-the-marne": (1914, "1914"),
    "the-red-baron": (1917, "1916–1918"),
    "the-christmas-truce-of-1914": (1914, "1914"),
    "the-kiel-mutiny": (1918, "1918"),
    "the-last-soldier-to-die-in-world-war-i": (1918, "1918"),
    "zone-rouge-and-the-iron-harvest": (1918, "1918–present"),
    "the-hindenburg-disaster": (1937, "1937"),
    "the-treaty-of-versailles": (1919, "1919"),
    "puyi-the-last-emperor-of-china": (1912, "1908–1912"),
    "the-mukden-incident": (1931, "1931"),
    "italian-vs-german-fascism": (1935, "1922–1945"),
    "the-molotov-ribbentrop-pact": (1939, "1939"),
    "wilhelm-canaris-working-against-the-nazis-from-the-inside": (1944, "1935–1945"),
    "hitlers-family": (1889, "1889–1945"),
    "operation-valkyrie-and-the-plot-to-kill-hitler": (1944, "1944"),
    "the-last-days-and-death-of-adolf-hitler": (1945, "1945"),
    "what-were-the-first-and-second-reichs": (1871, "800–1945"),
    "the-last-germans-to-surrender": (1945, "1945"),
    "the-children-of-nazis": (1945, "post-1945"),
    "did-the-us-have-advanced-knowledge-of-the-attack-on-pearl-harbor": (1941, "1941"),
    "the-battle-of-midway": (1942, "1942"),
    "the-winter-war": (1939, "1939–1940"),
    "operation-dragoon": (1944, "1944"),
    "operation-barbarossa": (1941, "1941"),
    "ramages-rampage": (1944, "1944"),
    "the-battle-of-stalingrad": (1942, "1942–1943"),
    "the-siege-of-tobruk": (1941, "1941"),
    "d-day": (1944, "1944"),
    "the-red-ball-express": (1944, "1944"),
    "operation-tannenbaum": (1940, "1940"),
    "operation-sealion": (1940, "1940"),
    "operation-long-jump": (1943, "1943"),
    "exercise-tiger-the-disastrous-rehearsal-for-d-day": (1944, "1944"),
    "the-battle-of-kasserine-pass": (1943, "1943"),
    "the-manhattan-project": (1942, "1942–1945"),
    "the-v1-and-v2-rockets": (1944, "1944–1945"),
    "the-history-of-the-ak-47": (1947, "1947"),
    "project-habakkuk": (1943, "1942–1943"),
    "how-close-were-the-nazis-to-making-an-atomic-bomb": (1942, "1939–1945"),
    "the-decision-to-drop-the-atomic-bomb": (1945, "1945"),
    "the-sinking-of-the-wilhelm-gustloff": (1945, "1945"),
    "post-wwii-german-expulsions": (1945, "1945–1950"),
    "admiral-isoroku-yamamoto": (1943, "1884–1943"),
    "the-kokoda-track": (1942, "1942"),
    "cracking-the-enigma-code": (1940, "1939–1945"),
    "german-pows-in-america": (1943, "1942–1946"),
    "the-bataan-death-march": (1942, "1942"),
    "the-axis-powers": (1940, "1939–1945"),
    "operation-fork-and-iceland-in-wwii": (1940, "1940"),
    "simo-hayha-the-white-death": (1940, "1939–1940"),
    "lyudmila-pavlichenko-the-deadliest-woman-in-world-war-ii": (1942, "1941–1942"),
    "the-night-witches": (1942, "1941–1945"),
    "operation-bernhard": (1943, "1942–1945"),
    "the-army-that-never-was": (1944, "1944"),
    "the-last-crow-war-chief": (1944, "1944"),
    "the-warsaw-ghetto-uprising": (1943, "1943"),
    "holodomor-the-worlds-forgotten-genocide": (1932, "1932–1933"),
    "the-wannsee-conference": (1942, "1942"),
    "the-1994-rwandan-genocide": (1994, "1994"),
    "francisco-macias-nguema": (1968, "1968–1979"),
    "adolf-eichmann-and-the-banality-of-evil": (1961, "1961"),
    "the-young-turks": (1908, "1908–1918"),
    "the-danish-resistance-in-wwii": (1943, "1940–1945"),
    # Unit 8
    "the-soviet-atom-bomb": (1949, "1949"),
    "the-lisbon-regicide-of-1908": (1908, "1908"),
    "the-marshall-plan": (1948, "1948–1951"),
    "a-history-of-nato": (1949, "1949"),
    "the-1989-romanian-revolution": (1989, "1989"),
    "the-berlin-wall": (1961, "1961–1989"),
    "the-berlin-airlift": (1948, "1948–1949"),
    "the-b-52-stratofortress": (1955, "1952–present"),
    "stanislav-petrov-the-man-who-saved-the-world": (1983, "1983"),
    "the-1969-sino-soviet-border-conflict": (1969, "1969"),
    "the-cambridge-five-spy-ring": (1951, "1930s–1963"),
    "the-acoustic-kitty-and-other-crazy-cia-plots": (1967, "1960s–1970s"),
    "the-origins-of-the-vietnam-war": (1954, "1946–1954"),
    "the-gulf-of-tonkin-incident": (1964, "1964"),
    "a-brief-overview-of-the-korean-war": (1950, "1950–1953"),
    "the-cultural-revolution": (1966, "1966–1976"),
    "the-great-leap-forward": (1958, "1958–1962"),
    "the-chinese-communist-revolution": (1949, "1927–1949"),
    "the-rise-and-fall-and-rise-of-deng-xiaoping": (1978, "1978–1997"),
    "the-may-fourth-movement": (1919, "1919"),
    "the-cuban-revolution": (1959, "1953–1959"),
    "the-nazino-tragedy-the-shocking-story-of-stalins-prison-island": (1933, "1933"),
    "the-great-emu-war": (1932, "1932"),
    "1972-munich-olympics-massacre": (1972, "1972"),
    "the-partition-of-india-and-pakistan": (1947, "1947"),
    "the-1956-suez-crisis": (1956, "1956"),
    "the-iranian-revolution": (1979, "1979"),
    "the-aswan-high-dam": (1970, "1960–1970"),
    "the-iran-hostage-crisis": (1979, "1979–1981"),
    # Unit 9
    "all-about-nuclear-power": (1954, "1954–present"),
    "the-channel-tunnel": (1994, "1988–1994"),
    "the-panama-canal": (1914, "1904–1914"),
    "origins-of-the-internet": (1969, "1969–1991"),
    "the-norwegian-sovereign-wealth-fund": (1990, "1990"),
    "malaria-the-disease-that-shaped-history": (-3000, "c. 3000 BCE"),
    "the-eradication-of-smallpox": (1980, "1980"),
    "the-spanish-flu-pandemic": (1918, "1918–1920"),
    "norman-borlaug-the-man-who-fed-the-world": (1970, "1960s–1970s"),
    "the-svalbard-global-seed-vault": (2008, "2008"),
    "the-honey-hunters-of-the-sundarbans": (2000, "present-day"),
    "the-bretton-woods-system": (1944, "1944–1971"),
    "flags-of-convenience-the-hidden-system-behind-global-shipping": (1948, "1948–present"),
    "the-history-of-the-shipping-container": (1956, "1956"),
    "the-history-of-sneakers-how-athletic-shoes-took-over-the-world": (1920, "c. 1900–present"),
    "the-rise-and-fall-of-opec": (1973, "1960–present"),
    "the-triangle-shirtwaist-factory-fire": (1911, "1911"),
    "the-tiananmen-square-massacre": (1989, "1989"),
    "the-extraordinary-life-of-josephine-baker": (1925, "1906–1975"),
    "domo-arigato-mr-momofuku": (1958, "1958"),
    "uncontacted-peoples": (2000, "present-day"),
    "the-league-of-nations": (1920, "1920–1946"),
    "the-formation-of-the-united-nations": (1945, "1945"),
}

UNIT_YEARS: dict[str, tuple[int, int | None]] = {
    "c. 1200 to c. 1450": (1200, 1450),
    "c. 1450 to c. 1750": (1450, 1750),
    "c. 1750 to c. 1900": (1750, 1900),
    "c. 1900 to present": (1900, None),  # None -> current year
}


# ---------------------------------------------------------------------------
# Tiny tree builder for the WordPress block markup
# ---------------------------------------------------------------------------
class Node:
    __slots__ = ("tag", "items", "own", "links", "children")

    def __init__(self, tag: str) -> None:
        self.tag = tag
        self.items: list[Node] = []
        self.own: list[str] = []
        self.links: list[tuple[str, str]] = []
        self.children: list[Node] = []


class PageParser(HTMLParser):
    """Builds an ordered event list: ('h2'|'p', text) and ('ol', Node)."""

    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.stack: list[Node] = []
        self.events: list[tuple[str, object]] = []
        self.capture: list | None = None

    def handle_starttag(self, tag: str, attrs) -> None:
        if tag in ("ol", "ul"):
            node = Node(tag)
            if self.stack:
                self.stack[-1].children.append(node)
            else:
                self.events.append((tag, node))
            self.stack.append(node)
        elif tag == "li":
            if not self.stack:
                return
            node = Node("li")
            self.stack[-1].items.append(node)
            self.stack.append(node)
        elif tag == "a":
            self.capture = ["a", [], dict(attrs).get("href") or ""]
        elif tag in ("h2", "p"):
            self.capture = [tag, []]

    def handle_endtag(self, tag: str) -> None:
        if tag in ("ol", "ul", "li"):
            if self.stack:
                self.stack.pop()
        elif tag == "a" and self.capture and self.capture[0] == "a":
            text = clean_text("".join(self.capture[1]))
            if self.stack and text:
                self.stack[-1].links.append((text, self.capture[2]))
            self.capture = None
        elif tag in ("h2", "p") and self.capture and self.capture[0] == tag:
            text = clean_text("".join(self.capture[1]))
            if text:
                self.events.append((tag, text))
            self.capture = None

    def handle_data(self, data: str) -> None:
        if self.capture is not None:
            self.capture[1].append(data)
        elif self.stack and self.stack[-1].tag == "li":
            self.stack[-1].own.append(data)


def clean_text(value: str) -> str:
    return " ".join(html.unescape(value).split())


def slug_of(url: str) -> str:
    return url.rstrip("/").split("/")[-1]


# ---------------------------------------------------------------------------
# Page -> units/topics/groups
# ---------------------------------------------------------------------------
def parse_curriculum(markup: str) -> list[dict]:
    parser = PageParser()
    parser.feed(markup)
    units: list[dict] = []
    unit: dict | None = None
    for kind, value in parser.events:
        if kind == "h2":
            match = re.match(r"Unit (\d+):\s*(.+?)\s*\((.+)\)\s*$", str(value))
            if not match:
                continue
            unit = {
                "number": int(match.group(1)),
                "title": match.group(2),
                "period": match.group(3),
                "startYear": None,
                "endYear": None,
                "summary": None,
                "topics": [],
            }
            units.append(unit)
        elif kind == "p" and unit is not None and unit["summary"] is None:
            unit["summary"] = str(value)
        elif kind == "ol" and unit is not None:
            for index, item in enumerate(value.items, 1):
                raw = clean_text("".join(item.own))
                title, detail = raw, None
                match = re.match(r"^(.*?)\s*\((.+)\)\s*$", raw)
                if match:
                    title, detail = match.group(1).strip(), match.group(2).strip()
                unit["topics"].append(
                    {
                        "id": f"{unit['number']}.{index}",
                        "title": title,
                        "detail": detail,
                        "groups": topic_groups(item),
                    }
                )
    return units


def topic_groups(topic: Node) -> list[dict]:
    """Flatten a topic's nested lists into ordered (title, [(text, url)])."""
    groups: list[list] = []

    def add(title: str | None, text: str, url: str) -> None:
        if groups and groups[-1][0] == title:
            groups[-1][1].append((text, url))
        else:
            groups.append([title, [(text, url)]])

    def walk(node: Node, path: list[str]) -> None:
        for item in node.items:
            own = clean_text("".join(item.own))
            nested = [child for child in item.children if child.tag in ("ol", "ul")]
            for text, url in item.links:
                add(" › ".join(path) if path else None, text, url)
            if own and not own.startswith("(") and nested:
                walk(nested[0], path + [own])
            else:
                for child in nested:
                    walk(child, path)

    for child in topic.children:
        walk(child, [])
    for text, url in topic.links:
        add(None, text, url)
    return [{"title": title, "entries": entries} for title, entries in groups]


# ---------------------------------------------------------------------------
# Feed
# ---------------------------------------------------------------------------
def norm(text: str) -> str:
    text = html.unescape(text).lower().replace("’", "'").replace("–", "-").replace("—", "-")
    text = re.sub(r"\((?:encore|remastered)\)", "", text)
    text = re.sub(r"[^a-z0-9]+", " ", text).strip()
    return re.sub(r"^the ", "", text)


def item_text(item: ET.Element, tag: str) -> str:
    node = item.find(tag)
    return (node.text or "").strip() if node is not None else ""


def item_link(item: ET.Element) -> str:
    return item_text(item, "link")


def item_pubdate(item: ET.Element) -> datetime:
    raw = item_text(item, "pubDate")
    try:
        return datetime.strptime(raw, "%a, %d %b %Y %H:%M:%S %z")
    except ValueError:
        return datetime(1970, 1, 1, tzinfo=timezone.utc)


def parse_duration(raw: str) -> int | None:
    raw = raw.strip()
    if not raw:
        return None
    if raw.isdigit():
        return int(raw)
    seconds = 0
    for part in raw.split(":"):
        seconds = seconds * 60 + int(part)
    return seconds


def trim_description(raw: str, limit: int = 400) -> str:
    text = clean_text(re.sub(r"<[^>]+>", " ", raw))
    if len(text) <= limit:
        return text
    cut = text[:limit].rsplit(" ", 1)[0].rstrip(",;:-")
    return cut + "…"


def load_feed(xml_bytes: bytes) -> tuple[list[ET.Element], str | None]:
    root = ET.fromstring(xml_bytes)
    channel = root.find("channel")
    items = channel.findall("item") if channel is not None else []
    image = channel.find(f"{ITUNES}image") if channel is not None else None
    channel_image = image.attrib.get("href") if image is not None else None
    return items, channel_image


def build_feed_index(items):
    by_slug: dict[str, list[ET.Element]] = {}
    by_title: dict[str, list[ET.Element]] = {}
    for item in items:
        link = item_link(item)
        if "everything-everywhere.com/" in link:
            by_slug.setdefault(slug_of(link), []).append(item)
        by_title.setdefault(norm(item_text(item, "title")), []).append(item)
    return by_slug, by_title


def newest(candidates: list[ET.Element]) -> ET.Element | None:
    return max(candidates, key=item_pubdate) if candidates else None


def resolve_episode(text: str, slug: str, by_slug, by_title) -> ET.Element | None:
    if slug in TITLE_MATCH:
        match = newest(by_title.get(norm(TITLE_MATCH[slug]), []))
        if match is not None:
            return match
    match = newest(by_slug.get(slug, []))
    if match is not None:
        return match
    return newest(by_title.get(norm(text), []))


def episode_payload(item: ET.Element, slug: str, channel_image: str | None) -> dict:
    duration = parse_duration(item_text(item, f"{ITUNES}duration"))
    image = item.find(f"{ITUNES}image")
    image_url = image.attrib.get("href") if image is not None else channel_image
    enclosure = item.find("enclosure")
    audio = enclosure.attrib.get("url") if enclosure is not None else None
    year, label = YEARS[slug]
    title = re.sub(r"\s*\((?:encore|remastered)\)\s*$", "", item_text(item, "title"), flags=re.I)
    return {
        "id": slug,
        "title": title,
        "pageUrl": f"{SITE}{slug}/",
        "audioUrl": audio,
        "pubDate": item_pubdate(item).astimezone(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "durationSec": duration,
        "description": trim_description(item_text(item, "description")),
        "imageUrl": image_url,
        "approxYear": year,
        "yearLabel": label,
    }


# ---------------------------------------------------------------------------
# Build
# ---------------------------------------------------------------------------
def fetch(url: str) -> bytes:
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=60) as response:
        return response.read()


def verify_audio(urls: dict[str, str]) -> list[str]:
    def probe(pair: tuple[str, str]) -> tuple[str, str | None]:
        slug, url = pair
        request = urllib.request.Request(
            url, headers={"User-Agent": USER_AGENT, "Range": "bytes=0-0"}
        )
        try:
            with urllib.request.urlopen(request, timeout=45) as response:
                code = response.status
            return slug, None if 200 <= code < 300 else f"HTTP {code}"
        except Exception as exc:  # noqa: BLE001 - report every failure
            return slug, str(exc)

    failures = []
    with ThreadPoolExecutor(max_workers=10) as pool:
        for slug, error in pool.map(probe, urls.items()):
            if error:
                failures.append(f"{slug}: audio URL failed ({error})")
    return failures


def build(page_bytes: bytes, feed_bytes: bytes, verify: bool) -> dict:
    markup = page_bytes.decode("utf-8", "replace")
    units = parse_curriculum(markup)
    items, channel_image = load_feed(feed_bytes)
    by_slug, by_title = build_feed_index(items)

    issues: list[str] = list(STATIC_ISSUES)
    seen: set[str] = set()
    episodes: dict[str, dict] = {}
    audio_urls: dict[str, str] = {}

    for topic_id, _original in FIX_LINKS:
        if not any(t["id"] == topic_id for u in units for t in u["topics"]):
            raise SystemExit(f"FIX_LINKS references unknown topic {topic_id}")

    for unit in units:
        start_year, end_year = UNIT_YEARS[unit["period"]]
        unit["startYear"] = start_year
        unit["endYear"] = end_year if end_year is not None else datetime.now(timezone.utc).year
        for topic in unit["topics"]:
            flat = []
            for group in topic["groups"]:
                entries = []
                for text, url in group["entries"]:
                    original = slug_of(url)
                    slug = FIX_LINKS.get((topic["id"], original), original)
                    if slug != original:
                        issues.append(
                            f"{topic['id']}: '{text}' linked to {original}, corrected to {slug}"
                        )
                    entries.append((text, slug))
                flat.append({"title": group["title"], "entries": entries})
            topic["groups"] = []

            for group in flat:
                ids = []
                for text, slug in group["entries"]:
                    if slug in seen:
                        issues.append(
                            f"{topic['id']}: duplicate episode {slug} removed (kept at first occurrence)"
                        )
                        continue
                    item = resolve_episode(text, slug, by_slug, by_title)
                    if item is None:
                        issues.append(f"{topic['id']}: no feed item found for '{text}' ({slug}); dropped")
                        continue
                    seen.add(slug)
                    ids.append(slug)
                    if slug not in episodes:
                        if slug not in YEARS:
                            raise SystemExit(f"missing YEARS entry for {slug}")
                        episodes[slug] = episode_payload(item, slug, channel_image)
                        if episodes[slug]["audioUrl"]:
                            audio_urls[slug] = episodes[slug]["audioUrl"]
                if ids:
                    topic["groups"].append({"title": group["title"], "entries": ids})

            for group in topic["groups"]:
                group["episodeIds"] = group.pop("entries")
            # Drop empty groups entirely (topics with no episodes keep []).
            topic["groups"] = [
                {"title": g["title"], "episodeIds": g["episodeIds"]}
                for g in topic["groups"]
                if g["episodeIds"]
            ]

    for topic_id, slug, note in RETITLED:
        if slug in episodes:
            issues.append(f"{topic_id}: {note} ('{episodes[slug]['title']}')")

    missing_years = [slug for slug in episodes if slug not in YEARS]
    if missing_years:
        raise SystemExit(f"missing YEARS entries: {missing_years}")
    missing_audio = [slug for slug, ep in episodes.items() if not ep["audioUrl"]]
    if missing_audio:
        raise SystemExit(f"missing audio for: {missing_audio}")

    if verify:
        failures = verify_audio(audio_urls)
        issues.extend(failures)
        print(f"verified {len(audio_urls)} audio URLs, {len(failures)} failed", file=sys.stderr)

    missing = [slug for unit in units for topic in unit["topics"] for g in topic["groups"] for slug in g["episodeIds"] if slug not in episodes]
    if missing:
        raise SystemExit(f"curriculum references unknown episodes: {missing}")

    return {
        "source": PAGE_URL,
        "feedUrl": FEED_URL,
        "generatedAt": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "units": units,
        "episodes": episodes,
        "issues": issues,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT)
    parser.add_argument("--page", type=Path, help="use a local copy of the curriculum page")
    parser.add_argument("--feed", type=Path, help="use a local copy of the RSS feed")
    parser.add_argument("--verify", action="store_true", help="probe every audio URL")
    args = parser.parse_args()

    page_bytes = args.page.read_bytes() if args.page else fetch(PAGE_URL)
    feed_bytes = args.feed.read_bytes() if args.feed else fetch(FEED_URL)
    data = build(page_bytes, feed_bytes, args.verify)

    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    topics = sum(len(unit["topics"]) for unit in data["units"])
    with_audio = sum(1 for ep in data["episodes"].values() if ep["audioUrl"])
    print(
        f"wrote {args.out}: {len(data['units'])} units, {topics} topics, "
        f"{len(data['episodes'])} episodes ({with_audio} with audio), "
        f"{len(data['issues'])} issues"
    )


if __name__ == "__main__":
    main()


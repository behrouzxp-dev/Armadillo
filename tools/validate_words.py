#!/usr/bin/env python3
"""Validate the archived 723-word general bank and all 1,500 playable topic words."""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GENERAL = ROOT / "app/src/main/assets/words.json"
TOPICS = ROOT / "app/src/main/assets/categories.json"
CATEGORY_NAMES = ("jobs", "animals", "foods", "places", "technology")
LEVELS = ("1", "2", "3")
EXPECTED_GENERAL = 723
EXPECTED_EACH = 100
EXPECTED_TOPICS = 1500


def normalize(value: str) -> str:
    canonical = value.translate(str.maketrans({"ك": "ک", "ي": "ی", "ى": "ی"}))
    return re.sub(r"\s+", " ", canonical.strip().replace("\u200c", " ").lower())


def read_words(items: object, label: str) -> list[str]:
    global failed
    if not isinstance(items, list):
        print(f"FAIL {label}: missing or not a list")
        failed = True
        return []
    for word in items:
        if not isinstance(word, str) or not word.strip():
            print(f"FAIL {label}: blank/non-text entry {word!r}")
            failed = True
        elif re.search(r"[A-Za-z]", word):
            print(f"FAIL {label}: non-Persian Latin spelling {word!r}")
            failed = True
    return [word for word in items if isinstance(word, str) and word.strip()]


failed = False
legacy_data = json.loads(GENERAL.read_text(encoding="utf-8"))
legacy_seen: dict[str, tuple[str, str]] = {}
for level in map(str, range(1, 6)):
    words = read_words(legacy_data.get(level), f"general level {level}")
    local: set[str] = set()
    for word in words:
        key = normalize(word)
        if key in local:
            print(f"FAIL duplicate in general level {level}: {word}")
            failed = True
        local.add(key)
        if key in legacy_seen:
            print(f"FAIL duplicate across general levels: {word!r} and {legacy_seen[key][1]!r}")
            failed = True
        else:
            legacy_seen[key] = (level, word)
    print(f"Preserved general level {level}: {len(words)} words")
if len(legacy_seen) != EXPECTED_GENERAL:
    print(f"FAIL preserved general bank: expected {EXPECTED_GENERAL} unique words, found {len(legacy_seen)}")
    failed = True

new_data = json.loads(TOPICS.read_text(encoding="utf-8"))
all_topics: dict[str, tuple[str, str]] = {}
for category in CATEGORY_NAMES:
    levels = new_data.get(category)
    if not isinstance(levels, dict):
        print(f"FAIL missing topic category {category}")
        failed = True
        continue
    category_seen: dict[str, tuple[str, str]] = {}
    for level in LEVELS:
        words = read_words(levels.get(level), f"{category} level {level}")
        if len(words) != EXPECTED_EACH:
            print(f"FAIL {category} level {level}: expected {EXPECTED_EACH}, found {len(words)}")
            failed = True
        local: set[str] = set()
        for word in words:
            key = normalize(word)
            if key in local:
                print(f"FAIL duplicate within {category} level {level}: {word}")
                failed = True
            local.add(key)
            if key in category_seen:
                prev = category_seen[key]
                print(f"FAIL duplicate in {category} across levels: {word!r} and {prev[1]!r} (level {prev[0]})")
                failed = True
            else:
                category_seen[key] = (level, word)
            if key in all_topics:
                prev = all_topics[key]
                print(f"FAIL duplicate across topic categories: {word!r} and {prev[1]!r} ({prev[0]})")
                failed = True
            else:
                all_topics[key] = (f"{category}.{level}", word)
        print(f"{category} level {level}: {len(words)} words")
    print(f"{category}: {len(category_seen)} unique words across three levels")

if len(all_topics) != EXPECTED_TOPICS:
    print(f"FAIL new topic bank: expected {EXPECTED_TOPICS} distinct words, found {len(all_topics)}")
    failed = True

print(f"Preserved general total: {len(legacy_seen)} unique words")
print(f"New topic total: {len(all_topics)} unique words")
sys.exit(1 if failed else 0)

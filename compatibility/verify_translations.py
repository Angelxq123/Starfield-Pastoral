"""Check shipped language coverage and Minecraft translation arguments, without game assets."""

import argparse
import json
from pathlib import Path
import re


SUPPORTED_LOCALES = (
    "en_us", "zh_cn", "de_de", "es_es", "fr_fr", "hu_hu",
    "it_it", "ja_jp", "ko_kr", "pt_br", "ru_ru", "tr_tr",
)
LANG_DIRECTORY = Path(__file__).resolve().parents[1] / "src/main/resources/assets/stardewcraft/lang"
ARGUMENT = re.compile(r"%%|%(?:(\d+)\$)?([sd])")


def unique_keys(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"duplicate key: {key}")
        result[key] = value
    return result


def arguments(text):
    """Numbered arguments may be reordered or repeated by a translation."""
    implicit = 0
    result = set()
    for match in ARGUMENT.finditer(text):
        if match.group(0) == "%%":
            continue
        if match.group(1):
            index = int(match.group(1))
        else:
            implicit += 1
            index = implicit
        result.add((index, match.group(2)))
    return result


def verify(directory):
    errors = []
    files = {path.stem: path for path in directory.glob("*.json")}
    for locale in sorted(set(SUPPORTED_LOCALES) - files.keys()):
        errors.append(f"{locale}: missing language file")
    languages = {}
    for locale, path in sorted(files.items()):
        try:
            data = json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=unique_keys)
            if not isinstance(data, dict):
                raise ValueError("language root must be an object")
            if any(not isinstance(value, str) for value in data.values()):
                raise ValueError("all translation values must be strings")
            languages[locale] = data
        except (OSError, ValueError) as error:
            errors.append(f"{locale}: {error}")
    if "en_us" not in languages:
        return errors
    reference = languages["en_us"]
    for locale, data in languages.items():
        for key in sorted(reference.keys() - data.keys()):
            errors.append(f"{locale}: missing key: {key}")
        for key in sorted(data.keys() - reference.keys()):
            errors.append(f"{locale}: key absent from en_us: {key}")
        for key in reference.keys() & data.keys():
            # Some event question titles intentionally have no text in any language.
            if reference[key].strip() and not data[key].strip():
                errors.append(f"{locale}: empty translation: {key}")
            if arguments(reference[key]) != arguments(data[key]):
                errors.append(f"{locale}: translation arguments differ: {key}")
    return errors


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", nargs="?", type=Path, default=LANG_DIRECTORY)
    args = parser.parse_args()
    errors = verify(args.directory)
    if errors:
        print("\n".join(errors))
        return 1
    print("All shipped languages have matching keys, nonempty translations and compatible arguments.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

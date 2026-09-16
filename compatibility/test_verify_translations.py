import json
from pathlib import Path
import tempfile
import unittest

from verify_translations import SUPPORTED_LOCALES, arguments, verify


class TranslationVerificationTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.directory = Path(self.temp.name)
        for locale in SUPPORTED_LOCALES:
            self.write(locale, {"name": "Name", "message": "%s: %s", "blank_title": ""})

    def write(self, locale, data):
        (self.directory / f"{locale}.json").write_text(json.dumps(data), encoding="utf-8")

    def test_numbered_reordered_and_repeated_arguments_are_valid(self):
        self.write("ja_jp", {"name": "名前", "message": "%2$s / %1$s / %1$s", "blank_title": ""})
        self.assertEqual([], verify(self.directory))
        self.assertEqual(set(), arguments("100%%s"))

    def test_missing_language_file_is_rejected(self):
        (self.directory / "de_de.json").unlink()
        self.assertIn("de_de: missing language file", verify(self.directory))

    def test_missing_empty_extra_and_malformed_arguments_are_rejected(self):
        self.write("fr_fr", {"name": "", "message": "%s", "extra": "Extra"})
        errors = verify(self.directory)
        for expected in ("missing key: blank_title", "empty translation: name",
                         "key absent from en_us: extra", "translation arguments differ: message"):
            self.assertIn(f"fr_fr: {expected}", errors)

    def test_duplicate_keys_and_non_text_values_are_rejected(self):
        (self.directory / "ru_ru.json").write_text('{"name":"A","name":"B"}', encoding="utf-8")
        self.write("ko_kr", {"name": 5})
        errors = verify(self.directory)
        self.assertIn("ru_ru: duplicate key: name", errors)
        self.assertIn("ko_kr: all translation values must be strings", errors)

    def test_invalid_json_is_reported(self):
        (self.directory / "hu_hu.json").write_text("{", encoding="utf-8")
        self.assertTrue(any(error.startswith("hu_hu:") for error in verify(self.directory)))


if __name__ == "__main__":
    unittest.main()

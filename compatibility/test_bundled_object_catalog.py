"""The shared object catalog must resolve on Linux and inside a case-sensitive JAR."""
import json
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


class BundledObjectCatalogTests(unittest.TestCase):
    def test_catalog_readers_reference_the_exact_shipped_filename(self):
        readers = (
            'data/VanillaObjectCatalog.java',
            'fishpond/service/FishPondQualifiedItemService.java',
            'manager/ArtifactDropService.java',
            'shop/ShopRegistry.java',
            'blockentity/DailyStatueBlockEntity.java',
            'npc/runtime/NpcInteractionService.java',
            'npc/runtime/VanillaGiftTasteResolver.java',
        )
        for reader in readers:
            with self.subTest(reader=reader):
                source = (ROOT / 'src/main/java/com/stardew/craft' / reader).read_text()
                match = re.search(r'"/?(data/stardewcraft/npc/vanilla/data/objects\.json)"', source, re.IGNORECASE)
                self.assertIsNotNone(match, 'Object catalog resource declaration missing')
                path = ROOT / 'src/main/resources'
                for part in match.group(1).split('/'):
                    self.assertIn(part, [entry.name for entry in path.iterdir()],
                                  f'Classpath resource case mismatch: {path / part}')
                    path = path / part
                data = json.loads(path.read_text())
                self.assertTrue('128' in data, 'Fish catalog is empty or incomplete')
                self.assertTrue('720' in data, 'Pet gift catalog is incomplete')


if __name__ == '__main__':
    unittest.main()

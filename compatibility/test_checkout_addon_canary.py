import tempfile
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

from checkout_addon_canary import supplement_dependency_checksums


class DependencyChecksumTests(unittest.TestCase):
    def test_supplement_preserves_verification_and_existing_entries(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            path = root / 'gradle/verification-metadata.xml'
            path.parent.mkdir()
            path.write_text('''<verification-metadata xmlns="https://schema.gradle.org/dependency-verification">
<configuration><verify-metadata>true</verify-metadata></configuration>
<components><component group="example" name="library" version="1"><artifact name="existing.jar"><sha256 value="existing"/></artifact></component></components>
</verification-metadata>''')
            entry = dict(group='example', name='library', version='1',
                         artifact='native.jar', sha256='a' * 64,
                         source='https://example.org/native.jar.sha256')
            supplement_dependency_checksums(root, [entry])
            first = path.read_bytes()
            supplement_dependency_checksums(root, [entry])
            self.assertEqual(first, path.read_bytes())
            ns = {'g': 'https://schema.gradle.org/dependency-verification'}
            tree = ET.parse(path)
            self.assertEqual('true', tree.findtext('.//g:verify-metadata', namespaces=ns))
            artifacts = tree.findall('.//g:artifact', ns)
            self.assertEqual(['existing.jar', 'native.jar'], [a.get('name') for a in artifacts])
            self.assertEqual('existing', artifacts[0].find('g:sha256', ns).get('value'))
            self.assertIsNone(tree.find('.//g:trusted-artifacts', ns))
            with self.assertRaisesRegex(SystemExit, 'conflicting checksum'):
                supplement_dependency_checksums(root, [{**entry, 'sha256': 'b' * 64}])
            self.assertEqual(first, path.read_bytes())


if __name__ == '__main__':
    unittest.main()

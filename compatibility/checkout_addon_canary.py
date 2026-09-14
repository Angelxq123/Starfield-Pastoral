#!/usr/bin/env python3
"""Checkout one pinned addon canary from the repository manifest."""

from __future__ import annotations

import json
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


def fail(message: str) -> None:
    raise SystemExit(f"Addon canary checkout failed: {message}")


def supplement_dependency_checksums(destination: Path, entries: list[dict]) -> None:
    """Add pinned platform artifacts without disabling or widening verification."""
    if not entries:
        return
    path = destination / "gradle/verification-metadata.xml"
    namespace = "https://schema.gradle.org/dependency-verification"
    ET.register_namespace("", namespace)
    ET.register_namespace("xsi", "http://www.w3.org/2001/XMLSchema-instance")
    tag = lambda name: f"{{{namespace}}}{name}"
    tree = ET.parse(path)
    components = tree.getroot().find(tag("components"))
    if components is None:
        fail("dependency metadata has no components")
    for entry in entries:
        attrs = {key: entry[key] for key in ("group", "name", "version")}
        component = next((node for node in components if node.attrib == attrs), None)
        if component is None:
            component = ET.SubElement(components, tag("component"), attrs)
        artifact = next((node for node in component
                         if node.get("name") == entry["artifact"]), None)
        if artifact is not None:
            hashes = [node.get("value") for node in artifact.findall(tag("sha256"))]
            if entry["sha256"] not in hashes:
                fail(f"conflicting checksum for {entry['artifact']}")
            continue
        artifact = ET.SubElement(component, tag("artifact"), {"name": entry["artifact"]})
        ET.SubElement(artifact, tag("sha256"), {
            "value": entry["sha256"], "origin": entry["source"],
        })
    ET.indent(tree, space="   ")
    tree.write(path, encoding="utf-8", xml_declaration=True)


def main() -> None:
    if len(sys.argv) != 3:
        fail("usage: checkout_addon_canary.py <addon-id> <destination>")

    project_root = Path(__file__).resolve().parents[1]
    addon_id = sys.argv[1]
    destination = Path(sys.argv[2]).resolve()
    manifest = json.loads(
        (project_root / "compatibility/addon-canaries.json").read_text(
            encoding="utf-8"
        )
    )
    addon = next(
        (entry for entry in manifest["addons"] if entry["id"] == addon_id),
        None,
    )
    if addon is None:
        fail(f"{addon_id} is missing from addon-canaries.json")
    if destination.exists():
        fail(f"destination already exists: {destination}")

    destination.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run(
        [
            "git",
            "clone",
            "--filter=blob:none",
            "--no-checkout",
            addon["repository"],
            str(destination),
        ],
        check=True,
    )
    subprocess.run(
        ["git", "-C", str(destination), "checkout", addon["commit"]],
        check=True,
    )

    supplement_dependency_checksums(
        destination, addon.get("additional_dependency_checksums", [])
    )


if __name__ == "__main__":
    main()

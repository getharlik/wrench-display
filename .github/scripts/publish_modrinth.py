#!/usr/bin/env python3
"""Uploads a release to Modrinth: one version per Fabric jar, one for the Paper plugin.

Every version also carries the resource pack zip. Run after `./gradlew build`:

    MODRINTH_TOKEN=... python3 .github/scripts/publish_modrinth.py v1.1
    python3 .github/scripts/publish_modrinth.py v1.1 --dry-run   # print what would be uploaded

The Minecraft versions of each Fabric jar come from fabric/stonecutter.properties.toml (mod.mc_releases),
the changelog from the matching "## <version>" section of CHANGELOG.md.
"""
import json
import os
import re
import sys
import tomllib
import urllib.request
import uuid
from pathlib import Path

API = "https://api.modrinth.com/v2"
PROJECT = "wrench-display"
FABRIC_API = "P7dR8mSH"  # Modrinth project id of Fabric API
USER_AGENT = "getharlik/wrench-display release script"
ROOT = Path(__file__).resolve().parents[2]


def gradle_property(name):
    for line in (ROOT / "gradle.properties").read_text().splitlines():
        key, _, value = line.partition("=")
        if key.strip() == name:
            return value.strip()
    raise SystemExit(f"{name} missing from gradle.properties")


def changelog(version):
    text = (ROOT / "CHANGELOG.md").read_text()
    match = re.search(rf"^## {re.escape(version)}\b.*?$(.*?)(?=^## |\Z)", text, re.M | re.S)
    if not match:
        raise SystemExit(f"CHANGELOG.md has no '## {version}' section")
    return match.group(1).strip()


def request(method, path, token, body=None, content_type=None):
    req = urllib.request.Request(API + path, data=body, method=method)
    req.add_header("User-Agent", USER_AGENT)
    if token:
        req.add_header("Authorization", token)
    if content_type:
        req.add_header("Content-Type", content_type)
    try:
        with urllib.request.urlopen(req) as response:
            return json.load(response)
    except urllib.error.HTTPError as e:
        raise SystemExit(f"{method} {path} failed: {e.code} {e.read().decode(errors='replace')}")


def multipart(data, files):
    boundary = uuid.uuid4().hex
    parts = [f'--{boundary}\r\nContent-Disposition: form-data; name="data"\r\n'
             f"Content-Type: application/json\r\n\r\n{json.dumps(data)}\r\n".encode()]
    for name, path in files:
        parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"; filename="{path.name}"\r\n'
                     f"Content-Type: application/octet-stream\r\n\r\n".encode() + path.read_bytes() + b"\r\n")
    parts.append(f"--{boundary}--\r\n".encode())
    return b"".join(parts), f"multipart/form-data; boundary={boundary}"


def single(pattern):
    found = sorted(ROOT.glob(pattern))
    if len(found) != 1:
        raise SystemExit(f"expected one file for {pattern}, found {[str(f) for f in found]} (run ./gradlew build first)")
    return found[0]


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    dry_run = "--dry-run" in sys.argv
    if len(args) != 1:
        raise SystemExit(__doc__)
    version = gradle_property("version")
    if args[0].removeprefix("v") != version:
        raise SystemExit(f"tag {args[0]} doesn't match version {version} in gradle.properties")
    token = os.environ.get("MODRINTH_TOKEN")
    if not dry_run and not token:
        raise SystemExit("MODRINTH_TOKEN is not set")

    notes = changelog(version)
    pack = single(f"resourcepack/build/distributions/wrench-display-pack-{version}.zip")
    # With the token so it also works while the project is still a draft (a dry run without one skips this).
    project_id = request("GET", f"/project/{PROJECT}", token)["id"] if token else PROJECT

    # Version tables are the ones with mod.mc_releases; the shared settings (deps., loomx.) parse as tables too.
    toml = tomllib.loads((ROOT / "fabric/stonecutter.properties.toml").read_text())
    releases = {mc: table for mc, table in toml.items() if isinstance(table, dict) and "mc_releases" in table.get("mod", {})}
    uploads = []
    for mc, table in releases.items():
        uploads.append({
            "name": f"{version} for Fabric {mc}",
            "version_number": f"{version}+{mc}-fabric",
            "game_versions": table["mod"]["mc_releases"],
            "loaders": ["fabric"],
            "dependencies": [{"project_id": FABRIC_API, "dependency_type": "required"}],
            "jar": single(f"fabric/versions/{mc}/build/libs/wrench-display-fabric-{version}+{mc}.jar"),
        })
    every_release = [r for table in releases.values() for r in table["mod"]["mc_releases"]]
    uploads.append({
        "name": f"{version} for Paper",
        "version_number": f"{version}-paper",
        "game_versions": every_release,
        "loaders": ["paper", "purpur", "folia"],
        "dependencies": [],
        "jar": single(f"paper/build/libs/wrench-display-paper-{version}.jar"),
    })

    for upload in uploads:
        jar = upload.pop("jar")
        data = {
            **upload,
            "project_id": project_id,
            "changelog": notes,
            "version_type": "release",
            "featured": True,
            "status": "listed",
            "file_parts": ["jar", "pack"],
            "primary_file": "jar",
        }
        print(f"{data['version_number']}: {jar.name} + {pack.name} for {', '.join(data['game_versions'])}")
        if dry_run:
            continue
        body, content_type = multipart(data, [("jar", jar), ("pack", pack)])
        created = request("POST", "/version", token, body, content_type)
        print(f"  -> https://modrinth.com/project/{PROJECT}/version/{created['id']}")


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Uploads a release to Modrinth: one version per Fabric jar and one for the Paper plugin in the mod project,
and the resource pack zip as a version of its own resource pack project (which the mod versions link to).
Versions that already exist are skipped, so a failed release can be run again. Run after `./gradlew build`:

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
PACK_PROJECT = "wrench-display-pack"
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


def lookup_project(slug, variable, token):
    """The project id, from the variable or else the slug. Drafts are only visible to tokens with "Read projects"."""
    if os.environ.get(variable):
        return os.environ[variable]
    if not token:
        return slug  # dry run without a token
    try:
        return request("GET", f"/project/{slug}", token)["id"]
    except SystemExit as e:
        if "404" in str(e):
            raise SystemExit(f"Modrinth can't see the project '{slug}' with this token. Check the slug; while the project "
                             "is a draft, the token needs the 'Read projects' scope (and 'Create versions' to upload). "
                             f"Alternatively set the {variable} variable to the project's id.")
        raise


def existing_versions(project_id, token):
    if not token:
        return {}
    try:
        versions = request("GET", f"/project/{project_id}/version", token)
    except SystemExit as e:
        if "404" in str(e):
            raise SystemExit(f"Modrinth won't list the versions of project {project_id} with this token. While the project "
                             "is a draft, the token needs the 'Read versions' scope (with 'Read projects' and 'Create versions').")
        raise
    return {v["version_number"]: v["id"] for v in versions}


def upload(project_id, data, file, token, dry_run, existing):
    """Creates one version with a single file; returns its id (None in a dry run)."""
    number = data["version_number"]
    if number in existing:
        print(f"{number}: already on Modrinth, skipped")
        return existing[number]
    print(f"{number}: {file.name} for {', '.join(data['game_versions'])}")
    if dry_run:
        return None
    data = {**data, "project_id": project_id, "file_parts": ["file"], "primary_file": "file",
            "version_type": "release", "featured": True, "status": "listed"}
    body, content_type = multipart(data, [("file", file)])
    created = request("POST", "/version", token, body, content_type)
    print(f"  -> https://modrinth.com/version/{created['id']}")
    return created["id"]


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
    project_id = lookup_project(PROJECT, "MODRINTH_PROJECT_ID", token)
    pack_project_id = lookup_project(PACK_PROJECT, "MODRINTH_PACK_PROJECT_ID", token)

    # Version tables are the ones with mod.mc_releases; the shared settings (deps., loomx.) parse as tables too.
    toml = tomllib.loads((ROOT / "fabric/stonecutter.properties.toml").read_text())
    releases = {mc: table for mc, table in toml.items() if isinstance(table, dict) and "mc_releases" in table.get("mod", {})}
    every_release = [r for table in releases.values() for r in table["mod"]["mc_releases"]]

    pack_version = upload(pack_project_id, {
        "name": version,
        "version_number": version,
        "game_versions": every_release,
        "loaders": ["minecraft"],
        "dependencies": [],
        "changelog": f"Resource pack for Wrench Display {version}.",
    }, single(f"resourcepack/build/distributions/wrench-display-pack-{version}.zip"), token, dry_run,
        existing_versions(pack_project_id, token))
    # Optional: vanilla players need the pack, but it is sent by the server, and players with the mod don't need it.
    pack = {"project_id": pack_project_id, "dependency_type": "optional"}
    if pack_version:
        pack["version_id"] = pack_version

    existing = existing_versions(project_id, token)
    for mc, table in releases.items():
        upload(project_id, {
            "name": f"{version} for Fabric {mc}",
            "version_number": f"{version}+{mc}-fabric",
            "game_versions": table["mod"]["mc_releases"],
            "loaders": ["fabric"],
            "dependencies": [{"project_id": FABRIC_API, "dependency_type": "required"}, pack],
            "changelog": notes,
        }, single(f"fabric/versions/{mc}/build/libs/wrench-display-fabric-{version}+{mc}.jar"), token, dry_run, existing)
    upload(project_id, {
        "name": f"{version} for Paper",
        "version_number": f"{version}-paper",
        "game_versions": every_release,
        "loaders": ["paper", "purpur", "folia"],
        "dependencies": [pack],
        "changelog": notes,
    }, single(f"paper/build/libs/wrench-display-paper-{version}.jar"), token, dry_run, existing)


if __name__ == "__main__":
    main()

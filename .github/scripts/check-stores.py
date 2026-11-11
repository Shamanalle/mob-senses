#!/usr/bin/env python3
"""Checks that every file of a release is in the stores with all its Minecraft versions and loaders.

Usage: check-stores.py <tag> [--warn-only]

The expected files come from the publish matrix in .github/workflows/publish.yml, so the two cannot
drift apart. Modrinth is read from its public API; CurseForge from api.cfwidget.com, which mirrors
the public file list (the CurseForge API itself needs a key we do not have, and the upload token
cannot list files); Hangar from hangar.papermc.io/api/v1. cfwidget refreshes a project every few
minutes, so a check right after an upload can miss the newest files.

A Markdown table goes to $GITHUB_STEP_SUMMARY; every gap is reported as an error (a warning with
--warn-only) and makes the script exit 1.
"""

import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

import yaml

CF_LOADERS = {"fabric": "Fabric", "quilt": "Quilt", "forge": "Forge", "neoforge": "NeoForge"}
AGENT = "Shamanalle/mob-senses store check"


def get_json(url, token=None, attempts=6):
    """GETs a JSON document; cfwidget answers 202 while it fetches a project for the first time."""
    headers = {"User-Agent": AGENT}
    if token:
        headers["Authorization"] = token
    for attempt in range(attempts):
        request = urllib.request.Request(url, headers=headers)
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                if response.status == 200:
                    return json.load(response)
        except urllib.error.HTTPError as error:
            if error.code not in (202, 429, 500, 502, 503, 504):
                raise RuntimeError(f"{url}: HTTP {error.code}") from None
        time.sleep(10 * (attempt + 1))
    raise RuntimeError(f"No answer from {url}")


def is_public_project(url):
    """Checks whether a store project URL is publicly visible (HTTP 200 without credentials)."""
    request = urllib.request.Request(url, headers={"User-Agent": AGENT})
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            return response.status == 200
    except urllib.error.HTTPError as error:
        if error.code in (401, 403, 404):
            return False
        raise RuntimeError(f"{url}: HTTP {error.code}") from None


def hangar_jwt(api_key):
    """Exchanges a Hangar API key for a short-lived JWT, or returns None when no key is set."""
    api_key = (api_key or "").strip()
    if not api_key:
        return None
    query = urllib.parse.urlencode({"apiKey": api_key})
    request = urllib.request.Request(
        f"https://hangar.papermc.io/api/v1/authenticate?{query}",
        method="POST",
        headers={"User-Agent": AGENT},
    )
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            token = json.load(response).get("token")
            return f"HangarAuth {token}" if token else None
    except urllib.error.HTTPError as error:
        print(f"::warning::Hangar authentication answered HTTP {error.code} (check HANGAR_TOKEN format: <uuid>.<secret>)")
        return None


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    warn_only = "--warn-only" in sys.argv
    if len(args) != 1:
        sys.exit(__doc__)
    tag = args[0]
    version = tag.removeprefix("v")

    with open(".github/publish.json") as f:
        ids = json.load(f)
    with open(".github/workflows/publish.yml") as f:
        rows = yaml.safe_load(f)["jobs"]["publish"]["strategy"]["matrix"]["include"]

    if not any(ids.get(key) for key in ("modrinth", "curseforge", "curseforge_plugin", "hangar")):
        print("::warning::No store projects in .github/publish.json, nothing to check")
        return

    notes = []
    modrinth = None
    if ids.get("modrinth"):
        if not is_public_project(f"https://api.modrinth.com/v2/project/{ids['modrinth']}"):
            print("::notice::Modrinth project is not public yet (awaiting moderation review)")
            notes.append("_Note: the Modrinth project is still awaiting moderation review (not public yet)._")
        modrinth_token = os.environ.get("MODRINTH_TOKEN")
        if modrinth_token or not notes:
            versions = get_json(f"https://api.modrinth.com/v2/project/{ids['modrinth']}/version", modrinth_token)
            modrinth = {v["version_number"]: v for v in versions}

    curseforge = {}
    for key in ("curseforge", "curseforge_plugin"):
        if ids.get(key):
            files = get_json(f"https://api.cfwidget.com/{ids[key]}").get("files", [])
            curseforge[key] = {f.get("name", ""): f for f in files} | {f.get("display", ""): f for f in files}

    hangar = None
    if ids.get("hangar"):
        hangar_public = is_public_project(f"https://hangar.papermc.io/api/v1/projects/{ids['hangar']}")
        if not hangar_public:
            print("::notice::Hangar project is not public yet (awaiting first version or moderation review)")
            notes.append("_Note: the Hangar project is not public yet (awaiting first version or moderation review)._")
        jwt = hangar_jwt(os.environ.get("HANGAR_TOKEN"))
        if jwt or hangar_public:
            data = get_json(f"https://hangar.papermc.io/api/v1/projects/{ids['hangar']}/versions?limit=25", jwt)
            hangar = {v["name"]: v for v in data.get("result", [])}

    lines = ["| File | Store | Result |", "| --- | --- | --- |"]
    gaps = []

    def report(row, store, missing):
        if missing:
            gaps.append(f"{store}: {row['name']}: {missing}")
        lines.append(f"| {row['name']} | {store} | {missing or 'ok'} |")

    for row in rows:
        game_versions = row["game-versions"].split()
        loaders = row["loaders"].split()

        # Modrinth: the version number is {version}+{target}
        if modrinth is not None:
            found = modrinth.get(f"{version}+{row['target']}")
            if found is None:
                report(row, "Modrinth", "not uploaded")
            else:
                problems = []
                missing = [v for v in game_versions if v not in found["game_versions"]]
                if missing:
                    problems.append("versions " + " ".join(missing))
                missing = [l for l in loaders if l not in found["loaders"]]
                if missing:
                    problems.append("loaders " + " ".join(missing))
                listed = ", ".join(found["game_versions"] + found["loaders"])
                report(row, "Modrinth", "; ".join(problems) + (f" (listed: {listed})" if problems else ""))
        elif ids.get("modrinth"):
            lines.append(f"| {row['name']} | Modrinth | awaiting review (set MODRINTH_TOKEN to inspect) |")

        # CurseForge: the mod rows go to the mod project, the plugin to its own project
        key = "curseforge_plugin" if row["target"] == "bukkit" else "curseforge"
        if key in curseforge:
            # The stores get the Fabric jar without "fabric" in its name, as publish.yml uploads it.
            file_name = "mob-senses-" + row["file"].replace("{v}", version)
            file_name = file_name.replace("-fabric-", "-")
            found = curseforge[key].get(file_name) or curseforge[key].get(f"{version} · {row['name']}")
            if found is None:
                report(row, "CurseForge", "not uploaded")
            else:
                listed = found.get("versions", [])
                problems = []
                # The plugin section lacks most patch versions: curseforge-bukkit.sh falls back to major.minor.
                # Its 1.16 entry is broken ("game version id: 0 does not exist"), so 1.16.5 cannot be listed.
                missing = [v for v in game_versions if v not in listed
                           and not (key == "curseforge_plugin" and (".".join(v.split(".")[:2]) in listed or v.startswith("1.16")))]
                if missing:
                    problems.append("versions " + " ".join(missing))
                if key == "curseforge":
                    missing = [CF_LOADERS[l] for l in loaders if CF_LOADERS[l] not in listed]
                    if missing:
                        problems.append("loaders " + " ".join(missing))
                report(row, "CurseForge", "; ".join(problems) + (f" (listed: {', '.join(listed)})" if problems else ""))

        # Hangar: Paper/Purpur/Folia/Spigot plugin
        if row["target"] == "bukkit" and hangar is not None:
            found = hangar.get(version)
            if found is None:
                report(row, "Hangar", "not uploaded")
            else:
                listed = found.get("platformDependencies", {}).get("PAPER", [])
                missing = [v for v in game_versions if v not in listed
                           and ".".join(v.split(".")[:2]) not in listed]
                report(row, "Hangar",
                       (f"versions {' '.join(missing)} (listed: {', '.join(listed)})" if missing else ""))
        elif row["target"] == "bukkit" and ids.get("hangar"):
            lines.append(f"| {row['name']} | Hangar | awaiting review (set HANGAR_TOKEN to inspect) |")

    header = [f"### Store check for {tag}", ""] + ([n + "\n" for n in notes] if notes else [])
    table = "\n".join(header + lines) + "\n"
    print(table)
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a") as f:
            f.write(table)
    level = "warning" if warn_only else "error"
    for gap in gaps:
        print(f"::{level}::{gap}")
    sys.exit(1 if gaps and not warn_only else 0)


if __name__ == "__main__":
    main()

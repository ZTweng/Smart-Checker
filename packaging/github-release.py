"""Publish using the existing Git Credential Manager login, never embedded tokens."""
import base64
import hashlib
import json
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
API = "https://api.github.com/repos/ZTweng/Smart-Checker"


def git(*args):
    return subprocess.check_output(["git", *args], cwd=ROOT)


credential = subprocess.run(
    ["git", "credential", "fill"],
    input="protocol=https\nhost=github.com\n\n",
    text=True, capture_output=True, check=True,
)
fields = dict(line.split("=", 1) for line in credential.stdout.splitlines() if "=" in line)
TOKEN = fields["password"]


def request(path, data=None, method=None, raw=False):
    url = path if path.startswith("https://") else API + path
    headers = {"Authorization": "Bearer " + TOKEN, "User-Agent": "Smart-Checker-release",
               "Accept": "application/vnd.github+json"}
    payload = data if raw else (json.dumps(data).encode() if data is not None else None)
    headers["Content-Type"] = "application/octet-stream" if raw else "application/json"
    req = urllib.request.Request(url, data=payload, headers=headers, method=method)
    with urllib.request.urlopen(req, timeout=180) as response:
        content = response.read()
        return json.loads(content) if content else None


def release():
    matches = [r for r in request("/releases?per_page=100") if r["tag_name"] == "v6.26.4"]
    if matches:
        return max(matches, key=lambda r: len(r["assets"]))
    notes = (ROOT / "packaging/release-notes.md").read_text(encoding="utf-8")
    return request("/releases", {"tag_name": "v6.26.4", "target_commitish": "main",
                               "name": "Smart Checker 6.26.4", "body": notes, "draft": True})


def upload(rel, file):
    existing = request(f"/releases/{rel['id']}/assets")
    name = file.name.replace(" ", ".")
    if any(asset["name"] == name and asset["size"] == file.stat().st_size for asset in existing):
        print("Already uploaded:", file.name, flush=True)
        return
    if any(asset["name"] == name for asset in existing):
        raise RuntimeError("Conflicting existing release asset: " + file.name)
    url = rel["upload_url"].split("{")[0] + "?name=" + urllib.parse.quote(file.name)
    result = request(url, file.read_bytes(), raw=True)
    print("Uploaded:", result["name"], flush=True)


def sync():
    ref = request("/git/ref/heads/main")
    parent = ref["object"]["sha"]
    old_commit = request("/git/commits/" + parent)
    remote = request("/git/trees/" + old_commit["tree"]["sha"] + "?recursive=1")
    if remote.get("truncated"):
        raise RuntimeError("Remote tree truncated")
    old = {entry["path"]: entry for entry in remote["tree"] if entry["type"] == "blob"}
    updates = []
    local = {}
    for record in git("ls-tree", "-rz", "HEAD").split(b"\0"):
        if not record:
            continue
        header, path = record.split(b"\t", 1)
        mode, kind, sha = header.decode().split()
        name = path.decode("utf-8")
        local[name] = sha
        if old.get(name, {}).get("sha") == sha:
            continue
        content = git("cat-file", "blob", sha)
        blob = request("/git/blobs", {"content": base64.b64encode(content).decode(), "encoding": "base64"})
        updates.append({"path": name, "mode": mode, "type": kind, "sha": blob["sha"]})
        print("Prepared:", name, flush=True)
    # Keep unrelated remote-only files; this upload is additive and never force-pushes.
    if not updates:
        print("Remote already matches tracked local files", flush=True)
        return
    tree = request("/git/trees", {"base_tree": old_commit["tree"]["sha"], "tree": updates})
    commit = request("/git/commits", {
        "message": "Release 6.26.4 with download website update links",
        "tree": tree["sha"], "parents": [parent],
    })
    rel = release()
    for relative in ["releases/windows/Smart Checker-6.26.4-Setup.exe",
                     "releases/android/Smart Checker-6.26.4.apk"]:
        upload(rel, ROOT / relative)
    request("/git/refs/heads/main", {"sha": commit["sha"], "force": False}, method="PATCH")
    print("Updated main:", commit["sha"], flush=True)


def status():
    runs = request("/actions/workflows/macos.yml/runs?per_page=3")
    for run in runs["workflow_runs"]:
        print(json.dumps({key: run[key] for key in
                          ["id", "head_sha", "status", "conclusion", "html_url"]}), flush=True)
    rel = release()
    print("Release draft:", rel["draft"], flush=True)
    for asset in request(f"/releases/{rel['id']}/assets"):
        print(asset["name"], asset["size"], flush=True)
    return runs, rel


if __name__ == "__main__":
    command = sys.argv[1]
    if command == "sync":
        sync()
    elif command == "status":
        status()
    elif command == "publish":
        runs, rel = status()
        assets = request(f"/releases/{rel['id']}/assets")
        required = {"Smart.Checker-6.26.4-Setup.exe", "Smart.Checker-6.26.4.apk",
                    "Smart.Checker-6.26.4-arm64.dmg"}
        if not required.issubset({asset["name"] for asset in assets}):
            raise RuntimeError("Missing release assets; keep draft unpublished")
        request(f"/releases/{rel['id']}", {"draft": False, "make_latest": "true"}, method="PATCH")
        print("Published:", rel["html_url"], flush=True)

"""Plan a linear UnifiedTV release and verify APK metadata; run only on trusted main."""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import zipfile

PACKAGE = "io.github.spidey000.unifiedtv"
FIRST_CODE = 11000
MAX_CODE = 2100000000
TAG = re.compile(r"v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)")
TYPE = re.compile(r"^(\w+)(?:\([^\r\n]+\))?(!)?:\s")
REQUIRED_PERMISSIONS = {
    "android.permission." + name for name in (
        "INTERNET", "READ_TV_LISTINGS", "REQUEST_INSTALL_PACKAGES",
        "ACCESS_NETWORK_STATE", "ACCESS_WIFI_STATE",
        "WRITE_SECURE_SETTINGS",
    )
} | {"yandex.tv.permission.ACCESS_TV_SOURCE"}
ALLOWED_PERMISSIONS = REQUIRED_PERMISSIONS | {PACKAGE + ".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"}
SECRET_MARKERS = re.compile(
    rb"-----BEGIN (?:[A-Z]+ )*PRIVATE KEY-----|gh[pousr]_[A-Za-z0-9]{36,}"
    rb"|github_pat_[A-Za-z0-9_]{50,}|eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+"
)


def command(*args):
    return subprocess.check_output(args, text=True)


def semver(tag):
    match = TAG.fullmatch(tag)
    return tuple(map(int, match.groups())) if match else None


def next_tag(version, commits):
    bump = 0
    for message in commits:
        headline = message.split("\n", 1)[0]
        kind = TYPE.match(headline)
        if (kind and kind[2]) or re.search(r"^BREAKING[ -]CHANGE:\s", message, re.MULTILINE):
            bump = 2
        elif kind and kind[1] == "feat":
            bump = max(bump, 1)
    major, minor, patch = version
    numbers = ((major, minor, patch + 1), (major, minor + 1, 0), (major + 1, 0, 0))
    return "v%d.%d.%d" % numbers[bump]


def package_metadata(aapt, apk):
    badging = command(aapt, "dump", "badging", apk)
    info = re.search(r"^package: name='([^']+)' versionCode='([0-9]+)' versionName='([^']+)'",
                     badging, re.MULTILINE)
    require(info and info[1] == PACKAGE, "Unexpected Android package")
    require(not re.search(r"^application-debuggable", badging, re.MULTILINE), "APK is debuggable")
    return int(info[2]), info[3], badging


def require(condition, explanation):
    if not condition:
        raise SystemExit(explanation)


def verify_signer(apksigner, apk, fingerprint):
    fingerprint = fingerprint.replace(":", "").lower()
    require(re.fullmatch(r"[0-9a-f]{64}", fingerprint), "Set UNIFIEDTV_SIGNER_SHA256 to the official certificate SHA-256")
    report = command(apksigner, "verify", "--print-certs", apk)
    digests = re.findall(r"^Signer #\d+ certificate SHA-256 digest: (\w+)$", report, re.MULTILINE)
    require(digests == [fingerprint], "APK signing certificate does not match the pinned certificate")
    require(not re.search(r"^Signer #\d+ certificate DN: .*CN=Android Debug\b", report, re.MULTILINE | re.IGNORECASE),
            "Debug certificate is not allowed")


def next_code(previous):
    require(previous is None or 0 < previous < MAX_CODE, "Previous versionCode is invalid or exhausted")
    return FIRST_CODE if previous is None else previous + 1


def plan(args):
    releases = [r for page in json.loads(command("gh", "api", "--paginate", "--slurp",
                f"repos/{os.environ['GH_REPO']}/releases?per_page=100")) for r in page if not r["draft"]]
    versions = {r["tag_name"]: r for r in releases if not r["prerelease"] and semver(r["tag_name"])}
    tags = [t for t in command("git", "tag", "--list").splitlines() if semver(t)]
    require(tags and set(tags) == set(versions), "Stable tags and releases differ; resolve any orphan tag/draft before releasing")
    base = max(tags, key=semver)
    sha = os.environ["GITHUB_SHA"]
    subprocess.run(["git", "merge-base", "--is-ancestor", base, sha], check=True)
    already_released = command("git", "rev-parse", f"{base}^{{commit}}").strip() == sha
    tag = base if already_released else next_tag(semver(base), command("git", "log", "-z", "--format=%B", f"{base}..{sha}").split("\x00"))
    previous = None
    for old_tag in sorted(tags, key=semver, reverse=True):
        apks = [a["name"] for a in versions[old_tag]["assets"] if a["name"].lower().endswith(".apk")]
        if not apks:
            continue
        require(apks == [f"UnifiedTV-{old_tag}.apk"], f"Unexpected APK assets on {old_tag}; investigate compatibility")
        subprocess.run(["git", "merge-base", "--is-ancestor", old_tag, sha], check=True)
        old_apk = Path(os.environ["RUNNER_TEMP"]) / "unifiedtv-previous.apk"
        subprocess.run(["gh", "release", "download", old_tag, "--pattern", apks[0], "--output", old_apk,
                        "--clobber"], check=True)
        verify_signer(args.apksigner, old_apk, args.fingerprint)
        previous, previous_name, _ = package_metadata(args.aapt, old_apk)
        require(previous_name == old_tag[1:], "Previous APK versionName differs from its tag")
        break
    with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as out:
        print(f"tag={tag}\nbase={base}\ncode={next_code(previous)}", file=out)
        print(f"already_released={str(already_released).lower()}\nhas_previous_apk={str(previous is not None).lower()}", file=out)


def verify(args):
    apk = Path(args.apk)
    require(apk.is_file() and 100_000 <= apk.stat().st_size < 5 * 1024 * 1024,
            "APK missing or outside 100,000 bytes–5 MiB size gate")
    verify_signer(args.apksigner, apk, args.fingerprint)
    code, name, badging = package_metadata(args.aapt, apk)
    require(code == args.code and name == args.name, "APK version does not match the release plan")
    require("sdkVersion:'26'" in badging and "targetSdkVersion:'34'" in badging, "Unexpected SDK levels")
    manifest = command(args.aapt, "dump", "xmltree", apk, "AndroidManifest.xml")
    require('"android.intent.category.LEANBACK_LAUNCHER"' in manifest, "Missing Android TV launcher category")
    perm_output = command(args.aapt, "dump", "permissions", apk)
    permissions = set(re.findall(r"^uses-permission(?:-sdk-\d+)?: name='([^']+)'", perm_output, re.MULTILINE))
    require(REQUIRED_PERMISSIONS <= permissions <= ALLOWED_PERMISSIONS,
            f"APK permissions differ from policy: missing={sorted(REQUIRED_PERMISSIONS - permissions)}, "
            f"unexpected={sorted(permissions - ALLOWED_PERMISSIONS)}")
    with zipfile.ZipFile(apk) as archive:
        require(sum(entry.file_size for entry in archive.infolist()) < 50 * 1024 * 1024, "Expanded APK is too large")
        for entry in archive.infolist():
            require(not entry.filename.lower().endswith((".jks", ".keystore", ".p12", ".pfx", ".pkcs12")),
                    "A keystore file was packaged in the APK")
            require(not SECRET_MARKERS.search(archive.read(entry)), "A possible private credential was found in the APK")
    print(f"Verified {PACKAGE} {name} (versionCode {code}), {apk.stat().st_size} bytes")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--aapt", required=True)
    parser.add_argument("--apksigner", required=True)
    parser.add_argument("--fingerprint", required=True)
    subcommands = parser.add_subparsers(dest="action", required=True)
    subcommands.add_parser("plan")
    check = subcommands.add_parser("verify")
    check.add_argument("apk")
    check.add_argument("name")
    check.add_argument("code", type=int)
    args = parser.parse_args()
    {"plan": plan, "verify": verify}[args.action](args)


if __name__ == "__main__":
    main()

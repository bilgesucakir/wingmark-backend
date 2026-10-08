#!/usr/bin/env python3
"""Imports the species in species-popular.json through the admin API, skipping species that already exist.

Usage:
  WINGMARK_ADMIN_TOKEN=<admin access token> python3 scripts/import_species.py [--dry-run] [--base-url URL] [--file PATH]

Species are matched by scientific name (case-insensitive). Nothing is changed with --dry-run.
Get an admin access token by logging in as an admin (POST /api/auth/login); it lasts 15 minutes.
"""
import argparse
import json
import os
import ssl
import sys
import urllib.error
import urllib.parse
import urllib.request

DEFAULT_BASE_URL = "https://wingmark-backend.onrender.com"
DEFAULT_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "species-popular.json")


def ssl_context():
    """Verified TLS. Uses the certifi bundle when installed (python.org builds on macOS often lack system certificates)."""
    try:
        import certifi
        return ssl.create_default_context(cafile=certifi.where())
    except ImportError:
        return ssl.create_default_context()


def request(method, url, token=None, body=None):
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Accept", "application/json")
    if data is not None:
        req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    with urllib.request.urlopen(req, timeout=30, context=ssl_context()) as response:
        text = response.read().decode("utf-8")
        return json.loads(text) if text else None


def existing_scientific_names(base_url):
    names = set()
    page = 0
    while True:
        result = request("GET", f"{base_url}/api/species?size=100&page={page}")
        names.update(s["scientificName"].lower() for s in result["content"])
        if page + 1 >= result["page"]["totalPages"]:
            return names
        page += 1


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--dry-run", action="store_true", help="only report what would be created")
    parser.add_argument("--base-url", default=DEFAULT_BASE_URL)
    parser.add_argument("--file", default=DEFAULT_FILE)
    args = parser.parse_args()

    token = os.environ.get("WINGMARK_ADMIN_TOKEN")
    if not token and not args.dry_run:
        sys.exit("Set WINGMARK_ADMIN_TOKEN to an admin access token (or use --dry-run).")

    with open(args.file, encoding="utf-8") as f:
        species = json.load(f)

    try:
        existing = existing_scientific_names(args.base_url)
    except urllib.error.URLError as e:
        if isinstance(e.reason, ssl.SSLCertVerificationError):
            sys.exit("TLS certificate check failed. Run 'pip3 install certifi' (or Python's 'Install Certificates.command' "
                     "on macOS) and try again. The check is never switched off.")
        sys.exit(f"Could not reach {args.base_url}: {e.reason}")
    created = skipped = failed = 0
    for entry in species:
        name = entry["scientificName"]
        if name.lower() in existing:
            skipped += 1
            print(f"skip    {name} (already exists)")
            continue
        if args.dry_run:
            created += 1
            print(f"would create {name}")
            continue
        try:
            request("POST", f"{args.base_url}/api/species", token, entry)
            created += 1
            existing.add(name.lower())
            print(f"created {name}")
        except urllib.error.HTTPError as e:
            failed += 1
            print(f"FAILED  {name}: HTTP {e.code} {e.read().decode('utf-8')[:200]}")

    verb = "would be created" if args.dry_run else "created"
    print(f"\n{created} {verb}, {skipped} skipped (already exist), {failed} failed")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()

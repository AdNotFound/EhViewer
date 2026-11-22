#!/usr/bin/env python3
"""Sync hosts.json with public DNS.

Resolves A and AAAA records for every tracked host through two independent
DoH providers (Cloudflare and Google), merges the answers, and rewrites
hosts.json at the repository root. Run by .github/workflows/update-hosts.yml;
can also be run by hand.

Safety rules:
- Curated anchor IPs (such as Cloudflare front IPs for API, and direct origin
  IPs for ExHentai) are preserved across updates.
- Invalid, loopback, private, or bogon IPs are strictly discarded.
- A host that resolves to nothing on both providers keeps its previous
  entries, so a transient resolver failure can never wipe the table.
- IPv4 and IPv6 families are preserved independently if a resolver temporarily
  fails to return one family.
- If no host has any IP changes, hosts.json is left untouched (no empty commit).
"""
import ipaddress
import json
import sys
import urllib.request
from datetime import date
from pathlib import Path

HOSTS = [
    "e-hentai.org",
    "forums.e-hentai.org",
    "repo.e-hentai.org",
    "api.e-hentai.org",
    "ehgt.org",
    "gt0.ehgt.org",
    "gt1.ehgt.org",
    "gt2.ehgt.org",
    "gt3.ehgt.org",
    "ul.ehgt.org",
    "exhentai.org",
    "s.exhentai.org",
    "raw.githubusercontent.com",
]

PROVIDERS = [
    "https://cloudflare-dns.com/dns-query?name={name}&type={rtype}",
    "https://dns.google/resolve?name={name}&type={rtype}",
]

TYPE_CODES = {"A": 1, "AAAA": 28}

# Curated anchor IPs to preserve:
# - exhentai.org: origin IPs directly
# - api.e-hentai.org: Cloudflare front IPs as fallback
CURATED_ANCHORS = {
    "exhentai.org": [
        "178.175.128.251", "178.175.128.252", "178.175.128.253", "178.175.128.254",
        "178.175.129.251", "178.175.129.252", "178.175.129.253", "178.175.129.254",
        "178.175.132.19", "178.175.132.20", "178.175.132.21", "178.175.132.22",
    ],
    "api.e-hentai.org": [
        "172.66.132.196", "172.66.140.62",
    ],
}

PINNED = {"exhentai.org"}

ROOT = Path(__file__).resolve().parent.parent
TARGET = ROOT / "hosts.json"


def is_safe_public_ip(ip_str: str) -> bool:
    try:
        addr = ipaddress.ip_address(ip_str)
        return addr.is_global and not addr.is_loopback and not addr.is_multicast and not addr.is_unspecified
    except ValueError:
        return False


def query(provider: str, name: str, rtype: str) -> set[str]:
    url = provider.format(name=name, rtype=rtype)
    request = urllib.request.Request(
        url,
        headers={"accept": "application/dns-json", "User-Agent": "hosts-sync"},
    )
    with urllib.request.urlopen(request, timeout=20) as response:
        payload = json.load(response)
    wanted = TYPE_CODES[rtype]
    return {
        answer["data"]
        for answer in payload.get("Answer", [])
        if answer.get("type") == wanted and is_safe_public_ip(answer["data"])
    }


def resolve(name: str, rtype: str) -> set[str]:
    merged: set[str] = set()
    for provider in PROVIDERS:
        try:
            merged |= query(provider, name, rtype)
        except Exception as exc:  # one provider down must not fail the sync
            print(f"warn: {provider.split('/')[2]} {name} {rtype}: {exc}", file=sys.stderr)
    return merged


def sort_ips(ips: set[str]) -> list[str]:
    def key(ip: str):
        addr = ipaddress.ip_address(ip)
        return (addr.version, int(addr))

    return sorted(ips, key=key)


def main() -> int:
    previous = {}
    if TARGET.exists():
        previous = json.loads(TARGET.read_text()).get("hosts", {})

    hosts: dict[str, list[str]] = {}
    for name in HOSTS:
        anchors = set(CURATED_ANCHORS.get(name, []))
        if name in PINNED:
            hosts[name] = sort_ips(set(previous.get(name, [])) | anchors)
            continue

        prev_ips = set(previous.get(name, []))
        prev_v4 = {ip for ip in prev_ips if ":" not in ip}
        prev_v6 = {ip for ip in prev_ips if ":" in ip}

        res_v4 = resolve(name, "A")
        res_v6 = resolve(name, "AAAA")

        # Keep previous records if resolution for that family returned nothing,
        # preventing transient DNS/network glitches from dropping valid IPs.
        effective_v4 = res_v4 if res_v4 else prev_v4
        effective_v6 = res_v6 if res_v6 else prev_v6

        fresh = effective_v4 | effective_v6 | anchors
        if not fresh:
            print(f"warn: {name} resolved nowhere, keeping previous entries", file=sys.stderr)
            fresh = prev_ips | anchors
        hosts[name] = sort_ips(fresh)

    changed = [
        name for name in HOSTS
        if set(hosts.get(name, [])) != set(previous.get(name, []))
    ]
    if not changed and set(hosts.keys()) == set(previous.keys()):
        print("hosts.json already up to date (no IP changes)")
        return 0

    document = {
        "version": 1,
        "updated": date.today().isoformat(),
        "hosts": hosts,
    }
    TARGET.write_text(json.dumps(document, separators=(",", ":"), sort_keys=True) + "\n")
    print(f"hosts.json updated: {', '.join(changed)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())

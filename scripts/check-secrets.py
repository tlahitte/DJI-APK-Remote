#!/usr/bin/env python3
"""Heuristic local hygiene check. Prints locations, NEVER secret values. Review before publishing."""
from pathlib import Path
import re
import sys
root = Path(__file__).resolve().parents[1]
patterns = [
    re.compile(rb'-----BEGIN (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----'),
    re.compile(rb'\bgh[pousr]_[A-Za-z0-9]{30,}\b'),
    re.compile(rb'\bgithub_pat_[A-Za-z0-9_]{50,}\b'),
    re.compile(rb'\bAKIA[A-Z0-9]{16}\b'),
    re.compile(rb'\bxox[baprs]-[A-Za-z0-9-]{20,}\b'),
]
failures = []
for p in root.rglob('*'):
    if not p.is_file() or '.git' in p.relative_to(root).parts:
        continue
    if p.suffix.lower() in {'.jks', '.keystore', '.p12', '.pfx', '.key', '.pem'} or p.name.startswith('.env'):
        failures.append(str(p.relative_to(root)))
        continue
    if any(d in p.relative_to(root).parts for d in ('build', '.gradle', '.kotlin')) or p.stat().st_size > 2_000_000:
        continue
    data = p.read_bytes()
    if b'\0' in data[:4096]:
        continue
    if any(pattern.search(data) for pattern in patterns):
        failures.append(str(p.relative_to(root)))
if failures:
    print('Potential sensitive files (review outside the repository):', *sorted(set(failures)), sep='\n')
    sys.exit(1)
print('Secret hygiene check passed: no private-key files or recognized token patterns in checked source.')

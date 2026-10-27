#!/usr/bin/env python3
"""Make a Shield Credits code for a refund request.

Usage:
  python3 make_credit.py --key nex_credit_private.pem --days 30 [--valid 60]

  --days   Premium days to give (1 credit = 1 day of Premium)
  --valid  days the code can be redeemed (default 60)

The customer types the code in the app: Premium -> Shield Credits -> Redeem.
Keep nex_credit_private.pem private (never put it in the GitHub repo). Anyone with it can mint codes.
Needs: pip install cryptography
"""
import argparse
import base64
import datetime
import secrets

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec


def b64u(b: bytes) -> str:
    return base64.urlsafe_b64encode(b).decode().rstrip("=")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--key", required=True)
    ap.add_argument("--days", type=int, required=True)
    ap.add_argument("--valid", type=int, default=60)
    a = ap.parse_args()
    if not 1 <= a.days <= 3650:
        raise SystemExit("--days must be 1..3650")
    key = serialization.load_pem_private_key(open(a.key, "rb").read(), password=None)
    expires = (datetime.date.today() + datetime.timedelta(days=a.valid)).strftime("%Y%m%d")
    payload = f"v1|{a.days}|{secrets.token_hex(6)}|{expires}".encode()
    sig = key.sign(payload, ec.ECDSA(hashes.SHA256()))
    print(f"NEX1-{b64u(payload)}.{b64u(sig)}")
    print(f"({a.days} days of Premium, redeem before {expires})")


if __name__ == "__main__":
    main()

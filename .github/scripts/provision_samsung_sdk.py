"""Provision an owner-supplied SDK without publishing its URL or binary."""

import hashlib
import io
import os
from pathlib import Path
import re
import sys
import urllib.parse
import urllib.request
import zipfile


def main():
    url = os.environ.get("SAMSUNG_HEALTH_SDK_URL", "")
    checksum = os.environ.get("SAMSUNG_HEALTH_SDK_SHA256", "").lower()
    if not url or not re.fullmatch(r"[a-f0-9]{64}", checksum):
        raise ValueError("SDK configuration missing. See docs/repository-workflow.md: Samsung SDK provisioning.")
    if urllib.parse.urlsplit(url).scheme != "https":
        raise ValueError("SDK download must use HTTPS.")

    class HttpsRedirects(urllib.request.HTTPRedirectHandler):
        def redirect_request(self, request, response, code, message, headers, newurl):
            if urllib.parse.urlsplit(newurl).scheme != "https":
                raise ValueError("SDK redirect must use HTTPS.")
            return super().redirect_request(request, response, code, message, headers, newurl)

    opener = urllib.request.build_opener(HttpsRedirects())
    with opener.open(url, timeout=60) as response:
        # Bound memory and reject an unexpected large response.
        data = response.read(64 * 1024 * 1024 + 1)
    if len(data) > 64 * 1024 * 1024:
        raise ValueError("SDK response exceeds the download limit.")
    if hashlib.sha256(data).hexdigest() != checksum:
        raise ValueError("SDK SHA-256 mismatch. Check the configured file and checksum.")
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        if "classes.jar" not in archive.namelist():
            raise ValueError("The configured SDK file is not an API AAR containing classes.jar.")
    destination = Path("app/libs/samsung-health-data-api.aar")
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_bytes(data)
    print("Samsung API AAR provisioned and SHA-256 verified.")


if __name__ == "__main__":
    try:
        main()
    except ValueError as error:
        # Do not expose URLs, response headers or authentication details.
        print(f"::error::{error}", file=sys.stderr)
        sys.exit(1)
    except Exception:
        print("::error::SDK provisioning failed. Check the private download and SHA-256 configuration; URL details are suppressed.", file=sys.stderr)
        sys.exit(1)

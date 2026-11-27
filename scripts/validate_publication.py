#!/usr/bin/env python3
"""Fail before building when either portal is missing; never log configured origins."""
import os
from urllib.parse import urlsplit


REQUIRED_ORIGINS = ("PORTAL_URL", "API_BASE_URL", "EFFIPLAN_ORIGIN")


def validate_configuration(environment):
    for name in REQUIRED_ORIGINS:
        value = environment.get(name, "")
        if not value or value != value.strip():
            raise ValueError(f"Missing or invalid publication secret: {name}")
        try:
            origin = urlsplit(value)
            valid = (
                origin.scheme == "https"
                and bool(origin.hostname)
                and origin.username is None
                and origin.password is None
                and origin.path in ("", "/")
                and not origin.query
                and not origin.fragment
                and "?" not in value
                and "#" not in value
                and not any(char.isspace() for char in value)
            )
            origin.port  # Reject malformed ports as well.
        except ValueError:
            valid = False
        if not valid:
            raise ValueError(f"Publication secret must be an HTTPS origin: {name}")


def main():
    try:
        validate_configuration(os.environ)
    except ValueError as error:
        raise SystemExit(str(error)) from None
    print("Publication configuration validated for both portals.")


if __name__ == "__main__":
    main()

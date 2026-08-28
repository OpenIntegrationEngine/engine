#!/usr/bin/env bash
# Generate .dockerignore from .gitignore.
#
# .gitignore is the authoritative list of build outputs and local artifacts. The
# Dockerfile builds via `COPY . .`, so anything git ignores (and that a clean CI
# checkout therefore never has) must also be kept out of the image build context;
# otherwise stale local artifacts leak into the image (this is how a leftover
# server/setup/appdata/keystore.jks once broke TLS startup).
#
# .gitignore and Docker's .dockerignore use *opposite* default anchoring:
#
#     git:    a pattern with no leading/embedded slash matches at ANY depth
#             (`build/` matches `client/build/`).
#     docker: a pattern is matched against the full path from the context root
#             (`build/` matches only root `build`; any depth needs `**/build`).
#
# This script translates the anchoring so the two stay equivalent. Edit
# .gitignore, then re-run:  tools/gen-dockerignore.sh
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GITIGNORE="$ROOT_DIR/.gitignore"
DOCKERIGNORE="$ROOT_DIR/.dockerignore"

# Excludes that are NOT build outputs (so they are not in .gitignore) but that the
# image build does not need. Keeping them out shrinks the context and avoids busting
# the build cache on unrelated changes.
docker_specific=(
    ".git"            # VCS metadata; never auto-excluded by Docker
    ".github/"        # CI config, not needed inside the image
    "**/Dockerfile*"
    "**/.dockerignore"
)

{
    cat <<'EOF'
# GENERATED FROM .gitignore by tools/gen-dockerignore.sh -- DO NOT EDIT BY HAND.
# .gitignore is authoritative; edit it and re-run the generator.
# (git and docker use opposite default path anchoring -- see the script.)

EOF

    # sed drops the many empty "# /path/" section-placeholder comments that pad
    # .gitignore, then squeezes the blank runs they leave behind. Banner and section
    # comments are kept for readability.
    sed -e '/^[[:space:]]*#[[:space:]]*\//d' -e 's/[[:space:]]*$//' "$GITIGNORE" \
        | cat -s \
        | while IFS= read -r line; do
            # Blank lines and comments pass through untouched.
            if [[ -z "$line" || "$line" == \#* ]]; then
                printf '%s\n' "$line"
                continue
            fi

            # A leading '!' re-includes; translate the rest and put it back.
            negate=""
            if [[ "$line" == '!'* ]]; then
                negate="!"
                line="${line#!}"
            fi

            # dockerignore matches a directory by its name, without a trailing slash.
            line="${line%/}"
            [[ -z "$line" ]] && continue

            case "$line" in
                /*)  translated="${line#/}" ;;   # git root-anchored -> docker root-relative
                */*) translated="$line" ;;       # embedded slash: already anchored in both
                *)   translated="**/$line" ;;    # git "any depth" -> explicit in docker
            esac

            printf '%s%s\n' "$negate" "$translated"
        done

    printf '\n# ---- Docker-specific (not build outputs, so not in .gitignore) ----\n'
    printf '%s\n' "${docker_specific[@]}"
} > "$DOCKERIGNORE"

echo "Wrote ${DOCKERIGNORE#"$ROOT_DIR"/}"

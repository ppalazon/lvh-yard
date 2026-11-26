#!/bin/sh
# Prints the section of CHANGELOG.md for a version, such as 0.2.0, without its heading.
# It fails when the file has no such section, or when the section is empty.
set -eu
version=${1:?Usage: sh dev/changelog-section.sh VERSION [FILE]}
file=${2:-CHANGELOG.md}
section=$(awk -v heading="## [$version]" '
  /^## \[/ || /^\[[^]]+\]: / { if (found) exit }
  index($0, heading) == 1 { found = 1; next }
  found { print }
' "$file")
if [ -z "$(printf '%s' "$section" | tr -d '[:space:]')" ]; then
  echo "$file has no entry for $version. Add a section \"## [$version] - <date>\"." >&2
  exit 1
fi
printf '%s\n' "$section"

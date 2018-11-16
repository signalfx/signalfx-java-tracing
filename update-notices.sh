#!/bin/bash

set -euo pipefail

git fetch datadog

bad_files=0

CHECK_ONLY=${CHECK_ONLY-"no"}

add_notice() {
  local f=$1
  local newf="${f}.new"

  # This should work for Java, Groovy, and Gradle files
  cat <<EOH > $newf
// Modified by SignalFx
EOH

  cat $f >> $newf
  mv $newf $f
}


for f in $(git diff --cached --name-status | awk '$1 == "M" { print $2 }'); do
  if git ls-tree --name-only -r datadog/master | grep $f > /dev/null 2>&1; then
    if ! head -n10 $f | grep 'Modified by SignalFx' > /dev/null 2>&1; then
      if [[ "$CHECK_ONLY" == "no" ]]; then
        add_notice $f
      else
        echo $f
        bad_files=1
      fi
    fi
  fi
done

for diff in $(git diff --cached --name-status | awk '$1 ~ "^R" { printf "%s,%s ",$2,$3 }'); do
  IFS=, read orig new <<< "$diff"
  if git ls-tree --name-only -r datadog/master | grep $orig > /dev/null 2>&1; then
    if ! head -n10 $new | grep 'Modified by SignalFx' > /dev/null 2>&1; then
      if [[ "$CHECK_ONLY" == "no" ]]; then
        add_notice $new
      else
        echo $new
        bad_files=1
      fi
    fi
  fi
done

if [[ $bad_files != 0 ]]; then
  cat <<EOH

ERROR: The above files are part of DataDog's original repository but you did not
       include the modification notice in conformity with the Apache-2.0 license.
       Please include it by running ./update-notices.sh from the root of the
       repository
EOH
  exit 1
fi

exit 0

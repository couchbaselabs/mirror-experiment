#! /bin/sh
set -e

loop=$(cat memory-init/build.txt)
rm -rf memory/crypt
mkdir -p crypt
zip -r "crypt/${loop}.zip" memory memory/.backup.yaml

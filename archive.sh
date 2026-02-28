#! /bin/sh
set -e

loop=$(cat memory-init/build.txt)
zip -r "crypt/${loop}.zip" memory memory/.backup.yaml

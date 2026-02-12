#! /bin/sh
set -e

gradle shadowJar

docker build . -t mirror || echo docker build failed

docker stop mirror && docker rm mirror || echo "was not running"

if [ ! -d memory ]; then
  cp -r memory-init memory
fi

mkdir -p memory/crypt/source
rsync -r app/src/main/java/org/example/ memory/crypt/source
#cp README.md memory/README.md

cp config.yaml memory/

docker run --name mirror -v ./memory:/home mirror
#| tee "$(tty)" | ./mqtt_stream 192.168.x.x 1883 agatha_stream

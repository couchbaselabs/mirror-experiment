#! /bin/sh
set -e

gradle shadowJar

docker build . -t mirror || echo docker build failed

docker stop mirror && docker rm mirror || echo "was not running"

if [ ! -d memory ]; then
  cp -r memory-init memory
fi
cp memory-init/prompt.txt memory/prompt.txt

mkdir -p memory/source
rsync -r app/src/main/java/org/example/ memory/source
#cp README.md memory/README.md
cp config.yaml memory/config.yaml

echo "Build number: T-$(cat memory-init/build.txt)" > memory/build.txt


docker run -v /var/run/docker.sock:/var/run/docker.sock --name mirror -v ./memory:/home mirror
#| tee "$(tty)" | ./mqtt_stream 192.168.x.x 1883 agatha_stream

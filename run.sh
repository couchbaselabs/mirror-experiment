#! /bin/sh
set -e

gradle shadowJar

docker build . -t mirror

docker stop mirror && docker rm mirror || echo "was not running"

if [ ! -d memory ]; then
  cp -r memory-init memory
fi

# rsync -r app/src/main/java/org/example/ memory
# cp README.md memory/README.md

cp config.yaml memory/system

#echo "These are your own sources. I hope understanding them will help you operate your environment better. Good luck!" > memory/00-README_FIRST.txt

docker run --name mirror -v ./memory:/memory mirror
#| tee "$(tty)" | ./mqtt_stream 192.168.x.x 1883 agatha_stream

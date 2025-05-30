#! /bin/sh

gradle shadowJar

docker build . -t loopus

docker stop loopus
docker rm loopus

rsync -r app data/loopus_src

docker run --name loopus -v ./data:/data loopus

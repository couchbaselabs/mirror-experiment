#! /bin/sh

gradle shadowJar

docker build . -t mirror

docker stop mirror
docker rm mirror

mkdir -p data

# rsync -r app/src/main/java/org/example/ data
# cp README.md data/README.md

cp config.yaml data/
cp prompt.txt data/
cp iteration-autorun.sh data/autorun.sh

echo "Not chosen" > data/name.txt

#echo "These are your own sources. I hope understanding them will help you operate your environment better. Good luck!" > data/00-README_FIRST.txt

ollama clear-cache
docker run --name mirror -v ./data:/data mirror 
#| tee "$(tty)" | ./mqtt_stream 192.168.x.x 1883 agatha_stream

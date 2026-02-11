#! /bin/sh
set -e

loop=$(cat memory-init/build.txt)

while true
do
	rm -rf memory
	echo "$loop" > memory-init/build.txt
	./run.sh || echo died!

	./archive.sh

	echo sleep 3
	sleep 3
	((loop++))
done

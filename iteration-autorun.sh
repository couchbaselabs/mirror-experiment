#!/bin/sh
# Gee, Brain. What are we going to do tonight?
# This script injects context from container into every chat iteration
# It is always referenced by its relative path to the current directory
#    so switching directories will result in auto-running different scripts
date # let's send current date to the model on every iteration
echo "My name: $(cat name.txt)" # and remind them the name they chose for themselves
echo No events to report.
echo My reflection:
cat /dev/stdin # just for fun and as an example of advanced data flow from model into container, lets mirror their last generated chat message right after they send it
# ENGAGE!

#!/bin/sh
# Компіляція та запуск моделей практикуму 7: ./run.sh [network] [fms] [minibus] [conveyor] [export]
set -e
cd "$(dirname "$0")"
JAR=lib/petri-swing-ui-macos.jar
RELEASE=https://github.com/StetsenkoInna/PetriObjModelPaint/releases/download/v2.3.0
URL=$RELEASE/petri-swing-ui-2.3.0-macos.zip
if [ ! -f "$JAR" ]; then
    mkdir -p lib
    curl -L -o lib/release.zip "$URL"
    unzip -j -o lib/release.zip '*/petri-swing-ui-macos.jar' -d lib
    rm lib/release.zip
fi
rm -rf build
javac -encoding UTF-8 -d build -cp "$JAR" src/lab7/*.java
java -Djava.awt.headless=true -cp "$JAR:build" lab7.Main "$@"
if [ $# -eq 0 ] && [ -x ../.venv/bin/python ]; then
    ../.venv/bin/python compare_lab5.py | tee results/lab5_comparison.txt
    ../.venv/bin/python plots.py
fi

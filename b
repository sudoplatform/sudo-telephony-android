#!/bin/bash
export DIR=$HOME/.m2/repository/com/anonyome/sudotelephony/
rm -rf "$DIR"
./gradlew --console=plain publishToMavenLocal
echo "$DIR"
ls -lh "$DIR"

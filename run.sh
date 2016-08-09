#!/usr/bin/env bash

NAME='gradle-2.14.1'
ZIP=${NAME}-bin.zip
[ ! -f ${ZIP}  ] && wget https://services.gradle.org/distributions/${ZIP}
[ ! -d ${NAME} ] && unzip ${ZIP}
${NAME}/bin/gradle build
java -jar build/libs/gzip_streamer-1.0-SNAPSHOT.jar $(which nginx)
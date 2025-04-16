#!/bin/bash

./wait-for-it.sh database:5432 -s -t 0 -- java -jar target/hotel-service-0.0.1-SNAPSHOT.jar &
promtail -config.file=/workdir/promtail-config.yml
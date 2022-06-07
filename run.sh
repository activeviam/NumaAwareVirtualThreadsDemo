#!/bin/bash

mvn install -e
java \
  --enable-preview \
  -cp target/uberjar.jar \
  --add-exports "java.base/jdk.internal.misc=ALL-UNNAMED" \
  --add-opens "java.base/java.lang=ALL-UNNAMED" \
  com.activeviam.experiments.loom.numa.NumaDemo

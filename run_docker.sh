#!/bin/bash

set -ex

sudo docker build -t activeviam-numa-demo .
sudo docker run --rm --cap-add=sys_nice activeviam-numa-demo

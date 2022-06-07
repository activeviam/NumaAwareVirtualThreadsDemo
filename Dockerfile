FROM ubuntu:20.04

WORKDIR /home

RUN apt-get update && apt-get install -y numactl

ADD "https://download.java.net/java/early_access/loom/6/openjdk-19-loom+6-625_linux-x64_bin.tar.gz" /home/jdk.tgz
RUN test "$(sha256sum /home/jdk.tgz | awk '{ print $1 }')" = \
    "b2d32f8146482590cb74378ea1d433b04e49d7b39bbb77a3bd7f5e318bee22aa"
RUN tar xzvf /home/jdk.tgz
ENV PATH="/home/jdk-19/bin:$PATH"

ADD "https://dlcdn.apache.org/maven/maven-3/3.8.5/binaries/apache-maven-3.8.5-bin.tar.gz" /home/mvn.tgz
RUN test "$(sha512sum /home/mvn.tgz | awk '{ print $1 }')" = \
    "89ab8ece99292476447ef6a6800d9842bbb60787b9b8a45c103aa61d2f205a971d8c3ddfb8b03e514455b4173602bd015e82958c0b3ddc1728a57126f773c743"
RUN tar xzvf /home/mvn.tgz
ENV PATH="/home/apache-maven-3.8.5/bin:$PATH"

ADD src /home/src
ADD pom.xml /home/pom.xml
RUN mvn install

CMD ["java", \
  "--enable-preview", \
  "-cp", "target/uberjar.jar", \
  "--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED", \
  "--add-opens", "java.base/java.lang=ALL-UNNAMED", \
  "com.activeviam.experiments.loom.numa.NumaDemo"]

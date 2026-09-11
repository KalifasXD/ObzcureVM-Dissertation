# Reproducible build/dev environment for the ObzcureVM core (thesis M1).
# Pins the exact toolchain so the "which JDK" problem never comes back.
#
# Base image ships Maven 3.9 + Eclipse Temurin JDK 17 already wired together.
# JDK 17 is what ObzcureVM's README documents and what its bundled ASM supports
# (its ClassReader rejects class files newer than Java 17 / major version 61).
# The pom.xml compiler source/target are pinned to 17 to match.
FROM maven:3.9-eclipse-temurin-17

# Everything happens under /work, which we bind-mount to the repo at run time,
# so files you edit on Windows/WSL are the same files the container builds.
WORKDIR /work

# Drop into a shell by default. You build/run manually inside the container
# during M1 (mvn clean package, then java --enable-preview -jar ...).
CMD ["bash"]

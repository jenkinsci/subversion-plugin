##
# Prepare Java and Maven
##

##
# Windows example (build executed via Git bash or similar)
export JAVA_HOME="/c/Program Files/Java/jdk-17"
export M2_HOME="/c/Program Files/Maven/apache-maven-3.9.9"

alias mvn="$M2_HOME/bin/mvn.cmd"

##
# Ubuntu example with OpenJDK or Adoptium Temurin installed:
export JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64"
export JAVA_HOME="/usr/lib/jvm/temurin-17-jdk-amd64"
export M2_HOME="/opt/apache-maven-3.9.9"

alias mvn="$M2_HOME/bin/mvn"

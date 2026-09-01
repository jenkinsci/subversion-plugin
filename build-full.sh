# Prepare Java and Maven
. ./__setup_env.priv.sh

#read -rsp $'Press any key to continue...\n' -n 1 key

# setup
dateStamp=$(date +"%Y-%m-%dT%H.%M.%S")

# Clean
mvn clean
read -rsp $'Press any key or wait 10 seconds to continue...\n' -n 1 -t 10;

LOG_BASE=".build.priv.log/"
mkdir -p "$LOG_BASE"

# Build
LOG_FILE="${LOG_BASE}build-full--$dateStamp.tex"
#mvn install
mvn -Dstyle.color=always install \
  | tee >(sed $'s/\033[[][^A-Za-z]*m//g' > "$LOG_FILE")

# Done&PAUSE
echo
echo "-----------------------------"
echo "Log saved to: ${LOG_FILE}"
echo "-----------------------------"
echo
read -rsp $'Press any key to continue...\n' -n 1 key

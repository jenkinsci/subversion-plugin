# Prepare Java and Maven
. ./__setup_env.priv.sh

#read -rsp $'Press any key to continue...\n' -n 1 key

# setup
dateStamp=$(date +"%Y-%m-%dT%H.%M.%S")

# Clean
mvn clean
read -rsp $'Press any key or wait 3 seconds to continue...\n' -n 1 -t 3;

# Quick build
mvn install -DskipTests

# Run local Jenkins
echo
echo "-----------------------------"
echo "Running local Jenkins instance."
echo "Wait a few minutes and open: http://localhost:8080/jenkins/"
echo "(CTRL+C to terminate)"
echo "-----------------------------"
echo
read -rsp $'Press any key or wait 3 seconds to continue...\n' -n 1 -t 3;
mvn hpi:run

# Done&PAUSE
#read -rsp $'Press any key to continue...\n' -n 1 key

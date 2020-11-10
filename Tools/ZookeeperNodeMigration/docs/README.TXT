To run the project from the command line, go to the folder that contains "ZookeeperNodeMigration.jar" and
type the following:

java -jar ZookeeperNodeMigration.jar

To distribute this project, zip up the dist folder (including the lib folder)
and distribute the ZIP file.

Usage:
ZookeeperNodeMigration input needs to be: 
[Input Zookeeper IP Address or Hostname] [Input Zookeeper Port Number] [Output Zookeeper IP Address or Hostname] [Output Zookeeper Port Number]

For example, if you execute the following command from command line line, the Zookeeper 
nodes will get copied from Zookeeper server on localhost:9983 to Zookeeper server on localhost:19983 :

java -jar ZookeeperNodeMigration.jar localhost 9983 localhost 19983


If you do not have Java installed on the machine, you can use the packaged version of Java that is distributed along 
with Autopsy. For example:

"C:\Program Files\Autopsy-4.16.0\jre\bin\java.exe" -jar ZookeeperNodeMigration.jar localhost 9983 localhost 19983


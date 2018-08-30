# Building Autopsy from source on Debian based Linux.

## Install Oracle Java and set JAVA_HOME.
- Use the instructions here: https://medium.com/coderscorner/installing-oracle-java-8-in-ubuntu-16-10-845507b13343
          NOTE: You may need to log out and login again to make JAVA_HOME environment variable visible to netbeans.

## Building sleuthkit from source.

- Install all necessary packages to build sleuthkit
'''
sudo apt-get update
sudo apt-get install libtool automake libpq-dev postgresql libewf-dev libafflib-dev libvhdi-dev libvmdk-dev git testdisk ant
'''
- Clone sleuthkit repository 
```
git clone https://github.com/sleuthkit/sleuthkit
```
- change into sleuthkit directory ```cd sleuthkit```
- Run the following commands:
```
./bootstrap
./configure
make
```
- change into binding/java/dist ```cd binding/java/dist```
- Run:
```
ln -s sleuthkit-VERSION.jar sleuthkit-postgresql-VERSION.jar
```
## Building Autopsy from source

- After building the sleuthkit now its time to build Autopsy

#### Setting Up TSK_HOME environment variable:
- Add TSK_HOME env to the /etc/environment
- TSK_HOME environment variable is the directory of sleuthkit
     NOTE: You may need to log out and login again to make JAVA_HOME environment variable visible to netbeans.
- Open Autopsy on netbeans and Autopsy now can be successfully build on Linux.

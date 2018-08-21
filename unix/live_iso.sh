#This script is used automate the process of creating the live ISO.

#!/bin/bash


set -e

#installing java8

apt-get purge ubiquity
add-apt-repository ppa:webupd8team/java;
apt-get update;
apt-get install oracle-java8-installer;
source /etc/profile.d/jdk.sh

#installing sleuthkit dependencies

apt-get update && apt-get upgrade;
apt-get install libtool automake libpq-dev postgresql libewf-dev libafflib-dev libvhdi-dev libvmdk-dev git testdisk ant build-essential aptitude wget unzip;
apt-get install libstdc++6;

#installing sleuthkit using the debian files

if [ "$1" != ""  ]; then
[ ! -f "./$1" ] && wget https://github.com/sleuthkit/sleuthkit/releases/download/sleuthkit-${1:15:5}/$1;
  apt-get -y install ./$1;
  rm ./$1
fi


installAutopsy () {
[ ! -f "./$1" ] && wget https://github.com/sleuthkit/autopsy/releases/download/${1%.*}/$1;
   [ ! -d "/${1%.*}" ] && unzip ./$1 -d /
   cd /${1%.*} && sh unix_setup.sh && cd -
   rm ./$1 > /dev/null
 }


#installing autopsy using the zip files

if [ "$2" != "" ]; then
   installAutopsy $2;
fi

[ ! -f "./launch_script_bootable.sh" ] && wget https://raw.githubusercontent.com/sleuthkit/autopsy/develop/unix/launch_script_bootable.sh
mv launch_script_bootable.sh /${2%.*}/autopsy.sh
sed -i -e "s/\/usr\/share\/autopsy-4.7.0\/bin\/autopsy/\/${2%.*}\/bin\/autopsy/g" /${2%.*}/autopsy.sh
chmod +x /${2%.*}/autopsy.sh

ln -s /${2%.*}/autopsy.sh /usr/local/bin/autopsy

touch /usr/share/applications/autopsy.desktop

echo -e "[Desktop Entry]\nVersion=1.0\nName=Autopsy\nComment=Complete Digital forensics analysis suite\nExec=sudo /usr/local/bin/autopsy\nIcon=/usr/share/icons/autopsy.png\nTerminal=true\nType=Application\nCategories=Utility;System;" > /usr/share/applications/autopsy.desktop

chmod +x /usr/share/applications/autopsy.desktop

#setup desktop files
mkdir /etc/skel/Desktop
cp /usr/share/applications/autopsy.desktop /etc/skel/Desktop/
cp /usr/share/applications/lxterminal.desktop /etc/skel/Desktop/

#setup autopsy icon
[ ! -f "./autopsy.png" ] && wget https://github.com/sleuthkit/autopsy/raw/develop/unix/autopsy.png
mv ./autopsy.png /usr/share/icons

#setup iso wallpaper
[ ! -f "./autopsy_wallpaper1.png" ] && wget https://github.com/sleuthkit/autopsy/raw/develop/unix/autopsy_wallpaper1.png
mv ./autopsy_wallpaper1.png /usr/share/lubuntu/wallpapers/autopsy_wallpaper.png
unlink /usr/share/lubuntu/wallpapers/lubuntu-default-wallpaper.png
unlink /usr/share/lubuntu/wallpapers/lubuntu-default-wallpaper.jpg
ln -s /usr/share/lubuntu/wallpapers/autopsy_wallpaper.png /usr/share/lubuntu/wallpapers/lubuntu-default-wallpaper.png
ln -s /usr/share/lubuntu/wallpapers/lubuntu-default-wallpaper.png /usr/share/lubuntu/wallpapers/lubuntu-default-wallpaper.jpg

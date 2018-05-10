#!/bin/bash

infoLog () {
  echo "INFO: "$1
}

errorLog () {
  echo "ERROR: "$1
  exit 1
}
Autopsybin=/usr/bin/autopsy4
[ -x "$Autopsybin" ] && infoLog "Autopsy found" || errorLog "Antopsy binaries not found exiting....."

createConfigDirectories () {
    if [ ! -d "$1" ]; then
      mkdir $1 && infoLog "$1 successfully created" || errorLog "error while creating $1"
    fi
    if [ ! -d "$2" ]; then
      mkdir $2 && infoLog "$2 successfully created" || errorLog "error while creating $2"
    fi
    if [ ! -d "$3" ]; then
      mkdir $3 && infoLog "$3 successfully created" || errorLog "error while creating $3"
    fi
    if [ ! -d "$4" ]; then
      mkdir $4 && infoLog "$4 successfully created" || errorLog "error while creating $4"
    fi
    return 0
}
options_length=0
showAndReadOptions (){
  echo "Select a mounted disk to create config directory"
  mnt=( $(mount | grep "media" | grep "rw" | awk '{print $3}') )
  length=${#mnt[@]}
  mnt[length]="No action"
  options_length=$(( $length + 1 ))
  x=1
  for word in "${mnt[@]}"
  do
    echo [$x] "${word}"
    x=$(($x + 1))
  done
  read option
}
showAndReadOptions
if [ "$option" -lt "1" ] || [ "$option" -gt "$options_length" ]; then
  infoLog "Please choose a valid option"
  showAndReadOptions
fi
if [ "$option" != "$options_length" ]; then
  index=$(( $option - 1 ))
  echo "Autopsy configurations will be stored in" "${mnt[$index]}"". Are you sure? (y/n)"
  read affirmation
  if [ "$affirmation" == "y" ] || [ "$affirmation" == "Y" ]; then
    [ -d "${mnt[$index]}" ]  && selectedMount=${mnt[$index]} || errorLog "Mount point not found"
    [ -w "$selectedMount" ] && autopsyConfigDir="${mnt[$index]}/AutopsyConfig" || errorLog "Mount point $selectedMount does not have write permission"
    cacheDirectory="$autopsyConfigDir/cachedir"
    userDirectory="$autopsyConfigDir/userdir"
    configDirectory="$autopsyConfigDir/configdata"
    createConfigDirectories $autopsyConfigDir $cacheDirectory $userDirectory $configDirectory
    if [ $? -eq 0 ]; then
        sh $Autopsybin --userdir $userDirectory --cachedir $cacheDirectory -J-Djava.io.tmpdir=$configDirectory
    fi
  fi
else
    sh $Autopsybin
fi

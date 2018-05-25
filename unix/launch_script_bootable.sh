#!/bin/bash

# This script is to be used by the maintainers of bootable Linux systems so that
# Autopsy can launch and use an external USB device for user configuration.  This
# will ensure that the user does not have to reconfigure Autopsy each time.
#
# The basic idea of the script is to let the user pick which external readwrite
# device to use, ensure that an Autopsy folder exists, and launch Autopsy so that
# it uses that folder for configuration.
#
# To use this script, maintainers should:
# - Update AUTOPSY_BIN to path where the autopsy script / folder are



AUTOPSY_BIN=/usr/share/autopsy-4.7.0/bin/autopsy

infoLog () {
  echo "INFO: "$1
}

errorLog () {
  echo "ERROR: "$1
  exit 1
}

Verify we can find the script
if [[ -x "$AUTOPSY_BIN" ]]; then
 infoLog "Autopsy found"
else
 errorLog "Autopsy binaries not found at $AUTOPSY_BIN. Exiting....."
fi


# Create folders on external drive
createConfigDirectories () {
    if [ ! -d "$1" ]; then
      mkdir $1
      if [ ! -d "$1" ]; then
	      errorLog "error while creating $1"
      else
        infoLog "$1 successfully created"
      fi
    fi
    return 0
}


options_length=0
# Display list of mounted devices, prompt user, and store
# results in global variables
showAndReadOptions () {
  echo "Select a mounted disk to create config directory"
  # Maintainers: Adjust these grep statements based on where your
  # platform mounts media.
  mnt=( $(mount | grep "media" | grep "rw" | awk '{print $3}') )

  # Add option to user to not use mounted media
  length=${#mnt[@]}
  mnt[length]="Do not store on mounted disk"
  options_length=$(( length + 1 ))

  x=1
  for word in "${mnt[@]}"
  do
    echo [$x] "${word}"
    x=$((x + 1))
  done
  read option
}


# Show mounted drives and loop until it is valid
while true
do
  showAndReadOptions
  if [ "$option" -ge "1" ] && [ "$option" -le "$options_length" ]; then
    break
  fi
  echo "Please choose a valid option"
done

if [ "$option" != "$options_length" ]; then
  index=$(( option - 1 ))
  echo "Autopsy configurations will be stored in" "${mnt[$index]}"". Are you sure? (y/n)"
  read affirmation
  if [ "$affirmation" == "y" ] || [ "$affirmation" == "Y" ]; then
    if [[ -d "${mnt[$index]}" ]]; then
      selectedMount=${mnt[$index]}
    else
      errorLog "Mount point not found"
    fi

    if [[ -w "$selectedMount" ]]; then
      autopsyConfigDir="${mnt[$index]}/AutopsyConfig"
    else
      errorLog "Mount point $selectedMount does not have write permission"
    fi

    # Make the directories on the media
    userDirectory="$autopsyConfigDir/userdir"
    createConfigDirectories $autopsyConfigDir && createConfigDirectories $userDirectory

    if [ $? -eq 0 ]; then
        sh $AUTOPSY_BIN --userdir $userDirectory
    fi
  fi
else
    sh $AUTOPSY_BIN
fi

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

#Verify we can find the script
if [[ ! -x "$AUTOPSY_BIN" ]]; then
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
  echo -e "\n"
  echo "Select a mounted disk to create config directory: "
  # Maintainers: Adjust these grep statements based on where your
  # platform mounts media.
  echo -e "\n"
  mnt=( $(findmnt -n -lo source,target,fstype,label,options,size | grep "media" | grep "rw" | awk '{print $1, $2, $4, $6}') )

  local length=${#mnt[@]}
  mnt[length]="Do not store on mounted disk"
  options_length=$(( length / 4 + 1 ))

  printf "%-10s\t%-10s\t%-30s\t%-10s\t%-10s\t\n" "Selection" "Source" "Target" "Label" "Size"

  echo "-----------------------------------------------------------------------------------------------------"

  for ((i=0;i< $options_length;i++));
  do
    printf "%-10s\t" "$(( i+1 ))"
    for((j=0;j<4;j++));
    do
      printf "%-10s\t" "${mnt[j + i * 4]}"
    done
    if [[ -d "${mnt[1 + i * 4]}/AutopsyConfig" ]]; then
      printf "%-10s\t" "Contains Autopsy Config data"
    fi
    echo -e "\n\n"
  done
  read -n 1 option
  echo -e "\n"
  if [[ $option = "" ]] || ! [[ "$option" =~ ^[0-9]+$ ]]; then
    echo "Please choose a valid option"
    showAndReadOptions
  fi
}

showCaseDirOptions () {

  echo "Please select a drive to store case data: "
  echo -e "\n"
  casedirremovable=( $(lsblk -lno NAME,RM,MOUNTPOINT,LABEL | awk '$3 != "" {print $1,$2,$3,$4}' | awk '$2 == 1 {print $3}') )
  casedir=( $(lsblk -lno NAME,SIZE,MOUNTPOINT | awk '$3 != "" {print $1,$2,$3}') )
  local lengthCaseDir=${#casedir[@]}
  optionsCasedirLength=$(( lengthCaseDir / 3 ))
  printf "%-10s\t%-10s\t%-10s\t%-30s\t\n" "Selection" "Disk" "Size" "Mount"

  echo "-----------------------------------------------------------------------------------------------------"

  for ((i=0;i<$optionsCasedirLength;i++));
  do
    printf "%-10s\t" "$(( i+1 ))"
    for((j=0;j<3;j++));
    do
      printf "%-10s\t" "${casedir[j + i  * 3]}"
    done
    if [[ -d "${casedir[2 + i * 3 ]}/AutopsyConfig" ]]; then
      printf "%-10s\t" "Contains Autopsy config data"
    fi
    echo -e "\n\n"
  done
  read -n 1 casedirOption
  echo -e "\n"
  if [[ $casedirOption = "" ]] || ! [[ "$casedirOption" =~ ^[0-9]+$ ]]; then
    echo "Please choose a valid option"
    showCaseDirOptions
  fi
}

showWarning() {
  RED='\033[0;31m'
  NC='\033[0m'
  local e match="$1"
  shift
  for e; do [[ "$e" == "$match" ]] && return 0; done
  echo -e "${RED}Warning: Case data stored in non removable disk cannot be saved${NC}"
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

#Show case drives and loop until it is valid

while true
do
  showCaseDirOptions
  if [ "$casedirOption" -ge "1" ] && [ "$casedirOption" -le "$optionsCasedirLength" ]; then
    break
  fi
  echo "Please choose a valid option"
done

if [ "$option" != "$options_length" ]; then
  index=$(( (option - 1) * 4 + 1 ))
  casedirIndex=$(( (casedirOption - 1) * 3 + 2 ))
  read -p "Autopsy configurations will be stored in ${mnt[$index]}. Are you sure? (y/n): " affirmation
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

    showWarning  "${casedir[$casedirIndex]}" "${casedirremovable[@]}"
    # Make the directories on the media
    userDirectory="$autopsyConfigDir/userdir"
    createConfigDirectories $autopsyConfigDir && createConfigDirectories $userDirectory

    if [ $? -eq 0 ]; then
      sh $AUTOPSY_BIN --userdir $userDirectory --liveAutopsy=${casedir[$casedirIndex]}
    fi
  fi
else
  sh $AUTOPSY_BIN
fi

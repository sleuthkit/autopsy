;==============================================================================
; Autopsy Forensic Browser
;
; Copyright 2019 Basis Technology Corp.
; Contact: carrier <at> sleuthkit <dot> org
;
; Licensed under the Apache License, Version 2.0 (the "License");
; you may not use this file except in compliance with the License.
; You may obtain a copy of the License at
;
;     http://www.apache.org/licenses/LICENSE-2.0
;
; Unless required by applicable law or agreed to in writing, software
; distributed under the License is distributed on an "AS IS" BASIS,
; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
; See the License for the specific language governing permissions and
; limitations under the License.
;==============================================================================
#include <GUIConstantsEx.au3>
#include <MsgBoxConstants.au3>
#include <ProgressConstants.au3>
#include <File.au3>
#include <WinAPIFiles.au3>
#include <ScrollBarConstants.au3>
#include <GuiEdit.au3>
#include <Date.au3>

;Get the list of names of algorithms 
Global $algorithms[3] ;increase size of array when adding new algorithms
$algorithms[0] = "Single data source"
$algorithms[1] = "Folder of logical files"
$algorithms[2] = "One data source per folder"

Global $algorithmDescriptions[3] ;increase size of array when adding new algorithms
$algorithmDescriptions[0] = "Create a single auto ingest manifest file for a single disk image or VM file."
$algorithmDescriptions[1] = "Create a single auto ingest manifest file for a single folder of logical files."
$algorithmDescriptions[2] = "Create a manifest file for the first supported image of each subfolder of a case folder. If no supported images exist in the folder a manifest will be generated for the folders contents as a logical file set. Supported disk image or VM files: .e01, .l01, .001, .ad1"

; $algorithms[2] = "All Files In One Folder"
Global $progressArea = Null
Global $manifestFileNameEnd = "Manifest"
Global $manifestExtension = ".xml"


;Return an array containing the names of all algorithms
Func GetAlgorithmNames()
	Return $algorithms
EndFunc   

;Return the description for the specified algorithm index
Func GetAlgorithmDescription($index)
	Return $algorithmDescriptions[$index]
EndFunc

;Return the name of the first algorithm as a default algorithm
Func GetDefaultAlgorithmName()
	Return $algorithms[0]
EndFunc

;Run the function that corresponds to the specified Algorithm name
;Use Null for $progressArea if not called from a GUI with a $progressArea
Func RunAlgorithm($selectedAlgorithm, $settings, ByRef $progressAreaRef)
	$progressArea = $progressAreaRef
	UpdateProgressArea("Analyzing: " & $settings[0])
	if ($selectedAlgorithm == $algorithms[2]) Then
		OneDataSourcePerFolder($settings)
	ElseIf ($selectedAlgorithm == $algorithms[0]) Then
		SingleDataSource($settings)
	ElseIf ($selectedAlgorithm == $algorithms[1]) Then
		SingleDataSource($settings)
;	ElseIf ($selectedAlgorithm == $algorithms[2]) Then
;		AllFilesInOneFolder($settings)
	EndIf
		UpdateProgressArea("-------------------------------------------------------------------------------------------") ;blank line for some
EndFunc

;Create a manifest file in the specified $caseDir named $manifestDir _Manifest.xml
;if the $manifestFile is specified the datasource included will be the file instead of the entire folder
Func GenerateCaseNameAndWriteManifestFile($caseDir, $subDirName, $manifestFile)
	Local $manifestName = ""
	Local $caseName = ""
	Local $dataSourcePath = ""
	;If the manifestDirectory is not Null use it for the file name
	if ($subDirName <> Null) Then
		$manifestName = $subDirName 
		$dataSourcePath = $manifestName 
		if ($manifestFile <> Null) Then
			$dataSourcePath = $dataSourcePath & "\" & $manifestFile
		EndIf
	;If the manifestDirectory was Null then use the file name 
	ElseIf ($manifestFile <> Null) Then
		$manifestName = $manifestFile
		$dataSourcePath = $manifestName 
	Else 
		UpdateProgressArea("ERROR: Invalid arguements provided, unable to create manifest file")
		Return
	EndIf
	
	Local $splitCaseDir = StringSplit($caseDir, "\", $STR_ENTIRESPLIT)
	$caseName = $splitCaseDir[$splitCaseDir[0]]
	
	Local $manfiestFilePath = $caseDir & "\" & $manifestName & "_" & $manifestFileNameEnd & $manifestExtension
	WriteManifestFile($manfiestFilePath, $manifestName, $caseName, $dataSourcePath)
EndFunc

;Write the specified manifest file.  
Func WriteManifestFile($manifestFilePath, $manifestName, $caseName, $dataSourcePath)
	_FileCreate($manifestFilePath)
	Local $fileHandle = FileOpen($manifestFilePath, $FO_APPEND)
	If $fileHandle == -1 Then
		UpdateProgressArea("ERROR: " & $manifestName & " Unable to create manifest file")
		Return
	EndIf
	FileWrite($fileHandle,'<?xml version="1.0" encoding="UTF-8" standalone="no"?>' & @CRLF)
	FileWrite($fileHandle,'<AutopsyManifest>' & @CRLF)
	FileWrite($fileHandle,'<CaseName>' & $caseName &'</CaseName>' & @CRLF)
	;Device ID is not a required field 
	FileWrite($fileHandle,'<DataSource>' & $dataSourcePath & '</DataSource>' & @CRLF)
	FileWrite($fileHandle,'</AutopsyManifest>' & @CRLF)
	FileClose($fileHandle)
	UpdateProgressArea($manifestName & " manifest created")
EndFunc

;get the extension of a file 
Func GetFileExtension($fileName)
	Local $fileExtension
	_PathSplit ($fileName, "", "", "", $fileExtension)
	Return $fileExtension
EndFunc

;Return 0 for false if no manifest files exist in the caseDir, or 1 for true if manifest files do exist
Func ManifestFilesAlreadyExist($fileList)
	Local $fileName 
	Local $fileExtension
	For $i = 1 To $fileList[0] Step 1
		_PathSplit ($fileList[$i], "", "", $fileName, $fileExtension)
		If StringCompare($fileExtension, $manifestExtension, $STR_NOCASESENSE) == 0 Then
			Local $splitFileName = StringSplit($fileName, "_", $STR_ENTIRESPLIT)
			if $splitFileName[0] > 1 Then ;It split into more than one chunk so the last chunk should match our _Manifest 
				If StringCompare($splitFileName[$splitFileName[0]], $manifestFileNameEnd, $STR_NOCASESENSE) == 0 Then
					UpdateProgressArea("Folder already contains manifest file: " & $fileList[$i])
					Return 1
				EndIf
			EndIf
		EndIf		
	Next
	Return 0
EndFunc

;Check if a manifest file already exists for a specific datasource in the case Dir
;Return 1 if a manifest exists 
;Return 0 if no manifest exists
Func ManifestAlreadyExists($manifestFilePath)
	If FileExists($manifestFilePath) == 1 Then
		Return 1
	Else
		Return 0
	EndIf
EndFunc


;Algorithm for the "One Data Source Per Folder" 
;Creates manifest files
Func OneDataSourcePerFolder($settings)
	Local $validDirectory = 1
    Local $caseDir = $settings[0]
	;_FileListToArray returns the count of files/folders as the first value then the contents
	Local $fileList = _FileListToArray($caseDir, Default, $FLTA_FILES, False)
	Local $caseDirSplit = StringSplit($caseDir, "\", $STR_ENTIRESPLIT)
	Local $caseDirName
	if ($caseDirSplit[0] > 1) Then
		;if case folder is longer than one directory display just the directory name in progress messages
		$caseDirName = $caseDirSplit[$caseDirSplit[0]]
	Else 
		;if there is only one directory use the entire case dir path
	EndIf	
	If (@error == 1)	Then
		$validDirectory = 0
		UpdateProgressArea("ERROR: " & $caseDirName & " not found")
		MsgBox($MB_OK, "Directory Not Found", "Selected directory " & $caseDirName & " was not found.")
	ElseIf (@error > 0) Then
		;An acceptable condition as no files means no manifest files
	EndIf

	Local $dirList = _FileListToArray($caseDir, Default, $FLTA_FOLDERS, True)
	If (@error ==4) Then
		UpdateProgressArea($caseDirName & " no folders found")
		MsgBox($MB_OK, "Selected Directory Empty", "Selected directory " & $caseDirName & " did not contain any subfolders to use as data sources for manifest files.")
		$validDirectory = 0
	EndIf
	
 	If $validDirectory = 1 Then
		Local $validExtensions[4] = [".e01", ".l01", ".001", ".ad1"]  ;valid extensions for the One Data Source Per Folder algorithm
		Local $subDirectoryFileList
		Local $validSubDirectory
		For $fileNumber = 1 TO $dirList[0] Step 1
			Local $manifestFile = Null
			Local $manifestDir = $dirList[$fileNumber]
			Local $splitManifestDir = StringSplit($manifestDir, "\", $STR_ENTIRESPLIT)
			Local $manifestDirName = $splitManifestDir[$splitManifestDir[0]]
			$subDirectoryFileList = _FileListToArray($dirList[$fileNumber], Default, Default, False)
			$validSubDirectory = 1
			If (@error == 1) Then
				$validSubDirectory = 0
				UpdateProgressArea("ERROR: " & $dirList[$fileNumber] & " not found")
			ElseIf (@error ==4) Then
				UpdateProgressArea($manifestDirName & " empty, no manifest created")
				$validSubDirectory = 0
			EndIf
			If $validSubDirectory == 1 Then
				For $i = 1 TO $subDirectoryFileList[0] Step 1
					Local $currentFilesExtension = GetFileExtension($subDirectoryFileList[$i])
					For $extension IN $validExtensions
						;should only be one file or directory in this folder since we checked the number of contents previously
						If StringCompare($extension, $currentFilesExtension, $STR_NOCASESENSE) == 0 Then
							$manifestFile = $subDirectoryFileList[$i]
							ExitLoop 2 ;match was found no reason to check remaining extensions or files in a One Data Source Per Folder algorithm
						EndIf
					Next
				Next
				Local $manifestFilePath = $caseDir & "\" & $manifestDirName & "_" & $manifestFileNameEnd & $manifestExtension
				If (ManifestAlreadyExists($manifestFilePath) <> 1) Then
					;should only be one file and it should end with a valid extension add as image file, or the whole directory is added as a logical file set
					GenerateCaseNameAndWriteManifestFile($caseDir, $manifestDirName, $manifestFile)
				Else 
					UpdateProgressArea($manifestDirName & " manifest exists, skipping")
				EndIf
			EndIf
		Next
		UpdateProgressArea($caseDirName & " manifest generation complete")
	EndIf
EndFunc

;Create a manifest file for a single data source in the same directory that contains the data source (also used for Folder of Logical Files)
Func SingleDataSource($settings)
	Local $dataSourcePath = $settings[0] 
	Local $caseDir = ""
	Local $caseDrive = ""
	Local $dsName = ""
	Local $dsExtension = ""
	_PathSplit ($dataSourcePath, $caseDrive, $caseDir, $dsName, $dsExtension)
	$caseDir = $caseDrive & $caseDir 
	Local $caseName = $settings[1]
	Local $manfiestFilePath = $caseDir & "\" & $dsName & "_" & $manifestFileNameEnd & $manifestExtension
	If (ManifestAlreadyExists($manfiestFilePath) <> 1) Then
		;should only be one file and it should end with a valid extension add as image file, or the whole directory is added as a logical file set
		WriteManifestFile($manfiestFilePath, $dsName, $caseName, $dsName & $dsExtension)
	Else 
		UpdateProgressArea($dsName & " manifest exists, skipping")
	EndIf
	
EndFunc

;Algorithm for the All Files in One Folder
;Creates manifest files for all files and directories in a single directory
Func AllFilesInOneFolder($settings)
	Local $validDirectory = 1
    Local $caseDir = $settings[0]
	;_FileListToArray returns the count of files/folders as the first value then the contents
	Local $fileList = _FileListToArray($caseDir, Default, $FLTA_FILES, False)
	If (@error == 1)	Then
		$validDirectory = 0
		UpdateProgressArea("Selected directory " & $caseDir & " was not found")
		MsgBox($MB_OK, "Directory Not Found", "Selected directory " & $caseDir & " was not found")
	ElseIf (@error > 0) Then
		Local $dirList = _FileListToArray($caseDir, Default, $FLTA_FOLDERS, True)
		If (@error ==4) Then
			UpdateProgressArea("Selected directory " & $caseDir & " was empty and contained nothing to generate manifest files for")
			MsgBox($MB_OK, "Selected Directory Empty", "Selected directory " & $caseDir & " was empty and contained nothing to generate manifest files for")
			$validDirectory = 0
		EndIf
		;An acceptable condition as no files means no manifest files
	ElseIf ManifestFilesAlreadyExist($fileList) == 1 Then  
		UpdateProgressArea("Selected directory " & $caseDir & " already contains manifest files, they must be deleted before generating new ones")
		MsgBox($MB_OK, "Manifest Files Exist", "Selected directory " & $caseDir & " already contains manifest files, they must be deleted before generating new ones")
		$validDirectory = 0
	EndIf
	Local $contentsList = _FileListToArray ($caseDir, Default, Default, False)
	If $validDirectory = 1 Then
		For $fileNumber = 1 TO $contentsList[0] Step 1
			Local $manifestDir = Null
			Local $manifestFile = $contentsList[$fileNumber]
			GenerateCaseNameAndWriteManifestFile($caseDir, $manifestDir, $manifestFile)
		Next
		UpdateProgressArea($caseDir & " manifest generation complete")
	EndIf
EndFunc

;If the progress area is Null it will not be updated
Func UpdateProgressArea($textToAdd)
    if ($progressArea <> Null) Then
		Local $currentProgressAreaText = GUICtrlRead($progressArea)
		$currentProgressAreaText = $currentProgressAreaText & @CRLF & "--" &  $textToAdd
		GUICtrlSetData($progressArea, $currentProgressAreaText)
		_GUICtrlEdit_Scroll($progressArea, $SB_SCROLLCARET)
	EndIf
EndFunc
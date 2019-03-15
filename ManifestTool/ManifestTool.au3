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
#include<ComboConstants.au3>
#include <EditConstants.au3>
#include<WindowsConstants.au3>
#include <ManifestGenerationAlgorithms.au3>


Opt("GUIOnEventMode", 1) ; Change to OnEvent mode
;==============================================
;
;Draw GUI and declare variables
;
;==============================================
local $windowHeight = 560
local $windowWidth = 460
local $windowTitle = "Autopsy Auto Ingest Manifest File Generator"
Global $hMainGUI = GUICreate($windowTitle, $windowWidth, $windowHeight) ;To make GUI resize add following args -1, -1, $WS_OVERLAPPEDWINDOW)
;GUICtrlSetResizing ($hMainGUI, $GUI_DOCKBORDERS)
GUISetOnEvent($GUI_EVENT_CLOSE, "CLOSEButton")

Global $propertiesFile = "ManifestTool.settings"
Global $workingDir = @WorkingDir

local $topMargin = 12
local $leftMargin = 12
local $labelOffset = 1
local $buttonOffset = -3
local $progressAreaInset = 8
local $distanceFromTop = $topMargin
local $distanceFromLeft = $leftMargin
Global $defaultDirectory = @MyDocumentsDir & "\"
local $labelWidth = 63
local $fieldWidth = 255
local $buttonWidth = 95
local $fieldHeight = 20
local $descriptionHeight = 50
local $progressAreaWidth = $windowWidth - 2*($progressAreaInset+$leftMargin)
local $gapBetweenWidth = 10
local $gapBetweenHeight = 10

;Draw the GUI Code
GUICtrlCreateLabel("Input", $distanceFromLeft, $distanceFromTop+$labelOffset)
$distanceFromLeft = $distanceFromLeft+$labelWidth+$gapBetweenWidth

Global $algorithmComboBox = GUICtrlCreateCombo(GetDefaultAlgorithmName(), $distanceFromLeft, $distanceFromTop, $fieldWidth, $fieldHeight, $CBS_DROPDOWNLIST)
GUICtrlSetOnEvent($algorithmComboBox, "Redraw")
Global $allAlgorithmNames = GetAlgorithmNames()
for $algorithmName IN $allAlgorithmNames
; Add additional items to the combobox.
	GUICtrlSetData($algorithmComboBox, $algorithmName)
Next

$distanceFromLeft = $leftMargin
$distanceFromTop = $distanceFromTop + $fieldHeight + $gapBetweenHeight

GUICtrlCreateLabel("Description", $distanceFromLeft, $distanceFromTop+$labelOffset)
$distanceFromLeft = $distanceFromLeft+$labelWidth+$gapBetweenWidth
;calculate height of progress area to use remaining space minus space for exit button
Global $descriptionArea = GUICtrlCreateEdit("", $distanceFromLeft, $distanceFromTop, $fieldWidth, $descriptionHeight, BitOr($ES_READONLY,$WS_VSCROLL, $ES_MULTILINE))

$distanceFromLeft = $leftMargin
$distanceFromTop = $distanceFromTop + $descriptionHeight + $gapBetweenHeight

Global $caseDirectoryLabel = GUICtrlCreateLabel("Case Directory", $distanceFromLeft, $distanceFromTop+$labelOffset)
$distanceFromLeft = $distanceFromLeft+$labelWidth+$gapBetweenWidth
Global $rootFolderField = GUICtrlCreateInput("", $distanceFromLeft, $distanceFromTop, $fieldWidth, $fieldHeight)
$distanceFromLeft = $distanceFromLeft +$fieldWidth+$gapBetweenWidth
Global $browseButton = GUICtrlCreateButton("Browse", $distanceFromLeft, $distanceFromTop+$buttonOffset, $buttonWidth)
$distanceFromLeft = $leftMargin
$distanceFromTop = $distanceFromTop + $fieldHeight + $gapBetweenHeight

Global $caseNameLabel = GUICtrlCreateLabel("Case Name", $distanceFromLeft, $distanceFromTop+$labelOffset)
$distanceFromLeft = $distanceFromLeft+$labelWidth+$gapBetweenWidth 
Global $caseNameField = GUICtrlCreateInput("", $distanceFromLeft, $distanceFromTop, $fieldWidth, $fieldHeight)
$distanceFromLeft = $distanceFromLeft +$fieldWidth+$gapBetweenWidth
$distanceFromTop = $distanceFromTop + $fieldHeight + $gapBetweenHeight

$distanceFromTop = $distanceFromTop + $gapBetweenHeight ;add an extra gap before Generate Manifest button
Global $generateManifestButton = GUICtrlCreateButton("Generate Manifest", $distanceFromLeft, $distanceFromTop+$buttonOffset, $buttonWidth)
GUICtrlSetOnEvent($generateManifestButton, "AlgorithmGenerateManifestAction")
$distanceFromTop = $distanceFromTop + $fieldHeight + $gapBetweenHeight
$distanceFromLeft = $leftMargin

$distanceFromTop = $distanceFromTop + $fieldHeight + $gapBetweenHeight ;add extra gap before progress area
local $ProgressLabel = GUICtrlCreateLabel("Progress", $distanceFromLeft, $distanceFromTop+$labelOffset)
$distanceFromTop = $distanceFromTop + $fieldHeight + $gapBetweenHeight

$distanceFromLeft = $distanceFromLeft + $progressAreaInset
$progressAreaHeight = $windowHeight -$distanceFromTop - $gapBetweenHeight - $gapBetweenHeight - $fieldHeight ;calculate height of progress area to use remaining space minus space for exit button
Global $progressField = GUICtrlCreateEdit("", $distanceFromLeft, $distanceFromTop, $progressAreaWidth, $progressAreaHeight, BitOr($ES_READONLY,$WS_VSCROLL, $ES_MULTILINE))

$distanceFromLeft = $distanceFromLeft + $progressAreaWidth - $buttonWidth
$distanceFromTop = $distanceFromTop + $progressAreaHeight + $gapBetweenHeight
Local $exitButton = GUICtrlCreateButton("Exit", $distanceFromLeft, $distanceFromTop+$buttonOffset, $buttonWidth)
GUICtrlSetOnEvent($exitButton, "CLOSEButton")


GUISetOnEvent($GUI_EVENT_CLOSE, "CLOSEButton")
GUISwitch($hMainGUI)
GUISetState(@SW_SHOW)
ChangeToDefaultGUI()

ReadPropertiesFile()

Local $oldCaseName = GUICtrlRead($caseNameField)
local $oldRootFolder = GUICtrlRead($rootFolderField)
While 1
    Sleep(100) ; Sleep to reduce CPU usage
	ValidateFields($oldCaseName, $oldRootFolder) ;validate here so that we check the current value of any input areas without requiring a change in focus
	$oldCaseName = GUICtrlRead($caseNameField)
	$oldRootFolder = GUICtrlRead($rootFolderField)
WEnd


;==============================================
;
;Functions
;
;==============================================

; Read the saved properties file, if none exist make one with the current settings
Func ReadPropertiesFile()
If FileExists($propertiesFile) <> 1 Then
	FileChangeDir($workingDir)
	_FileCreate($propertiesFile)
	WritePropertiesFile()
Endif
	Local $propertiesFileHandle = FileOpen($propertiesFile, $FO_READ)
	Local $savedSelection = FileReadLine($propertiesFileHandle, 1)
	Local $indexOfSelection = _ArraySearch($allAlgorithmNames, $savedSelection)
	if ($indexOfSelection >= 0) Then
		GUICtrlSetData($algorithmComboBox, $savedSelection, $savedSelection)
	EndIf
	Local $savedDirectory = FileReadLine($propertiesFileHandle, 2)
	if (FileExists($savedDirectory)) Then
		$defaultDirectory = $savedDirectory
	EndIf
	FileClose($propertiesFileHandle)
	Redraw()
EndFunc

; Write the current settings to the properties file
Func WritePropertiesFile()
	FileChangeDir($workingDir)
	Local $propertiesFileHandle = FileOpen($propertiesFile, $FO_OVERWRITE)
	If $propertiesFileHandle == -1 Then  ;can't access the properties file so exit
		Return
	EndIf
	FileWrite($propertiesFileHandle, GUICtrlRead($algorithmComboBox) & @CRLF)
	FileWrite($propertiesFileHandle, $defaultDirectory & @CRLF)
	FileClose($propertiesFileHandle) 
EndFunc



;Make only the settings and labels relevent to the selected Algorithm visible using $GUI_SHOW and $GUI_HIDE
Func Redraw()
    ; Note: At this point @GUI_CtrlId would equal algorithmComboBox
	Local $selectedAlgName = GUICtrlRead($algorithmComboBox)
	;Move controls based on what is hidden or shown using ControlGetPos() and GUICtrlSetPos()
	If  $selectedAlgName == $allAlgorithmNames[2] Then ;"One Data Source Per Folder"
		ChangeToDefaultGUI()
		GUICtrlSetData($descriptionArea, GetAlgorithmDescription(2))
	ElseIf $selectedAlgName == $allAlgorithmNames[0] Then ;"Single Data Source"
		ChangeToSingleDataSourceGUI()
		GUICtrlSetData($descriptionArea, GetAlgorithmDescription(0))
	ElseIf $selectedAlgName == $allAlgorithmNames[1] Then ;"Folder of Logical Files"
		ChangeToFolderOfLogicalFilesGUI()
		GUICtrlSetData($descriptionArea, GetAlgorithmDescription(1))
	EndIf
EndFunc   ;==>AlgorithmComboBox

;Change the controls displayed in the GUI to the ones needed for the Single Data Source algorithm
Func ChangeToSingleDataSourceGUI()
	ClearFields()
	GUICtrlSetData($caseDirectoryLabel, "Data Source")
	GUICtrlSetState($caseNameField, $GUI_SHOW)
	GUICtrlSetState($caseNameLabel, $GUI_SHOW)
	GUICtrlSetOnEvent($browseButton, "BrowseForDataSourceFile")
	GUICtrlSetState($generateManifestButton, $GUI_DISABLE)

EndFunc 

;Change the controls displayed in the GUI to the ones needed for the Folder of Logical Files algorithm
Func ChangeToFolderOfLogicalFilesGUI()
	ClearFields()
	GUICtrlSetData($caseDirectoryLabel, "Data Source")
	GUICtrlSetData($caseDirectoryLabel, "Data Source")
	GUICtrlSetState($caseNameField, $GUI_SHOW)
	GUICtrlSetState($caseNameLabel, $GUI_SHOW)
	GUICtrlSetOnEvent($browseButton, "Browse")
	GUICtrlSetState($generateManifestButton, $GUI_DISABLE)
EndFunc 

;Change the controls displayed in the GUI to the ones needed for One Data Source Per Folder
Func ChangeToDefaultGUI()
	ClearFields()
	GUICtrlSetData($caseDirectoryLabel, "Case Directory")
	GUICtrlSetState($rootFolderField, $GUI_SHOW)
	GUICtrlSetState($caseDirectoryLabel, $GUI_SHOW)
	GUICtrlSetState($caseNameField, $GUI_HIDE)
	GUICtrlSetState($caseNameLabel, $GUI_HIDE)
	GUICtrlSetOnEvent($browseButton, "Browse")
	;rename to RootDirectory to root directory
	;hide case name field
	GUICtrlSetState($generateManifestButton, $GUI_DISABLE)
EndFunc

;ensure that all fields for the selected algorithm are valid
Func ValidateFields($oldCaseName, $oldRootFolder)
	Local $dataSourcePath = GUICtrlRead($rootFolderField)
	Local $caseName =  GUICtrlRead($caseNameField)
	if ($dataSourcePath <> $oldRootFolder Or $caseName <> $oldCaseName) Then
		Local $selectedAlgName = GUICtrlRead($algorithmComboBox)
		If  $selectedAlgName == $allAlgorithmNames[2] Then ;"One Data Source Per Folder"
			ValidateDefaultFields($dataSourcePath)
		ElseIf $selectedAlgName == $allAlgorithmNames[0] Then ;"Single Data Source"
			ValidateSingleDataSourceFields($dataSourcePath, $caseName)
		ElseIf $selectedAlgName == $allAlgorithmNames[1] Then ;"Folder of Logical Files"
			ValidateSingleDataSourceFields($dataSourcePath, $caseName)
		EndIf
	EndIf
EndFunc

;ensure that the settings for the default algorithm are valid before enabling it 
Func ValidateDefaultFields($rootFolderPath)
	if ($rootFolderPath <> "" And FileExists($rootFolderPath)) Then
		GUICtrlSetState($generateManifestButton, $GUI_ENABLE)			
	Else
		GUICtrlSetState($generateManifestButton, $GUI_DISABLE)	
	EndIf
EndFunc

;ensure that the settings for the Single Data Source and Folder of Logical Files algorithms are valid 
Func ValidateSingleDataSourceFields($dataSourcePath, $caseName)
	if ($dataSourcePath <> "" And FileExists($dataSourcePath) And $caseName <> "") Then
		GUICtrlSetState($generateManifestButton, $GUI_ENABLE)
	Else		
		GUICtrlSetState($generateManifestButton, $GUI_DISABLE)	
	EndIf	
EndFunc

;clear all input fields, and reset them to an empty string
Func ClearFields()
	GUICtrlSetData($rootFolderField, "")
	GUICtrlSetData($caseNameField, "")
EndFunc

;Open a directory chooser
Func Browse()
    ; Note: At this point @GUI_CtrlId would equal $browseButton
	GUICtrlSetState($browseButton, $GUI_DISABLE)
	Local $selectedDirectory = FileSelectFolder("Select Folder", $defaultDirectory)
	Local $caseDir = ""
	Local $caseDrive = ""
	If (FileExists($selectedDirectory)) Then
		_PathSplit($selectedDirectory, $caseDrive, $caseDir, "", "")
		$defaultDirectory  = $caseDrive & $caseDir 
		GUICtrlSetData($rootFolderField, $selectedDirectory)
	EndIf
	If  GUICtrlRead($algorithmComboBox) == $allAlgorithmNames[2] Then ;"One Data Source Per Folder"
		If ($selectedDirectory == $defaultDirectory) Then ;Don't allow root drives as selected directory for this algorithm
			MsgBox(0, "Invalid Case Directory", "The directory is used to determine the case name and can not be the root directory of a disk.")
			GUICtrlSetData($rootFolderField, "")
		EndIf
	EndIf
	GUICtrlSetState($caseNameField, $GUI_FOCUS)
	GUICtrlSetState($browseButton, $GUI_ENABLE)
EndFunc   ;==>BrowseButton

; Open a file chooser
Func BrowseForDataSourceFile()
    ; Note: At this point @GUI_CtrlId would equal $browseButton
	GUICtrlSetState($browseButton, $GUI_DISABLE)	
	Local $selectedDataSource = FileOpenDialog("Select Data Source", $defaultDirectory, "All Supported Types (*.img; *.dd; *.001; *.aa; *.raw; *.bin; *.E01; *.vmdk; *.vhd) |Raw Images (*.img; *.dd; *.001; *.aa; *.raw; *.bin) |Encase Images (*.E01) |Virtual Machines (*.vmdk; *.vhd) |Logical Evidence File (*.L01) |All Files (*.*)", $FD_FILEMUSTEXIST)
	Local $caseDir = ""
	Local $caseDrive = ""
	If (FileExists($selectedDataSource)) Then
		_PathSplit ($selectedDataSource, $caseDrive, $caseDir, "", "")
		$defaultDirectory  = $caseDrive & $caseDir 
		GUICtrlSetData($rootFolderField, $selectedDataSource)
	EndIf
	GUICtrlSetState($caseNameField, $GUI_FOCUS)
	GUICtrlSetState($browseButton, $GUI_ENABLE)
EndFunc

;Perform the action associated with the generate manifest button which should be defined in ManifestGenerationAlgorithms.au3
Func AlgorithmGenerateManifestAction()
    ; Note: At this point @GUI_CtrlId would equal $generateManifestButton
	GUICtrlSetState($generateManifestButton, $GUI_DISABLE)
	RunAlgorithm(GUICtrlRead($algorithmComboBox), GetSettings(), $progressField)
	GUICtrlSetState($generateManifestButton, $GUI_ENABLE)
EndFunc   ;==>GenerateManifestButton

;Get an array of settings as they are set on this panel
Func GetSettings()
	Local $settings[2]
	$settings[0] =  GUICtrlRead($rootFolderField)
	$settings[1] = GUICtrlRead($caseNameField)
	Return $settings
EndFunc 

;Close the tool
Func CLOSEButton()
    ; Note: at this point @GUI_CtrlId would equal $GUI_EVENT_CLOSE,
    ; @GUI_WinHandle will be either $hMainGUI or $hDummyGUI
	GUICtrlSetState($exitButton, $GUI_DISABLE)
    If @GUI_WinHandle = $hMainGUI Then
		WritePropertiesFile()
		Exit
    EndIf
	GUICtrlSetState($exitButton, $GUI_ENABLE)
EndFunc   ;==>CLOSEButton 
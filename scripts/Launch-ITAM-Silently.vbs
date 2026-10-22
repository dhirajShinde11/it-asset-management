' ==============================================================================
' BMC AssetHub - Silent VBScript Wrapper
' Launches the PowerShell background runner with ZERO console popups or flashes
' ==============================================================================

Set objFSO = CreateObject("Scripting.FileSystemObject")
strScriptDir = objFSO.GetParentFolderName(WScript.ScriptFullName)
strProjectRoot = objFSO.GetParentFolderName(strScriptDir)

strPsScript = """" & strScriptDir & "\run-itam-background.ps1"""
strCommand = "powershell.exe -ExecutionPolicy Bypass -WindowStyle Hidden -NoProfile -File " & strPsScript

Set objShell = CreateObject("WScript.Shell")
' 0 = vbHide (completely hidden window)
objShell.CurrentDirectory = strProjectRoot
objShell.Run strCommand, 0, False

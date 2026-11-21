; MochiLedger Windows NSIS Installer Script (Modern UI 2, Unicode)
Unicode True
SetCompressor /SOLID lzma

!include "MUI2.nsh"
!include "x64.nsh"

!ifndef APP_VERSION
  !define APP_VERSION "1.0.1"
!endif
!ifndef SOURCE_DIR
  !define SOURCE_DIR "..\..\build\compose\binaries\main\app\MochiLedger"
!endif
!ifndef OUTPUT_FILE
  !define OUTPUT_FILE "..\..\..\MochiLedger-windows-x64-setup.exe"
!endif

!define APP_NAME "MochiLedger"
!define APP_DISPLAY_NAME "墨记 MochiLedger"
!define APP_PUBLISHER "ackd7"
!define APP_URL "https://github.com/ackd7/MochiLedger"
!define APP_EXE "MochiLedger.exe"
!define UNINSTALL_REG_KEY "Software\Microsoft\Windows\CurrentVersion\Uninstall\${APP_NAME}"

Name "${APP_DISPLAY_NAME} ${APP_VERSION}"
OutFile "${OUTPUT_FILE}"
InstallDir "$PROGRAMFILES64\${APP_NAME}"
InstallDirRegKey HKLM "${UNINSTALL_REG_KEY}" "InstallLocation"
RequestExecutionLevel admin

; Modern UI configuration
!define MUI_ABORTWARNING
!define MUI_FINISHPAGE_RUN "$INSTDIR\${APP_EXE}"
!define MUI_FINISHPAGE_RUN_TEXT "立即启动 ${APP_DISPLAY_NAME}"

; Installer pages
!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_LICENSE "..\..\..\LICENSE"
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH

; Uninstaller pages
!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES

; Languages (Simplified Chinese default + English)
!insertmacro MUI_LANGUAGE "SimpChinese"
!insertmacro MUI_LANGUAGE "English"

Section "主程序 (必需)" SecMain
  SectionIn RO
  SetOutPath "$INSTDIR"

  ; Copy all files produced by Compose Desktop createDistributable
  File /r "${SOURCE_DIR}\*.*"

  ; Create Uninstaller
  WriteUninstaller "$INSTDIR\Uninstall.exe"

  ; Register in Windows Apps & Features / Control Panel
  WriteRegStr HKLM "${UNINSTALL_REG_KEY}" "DisplayName" "${APP_DISPLAY_NAME}"
  WriteRegStr HKLM "${UNINSTALL_REG_KEY}" "DisplayVersion" "${APP_VERSION}"
  WriteRegStr HKLM "${UNINSTALL_REG_KEY}" "Publisher" "${APP_PUBLISHER}"
  WriteRegStr HKLM "${UNINSTALL_REG_KEY}" "URLInfoAbout" "${APP_URL}"
  WriteRegStr HKLM "${UNINSTALL_REG_KEY}" "InstallLocation" "$INSTDIR"
  WriteRegStr HKLM "${UNINSTALL_REG_KEY}" "DisplayIcon" "$INSTDIR\${APP_EXE}"
  WriteRegStr HKLM "${UNINSTALL_REG_KEY}" "UninstallString" '"$INSTDIR\Uninstall.exe"'
  WriteRegDWORD HKLM "${UNINSTALL_REG_KEY}" "NoModify" 1
  WriteRegDWORD HKLM "${UNINSTALL_REG_KEY}" "NoRepair" 1

  ; Start Menu Shortcuts
  CreateDirectory "$SMPROGRAMS\${APP_DISPLAY_NAME}"
  CreateShortCut "$SMPROGRAMS\${APP_DISPLAY_NAME}\${APP_DISPLAY_NAME}.lnk" "$INSTDIR\${APP_EXE}"
  CreateShortCut "$SMPROGRAMS\${APP_DISPLAY_NAME}\卸载 ${APP_DISPLAY_NAME}.lnk" "$INSTDIR\Uninstall.exe"
SectionEnd

Section "创建桌面快捷方式" SecDesktop
  CreateShortCut "$DESKTOP\${APP_DISPLAY_NAME}.lnk" "$INSTDIR\${APP_EXE}"
SectionEnd

Section "Uninstall"
  ; Remove shortcuts
  Delete "$DESKTOP\${APP_DISPLAY_NAME}.lnk"
  Delete "$SMPROGRAMS\${APP_DISPLAY_NAME}\${APP_DISPLAY_NAME}.lnk"
  Delete "$SMPROGRAMS\${APP_DISPLAY_NAME}\卸载 ${APP_DISPLAY_NAME}.lnk"
  RMDir "$SMPROGRAMS\${APP_DISPLAY_NAME}"

  ; Remove installed files (user database in %APPDATA%\MochiLedger is preserved)
  RMDir /r "$INSTDIR"

  ; Remove uninstall registry entry
  DeleteRegKey HKLM "${UNINSTALL_REG_KEY}"
SectionEnd

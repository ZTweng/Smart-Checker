#define AppName "Smart Checker"
#define AppVersion "6.26.3"

[Setup]
AppId={{FE019F7E-FAF8-4BB6-96A7-40F9939CCF37}
AppName={#AppName}
AppVersion={#AppVersion}
AppPublisher=广州大学团委学生社团管理中心
VersionInfoCompany=广州大学团委学生社团管理中心
VersionInfoVersion=6.26.3.0
VersionInfoProductVersion=6.26.3
DefaultDirName={localappdata}\Programs\{#AppName}
DefaultGroupName={#AppName}
DisableDirPage=no
DisableProgramGroupPage=yes
AppendDefaultDirName=yes
UsePreviousAppDir=yes
PrivilegesRequired=lowest
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
OutputDir=..\releases\windows
OutputBaseFilename=Smart Checker-{#AppVersion}-Setup
SetupIconFile=icons\smart-checker.ico
UninstallDisplayIcon={app}\smart-checker-original.ico
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
CloseApplications=yes
Uninstallable=yes

[Files]
Source: "..\desktopApp\build\compose\binaries\main\app\Smart Checker\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs
Source: "icons\smart-checker.ico"; DestDir: "{app}"; DestName: "smart-checker-original.ico"; Flags: ignoreversion

[Icons]
Name: "{group}\{#AppName}"; Filename: "{app}\Smart Checker.exe"; IconFilename: "{app}\smart-checker-original.ico"
Name: "{autodesktop}\{#AppName}"; Filename: "{app}\Smart Checker.exe"; IconFilename: "{app}\smart-checker-original.ico"; Tasks: desktopicon

[Tasks]
Name: desktopicon; Description: "Create a desktop shortcut"

[Run]
Filename: "{app}\Smart Checker.exe"; Description: "Launch {#AppName}"; Flags: nowait postinstall skipifsilent

[Code]
function WithAppFolder(Path: String): String;
begin
  Path := RemoveBackslashUnlessRoot(Trim(Path));
  if CompareText(ExtractFileName(RemoveBackslashUnlessRoot(Path)), '{#AppName}') <> 0 then
    Path := AddBackslash(Path) + '{#AppName}';
  Result := Path;
end;

function NextButtonClick(CurPageID: Integer): Boolean;
begin
  if CurPageID = wpSelectDir then
    WizardForm.DirEdit.Text := WithAppFolder(WizardForm.DirEdit.Text);
  Result := True;
end;

function PrepareToInstall(var NeedsRestart: Boolean): String;
begin
  { Also normalize silent installs that supply /DIR directly. }
  WizardForm.DirEdit.Text := WithAppFolder(WizardForm.DirEdit.Text);
  Result := '';
end;

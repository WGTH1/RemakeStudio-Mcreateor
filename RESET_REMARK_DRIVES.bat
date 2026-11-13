@echo off
setlocal

echo Cleaning temporary Remark Studio drive mappings...
echo.

for %%D in (R S T U V W X Y Z Q P O N M L K J I H G F E D) do (
  if exist "%%D:\RUN_REMARK_STUDIO.bat" if exist "%%D:\gradlew.bat" (
    echo Removing %%D:
    subst %%D: /d >nul 2>&1
  )
)

echo.
echo Done. You can run RUN_REMARK_STUDIO.bat again now.
pause

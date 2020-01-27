@echo off
set "OUTPUT=.\EMBS_Open_Assessment_1_Submission"

if exist %OUTPUT% (
    rmdir /S /Q %OUTPUT%
)

mkdir %OUTPUT%
mkdir %OUTPUT%\Question2
mkdir %OUTPUT%\Question3

echo. >> %OUTPUT%\contents.txt

echo Report: .\EMBS_Report.pdf >> %OUTPUT%\contents.txt

echo. >> %OUTPUT%\contents.txt

echo Question 2 Files: >> %OUTPUT%\contents.txt
echo   .\Question2\Source.java >> %OUTPUT%\contents.txt
copy .\EMBS_Q2\src\embs\Source.java %OUTPUT%\Question2\Source.java

echo. >> %OUTPUT%\contents.txt

echo Question 3 Files: >> %OUTPUT%\contents.txt
for %%F in (".\EMBS_Q3\src\*.java") do echo   .\Question3\%%~nxF >> %OUTPUT%\contents.txt
copy .\EMBS_Q3\src\*.java %OUTPUT%\Question3\
echo   .\Question3\updated_model.xml >> %OUTPUT%\contents.txt
copy .\EMBS_Q3\updated_model.xml %OUTPUT%\Question3\updated_model.xml

echo. >> %OUTPUT%\contents.txt

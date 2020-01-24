@echo off
set "OUTPUT=.\EMBS_Open_Assessment_1_Submission"

if exist %OUTPUT% (
    rmdir /S %OUTPUT%
)

mkdir %OUTPUT%
mkdir %OUTPUT%\Question2
mkdir %OUTPUT%\Question3

copy .\EMBS_Q2\src\embs\Source.java %OUTPUT%\Question2\Source.java

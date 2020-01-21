set "MRC=C:\Apps\moterunner\win32\bin\mrc.exe"

if not exist %MRC% (
  set "MRC=C:\Moterunner\moterunner\win32\bin\mrc.exe"
)

%MRC% --verbose --assembly=Source-1.0 embs\Source.java -r:logger-11.0
%MRC% --verbose --assembly=SinkA-1.0 embs\SinkA.java -r:logger-11.0
%MRC% --verbose --assembly=SinkB-1.0 embs\SinkB.java -r:logger-11.0
%MRC% --verbose --assembly=SinkC-1.0 embs\SinkC.java -r:logger-11.0

set "MRC=C:\Apps\moterunner\win32\bin\mrc.exe"

if not exist %MRC% (
  set "MRC=C:\Moterunner\moterunner\win32\bin\mrc.exe"
)

%MRC% --verbose --assembly=Source-1.0 embs\Source.java -r:logger-11.0
rem %MRC% --verbose --assembly=Sink-1.0 embs\Sink.java -r:logger-11.0
rem %MRC% --verbose --assembly=Sink2-1.0 embs\Sink.java -r:logger-11.0

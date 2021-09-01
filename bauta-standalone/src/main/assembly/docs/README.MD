How to use:
Run script and put desired directory as input. 
If the directory does not exist, the required directories will be created and put as input arguments on the .jar file. 
Using relative path will set bauta_home under the bin folder.
Bauta will start at http://localhost:8080/ui. Thereafter you need to create a job and a script.
If you want an example of jobs and scripts, you can find them in the "examples" directory.
Then execute the job by pressing play in the GUI. 


Windows:
Run startBauta.bat and put your desired bauta_home directory as input argument.
Relative path: startBauta.bat bauta_home 
Absolute path: startBauta.bat C:\Users\me\bauta_home 


Mac/Linux:
Run startBauta.sh and put your desired bauta_home directory as input argument of flag "-h". You need to use sudo.
Relative path example: sudo bash startBauta.sh -h bauta_home
Absolute path example: sudo bash startBauta.sh -h /Users/me/Desktop/Standalone-Bauta
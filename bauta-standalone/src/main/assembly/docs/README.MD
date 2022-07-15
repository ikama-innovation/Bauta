How to use:
Run the script and put desired directory as input. 
If the directory does not exist, the required directories will be created and put as input arguments on the .jar file. 
It is recommended to use the prepared /home folder adjacent to the bin directory by using relative path.
Bauta will start at http://localhost:8080/ui. Thereafter you need to create a job and a script.
You have a few examples of jobs and scripts, more can be created by putting them into the respective /jobs and /scripts folder under your bauta_home directory
Then execute the job by pressing play in the GUI.

Windows:
Run startBauta.bat and put your desired bauta_home directory as input argument. 
If no argument is provided, bauta_home is set to the /home folder adjacent to /bin. 
Relative path: startBauta.bat home 
Absolute path: startBauta.bat C:\Users\me\bauta_home 


Mac/Linux:
Run startBauta.sh and put your desired bauta_home directory as input argument of flag "-h". You need to use sudo.
Relative path example: sudo bash startBauta.sh -h home
Absolute path example: sudo bash startBauta.sh -h /Users/me/Desktop/Standalone-Bauta


Docker:
When using an image of bauta, you need to bind a local directory to the container-directory /opt/bauta/home.
For more info on volume binding, see Docker documentation.


Links:
https://github.com/ikama-innovation/Bauta
https://hub.docker.com/repository/docker/ikamainnovation/bauta/
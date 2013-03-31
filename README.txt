CS5300 Assignment 1b
Eric Swidler(ejs296), Alicia Chui (awc64), and Jessica Kane (jlk283)


SOURCE FILES
============
LargeScaleInfoA3.java - handles client requests and sending RPC calls
RPCPRotocol.java - defines RPC methods


DEPLOYMENT LOCATION
============
http://cs5300-assignment1b-env-mvuuanm2jf.elasticbeanstalk.com/index.html


Cookie Format:
=============
The cookie stores, in this exact order: 
    sessionID, version number, IPP Primary, and IPP backup. 

Elements are separated by an underscore "_"

IPP's have the format: IPaddr_UDPport
sessionID has the format: sessnum_IPPlocal

Cookies are parsed by the private method parseCookieValue(String cookieVal),
which returns a ConcurrentHashMap where the keys are "sessionID", "version",
"IPP_1", and "IPP_2". The method has been designed to support cookie values 
that had more than 2 IPP's (the keys would automatically generated to be 
IPP_3, IPP_4, etc.) 


Getting Session State
===================
Getting the Session State is done in the beginning of the method handleCommand.
The local server first checks to see if IPP Primary or IPP Backup is equal
to IPP local. If it is, then it uses the values in its local sessionTable.
Otherwise, we send a sessionReadClient call to IPP Primary. If IPP Primary
doesn't respond, then we send a sessionReadClient to IPP backup. If 
IPP backup doesn't respond, then we print an error message on the html page. 
If either IPP Primary or IPP backup respond, we take the returned value
and parse it to get the foundVersion, and the data (message). If the 
foundVersion is greater than the version found in the cookie, then we use
the newer version. Otherwise, we use version number found in the cookie, 
and the data submitted in the form. We record whether we used the local
sessionTable, IPP Primary, or IPP backup in the variable "choice", which
is stored in the sessionTable under the key "choice". 


Storing Session State
==================
Storing the session state occurs in the the method handleCommand. Throughout
the method, the local server is saving the new version number, expiration time,
discard time, and message into its sessionTable. Then at the end of the
handleCommand method, we send a sessionWriteClient call to a random AS in the 
local server's member-set. To generate a random AS, we create an ArrayList of 
1,2,..., n where n is the number of members in the memberset. We then shuffle
these numbers to get a random ordering of the members. We start at the beginning
of this random ordering and send a sessionWriteClient. If the call timesout, 
then we use the next member in the random ordering. After we get a successful
sessionWriteClient call, then this AS becomes our new IPP backup. The cookie
that is attached to the response is <sessionID, new_version, IPPLocal, IPP_newBackup> . 


Garbage Colletion
===============
Garbage collection is done by the thread janitorThread, which is an instance 
of our private class GarbageCollector. This thread goes through the sessionTable
and checks for any sessions that have discard_times less than the current time. 
If such sessions are found, they are removed from the sessionTable. 


MemberSet (mbrSet)
================
A memberset is represented by an ArrayList of members, each of which has their 
IP address and port number recorded in a Concurrent Hashmap (keys are "ip" and
"port"). Members found in the cookieData are added to the memberSet (this is 
done in the method handleCookie in LArgeScaleInfoA3.java. In RPCProtocol.java,
members are added (*********TODO************)


Crash Button
===========
(*******TODO*******)


RPC method design
=================
(*******TODO*******)


Extra Credit
==================
(*******TODO*******)


Elastic Beanstalk Documentation
===========================
For our current deployment, the environment is CS5300_Assignment1b_Env, and CS5300_Assignment1b_App. The application can be accessed at http://cs5300-assignment1b-env-mvuuanm2jf.elasticbeanstalk.com/index.html . The environment is set up to be 19-k fault tolerant. Once a server is detected as down (which could be caused by hitting the crash button), the server is replaced by the Elastic Beanstalk auto scaler. 

The application is uploaded to AWS Elastic Beanstalk, by a war file or Eclipse git push. The following settings are changed from the default values via. altering the environment configuration on the console:

Health Check
---------------------------
Application Health Check URL: /index.html
Health Check Interval: 5
Health Check Timeout: 2
Healthy Check Count Threshold: 2
Unhealthy Check Count Threshold: 2

Auto Scaling
---------------------------
Minimum Instance Count: 20
Maximum Instance Count: 24

Scaling Trigger
---------------------------
Trigger Measurement: UnhealthyHostCount
Trigger Statistic: Minimum
Unit of Measurement: Count
Measurement Period: 1
Breach Duration: 1
Upper Threshold: 1
Upper Breach Scale Increment: 1
Lower Threshold: 0
Lower Breach Scale Increment: -1

And, lastly, the auto-scaler needs to use the Elastic Beanstalk definition of a server crash instead of the EC2 definition of the server crash. (ELB dictates a crash as failing a health check, while EC2 only defines a crash as not running.) This can only be changed through the AWS command line interface, using the command: 

as-update-auto-scaling-group MY_GROUP_NAME --health-check-type ELB  --grace-period 60   

In our case, it's:

as-update-auto-scaling-group awseb-e-xkefv25gxi-stack-AWSEBAutoScalingGroup-1VIJWCYPBJBMB --health-check-type ELB  --grace-period 60 



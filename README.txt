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
every RPC communication recieved by a server identifies the serverID of the client based on the request's origin IP and its UDP server port embedded in its communication.  There is then a check for whether this identified serverID exists in the memberSet, and if it doesn't then it is added due to direct evidence of its existence.  Members that have attempted contacts which timeout in the RPC methods cause the member to be removed from the mbrSet.


Crash Button
===========
The crash button sends a "crash" command to the proceeding AS that the load balancer serves.  The AS that recieves the simulated crash displays its serverID in HTML form to indicitate which server has crashed.  This AS then closes its UDP listening port (the RPC server) and a boolean flag is triggered where all subsequent HTTP GET requests are served with an HTTP error.  Specifically, the error is a SC_EXPECTATION_FAILED which simulates the server running into a exception occuring on the AS.  This server will then neither serve HTTP nor RPC requests on its previously open ports.


RPC method design
=================
The methods for RPC communication is divided into client side and server side methods.  The four main RPC methods were implemented (SessionRead, SessionWrite, SessionDelete, and GetMembers).  The RPCProtocol java file is roughly divided between the first half devoted to client side communication and the second half with RPC listening server methods.  RPC methods are abstracted and generalized on the client and server end for most of the communication construction and sending, but each side has specific RPC methods recognized according to operations codes in the communication.  This operation code specifies how to handle the data in the communication.  Call ids are used in the RPC client to serialize and identify responses from RPC servers.  Communications that timeout or cause an I/O error prompt the client to remove the corresponding member who it was trying to communicate with from the mbrSet.


Extra Credit
==================
We implemented the accelerated Group Membership Protocol for quickly getting an initialized server up to speed with its mbrSet.  This is performed in the handleCommand method after it's been established where the session's most up to date version is from.  If a server has 2 or fewer members in its memberset and recieves a request containing a cookie with the most up to date session info on an IPPprimary and secondary that is not local, it performs a getMembers() call.  The IPPPrimary (or secondary if the first times out) responds with a list of its own mbrSet for this local server to add to its own.  The max size of the requested set is 20, which is expected to be small enough to be sent over a single packet.  This will allow the new server to more quickly expand its mbrSet size and increase its knowledge of other live servers.


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



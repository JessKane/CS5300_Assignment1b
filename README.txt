CS5300_Assignment1b
===================

SOURCE FILES
============
LargeScaleInfoA3.java - handles client requests and sending RPC calls
RPCPRotocol.java - defines RPC methods

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
(*******TODO*********)






package server;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.concurrent.ConcurrentHashMap;

public class RPCProtocol {
	

	
	//Hashtable of sessionIDs to a table of information on their message, location, and expiration data.
	ConcurrentHashMap<String, ConcurrentHashMap<String, String>> sessionTable = new ConcurrentHashMap<String, ConcurrentHashMap<String, String>>();
	
	//Set of known servers, their ips and corresponding listening ports
	ArrayList<ConcurrentHashMap<String, String>> mbrSet = new ArrayList<ConcurrentHashMap<String, String>>();
	
	//Deliminator, using pound since underscore is used in location tracking for sessions
	String delim = "#";
	
	//Call id number tracker
	private static int callID_num = 0;
		
	public RPCProtocol(ConcurrentHashMap<String, ConcurrentHashMap<String, String>> sessionTable, ArrayList<ConcurrentHashMap<String, String>> mbrSet){
		
		this.sessionTable = sessionTable;
		this.mbrSet = mbrSet;
		RPCServer rpcs1 = new RPCServer();
		RPCServer rpcs2 = new RPCServer();
		RPCServer rpcs3 = new RPCServer();
	}
	
	private void print(String s){
		System.out.println(s);
	}

	private String getCallID(){
		callID_num += 1;
		return callID_num + "";
	}
	
	/**
	 * Returns the most recent version and message for a session if a requested server or all 
	 * servers in the mbrSet is queried.
	 * 
	 * @param SID, id of the session
	 * @param version, version number of the session
	 * @param destAddr, destination ip address (null for all mbrSet)
	 * @param destPort, destination port (null for all mbrSet)
	 * @return the found_version and data deliminated by a #.  notFound message is returned if none is found
	 */
	
	//HAVE TO ACCOUNT FOR POUNDS IN MESSAGE
	public String sessionReadClient(String SID, String version, String destAddr, String destPort){
		String s = Operation.SessionRead.id + delim + SID + delim + version;
		try {
			String[] strArr = new String(RPCClient(s, destAddr, destPort).getData(), "UTF-8").split(delim);
			return strArr[1] + "#" + strArr[2];
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	/**
	 * 
	 * 
	 * @param SID, id of the session
	 * @param version, version number of the session
	 * @param data, message data to be stored for this session
	 * @param discard_time
	 * @param destAddr, destination ip address (null for all mbrSet)
	 * @param destPort, destination port (null for all mbrSet)
	 * @return The reply is just an acknowledgement or failure message
	 */
	
	public String sessionWriteClient(String SID, String version, String data, String discard_time, String destAddr, String destPort){
		String s = Operation.SessionWrite.id + delim + SID + delim + version + delim + data + delim + discard_time;
		try {
			return new String(RPCClient(s, destAddr, destPort).getData(), "UTF-8").split(delim)[1];
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	/**
	 * If the server holds the specified (or an older) version of the session, then it is deleted
	 * 
	 * @param SID, id of the session
	 * @param version, version number of the session
	 * @param destAddr, destination ip address (null for all mbrSet)
	 * @param destPort, destination port (null for all mbrSet)
	 * @return, acknowledgment or failure message
	 */
	public String sessionDeleteClient(String SID, String version, String destAddr, String destPort){
		String s = Operation.SessionDelete.id + delim + SID + delim + version + delim;
		try {
			return new String(RPCClient(s, destAddr, destPort).getData(), "UTF-8").split(delim)[1];
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	/**
	 * The reply is a subset of the mbrSet of the called server, containing at most sz members, chosen uniformly at random without replacement.
	 * 
	 * @param sz, size of desired members list
	 * @param destAddr, destination ip address (null for all mbrSet)
	 * @param destPort, destination port (null for all mbrSet)
	 * @return an arraylist of members, detailing their ip addresses and ports
	 */
	public ArrayList<Hashtable<String,String>> getMembersClient(int sz, String destAddr, String destPort){
		String s = Operation.GetMembers.id + delim + sz + delim;
		String[] responceArr = null;
		try {
			responceArr = (new String(RPCClient(s, destAddr, destPort).getData(), "UTF-8")).split(delim);
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		
		ArrayList<Hashtable<String,String>> mbrs = new ArrayList<Hashtable<String,String>>();
		
		
		for(String mbr : Arrays.copyOfRange(responceArr, 1, responceArr.length)){
			Hashtable<String, String> insert = new Hashtable<String, String>();
			try{
				insert.put("ip", mbr.split(":")[0]);
				insert.put("port", mbr.split(":")[1]);
				mbrs.add(insert);
			} catch (ArrayIndexOutOfBoundsException e){
				//continue
			}
		}
		return mbrs;
	}
	
	private DatagramPacket RPCClient(String s, String destAddr, String destPort){
		//Socket for sending and receiving datagram packets, initialized in constructor
		DatagramSocket rpcSocket = null;
		try {
			rpcSocket = new DatagramSocket();
		} catch (SocketException e1) {
			e1.printStackTrace();
		}
		int serverPort = rpcSocket.getLocalPort();
		System.out.println(serverPort);
		
		String callID = getCallID();
		s = callID + delim + s;
		
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream( );
		
		DatagramPacket recvPkt = null;
		
		System.out.println("RPC SESSION READ CLIENT");
		
		try {
			outputStream.write( s.getBytes() );

			byte[] outBuf = outputStream.toByteArray( );
			
			if(destAddr != null && destPort != null){
				clientSendPkt(outBuf, destAddr, destPort, rpcSocket);
			} else{
				for( @SuppressWarnings("rawtypes") ConcurrentHashMap<String, String> mbr : mbrSet  ) {
					clientSendPkt(outBuf, (String) mbr.get("ip"), (String) mbr.get("port"), rpcSocket);
				}
			}
			byte [] inBuf = new byte[Byte.MAX_VALUE];
			recvPkt = new DatagramPacket(inBuf, inBuf.length);
				do {
				recvPkt.setLength(inBuf.length);
				rpcSocket.receive(recvPkt);
				} //the callID in inBuf is not the expected one
				while(!new String(recvPkt.getData(), "UTF-8").split(delim)[0].equals(callID) );
		} catch(InterruptedIOException iioe) {
			// timeout 
			System.out.println("TIMED OUT RPC CALL");
			recvPkt = null;
		} catch(IOException ioe) {
			System.out.println("IO EXCEPTION RPC CALL");
		// other error 
		} 
		System.out.println("END OF RPC CALL");
		return recvPkt;
	}

	private void clientSendPkt(byte[] outBuf, String destAddr, String destPort, DatagramSocket rpcSocket) {
		InetAddress addr;
		try {
			addr = InetAddress.getByName(destAddr);
		
			int port = Integer.parseInt(destPort);
			
			System.out.println(destAddr + " " + destPort);
			
			DatagramPacket sendPkt = new DatagramPacket(outBuf, outBuf.length, addr, port);
			rpcSocket.send(sendPkt);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private String SessionRead(byte[] data, int length) {
		String[] dataStringArr = null;
		
		try {
			dataStringArr = (new String(data, "UTF-8")).split(delim);
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		
		String SID = dataStringArr[2];
		String version = dataStringArr[3];
		
		
		if(sessionTable.containsKey(SID)){
			return sessionTable.get(SID).get("version") + delim + sessionTable.get(SID).get("message");
		}else {
			return "notFound";
		}
	}
	private String SessionWrite(byte[] data, int length, DatagramSocket rpcSocket, String reqIP, String reqPort) {
		String[] dataStringArr = null;
		
		try {
			dataStringArr = (new String(data, "UTF-8")).split(delim);
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		
		String SID = dataStringArr[2];
		String version = dataStringArr[3];
		String message = dataStringArr[4];
		String experation = dataStringArr[5];
		
		ConcurrentHashMap<String, String> sessionValues = new ConcurrentHashMap<String, String>();
		sessionValues.put("version", dataStringArr[3]);
		sessionValues.put("message", dataStringArr[4]);
		sessionValues.put("expiration-timestamp", dataStringArr[5]);
		
		String ip = null;
		try {
			ip = InetAddress.getLocalHost().getHostAddress();
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		sessionValues.put("location", ip + "_" + rpcSocket.getLocalPort() 
				+ "_" + reqIP + "_" + reqPort);
		
		sessionTable.put(SID, sessionValues);
		
		
		return "ack";
	}
	private String SessionDelete(byte[] data, int length) {
		String[] dataStringArr = null;
		
		try {
			dataStringArr = (new String(data, "UTF-8")).split(delim);
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		
		String SID = dataStringArr[2];
		try{
			int version = Integer.parseInt(dataStringArr[3]);
			
			if(sessionTable.containsKey(SID)){
				if(Integer.parseInt(sessionTable.get(SID).get("version")) <= version){
					sessionTable.remove(SID);
					return "ack";
				} else{
					return "cannotDelNewerVer";
				}
				
			}else {
				return "notFound";
			}
		} catch(Exception e){
			print(e.getMessage() + " " + e.getLocalizedMessage());
			return null;
		}
	}
	private String GetMembers(byte[] data, int length) {
		String[] dataStringArr = null;
		
		try {
			dataStringArr = (new String(data, "UTF-8")).split(delim);
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		int sz = -1;
		try{
			sz = Integer.parseInt(dataStringArr[2]);
		} catch(Exception e){
			print(e.getMessage() + " " + e.getLocalizedMessage());
		}
		
		String out = "";
		for(int i = 0; i < sz; i++){
			try{
				ConcurrentHashMap<String, String> curMbr = mbrSet.get(i);
				out +=  curMbr.get("ip") + ":" + curMbr.get("port") + delim;
			} catch(IndexOutOfBoundsException e){
				//continue
			}
		}
		
		return out;
	}
	
	private class RPCServer extends Thread{
		private DatagramSocket rpcSocket;
		private int serverPort;
		
		RPCServer(){
			ConcurrentHashMap<String, String> myip = new ConcurrentHashMap<String, String>();
			
			try {
				rpcSocket = new DatagramSocket();
				serverPort = rpcSocket.getLocalPort();
				myip.put("ip", InetAddress.getLocalHost().getHostAddress());
			} catch (UnknownHostException e) {
				e.printStackTrace();
			} catch (SocketException e1) {
				e1.printStackTrace();
			}
			myip.put("port", serverPort + "");
			mbrSet.add(myip);
			
			start();
		}
		
		public void run(){
			while(true) {
			    byte[] inBuf = new byte[Byte.MAX_VALUE];
			    DatagramPacket recvPkt = new DatagramPacket(inBuf, inBuf.length);
			    try {
			    	System.out.println("rpc server on port " + serverPort);
					rpcSocket.receive(recvPkt);
				
				    InetAddress returnAddr = recvPkt.getAddress();
				    int returnPort = recvPkt.getPort();
				    
				    // here inBuf contains the callID and operationCode
				    String inData = new String(recvPkt.getData(), "UTF-8");
				    print("RPC Server Command Recieved " + inData);
				    int operationCode = Integer.parseInt(inData.split(delim)[1]); // get requested operationCode
				    String callID = inData.split(delim)[0] + delim;
				    byte[] outBuf = null;
				    	
			    	if(operationCode == Operation.SessionRead.getId()){
			    		outBuf = (callID + SessionRead(recvPkt.getData(), recvPkt.getLength())).getBytes();
					}
			    	else if(operationCode == Operation.SessionWrite.getId()){
			    		outBuf = (callID + SessionWrite(recvPkt.getData(), recvPkt.getLength(), rpcSocket, returnAddr.getHostAddress(), returnPort + "")).getBytes();
			    	}
			    	else if(operationCode == Operation.SessionDelete.getId()){
			    		outBuf = (callID + SessionDelete(recvPkt.getData(), recvPkt.getLength())).getBytes();
			    	}
			    	else if(operationCode == Operation.GetMembers.getId()){
			    		outBuf = (callID + GetMembers(recvPkt.getData(), recvPkt.getLength())).getBytes();
			    	}
			    	else{
			    		//INVALID COMMAND 
			    		print("INVALID RPC COMMAND RECIEVED");
			    	}
			    	
				    DatagramPacket sendPkt = new DatagramPacket(outBuf, outBuf.length,
				    returnAddr, returnPort);
					rpcSocket.send(sendPkt);
					
				} catch (IOException e) {
					e.printStackTrace();
				} catch(RuntimeException e){
					break;
				}
			  }
		}
	}
	
	//Operations
	public enum Operation {
		SessionRead(1, "SessionRead"),
		SessionWrite(2, "SessionWrite"),
		SessionDelete(3, "SessionDelete"),
		GetMembers(4, "GetMembers");
		
		public int id;
	    private String name;

	    private Operation( int id, String name ) {this.id = id;this.name = name;}

	    public String getName() { return name;  }

	    public int getId() { return id; }

	    public String toString() { return name;}
	}
}

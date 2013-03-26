
package server;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
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
	
	//UDP RPC Listening Server
	private RPCServer rpcs;
		
	@SuppressWarnings("unchecked")
	public RPCProtocol(ConcurrentHashMap<String, ConcurrentHashMap<String, String>> sessionTable, ArrayList<ConcurrentHashMap<String, String>> mbrSet){
		
		this.sessionTable = sessionTable;
		this.mbrSet = mbrSet;
		
		rpcs = new RPCServer();
		//Temporary listening servers
		RPCServer rpcs1 = new RPCServer();
		RPCServer rpcs2 = new RPCServer();
		RPCServer rpcs3 = new RPCServer();
		
		//REMOVE AFTER TESTING, THIS REMOVES ONES SELF TO MBRSET
		/*for(ConcurrentHashMap<String, String> ip : (ArrayList<ConcurrentHashMap<String, String>>)mbrSet.clone()){
			try {
				if(ip.equals(ipToConcurrentHashMap(InetAddress.getLocalHost().getHostAddress(), Integer.parseInt(rpcs.getLocalPort())))){
					mbrSet.remove(ip);
					print("Removed listining rpcs port");
				}
			} catch (NumberFormatException e) {
				e.printStackTrace();
			} catch (UnknownHostException e) {
				e.printStackTrace();
			}
		}
		print(mbrSet.toString());*/
	}
	
	private void print(String s){
		System.out.println(s);
	}

	private String getCallID(){
		callID_num += 1;
		return callID_num + "";
	}
	
	public String getUDPLocalPort(){
		return rpcs.getLocalPort();
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
			return strArr[1] + delim + strArr[2];
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch(Exception e){
			return "notFound";
		}
		return null;
	}
	
	/**
	 * Writes a session and its information to a destination's sessionTable.
	 * 
	 * @param SID, id of the session
	 * @param version, version number of the session
	 * @param data, message data to be stored for this session
	 * @param discard_time, the expiration time of the session
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
	
	@SuppressWarnings("unchecked")
	private DatagramPacket RPCClient(String s, String destAddr, String destPort){
		//Socket for sending and receiving datagram packets, initialized in constructor
		DatagramSocket rpcSocket = null;
		DatagramPacket recvPkt = null;
		try {
			print("RPC Operation from client with string: " + s);
			
			rpcSocket = new DatagramSocket();
			rpcSocket.setSoTimeout(10000);
			print("TIMEOUT " + rpcSocket.getSoTimeout());
		
			int serverPort = rpcSocket.getLocalPort();
			System.out.println(serverPort);
			
			String callID = getCallID();
			s = callID + ":" + rpcSocket.getLocalPort() + delim + s;
			
			ByteArrayOutputStream outputStream = new ByteArrayOutputStream( );
			
			outputStream.write( s.getBytes() );

			byte[] outBuf = outputStream.toByteArray( );
			
			if(destAddr != null && destPort != null){
				clientSendPkt(outBuf, destAddr, destPort, rpcSocket);
			} else{
				for( @SuppressWarnings("rawtypes") ConcurrentHashMap<String, String> mbr : 
					(ArrayList<ConcurrentHashMap<String, String>>)mbrSet.clone()  ) {
					clientSendPkt(outBuf, (String) mbr.get("ip"), (String) mbr.get("port"), rpcSocket);
				}
			}
			byte [] inBuf = new byte[Byte.MAX_VALUE];
			recvPkt = new DatagramPacket(inBuf, inBuf.length);
				do {
				recvPkt.setLength(inBuf.length);
				rpcSocket.receive(recvPkt);
				print("GOT SOMETHInG " + new String(recvPkt.getData(), "UTF-8").split(delim)[0].split(":")[0]);
				} //the callID in inBuf is not the expected one
				while(!new String(recvPkt.getData(), "UTF-8").split(delim)[0].split(":")[0].equals(callID) );
		} catch(SocketTimeoutException e){
			print("TIMEOUT ENCOUNTERED");
			//handleTimedOut()
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
	
	public ConcurrentHashMap<String, String> ipToConcurrentHashMap(String ip, int serverPort){
		ConcurrentHashMap<String, String> myip = new ConcurrentHashMap<String, String>();
		myip.put("ip", ip);
		myip.put("port", serverPort + "");
		return myip;
	}
	
	private class RPCServer extends Thread{
		private DatagramSocket rpcSocket;
		private int serverPort;
		
		RPCServer(){
			
			try {
				rpcSocket = new DatagramSocket();
				serverPort = rpcSocket.getLocalPort();
				
				//REMOVE AFTER TESTING, THIS ADDS ONES SELF TO MBRSET
				mbrSet.add(ipToConcurrentHashMap(InetAddress.getLocalHost().getHostAddress(),serverPort));
			} catch (UnknownHostException e) {
				e.printStackTrace();
			} catch (SocketException e1) {
				e1.printStackTrace();
			}
			
			System.out.println("rpc server on port " + serverPort);
			start();
		}
		
		
		public String getLocalPort() {
			return serverPort + "";
		}

		public void run(){
			while(true) {
			    byte[] inBuf = new byte[Byte.MAX_VALUE];
			    DatagramPacket recvPkt = new DatagramPacket(inBuf, inBuf.length);
			    try {
					rpcSocket.receive(recvPkt);
				
				    InetAddress returnAddr = recvPkt.getAddress();
				    
				    // here inBuf contains the callID and operationCode
				    String inData = new String(recvPkt.getData(), "UTF-8");
				    print("RPC Server Command Recieved " + inData);
				    int operationCode = Integer.parseInt(inData.split(delim)[1]); // get requested operationCode
				    String callID = inData.split(delim)[0] + delim;
				    String returnPort = callID.split(":")[1].replace(delim, "");
				    checkForMbrship(returnAddr, returnPort);
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
				    returnAddr, Integer.parseInt(returnPort));
				    
				    //TESTING FOR TIMEOUT
				    /*try {
						sleep(100);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}*/
					rpcSocket.send(sendPkt);
					
				} catch (IOException e) {
					e.printStackTrace();
				} catch(RuntimeException e){
					break;
				}
			  }
		}
	}
	
	/*
	 * Checks if an encountered ip address is in the mbrSet
	 */
	private void checkForMbrship(InetAddress returnAddr, String returnPort){
		boolean flag = false;
		for (ConcurrentHashMap<String, String> mbr: mbrSet){
			if(mbr.get("ip").equals(returnAddr.getHostAddress()) && mbr.get("port").equals(returnPort)){
				flag = true;
			}
		}
		if (!flag){
			ConcurrentHashMap<String, String> toInsert = new ConcurrentHashMap<String, String>();
			toInsert.put("ip", returnAddr.getHostAddress());
			toInsert.put("port", returnPort);
			mbrSet.add(toInsert);
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

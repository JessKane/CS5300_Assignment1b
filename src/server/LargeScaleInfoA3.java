/**
 * TODO: add members if it appears in cookie
 * TODO: crash button
 * TODO: random AS generator --> fix so it doesn't repeat 
 */
package server;


import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Hashtable;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet("/")
public class LargeScaleInfoA3 extends HttpServlet {
	//Servlet metadata
	private static final long serialVersionUID = 1L;

	// minimum amount of time, in seconds, a session is required to remain accessible after the last client request
	private static final int SESSION_TIMEOUT_SECS = 60;

	private static int session_num = 0;
	
	//Thread safe form of the simple date format
	public static class DateFormatThreadSafe {

		  private static final ThreadLocal<DateFormat> df
		                 = new ThreadLocal<DateFormat>(){
		    @Override
		    protected DateFormat initialValue() {
		        return new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
		    }
		  };

	  public Date parse(String source)
	                     throws ParseException{
	    Date d = df.get().parse(source);
	    return d;
	  }
		  
		public String format(Date time) {
			 String d = df.get().format(time);
			 return d;
		}
		  
	}
	private static final DateFormatThreadSafe df = new DateFormatThreadSafe();
	
	private static final int delta = 2; //used for expr and discard time
	private static final int tau = 2;		//used for discard time


	//ConcurrentHashMap of sessionIDs to a table of information on their message, location, and expiration data.
	ConcurrentHashMap<String,ConcurrentHashMap<String,String>> sessionTable = new ConcurrentHashMap<String,ConcurrentHashMap<String,String>>();

	//Set of known servers, their ips and corresponding listening ports
	ArrayList<ConcurrentHashMap<String,String>> mbrSet = new ArrayList<ConcurrentHashMap<String,String>>();
	
	//Cookie name that is searched for in this project
	String a2CookieName = "CS5300PROJ1SESSION";

	//Garbage Collector - cleans up expired sessions from sessionTable
	GarbageCollector janitorThread = new GarbageCollector("name");
	
	//RPC Protocol
	RPCProtocol rpcp;
	
	/*
	 * Constructor for initializing RPC handling
	 */
	public LargeScaleInfoA3(){
		rpcp = new RPCProtocol(sessionTable, mbrSet);
	}

	/*
	 * Base method handling requests
	 */
	@Override
	public void doGet(HttpServletRequest request,HttpServletResponse response)	throws ServletException, IOException {
		PrintWriter out = response.getWriter();
		
		Cookie cookie = handleCookie(request, response);
		handleCommand(response, request, out, cookie);
		ConcurrentHashMap<String,String> parsed = parseCookieValue(cookie.getValue());
		String sessionID = parsed.get("sessionID");

		out.println("<html>\n<body>\n<br>&nbsp;<br>");
		
		//PRINT SESSIONID

		out.println(getMessage(sessionID));
		out.println(getForm());
		out.println(getSessionID(sessionID));
		out.println("<u>ServerID:</u> " + getIPPLocal(rpcp));
		out.println(getSessionExp(sessionID));
		out.println("<p> <u>Memberset:</u> " + rpcp.mbrSet);
		out.println(getChoice(sessionID));
		out.println("<p><u>IPP Primary:</u> " + parsed.get("IPP_1"));
		if (parsed.containsKey("IPP_2")){
			out.println("<p><u>IPP Backup:</u> " + parsed.get("IPP_2"));
		}
		else{
			out.println("<p><u>IPP Backup:</u> none");
		}

		out.println(getDiscardTime(sessionID));
		out.println("</body>\n</html>");
	}

	/*
	 * Examines cookies from the request to either extract or generate a new sessionID.  Additionally attaches a cookie to the response.
	 */
	private Cookie handleCookie(HttpServletRequest request, HttpServletResponse response) {
		String sessionID = "-1";
		Cookie a2Cookie = null;

		//Check if there is a relevant cookie and extract sessionID
		if(request.getCookies() != null){
			System.out.println("old cookie");
			for(Cookie c : request.getCookies()){
					System.out.println("cookieVal of old cookie:" + c.getValue());
				
				ConcurrentHashMap<String,String> parsed= parseCookieValue(c.getValue());
				//				System.out.println(c.getValue());
				if(c.getName().equals(a2CookieName) && sessionTable.containsKey(parsed.get("sessionID"))){
					a2Cookie = c;
					sessionID = parsed.get("sessionID");
					//					System.out.println("SessionID: " + sessionID);
				}
			}
		}

		//If no cookie was found, generate a new one
		//Also fill sessionTable with new sessionID entry 
		if (a2Cookie == null) { 		 
			System.out.println(" -- new cookie --");
			sessionID = getNextSessionID(request);

			// create new timestamp
			Calendar cal = Calendar.getInstance();
			cal.add(Calendar.SECOND, SESSION_TIMEOUT_SECS);

			ConcurrentHashMap<String, String> sessionValues = new ConcurrentHashMap<String, String>();
			sessionValues.put("version", 1 +"");
			sessionValues.put("message", "");
			sessionValues.put("expiration-timestamp", df.format(cal.getTime()));
			String ip= getIPPLocal(rpcp);



			sessionTable.put(sessionID + "", sessionValues);
			String cookieVal = sessionID+"_"
					+ sessionValues.get("version")+"_"
					+ getIPPLocal(rpcp);

			a2Cookie = new Cookie(a2CookieName, cookieVal);
		}

		//Add cookie to response regardless, as it always contains new expiration and version information
		response.addCookie(a2Cookie);
		System.out.println("cookie val: " + a2Cookie.getValue());
		return a2Cookie;
	}

	/*
	 * Determines the next available sessionID for use
	 */
	private String getNextSessionID(HttpServletRequest request){
		session_num ++;
		String sessionID = "";
		sessionID = ""+session_num+"_"+getIPPLocal(rpcp);
		return sessionID;
	}

	/*
	 * Examines the request for the 'cmd' value, and performs the pertinent action
	 * After action is completed, server generates a new cookie to return to client
	 */
	private void handleCommand(HttpServletResponse response, HttpServletRequest request, PrintWriter out, Cookie c){
		ConcurrentHashMap<String,String> parsed= parseCookieValue(c.getValue());
		String sessionID = parsed.get("sessionID");
		String oldVersion = sessionTable.get(sessionID).get("version"); 		//TODO: get new version number from sessionRead data?
		String cmd = request.getParameter("cmd");
		String message = request.getParameter("NewText");
		String choice = ""; //picked primary, backup, or cache?
		
		//RPC stuff TODO 
		
		//---------(1) send sessionReadClient to IPP_primary and IPP_backup----------------
		//IPPLocal
		
		String IPP_local = getIPPLocal(rpcp);
		String[] IPPLocal_split = IPP_local.split("_");
		String local_IP = IPPLocal_split[0];
		String local_port = IPPLocal_split[1];
		
		//IPP Primary
		String IPP_1 = parsed.get("IPP_1");
		String[] IPP1_split = IPP_1.split("_");
		String IP_addr_1 = IPP1_split[0];
		String port_1 = IPP1_split[1];
		
		//IPP Backup (if present)
		String IPP_2 = "";
		if (parsed.containsKey("IPP_2")){
			IPP_2 = parsed.get("sessionID");
		}
		
		System.out.println("IPP Local: " + IPP_local);
		System.out.println("IPP_1: " + IPP_1);
		System.out.println("IPP_2: " + IPP_2);
		
		//---- check to see if IPP_primary or IPP_backup to see if they are equal to IPPLocal ---
		if (IPP_1.equals(IPP_local) || IPP_2.equals(IPP_local)){

			//TODO: use local SSTbl? or use what was submitted in in form?
			choice = "cache";
		}
		else{		
			String readResponse = rpcp.sessionReadClient(sessionID, oldVersion, IP_addr_1, port_1);
			System.out.println("RPC Read response: " +  readResponse);
			
			System.out.println("Read Response from ipp1: " + readResponse);
			//IPP backup
			if (readResponse.equals("notFound")){		//IPP_1 failed	
				if (parsed.containsKey("IPP_2")){
					String[] IPP2_split = IPP_2.split("_");
					String IP_addr_2 = IPP2_split[0];
					String port_2 = IPP2_split[1];
					readResponse =  rpcp.sessionReadClient(sessionID, oldVersion, IP_addr_2, port_2);
					System.out.println("Read Response from ipp2: " + readResponse);
					
					if (!(readResponse.equals("notFound"))){
						choice="backup";
					}
				}
			}
			
			else {	//IPP ok
				choice = "primary";
			}
			
			
			//-----------(2)if there is a response from either, use found_Version and new data from now onwards---------------
			//newData = message. If found_Version more recent than your version, then use newData as message
			System.out.println("readResponse: " + readResponse);
			if (readResponse.equals("notFound")){
				/*TODO: you should return an HTML page with a message saying the session timed out 
				or failed (you will be able to tell the difference between these in some but possibly not all cases), 
				and make sure the cookie for the timed-out-or-lost session is deleted from the browser.*/
				//out.write("<html>\n<body>\n<br>&nbsp;\n Session timed out or failed \n</body>\n</html>");
				out.write("<html>\n<body>\n<br>&nbsp;\n<br><big><big><b>Session has timed out or failed<br>&nbsp;<br>\n</b></big></big>\n</body>\n</html>");
			}
			else{
				//TODO
				//parse the data returned by IPP primary or backup, and use this data from now onwards
				String[] readResponse_split = readResponse.split("#");
				oldVersion = readResponse_split[0];
				message = readResponse_split[1];	
			}
		}
		
		//Don't do anything if no command was provided
		if(cmd == null){
			return;
		} 
		//Destroy relevant session 
		else if(cmd.equals("LogOut")){
			System.out.println("LogOut command");
			
			rpcp.sessionDeleteClient(sessionID, oldVersion, local_IP, local_port);
			sessionTable.remove(sessionID); //TODO: change to rpcp.sessionDeleteClient 
			
			out.write("<html>\n<body>\n<br>&nbsp;\n<br><big><big><b>Bye!<br>&nbsp;<br>\n</b></big></big>\n</body>\n</html>");
			out.close();
		} else if (cmd.equals("Replace") && message.length() > 512) {
			System.out.println("Message is greater than 512 bytes. Request ignored and no cookie made");
			return;
		} else {
			String cookieVal=sessionID;
			//update version number
			String newVersion = ((Integer)(Integer.parseInt(oldVersion) + 1)).toString();
			sessionTable.get(sessionID).put("version", newVersion);
			cookieVal += "_"+ newVersion;
//			System.out.println("new version put in. cookieVal: "+cookieVal);


			//update expiration timestamp
			Calendar cal = Calendar.getInstance();
			cal.add(Calendar.SECOND, SESSION_TIMEOUT_SECS);
			String newExpr =  df.format(cal.getTime());
			sessionTable.get(sessionID).put("expiration-timestamp", newExpr);

			//Update message for session
			if(cmd.equals("Replace")) {
				System.out.println("Replace command");
				//				System.out.println("String length: " + message.length());
				sessionTable.get(sessionID).put("message", message);
				
			} else if(cmd.equals("Refresh")){
				System.out.println("Refresh command");
				
				//Maintain stored message for session to be displayed
				message = sessionTable.get(sessionID).get("message");
			} 

//			System.out.println("new message put in. cookieVal: "+cookieVal);
			
			//-----(3) sessionWrite to random AS in memberset---------
			
			/*TODO: use mbrset, or this: 
			 * ArrayList<Hashtable<String,String>> random_mbrset = rpcp.getMembersClient(20, IP_addr_1, port_1);
			 */
			
			Random rand = new Random();
			String write_result = null;
			String final_AS_ip = ""; 		//will hold the IP address of IPPbackup
			String final_AS_port = "";		//will hold the port number of IPPbackup
			String final_discardTime = "";
			//try until you get a response 
			while (write_result == null){
				ConcurrentHashMap<String,String> random_AS = mbrSet.get(rand.nextInt(mbrSet.size())); //get random AS
				Calendar discard_time_cal = Calendar.getInstance();
				discard_time_cal.add(Calendar.SECOND, SESSION_TIMEOUT_SECS + 2*delta + tau);
				String discard_time =  df.format(discard_time_cal.getTime());
				
				//TODO: check: data = message??
				System.out.println("MESSAGE " + message);
				write_result = rpcp.sessionWriteClient(sessionID, newVersion, message, discard_time, random_AS.get("ip"), random_AS.get("port"));
				
				if (write_result != null){
					final_AS_ip = random_AS.get("ip");
					final_AS_port = random_AS.get("port");
					final_discardTime = discard_time;
				}
			}
		
			sessionTable.get(sessionID).put("discard_time", final_discardTime); //TODO: check: does discardTime get put into SSTbl? 

			//put choice (where you're getting data from) into sessionTable
			sessionTable.get(sessionID).put("choice", choice); 
			
			//-------(4) make a new cookie with IPP primary and backup----------
			//cookieVal so far only has session_ID and version number
			
			String IPP_newBackup = final_AS_ip + "_" + final_AS_port;
			cookieVal += "_" + IPP_local + "_" + IPP_newBackup;				
			
			System.out.println("new cookie for request: " + cookieVal);
			Cookie newCookie = new Cookie(a2CookieName, cookieVal);
			response.addCookie(newCookie);
			System.out.println(cookieVal);
		}
	}


	/*
	 * Generates the html input form for the page
	 */
	private String getForm() {
		String out = "<form method=GET action=''>";
		out += "<input type=submit name=cmd value=Replace>&nbsp;&nbsp;<input type=text name=NewText size=40 maxlength=512>&nbsp;&nbsp;\n";
		out += "</form>\n";
		out += "<form method=GET action=''>\n";
		out += "<input type=submit name=cmd value=Refresh>\n";
		out += "</form>\n";
		out += "<form method=GET action=''>\n";
		out += "<input type=submit name=cmd value=LogOut>\n";
		out += "</form>\n";

		return out;
	}

	/*
	 * Creates the html with the session's relevant message or a default greeting if none is provided
	 */
	private String getMessage(String sessionID) {
		String out = "<big><big><b>";

		if((sessionID != null) && sessionTable.containsKey(sessionID)){
			//Check if there is anything but the default blank message
			if(!sessionTable.get(sessionID).get("message").equals("")){				
				out += sessionTable.get(sessionID).get("message");
			} else{
				out += "Hello, User!";
			}

		} else{
			out += "Issue with cookies";
		}

		out += "<br>&nbsp;<br></b></big></big>";

		return out;
	}

	/*
	 * Creates the html displaying the hashed expiration timestamp for the session
	 */
	private String getSessionExp(String sessionID) {
		String out = "<p><u>Expires</u>: ";

		if((sessionID != null) && sessionTable.containsKey(sessionID)){
			out += sessionTable.get(sessionID).get("expiration-timestamp");			
			String expirationDate = sessionTable.get(sessionID).get("expiration-timestamp");
			Date expDate = null;
			Date currentDate = new Date();
			try {
				expDate = df.parse(expirationDate);
			} catch (ParseException e) {
				System.out.println("Cookie Date Parse Error");
			} catch(NumberFormatException e){
				System.out.println("Number Format " + expirationDate);
			}

			if (expDate.after(currentDate)) {
				long ms = expDate.getTime() - currentDate.getTime();
				Double minutes = (double)(ms/(1000.0 * 60.0));
				out += String.format(", %.2f minutes from now.", minutes);
			} 

		} else{
			out += "Issue with cookies";
		}

		out += "</p>";

		return out;
	}

	/*
	 * puts version number on html page
	 * for debugging purposes
	 */
	private String getVersionNumber(String sessionID) {
		String out = "<p>Version Number: ";

		if((sessionID != null) && sessionTable.containsKey(sessionID)){
			out += sessionTable.get(sessionID).get("version");
		} else{
			out += "Issue with cookies";
		}

		out += "</p>";

		return out;
	}

	/*
	 * print discard time  on HTML page
	 */
	private String getDiscardTime(String sessionID) {
		String out = "<p><u>Discard Time:</u> ";

		if((sessionID != null) && sessionTable.containsKey(sessionID)){
			out += sessionTable.get(sessionID).get("discard_time");
		} else{
			out += "Issue with cookies";
		}

		out += "</p>";

		return out;
	}	
	
	/*
	 * print origin of data (primary, backup, or cache) on HTML page
	 */
	private String getChoice(String sessionID) {
		String out = "<p><u>Choice:</u> ";

		if((sessionID != null) && sessionTable.containsKey(sessionID)){
			out += sessionTable.get(sessionID).get("choice");
		} else{
			out += "Issue with cookies";
		}

		out += "</p>";

		return out;
	}	
	
	/*
	 * print sessionID on HTML page
	 * for debugging purposes
	 */
	private String getSessionID(String sessionID) {
		String out = "<p><u>SessionID:</u> ";

		if((sessionID != null) && sessionTable.containsKey(sessionID)){
			out += sessionID;
		} else{
			out += "Issue with cookies";
		}

		out += "</p>";

		return out;
	}	

	/*cookieVal is the string used as the value of a Cookie
	 *Includes, in this exact order: sessionID, version, location (arbitrarily long)
	 *Each information of the cookie is in the following format: 'key=value,'
	 *parseCookieValue parses the string into a ConcurrentHashMap
	 */
	private ConcurrentHashMap<String,String> parseCookieValue(String cookieVal){
		ConcurrentHashMap<String,String> parsed= new ConcurrentHashMap<String,String>();
		String[] underscoreParsed = cookieVal.split("_");
		if (underscoreParsed.length != 5){
			System.out.println("array is " + underscoreParsed.length + " components long");
			System.out.println(cookieVal);
		}
		parsed.put("sessionID", underscoreParsed[0]+"_"+underscoreParsed[1]+"_"+underscoreParsed[2]);
		parsed.put("version", underscoreParsed[3]);
		
		int counter = 1;
		for (int i = 4; i < underscoreParsed.length; i = i + 2){
			String key = "IPP_" + counter; //start at IPP_1
			parsed.put(key, underscoreParsed[i] + "_" + underscoreParsed[i+1]);
			counter ++;
		}

		System.out.println(parsed);

		return parsed;
	}
	
	private String getIPPLocal(RPCProtocol rpcp){
		String ip = "";
		try {
			ip = InetAddress.getLocalHost().getHostAddress() + "_" + rpcp.getUDPLocalPort();
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return ip;
	}

	/*
	 * background thread that cleans up expired session from sessionTable
	 * compares current time to expiration time of session to see if session is expired. 
	 */
	private class GarbageCollector extends Thread {
		GarbageCollector(String name){
			super(name);
			start();
		}

		public void run(){
			while(true){
				for (String sessionID: sessionTable.keySet()){
					ConcurrentHashMap<String,String> session = sessionTable.get(sessionID);
					String exprString= session.get("expiration-timestamp");

					Date expDate = null;
					try {
						expDate = df.parse(exprString);
					} catch (ParseException e) {
						System.out.println("Failure in parsing date");
					}
					if ((new Date()).after(expDate)){
						System.out.println("Session " + sessionID + " has expired");
						sessionTable.remove(sessionID);
						System.out.println("sessiontable size: "+ sessionTable.size());
					}
					else{
						//					System.out.println("Session #"+sessionID + " not expired");
					}

				}
				/*try {
					sleep(5000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}*/
			}
		}
	}
}
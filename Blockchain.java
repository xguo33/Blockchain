//package Ashely1;
/*---------------------------------------------------------------

Blockchain.java Xu Guo May 25th 2022

source file：
Prof. CLark Elliot programs:
    BlockJ.java
    WorkB.java
    bc.java
    BlockInputG.java

The website from those programs:
Reading lines and tokens from a file:
http://www.fredosaurus.com/notes-java/data/strings/96string_examples/example_stringToArray.html
Good explanation of linked lists:
https://beginnersbook.com/2013/12/linkedlist-in-java-with-example/
Priority queue:
https://www.javacodegeeks.com/2013/07/java-priority-queue-priorityqueue-example.html
https://www.quickprogrammingtips.com/java/how-to-generate-sha256-hash-in-java.html  @author JJ
https://dzone.com/articles/generate-random-alpha-numeric  by Kunal Bhatia  路  Aug. 09, 12 路 Java Zone
http://www.javacodex.com/Concurrency/PriorityBlockingQueue-Example
...etc in case I miss some website

I also read websites to help:
https://medium.com/programmers-blockchain/create-simple-blockchain-java-tutorial-from-scratch-6eeed3cb03fa

how I compile the program
javac -classpath .;gson-2.8.2.jar Blockchain.java
java -classpath .;gson-2.8.2.jar Blockchain 0
java -classpath .;gson-2.8.2.jar Blockchain 1
java -classpath .;gson-2.8.2.jar Blockchain 2

 */

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Random;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/*The Port class, Configure ports*/
class Ports {
	public static int KeyServerPortBase = 4710;// receive public keys base
	public static int UnverifiedBlockServerPortBase = 4820;// receive unverified blocks base
	public static int BlockchainServerPortBase = 4930;// receive updated blockchains base

	public static int KeyServerPort;
	public static int UnverifiedBlockServerPort;
	public static int BlockchainServerPort;

	/*base+process number*/
	public void setPorts() {
		KeyServerPort = KeyServerPortBase + (Blockchain.PID );
		UnverifiedBlockServerPort = UnverifiedBlockServerPortBase + (Blockchain.PID );
		BlockchainServerPort = BlockchainServerPortBase + (Blockchain.PID );
	}
}

/*
code from bc.java and blockinputG.java
the jave objects we need in the blockchain */
class BlockRecord implements Serializable {

	/*why chose serializable ?
     * because the objects are sent over socker stream
	 * Examples of block fields, I picked some I think that
	 * I already know their meaning and how I can use them
	 * For example the previoushas will be useful sha256
	 * but I still working on it so use // to commet it first
	 */
	String BlockID;
	int VerificationProcessID;
	//String PreviousHash; working on the sha 256

	String TimeStamp;
	String Data;

	public String getTimeStamp() {
		return TimeStamp;
	}
   // very important, it can identify an event happen also it can make sure no one can change the blockchain
	// get the timestamp first
	public void setTimeStamp(String TS) {
		this.TimeStamp = TS;
	}

	public String getData() {
		return Data;
	}

	public void setData(String DATA) {
		this.Data = DATA;
	}

	public String getBlockID() {
		return BlockID;
	}

	public void setBlockID(String blockID) {
		BlockID = blockID;
	}

	public int getVerificationProcessID() {
		return VerificationProcessID;
	}

	public void setVerificationProcessID(int verificationProcessID) {
		VerificationProcessID = verificationProcessID;
	}



}

public class Blockchain {

	static String serverName = "127.0.0.1";
	static String blockchain = "[First block]";
	static int numProcesses = 3; //you can get as much you want but this assignment we only need 3
	static LinkedList<String> blockchainIDs=new LinkedList<String>();
	static int PID = 0; // Our process ID

	static FileWriter writeLog=getFileWriter("BlockchainLog.txt");// write down the blockchainlog.txt
	static FileWriter blockchainLedgerJson=getFileWriter("BlockchainLedger.json");// write the ledger.json

	/*Create a program to write to the log
	 * however, my log is using the cmd consele*/
	static FileWriter getFileWriter(String fileName) {
		FileWriter writeLog=null;
		try {
			writeLog= new FileWriter(new File(fileName),true);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return writeLog;

	}
	LinkedList<BlockRecord> recordList = new LinkedList<BlockRecord>();
	final PriorityBlockingQueue<BlockRecord> ourPriorityQueue = new PriorityBlockingQueue<>(100, BlockTSComparator);//priority queue

	public static Comparator<BlockRecord> BlockTSComparator = new Comparator<BlockRecord>() {
		@Override
		/*
		Sort timestamps in ascending order
		the smallest one will always go first
		 */
		public int compare(BlockRecord b1, BlockRecord b2) {
			//get the timestamps of each block
			String s1 = b1.getTimeStamp();
			String s2 = b2.getTimeStamp();
			if (s1 == s2) {
				return 0;
			}// if they are same return 0
			if (s1 == null) {
				return -1;
			}// if s1 equal null return -1
			if (s2 == null) {
				return 1;
			}// if s2 equal null return 1
			return s1.compareTo(s2);// otherwise, run the compare method
		}
	};

	public void KeySend() { //send puublic key using multicast
		Socket sock;
		PrintStream toServer;
		PrintWriter pw;
		try {
			pw=new PrintWriter(writeLog);
			for (int i = 0; i < numProcesses; i++) {// Send public key to different sever
				sock = new Socket(serverName, Ports.KeyServerPortBase + i);// send the fake public key
				toServer = new PrintStream(sock.getOutputStream());
				toServer.println("FakeKeyProcess" + Blockchain.PID);// fake the public key
				pw.append("FakeKeyProcess" + Blockchain.PID);//fake public key = fakekeryprocess + process id number
				toServer.flush();
				sock.close();
			}
			pw.close();
		} catch (Exception x) {
			x.printStackTrace();
		}
	}

	/*
	 * create and send some unverified block using some simple data and timestamp
	 * all processes can be multicasted
	 * socket will help to send the object
	 */
	public void UnverifiedSend() {

		Socket UVBsock; //UVB= unverified block server
		BlockRecord tempRec;

		String fakeBlockData;//use some fake data
		String T1;
		String TimeStampString;
		Date date;
		Random r = new Random();

		PrintWriter pw;


		try {
			pw=new PrintWriter(writeLog);
			String inputFileName="BlockInput"+Blockchain.PID+".txt";// get the blockInput.txt
			Scanner scanner=new Scanner(new File(inputFileName));// scan the file
			for (int i = 0;scanner.hasNextLine(); ++i) {// use for loop to read the text
				// and produce them to block chain record
				BlockRecord BR = new BlockRecord();

				date = new Date();
				T1 = String.format("%1$s %2$tF.%2$tT", "", date); // creat a time stramp
				TimeStampString = T1 + "." + i; //

				BR.setTimeStamp(TimeStampString); //who happen first and who will be the first

				String suuid = new String(UUID.randomUUID().toString());//use uuid to set up block id
				BR.setBlockID(suuid);

				fakeBlockData= Integer.toString(((Blockchain.PID + 1) * 10) + i) + "."+TimeStampString+
						" "+scanner.nextLine();// follow assignment request to set up data format

				BR.setData(fakeBlockData);
				recordList.add(BR);
			}
			Collections.shuffle(recordList);// shuffle the list and priority will sorts them
			Iterator<BlockRecord> iterator = recordList.iterator();// put the shuffle list to iterator


			PrintStream toServerOOS = null;
			for (int i = 0; i < numProcesses; i++) {
				System.out.println("Sending UVBs to process " + i + "...");
				pw.append("Sending UVBs to process " + i + "...");
				iterator = recordList.iterator();//Iterate over each record in turn
				/*
				because different process need to iterate from begin to the end
				 */
				while (iterator.hasNext()) {// only if the iterator still have a record
					/*
					use sock establish communication
					 */
					UVBsock = new Socket(serverName, Ports.UnverifiedBlockServerPortBase + i);
					toServerOOS = new PrintStream(UVBsock.getOutputStream());
					Thread.sleep((r.nextInt(9) * 100));
					//TODO:send
					tempRec = iterator.next();// take the record



					Gson gson = new GsonBuilder().setPrettyPrinting().create();
					// and convert it to json

					String json = gson.toJson(tempRec);



					toServerOOS.print(json);
					toServerOOS.flush();

					pw.append(tempRec.toString());

					UVBsock.close();
				}
			}


			Thread.sleep((r.nextInt(9) * 100));
			pw.close();

		} catch (Exception x) {
			x.printStackTrace();
		}
	}

	public static void main(String args[]) {
		//args=new String[1];
		//args[0]="2";
		Blockchain s = new Blockchain();
		s.run(args);//run the main method

	}







	public void run(String args[]) {

		PrintWriter pw;

		pw=new PrintWriter(writeLog);

		System.out.println("Running now\n");

		pw.append("Running now\n");

		PID = (args.length < 1) ? 0 : Integer.parseInt(args[0]);// if don't say which process, then will be 0
		System.out.println("Xu Guo's Block Coordination Framework. Use Control-C to stop the process.\n");
		System.out.println("Using processID " + PID + "\n");

		pw.append("Xu Guo's Block Coordination Framework. Use Control-C to stop the process.\n");
		pw.append("Using processID " + PID + "\n");

		new Ports().setPorts(); // we have a port number base , and pid will join in

		new Thread(new PublicKeyServer()).start();// new thread for connect public key server
		new Thread(new UnverifiedBlockServer(ourPriorityQueue)).start();// new thread for unvrified block server
		new Thread(new BlockchainServer()).start();// new thread for block chain server
		try {
			Thread.sleep(2000);//sleep 2s to send key
		} catch (Exception e) {
		}
		KeySend();
		try {
			Thread.sleep(1000);//sleep 1s
		} catch (Exception e) {
		}
		new Blockchain().UnverifiedSend();// some new unverified block will be multicast to all server
		try {
			Thread.sleep(1000);//sleep 1s
		} catch (Exception e) {
		}
		new Thread(new UnverifiedBlockConsumer(ourPriorityQueue)).start();// new thread for consuming the unverified block
		//in the queue

		pw.close();// printwriter can stop write
	}
}



class UnverifiedBlockConsumer implements Runnable {
	PriorityBlockingQueue<BlockRecord> queue;
	int PID;

	UnverifiedBlockConsumer(PriorityBlockingQueue<BlockRecord> queue) {// we talked about consuming unvrifed bloock before
		this.queue = queue;//Our priority queue is bound to a local variable.
	}

	public void run() {
		String data;
		BlockRecord tempRec;
		PrintStream toBlockChainServer;
		Socket BlockChainSock;
		String newblockchain;
		String fakeVerifiedBlock;
		Random r = new Random();

		System.out.println("Starting the Unverified Block Priority Queue Consumer thread.\n");

		PrintWriter pw=new PrintWriter(Blockchain.writeLog);// write log
		pw.append("Starting the Unverified Block Priority Queue Consumer thread.\n");// write this sentence to log

		try {
			while (true) {
				tempRec = queue.take();
				data = tempRec.getData();
				int j;

				for (int i = 0; i < 100; i++) {
					j = ThreadLocalRandom.current().nextInt(0, 10);
					Thread.sleep((r.nextInt(9) * 100));
					if (j < 3)
						break;
				}

				//TODO:CHECK
				if (  Blockchain.blockchain.indexOf(data.substring(1, 9)) < 0) {
					fakeVerifiedBlock = "[" + data + " verified by P" + Blockchain.PID + " at time "
							+ Integer.toString(ThreadLocalRandom.current().nextInt(100, 1000)) + "]\n";

					String tempblockchain = fakeVerifiedBlock + Blockchain.blockchain;
					if(!Blockchain.blockchainIDs.contains(tempRec.BlockID) ) {Blockchain.blockchainIDs.add(tempRec.BlockID);																	// chain

						tempRec.setVerificationProcessID(Blockchain.PID);}


					for (int i = 0; i < Blockchain.numProcesses; i++) {

						BlockChainSock = new Socket(Blockchain.serverName, Ports.BlockchainServerPortBase + i);
						toBlockChainServer = new PrintStream(BlockChainSock.getOutputStream());
						toBlockChainServer.println(tempblockchain);


						toBlockChainServer.flush();
						BlockChainSock.close();

						pw.append(tempblockchain);

						if(i==0) {
							PrintWriter pwJson = new PrintWriter(Blockchain.blockchainLedgerJson);
							Gson gson = new GsonBuilder().setPrettyPrinting().create();
							String json = gson.toJson(tempRec);
							pwJson.append(json + "\n");
							pwJson.close();
						}
					}

				}


				Thread.sleep(1500);
			}
		} catch (Exception e) {
			System.out.println(e);
		}

		pw.close();
	}
}



class PublicKeyServer implements Runnable {
// public key server methond

	public void run() {
		int q_len = 6;
		Socket keySock;
		System.out.println("Starting Key Server input thread using " + Integer.toString(Ports.KeyServerPort));

		PrintWriter pw=new PrintWriter(Blockchain.writeLog);//write our log
		pw.append("Starting Key Server input thread using " + Integer.toString(Ports.KeyServerPort));

		try {
			ServerSocket servsock = new ServerSocket(Ports.KeyServerPort, q_len);
			while (true) {
				keySock = servsock.accept();//monitor if someone use sock
				new PublicKeyWorker(keySock).start();
			}
		} catch (IOException ioe) {
			System.out.println(ioe);
			pw.append(ioe.getMessage());
		}

		pw.close();
	}
}

class PublicKeyWorker extends Thread {
	Socket keySock;
	PublicKeyWorker(Socket s) {
		keySock = s;
	}

	public void run() {
		try {
			PrintWriter pw=new PrintWriter(Blockchain.writeLog);

			BufferedReader in = new BufferedReader(new InputStreamReader(keySock.getInputStream()));
			// read data
			String data = in.readLine();
			System.out.println("Got key: " + data);// print data
			keySock.close();

			pw.append("Got key: " + data);// print writer write
			pw.close();
		} catch (IOException x) {
			x.printStackTrace();
		}
	}
}

class UnverifiedBlockServer implements Runnable {
	BlockingQueue<BlockRecord> queue;
	UnverifiedBlockServer(BlockingQueue<BlockRecord> queue) {
		this.queue = queue;}//receive the prioirty queue




	class UnverifiedBlockWorker extends Thread {
		Socket sock;
		BlockRecord BR = new BlockRecord();//Instantiate a new block record

		UnverifiedBlockWorker(Socket s) {
			sock = s;
		}

		public void run() {

			try {
				PrintWriter pw=new PrintWriter(Blockchain.writeLog);
				//TODO:recive
				BufferedReader  unverifiedIn = new BufferedReader (new InputStreamReader(sock.getInputStream()));// read data from sock
				String json="";
				//String line=unverifiedIn.readLine();
				// covert it to json
				json+=unverifiedIn.readLine()+"\n";
				json+=unverifiedIn.readLine()+"\n";
				json+=unverifiedIn.readLine()+"\n";
				json+=unverifiedIn.readLine()+"\n";
				json+=unverifiedIn.readLine()+"\n";
				json+=unverifiedIn.readLine()+"\n";

				//System.out.println("recive json:"+json);

				BR=new Gson().fromJson(json, BlockRecord.class);//Revert to block object

				System.out.println("Received UVB: " + BR.getTimeStamp() + " " + BR.getData());
				pw.append("Received UVB: " + BR.getTimeStamp() + " " + BR.getData());
				queue.put(BR);//put object to queue
				sock.close();
				pw.close();
			} catch (Exception x) {
				x.printStackTrace();
			}


		}
	}
	public void run() {// receive the unverified block
		int q_len = 6;
		Socket sock;
		System.out.println("Starting the Unverified Block Server input thread using "
				+ Integer.toString(Ports.UnverifiedBlockServerPort));

		PrintWriter pw=new PrintWriter(Blockchain.writeLog);
		pw.append("Starting the Unverified Block Server input thread using "
				+ Integer.toString(Ports.UnverifiedBlockServerPort));

		try {
			ServerSocket UVBServer = new ServerSocket(Ports.UnverifiedBlockServerPort, q_len);
			while (true) {//if someone connrct
				sock = UVBServer.accept();//receive the unverified block
				new UnverifiedBlockWorker(sock).start();
			}
		} catch (IOException ioe) {
			System.out.println(ioe);
			pw.append(ioe.getMessage());
		}

		pw.close();
	}
}
/*
block chain server
 */
class BlockchainServer implements Runnable {
	public void run() {
		int q_len = 6;
		Socket sock;
		System.out.println(
				"Starting the Blockchain server input thread using " + Integer.toString(Ports.BlockchainServerPort));

		PrintWriter pw=new PrintWriter(Blockchain.writeLog);
		pw.append("Starting the Blockchain server input thread using " + Integer.toString(Ports.BlockchainServerPort));
		try {
			ServerSocket servsock = new ServerSocket(Ports.BlockchainServerPort, q_len);
			while (true) {//once connect
				sock = servsock.accept();
				new BlockchainWorker(sock).start();//start up blockchainworker method
			}
		} catch (IOException ioe) {
			System.out.println(ioe);
			pw.append(ioe.getMessage());
		}
		pw.close();
	}
}
class BlockchainWorker extends Thread {
	Socket sock;

	BlockchainWorker(Socket s) {
		sock = s;
	}

	public void run() {
		try {
			PrintWriter pw=new PrintWriter(Blockchain.writeLog);


			BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream()));
			String blockData = "";
			String blockDataIn;
			while ((blockDataIn = in.readLine()) != null) {
				blockData = blockData + "\n" + blockDataIn;
			}
			Blockchain.blockchain = blockData;//put all read data to the blockchain


			/************************work******************/
			if(Work.isWorkSolve(blockData)) {// if work can solve
				System.out.println("************Process "+Blockchain.PID+"   slove the puzzle");//print information
				pw.append("\"************Process \"+Blockchain.PID+\"   slove the puzzle\"\n");

			}


			System.out.println("         --NEW BLOCKCHAIN--\n" + Blockchain.blockchain + "\n\n");
			pw.append("         --NEW BLOCKCHAIN--\n" + Blockchain.blockchain + "\n\n");


			sock.close();
			pw.close();
		} catch (IOException x) {
			x.printStackTrace();
		}
	}
}

/*
work method help us sovle the puzzle
use hash value and sha256
 */
class Work {



	public static String ByteArrayToString(byte[] ba) {
		StringBuilder hex = new StringBuilder(ba.length * 2);//instantiate a string builder with double length
		for (int i = 0; i < ba.length; i++) {// for each bit
			hex.append(String.format("%02X", ba[i]));//change them to hex and output
		}
		return hex.toString();
	}

	public static String randomAlphaNumeric(int count) {
		StringBuilder builder = new StringBuilder();// instantiate a string builder
		while (count-- != 0) {// if there still have count
			int character = (int) (Math.random() * ALPHA_NUMERIC_STRING.length());
			builder.append(ALPHA_NUMERIC_STRING.charAt(character));
		}
		return builder.toString();
	}
	private static final String ALPHA_NUMERIC_STRING = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
	static String someText = "one two three";
	static String randString;

	public static boolean isWorkSolve(String blockData) {

		String concatString = "";// random seed
		String stringOut = "";

		randString = randomAlphaNumeric(8);
		int workNumber = 0;

		try {

			for (int i = 1; i < 20; i++) {
				randString = randomAlphaNumeric(8);//only 8 digit
				concatString = blockData + randString;//put randString and blockdata together
				MessageDigest MD = MessageDigest.getInstance("SHA-256");// initialize the sha-256 algorithm
				byte[] bytesHash = MD.digest(concatString.getBytes("UTF-8"));//get hash value

				stringOut = ByteArrayToString(bytesHash);

				workNumber = Integer.parseInt(stringOut.substring(0, 4), 16);

				if (workNumber < 5000) {
					return true;
				}

			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}

		return false;

	}

}

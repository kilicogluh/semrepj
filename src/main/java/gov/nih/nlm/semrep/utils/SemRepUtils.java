package gov.nih.nlm.semrep.utils;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;
import java.util.logging.Logger;

import edu.stanford.nlp.util.CoreMap;
import gov.nih.nlm.bioscores.core.Expression;
import gov.nih.nlm.ling.core.Document;
import gov.nih.nlm.ling.core.Span;
import gov.nih.nlm.ling.core.SpanList;
import gov.nih.nlm.ling.core.SurfaceElement;
import gov.nih.nlm.ling.sem.Entity;
import gov.nih.nlm.ling.sem.Predicate;
import gov.nih.nlm.ling.sem.SemanticItem;
import gov.nih.nlm.semrep.Constants;
import gov.nih.nlm.semrep.core.Chunk;
import gov.nih.nlm.semrep.core.SRSentence;
import gov.nih.nlm.semrep.preprocess.SentenceAnnsSO;

/**
 * A class that contains methods broadly useful in SemRep.
 * 
 * @author Halil Kilicoglu
 *
 */

public class SemRepUtils {
    private static Logger log = Logger.getLogger(SemRepUtils.class.getName());
    
	public static List<String> LEFT_PARENTHESES = Arrays.asList("(","{","[");
	public static List<String> RIGHT_PARENTHESES = Arrays.asList(")","}","]");
	public static List<String> APPOSITIVE_INDICATORS = Arrays.asList("such as", "particularly", "in particular", "including");

    /**
     * Obtains a socket to connect to a server on a given port. It will return null
     * if a socket cannot be obtained.
     * 
     * @param serverName
     *            name of the server
     * @param serverPort
     *            port to connect
     * @return a <code>Socket</code> object for connection
     */
    public static Socket getSocket(String serverName, int serverPort) {
	Socket s = null;
	try {
	    s = new Socket(serverName, serverPort);
	} catch (UnknownHostException uhe) {
	    log.warning("Unable to bind socket to server at " + serverName + ":" + serverPort + ".");
	    uhe.printStackTrace();
	} catch (IOException ioe) {
	    log.warning("General IO error at creating socket to server at " + serverName + ":" + serverPort + ".");
	    ioe.printStackTrace();
	}
	return s;
    }

    /**
     * Closes a socket gracefully.
     * 
     * @param socket
     *            Socket object to close
     */
    public static void closeSocket(Socket socket) {
	try {
	    socket.close();
	} catch (IOException ioe) {
	    log.warning(
		    "Failed to close socket to " + socket.getInetAddress().getHostAddress() + ":" + socket.getPort());
	    ioe.printStackTrace();
	}
    }

    /**
     * Queries a server with the given socket and input string. It will return an
     * empty string if a socket problem is encountered.
     * 
     * @param socket
     *            the socket connected with the the server
     * @param input
     *            string to be processed by the server process
     * @return string returned by server process
     */
    public static String queryServer(Socket socket, String input) {
	StringBuilder sb = new StringBuilder();
	try {
	    // write text to the socket
	    DataInputStream bis = new DataInputStream(socket.getInputStream());
	    BufferedReader br = new BufferedReader(new InputStreamReader(bis));
	    PrintWriter bw = new PrintWriter(socket.getOutputStream(), true);
	    bw.println(input);
	    bw.flush();
	    String line = null;
	    do {
		line = br.readLine();
		sb.append(line);
	    } while (line != null && line.isEmpty() == false);
	    bis.close();
	    br.close();
	} catch (IOException ioe) {
	    log.warning("Socket I/O error: " + socket.getInetAddress().getHostName() + ":" + socket.getPort());
	    ioe.printStackTrace();
	}
	return sb.toString();
    }

    /**
     * Queries a server with the given socket and input string. It will return an
     * empty string if a socket problem is encountered.
     * 
     * @param socket
     *            the socket connected with the the server
     * @param input
     *            string to be processed by the server process
     * @return string returned by server process
     */
    public static List<CoreMap> stanfordQueryServer(Socket socket, String input) {
	List<CoreMap> sentenceAnns = null;
	try {
	    // write text to the socket
	    //  DataInputStream bis = new DataInputStream(socket.getInputStream());
	    // BufferedReader br = new BufferedReader(new InputStreamReader(bis));
	    ObjectInputStream istream = new ObjectInputStream(socket.getInputStream());
	    PrintWriter bw = new PrintWriter(socket.getOutputStream(), true);
	    bw.println(input);
	    bw.flush();
	    /*- String line = null;
	    // do {
	    line = br.readLine();
	    sb.append(line);
	    // } while (line != null && line.isEmpty() == false);
	     * 
	     */
	    SentenceAnnsSO serializable = (SentenceAnnsSO) istream.readObject();
	    sentenceAnns = serializable.getSentenceAnns();
	    istream.close();
	    bw.close();
	} catch (IOException ioe) {
	    log.warning("Socket I/O error: " + socket.getInetAddress().getHostName() + ":" + socket.getPort());
	    ioe.printStackTrace();
	} catch (Exception e) {
	    e.printStackTrace();
	}
	return sentenceAnns;
    }
    
    
	// needs work
	public static boolean balancedParentheses(List<Chunk> chunks){
		Stack<String> stack = new Stack<String>();
		for (Chunk c: chunks) {
			String str = c.getText();
			if (LEFT_PARENTHESES.contains(str)) 
				stack.push(Integer.toString(LEFT_PARENTHESES.indexOf(str)));
			else if (RIGHT_PARENTHESES.contains(str)) {
				int ind = Integer.parseInt(stack.pop());
				if (ind != RIGHT_PARENTHESES.indexOf(str)) return false;
			}
		}
		return stack.empty();
	}
	
	public static List<Entity> filterByEntities(SurfaceElement se, boolean includeExp) {
		List<Entity> ents = new ArrayList<>();
		LinkedHashSet<SemanticItem> sems = se.filterByEntities();
		if (sems != null) {
			for (SemanticItem sem: sems) {
				if (sem instanceof Entity && 
						(includeExp || !(sem instanceof Expression))) {
					ents.add((Entity)sem);
				}
			}
		}
		return ents;	
	}
	
	public static List<Entity> filterEntitiesBySpan(Document doc, SpanList sp, boolean allowOverlap, boolean includeExp) {
		List<Entity> ents = new ArrayList<>();
		LinkedHashSet<SemanticItem> sems =Document.getSemanticItemsByClassSpan(doc, Entity.class, sp, allowOverlap);
		if (sems != null) {
			for (SemanticItem sem: sems) {
				if (sem instanceof Entity && 
						(includeExp || !(sem instanceof Expression))) {
					ents.add((Entity)sem);
				}
			}
		}
		return ents;
	}
	
	public static List<Predicate> filterByPredicates(SurfaceElement se) {
		List<Predicate> preds = new ArrayList<>();
		LinkedHashSet<SemanticItem> sems = se.filterByPredicates();
		if (sems != null) {
			for (SemanticItem sem: sems) {
				if (sem instanceof Predicate) {
					preds.add((Predicate)sem);
				}
			}
		}
		return preds;	
	}

	
	public static List<String> findSemanticGroups(Set<String> semtypes) {
		Set<String> grpset = new HashSet<>();
		for (String s: semtypes) {
			List<String> grps = Constants.SEMGROUP_MAP.get(s);
			if (grps != null) grpset.addAll(grps);
		}
		List<String> grplist = new ArrayList<>(grpset);
		Collections.sort(grplist);
		return grplist;
	}
	
	public static boolean areAppositive(SRSentence sent, Chunk np1, Chunk np2) {
		List<Chunk> intervening = sent.interveningChunks(np1, np2);
		if (intervening.size() != 1) return false;
		Chunk inter = intervening.get(0);
		Chunk next = sent.nextChunk(np2);
		if ((next!= null && next.isPunctuation()) &&  inter.getText().equals(",")) return true;
		if (LEFT_PARENTHESES.contains(inter.getText())) return true;
		if (APPOSITIVE_INDICATORS.contains(inter.getText())) return true;
		return false;
	}
	
	public static LinkedHashSet<SemanticItem> getSalientSemantics(SurfaceElement e, boolean headOnly) {
		LinkedHashSet<SemanticItem> es = new LinkedHashSet<>();
		if (headOnly) {
			es.addAll(e.getHeadSemantics());
		} else {
			es = e.getSemantics();
		}
		return es;
	}
  
}

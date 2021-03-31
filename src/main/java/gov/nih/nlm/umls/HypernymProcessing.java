package gov.nih.nlm.umls;

import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

import gov.nih.nlm.ling.core.Document;
import gov.nih.nlm.ling.core.SurfaceElement;
import gov.nih.nlm.ling.core.Word;
import gov.nih.nlm.ling.sem.Argument;
import gov.nih.nlm.ling.sem.Concept;
import gov.nih.nlm.ling.sem.Entity;
import gov.nih.nlm.ling.sem.SemanticItem;
import gov.nih.nlm.ner.metamap.ScoredUMLSConcept;
import gov.nih.nlm.semrep.core.Chunk;
import gov.nih.nlm.semrep.core.ChunkSE;
import gov.nih.nlm.semrep.core.SRSentence;
import gov.nih.nlm.semrep.utils.SemRepUtils;

public class HypernymProcessing {
	
	private static Logger log = Logger.getLogger(HypernymProcessing.class.getName());	
	private int hierarchyDBServerPort;
	private String hierarchyDBServerName;
	
	private List<String> GEOA_HYPERNYMS = Arrays.asList("country", "countries", "islands", "continent", "locations", "city", "cities");
	private int MAX_DISTANCE = 5;

	public static List<String> NON_HYPERNYM_CUIS = Arrays.asList("C1457887" // Symptoms
			);
	
	public HypernymProcessing(Properties props) {
		this.hierarchyDBServerPort = Integer.parseInt(props.getProperty("hierarchyDB.server.port", "12349"));
		this.hierarchyDBServerName = props.getProperty("hierarchyDB.server.name", "localhost");
	}
	
	public boolean lookup(String cuiPair) {
		Socket s = 	SemRepUtils.getSocket(hierarchyDBServerName, hierarchyDBServerPort);
		if (s == null) return false;
		long mmbeg = System.currentTimeMillis();
		String answer = SemRepUtils.queryServer(s, cuiPair);
		if (answer != null && !answer.equalsIgnoreCase("null")) {
			if(answer.contains("true")) return true;
			if(answer.contains("false")) return false;
		}
		SemRepUtils.closeSocket(s);
		long mmend = System.currentTimeMillis();
		log.fine("Completed hierarchy database lookup with " + cuiPair + " ..." +(mmend-mmbeg) + " msec.");
		return false;
	}
	
	public List<Argument> hypernymProcessing(Chunk chunk) {
		if (chunk.isNP() == false) return null;
		List<Argument> intraHyp = intraNP(chunk);
		if (intraHyp != null) return intraHyp;
		List<Argument> interHyp = interNP(chunk);
		return interHyp;
	}
	
	public List<Argument> intraNP(Chunk np) {
		SurfaceElement first = null, second = null;
		List<ChunkSE> seList = np.getSurfaceElementList();
		List<Argument> result;
		
		int size = seList.size();
		for(int i = size - 1; i >= 0; i--) {
			ChunkSE sec = seList.get(i);
			if(first == null) {
				if (np.isHeadOf(sec)) 
					first = sec.getSurfaceElement();
			}else {
				if (np.isModifierOf(sec)) {
					second = sec.getSurfaceElement();
					result = findHypernymyBetweenSurfaceElements(first,second,true);
					if (result != null) return result;
					else {
						first = second;
						second = null;
					}
				}
			}
		}
		// OTHER with coordination 
		SurfaceElement head = np.getHead();
		boolean headFound = false;
		boolean otherFound = false;
		boolean conjunctFound = false;
		for(int i = size - 1; i >= 0; i--) {
			SurfaceElement surfEl = seList.get(i).getSurfaceElement();
			if (surfEl.equals(head)) { headFound = true; continue;}
			if (headFound && np.isModifierOf(surfEl) && surfEl.getLemma().equals("other")) {
				otherFound = true; continue;
			}
			if (otherFound && (surfEl.getLemma().equals("and") || surfEl.getLemma().equals("or"))) {
				conjunctFound = true; continue;
			}
			if (conjunctFound && np.isModifierOf(surfEl)) {
				result = findHypernymyBetweenSurfaceElements(surfEl, head, false);
				return result;
			}
		}
		return null;
	}
	
	/***
	 *
	 * @param chunk
	 * @return
	 */
	public List<Argument> interNP(Chunk np) {
		SurfaceElement head = np.getHead();
		SRSentence sent = (SRSentence) head.getSentence();
		
		Chunk next = sent.nextChunk(np);
		int dist = 1;
		while (next != null && dist <= MAX_DISTANCE) {
			// TODO: If (Coordinate(NP,NP2)) return null; // is this possible with the current chunking?
			if (next.isNP()) { 
				SurfaceElement headNext = next.getHead();
				String type = interveningPhrases(sent,np,next);
				if (type != null ) {
					List<Argument> args = null;
					if (type.equals("APPOS") || type.contentEquals("PAREN"))
						args = findHypernymyBetweenSurfaceElements(head,headNext, true);
					else 
						args = findHypernymyBetweenSurfaceElements(head,headNext, false);
					if (args != null) return args;
				}
			}
			next = sent.nextChunk(next);
			dist++;
		}
		return null;
	}
	
	public List<Argument> findHypernymyBetweenSurfaceElements(SurfaceElement first, SurfaceElement second, boolean bothDirections) {
		if(first == null || second == null) return null;
		Document doc = first.getSentence().getDocument();
		LinkedHashSet<SemanticItem> firstEntities = Document.getSemanticItemsByClassSpan(doc,Entity.class,first.getSpan(), true);
		if(firstEntities.size() == 0) return null;
		LinkedHashSet<SemanticItem> secondEntities = Document.getSemanticItemsByClassSpan(doc,Entity.class,second.getSpan(), true);
		if(secondEntities.size() == 0) return null;
		String firstCUI = null, secondCUI = null;
		ScoredUMLSConcept firstConcept = null, secondConcept = null;
		SemanticItem firstEntity = null, secondEntity = null;
		Argument subject, object;
		List<Argument> args = new ArrayList<>();
		for(SemanticItem si: firstEntities) {
			 if(((Entity)si).getSense() instanceof ScoredUMLSConcept) {
				 firstEntity = si;
				 firstConcept = (ScoredUMLSConcept)((Entity)si).getSense();
				 firstCUI = ((Entity)si).getSense().getId();
			 }
		}
		for(SemanticItem si: secondEntities) {
			 if(((Entity)si).getSense() instanceof ScoredUMLSConcept) {
				 secondEntity = si;
				 secondConcept = (ScoredUMLSConcept)((Entity)si).getSense();
				 secondCUI = ((Entity)si).getSense().getId();
			 }
		}
		if (firstConcept == null || secondConcept == null) return null;
		if (firstCUI.equals(secondCUI)) return null;
		
		// Exclude some terms as hypernyms (e.g. Symptoms)
		if (NON_HYPERNYM_CUIS.contains(secondCUI) ||
				(bothDirections && NON_HYPERNYM_CUIS.contains(firstCUI))) return null;
		
		if(!semGroupMatch(firstConcept.getSemGroups(), secondConcept.getSemGroups())) return null;
		if(lookup(firstCUI+secondCUI)) {
			if (allowedGEOA(firstConcept,secondConcept)) {
				subject = new Argument("Subject", firstEntity);
				object = new Argument("Object", secondEntity);
				args.add(subject);
				args.add(object);
				return args;
			}
		}
		if(bothDirections && lookup(secondCUI+firstCUI)) {
			if (allowedGEOA(secondConcept,firstConcept)) {
				subject = new Argument("Subject", secondEntity);
				object = new Argument("Object", firstEntity);
				args.add(subject);
				args.add(object);
				return args;
			}
		}
		return null;
	}
	
	private static boolean semGroupMatch(LinkedHashSet<String> first, LinkedHashSet<String> second) {
		for(String s : first) {
			if(!s.contains("conc") && !s.contains("anat") && second.contains(s)) return true;
		}
		return false;
	}
	
	public String interveningPhrases(SRSentence sentence, Chunk np1, Chunk np2) {
		if (SemRepUtils.areAppositive(sentence,np1,np2)) return "APPOS";
		List<Chunk> intervening = sentence.interveningChunks(np1, np2);
		if (SemRepUtils.balancedParentheses(intervening) == false) return "PAREN";
		if (containsCopularVerb(intervening)) return "COPULA";
		if (containsOther(intervening,np2)) return "OTHER";
		return null;
	}
	

	
	public boolean containsCopularVerb(List<Chunk> intervening) {
		if (intervening == null || intervening.size() < 1) return false;
		List<Word> ws = Chunk.listOfWords(intervening);
		
		int ind = -1;
		for (int i=0; i < ws.size(); i++) {
			Word w = ws.get(i);
			if (w.getLemma().equals("be") && w.getCategory().equals("VB")) { ind = i; break;}
		}
		// BE found, ensure that not followed by a past-participle OR if it does, it is also followed by 'as'
		if (ind >= 0) {
			if (ws.size() == ind +1) return true; // BE is the only intervening element
			if (ws.size() > ind+1) { 
				Word next = ws.get(ind+1);
				if (next.getPos().equals("VBN") == false || (ws.size() > ind+2 && ws.get(ind+2).getLemma().equals("as"))) 
					return true;
			}
		}
		// REMAIN
		for (int i=0; i < ws.size(); i++) {
			Word w = ws.get(i);
			if (w.getLemma().equals("remain") && w.getCategory().equals("VB")) return true;
		}
		return false;
	}
	
	private boolean containsOther(List<Chunk> intervening, Chunk otherCh) {
		if (intervening == null || intervening.size() != 1) return false;
		List<Word> ws = Chunk.listOfWords(intervening);
		if (ws == null) return false;
		
		if (ws.size() == 1) {
			Word w0 = ws.get(0);
			if (w0.getLemma().equals("and") || w0.getLemma().equals("or")) {
				SurfaceElement s = otherCh.leftmostMod();
				if (s != null && s.getLemma().equals("other")) return true;
			}
		}
		return false;
	}


	
	public boolean allowedGEOA(Concept conc1, Concept conc2) {
		if (conc1.getSemtypes().contains("geoa") && conc2.getSemtypes().contains("geoa")) {
			String concName = conc2.getName();
			String[] toks = concName.split("[ ]+");	
			String lastTok = toks[toks.length-1];
			return (GEOA_HYPERNYMS.contains(lastTok));
		} 
		return true;
	}
}

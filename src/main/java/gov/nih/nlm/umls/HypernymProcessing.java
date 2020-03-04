package gov.nih.nlm.umls;

import java.net.Socket;
import java.util.*;
import java.util.logging.Logger;
import org.javatuples.Pair;

import com.sun.javaws.exceptions.InvalidArgumentException;
import gov.nih.nlm.ling.core.Chunk;
import gov.nih.nlm.ling.core.Document;
import gov.nih.nlm.ling.core.Sentence;
import gov.nih.nlm.ling.core.SurfaceElement;
import gov.nih.nlm.ling.sem.Argument;
import gov.nih.nlm.ling.sem.Concept;
import gov.nih.nlm.ling.sem.Entity;
import gov.nih.nlm.ling.sem.SemanticItem;
import gov.nih.nlm.ner.metamap.ScoredUMLSConcept;
import gov.nih.nlm.semrep.core.SRSentence;
import gov.nih.nlm.semrep.utils.SemRepUtils;


public class HypernymProcessing {
	
	private static Logger log = Logger.getLogger(HypernymProcessing.class.getName());	
	private int hierarchyDBServerPort;
	private String hierarchyDBServerName;
	private List<String> GEOA = Arrays.asList("country", "countries", "islands", "continent", "locations", "cities");
	private int MAX_LENGTH = 2;
	private List<String> APPOSITIVES = Arrays.asList("such as", "particularly", "including");

	public HypernymProcessing(Properties props) {
		this.hierarchyDBServerPort = Integer.parseInt(props.getProperty("hierarchyDB.server.port", "12349"));
		this.hierarchyDBServerName = props.getProperty("hierarchyDB.server.name", "localhost");
	}
	
	public boolean find(String input) {
		Socket s = 	SemRepUtils.getSocket(hierarchyDBServerName, hierarchyDBServerPort);
		if (s == null) return false;
		long mmbeg = System.currentTimeMillis();
		String answer = SemRepUtils.queryServer(s, input);
		if (answer != null && !answer.equalsIgnoreCase("null")) {
			if(answer.contains("true")) return true;
			if(answer.contains("false")) return false;
		}
		SemRepUtils.closeSocket(s);
		long mmend = System.currentTimeMillis();
		log.info("Completed processing hierarchy database lookup with " + input + " ..." +(mmend-mmbeg) + " msec.");
		return false;
	}
	
	public List<Argument> intraNP(Chunk chunk) {
		if(!chunk.getChunkType().equalsIgnoreCase("NP"))
			return null;
		SurfaceElement first = null, second = null;
		List<SurfaceElement> seList = chunk.getSurfaceElementList();
		List<Argument> result;
		int size = seList.size();
		for(int i = size - 1; i >= 0; i--) {		
			if(first == null) {
				if(seList.get(i).getChunkRole() == 'H') {
					first = seList.get(i);
				}
			}else {
				if(seList.get(i).getChunkRole() == 'M') {
					second = seList.get(i);
					result = findIntraNPBetweenSurfaceElements(first, second);
					if (result != null) return result;
					else {
						first = second;
						second = null;
					}
				}
			}
		}
		return null;
	}
	
	public List<Argument> findIntraNPBetweenSurfaceElements(SurfaceElement first, SurfaceElement second) {
		if(first == null || second == null) return null;
		Document doc = first.getSentence().getDocument();
		LinkedHashSet<SemanticItem> firstEntities = Document.getSemanticItemsBySpan(doc, first.getSpan(), true);
		if(firstEntities.size() == 0) return null;
		LinkedHashSet<SemanticItem> secondEntities = Document.getSemanticItemsBySpan(doc, second.getSpan(), true);
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
		if (firstCUI.equals("C1457887") || secondCUI.equals("C1457887")) return null;
		if (firstCUI.equals(secondCUI)) return null;
		if(!semGroupMatch(firstConcept.getSemGroups(), secondConcept.getSemGroups())) return null;
		if(find(firstCUI+secondCUI)) {
			subject = new Argument("subject", firstEntity);
			object = new Argument("object", secondEntity);
			args.add(subject);
			args.add(object);
			return args;
		}
		if(find(secondCUI+firstCUI)) {
			subject = new Argument("subject", secondEntity);
			object = new Argument("object", firstEntity);
			args.add(subject);
			args.add(object);
			return args;
		}
		return null;
	}
//
//	public static boolean semGroupMatch(LinkedHashSet<String> first, LinkedHashSet<String> second) {
//		for(String s : first) {
//			if(!s.contains("conc") && !s.contains("anat") && second.contains(s)) return true;
//		}
//		return false;
//	}

	public List<Argument> hypernymy(ScoredUMLSConcept head, ScoredUMLSConcept mod) {
		LinkedHashSet<String> headGroup = head.getSemGroups();
		LinkedHashSet<String> modGroup = mod.getSemGroups();

		List<Argument> args = new ArrayList<>();

		for (String h : headGroup) {
			for (String m : modGroup) {
				if (h.equals(m)){
					if (h.equals("anat") || h.equals("conc")) {// shared group equals Anatomy or Concepts
						return null;
					}
				}
			}
		}
		String headCUI = head.getId(); //TODO: check if this gets CUI
		String modCUI = mod.getId();
		if (find(modCUI + headCUI)) {// check UMLS hiserarchy for modCUI: headCUI
			//not sure how to get term variable from concept
		}
	}

	public boolean interveningPhrasesOK(Chunk NP1, Chunk NP2) {
		try {
			List<Chunk> interveningList = getChunksInBetween(NP1, NP2);
			int distance = interveningList.size();
			if (distance == 1) {
				boolean apposite = mustBeAppositives(NP1, NP2);
				if (apposite) return true;
			}
			if (parenthesis(interveningList)) {
				return false;
			}
			if (distance > 1) {
				boolean verbsFound = findverbs(interveningList);
				if (verbsFound) return true;
			}
			if (distance == 2) {

			}
		} catch (InvalidArgumentException e){

		}
		return false;
	}


	public boolean mustBeAppositives(Chunk NP1, Chunk NP2) {
		String string1 = NP1.getString();
		String string2 = NP2.getString();

		if (string1.contains(",") && string2.contains(",")) {
			return true;
		} else if (string2.contains("(")) {
			return true;
		} else {
			for (String word: APPOSITIVES) {
				if (string2.contains(word)) {
					return true;
				}
			}
		}
		return false;
	}

	public boolean parenthesis(List<Chunk> interveningList){
		Stack<String> stack = new Stack<String>();
		for (Chunk c: interveningList) {
			String str = c.getString();
			if (str.equals("(") || str.equals("[") || str.equals("{") ) {
				stack.push("(");
			} else if (stack.peek().equals(")") || stack.peek().equals("]") || stack.peek().equals("}")) {
				stack.pop();
			}
		}
		return stack.empty();
	}




	public boolean findverbs(List<Chunk> interveningList) {
		for (int i = 0; i < interveningList.size() - 1; i++) {
			List<String> pair = interveningList.get(i).getPosLemma();
			if ((pair.get(0).equals("VBP")) && (pair.get(1).equals("be"))) {
				List<String> followingPair = interveningList.get(i+1).getPosLemma();
				if (! (followingPair.get(0).equals("VBP"))) {
					return true;
				} else {
					if (i < interveningList.size() - 2) {
						if (interveningList.get(i+2).getPosLemma().get(1).equals("as")) {
							return true;
						}
					}
				}
			}
			if (pair.get(1).equals("remain")) {
				return true;
			}
		}
		return false;
	}


	public boolean allowedGEOA(Concept Concept1, Concept Concept2) {
		if (Concept1.getSemtypes().contains("geoa") && Concept2.getSemtypes().contains("geoa")) {
			String last = Concept2.getName();
			for (String s: GEOA) {
				if (last.substring(last.length() - 1) == s) {
					return true;
				}
			}
			return false;
		}
		return false;
	}

	public List<Chunk> getChunksInBetween(Chunk chunk1, Chunk chunk2) throws InvalidArgumentException{
		if (chunk1.getSurfaceElementList().get(0).getSentence().getId() == chunk2.getSurfaceElementList().get(0).getSentence().getId()) {
			SRSentence sentence = (SRSentence) chunk1.getSurfaceElementList().get(0).getSentence();
			List<Chunk> chunks = sentence.getChunks();

			List<Chunk> listOfChunks = new ArrayList<Chunk>();
			boolean addChunk = false;
			for (Chunk c: chunks) {
				if (c == chunk1) {
					addChunk = true;
				}
				if (addChunk) {
					listOfChunks.add(c);
				}
				if (c == chunk2) {
					return listOfChunks;
				}
			}
		} else {
			log.warning("Chunk not in same sentence");
		}
		return null;
	}
}

package gov.nih.nlm.umls;

import java.io.*;
import java.lang.ref.PhantomReference;
import java.net.Socket;
import java.util.*;
import java.util.logging.Logger;

import gov.nih.nlm.ling.core.Chunk;
import gov.nih.nlm.ling.core.Document;
import gov.nih.nlm.ling.core.SurfaceElement;
import gov.nih.nlm.ling.sem.Argument;
import gov.nih.nlm.ling.sem.Concept;
import gov.nih.nlm.ling.sem.Entity;
import gov.nih.nlm.ling.sem.SemanticItem;
import gov.nih.nlm.ner.metamap.ScoredUMLSConcept;
import gov.nih.nlm.nls.lvg.Util.In;
import gov.nih.nlm.semrep.utils.SemRepUtils;
import org.apache.xpath.operations.Mod;


import opennlp.tools.tokenize.Tokenizer;
import gov.nih.nlm.ling.util.FileUtils;


public class HypernymProcessing {

	private static Logger log = Logger.getLogger(HypernymProcessing.class.getName());
	private int hierarchyDBServerPort;
	private String hierarchyDBServerName;

	public HypernymProcessing(Properties props) {
		this.hierarchyDBServerPort = Integer.parseInt(props.getProperty("hierarchyDB.server.port", "12349"));
		this.hierarchyDBServerName = props.getProperty("hierarchyDB.server.name", "localhost");
	}

	public boolean find(String input) {
		Socket s = SemRepUtils.getSocket(hierarchyDBServerName, hierarchyDBServerPort);
		if (s == null) return false;
		long mmbeg = System.currentTimeMillis();
		String answer = SemRepUtils.queryServer(s, input);
		if (answer != null && !answer.equalsIgnoreCase("null")) {
			if (answer.contains("true")) return true;
			if (answer.contains("false")) return false;
		}
		SemRepUtils.closeSocket(s);
		long mmend = System.currentTimeMillis();
		log.info("Completed processing hierarchy database lookup with " + input + " ..." + (mmend - mmbeg) + " msec.");
		return false;
	}

	public List<Argument> intraNP(Chunk chunk) {
		if (!chunk.getChunkType().equalsIgnoreCase("NP"))
			return null;
		SurfaceElement first = null, second = null;
		List<SurfaceElement> seList = chunk.getSurfaceElementList();
		List<Argument> result;
		int size = seList.size();
		for (int i = size - 1; i >= 0; i--) {
			if (first == null) {
				if (seList.get(i).getChunkRole() == 'H') {
					first = seList.get(i);
				}
			} else {
				if (seList.get(i).getChunkRole() == 'M') {
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
		if (first == null || second == null) return null;
		Document doc = first.getSentence().getDocument();
		LinkedHashSet<SemanticItem> firstEntities = Document.getSemanticItemsBySpan(doc, first.getSpan(), true);
		if (firstEntities.size() == 0) return null;
		LinkedHashSet<SemanticItem> secondEntities = Document.getSemanticItemsBySpan(doc, second.getSpan(), true);
		if (secondEntities.size() == 0) return null;
		String firstCUI = null, secondCUI = null;
		ScoredUMLSConcept firstConcept = null, secondConcept = null;
		SemanticItem firstEntity = null, secondEntity = null;
		Argument subject, object;
		List<Argument> args = new ArrayList<>();
		for (SemanticItem si : firstEntities) {
			if (((Entity) si).getSense() instanceof ScoredUMLSConcept) {
				firstEntity = si;
				firstConcept = (ScoredUMLSConcept) ((Entity) si).getSense();
				firstCUI = ((Entity) si).getSense().getId();
			}
		}
		for (SemanticItem si : secondEntities) {
			if (((Entity) si).getSense() instanceof ScoredUMLSConcept) {
				secondEntity = si;
				secondConcept = (ScoredUMLSConcept) ((Entity) si).getSense();
				secondCUI = ((Entity) si).getSense().getId();
			}
		}
		if (firstConcept == null || secondConcept == null) return null;
		if (firstCUI.equals("C1457887") || secondCUI.equals("C1457887")) return null;// too many false positives
		if (firstCUI.equals(secondCUI)) return null;
		if (!semGroupMatch(firstConcept.getSemGroups(), secondConcept.getSemGroups())) return null;
		if (find(firstCUI + secondCUI)) {
			subject = new Argument("subject", firstEntity);
			object = new Argument("object", secondEntity);
			args.add(subject);
			args.add(object);
			return args;
		}
		if (find(secondCUI + firstCUI)) {
			subject = new Argument("subject", secondEntity);
			object = new Argument("object", firstEntity);
			args.add(subject);
			args.add(object);
			return args;
		}
		return null;
	}

	public static boolean semGroupMatch(LinkedHashSet<String> first, LinkedHashSet<String> second) {
		for (String s : first) {
			if (!s.contains("conc") && !s.contains("anat") && second.contains(s)) return true;
		}
		return false;
	}
// should work for 'aspirin is an analgesic' or 'aspirin has been used as an analgesic'
// This is not implemented
// MaxDistance could be 5 for now, but we need to test. We need to also know how punctuations are being chunked.

	/***
	 * Check if the arguments are right
	 * check how to iterate the chunks
	 */

	public List<Argument> InterNPHypernymy(Chunk NP, int MaxDistance) {
		if (NP == null) return null;
		Document doc = NP.getSentence().getDocument();
		LinkedHashSet<SemanticItem> NpEntities = Document.getSemanticItemsBySpan(doc, NP.getSpan(), true);
		if (NpEntities.size() == 0) return null;
		String NpCUI = null;
		ScoredUMLSConcept firstConcept = null;
		SemanticItem NPEntity = null;
		Argument subject, object;
		List<Argument> args = new ArrayList<>();
		for (SemanticItem si : NpEntities) {
			if (((Entity) si).getSense() instanceof ScoredUMLSConcept) {
				NPEntity = si;
				firstConcept = (ScoredUMLSConcept) ((Entity) si).getSense();
				NpCUI = ((Entity) si).getSense().getId();
			}
		}
		if (firstConcept == null) return null;
		int i = 0;
		// how to iterate through the chunks?
//		while (i < MaxDistance){
//			for
//
//
//			}
//
		return null;
	}


	// Where we consult the Berkeley DB tree for hierarchical relationships
	//semantic group match

	/**
	 * not yet tested
	 *
	 * @param HeadConcept
	 * @param ModConcept
	 * @return isa-relationship betweeh head concept and mod concept if there is one
	 */
	public String Hypernymy(ScoredUMLSConcept HeadConcept, ScoredUMLSConcept ModConcept) {
		LinkedHashSet<String> HeadSemGroup = HeadConcept.getSemGroups();
		LinkedHashSet<String> ModSemGroup = ModConcept.getSemGroups();

		//find bigger list for outer loop
		LinkedHashSet<String> outer = (HeadSemGroup.size() > ModSemGroup.size()) ? HeadSemGroup : ModSemGroup;
		LinkedHashSet<String> inner = (HeadSemGroup.size() > ModSemGroup.size()) ? ModSemGroup : HeadSemGroup;

		for (String s1 : outer) {
			for (String s2 : inner) {
				if (s1.equals(s2)) {
					if (s1 == "anat" || s1 == "conc") {//anatomy or concepts
						return null;
					}
				}
			}
		}
		String HeadCUI = HeadConcept.getId();
		String ModCUI = ModConcept.getId();
		if (find(HeadCUI + ModCUI)) {
			return "ModCUI-ISA-HeadCUI";
		} else if (find(ModCUI + HeadCUI)) {
			return "HeadCUI-ISA-ModCUI";
		}
		return null;

	}

	//returns whether two NP chunks are coordinated. We currently do not have the means to determine this, so just return false for now (a placeholder)
	public boolean Coordinate(Chunk NP1, Chunk NP2) {
		return false;
	}

	/***
	 * check whether the phrases between the phrases of interest conform to syntactic rules
	 * Assuming that NP1 and NP2 are next to each other
	 * @param NP1 Chunk
	 * @param NP2 Chunk
	 * @return True if the phraes have a valid syntactic relationship
	 */
	public boolean InterveningPhrasesOK(Chunk NP1, Chunk NP2) {
		int Distance = NP1.getSurfaceElementList().size() + NP2.getSurfaceElementList().size();
		//get sentence and get the chunks btw thm]sm
		List<SurfaceElement> NP1List = NP1.getSurfaceElementList();
		List<SurfaceElement> NP2List = NP2.getSurfaceElementList();

		// not sure if this is the correct understanding of Interveninglist
		List<SurfaceElement> InterveningList = new ArrayList<>();
		InterveningList.addAll(NP1List);
		InterveningList.addAll(NP2List);

		if (Distance == 1) {
			boolean Apposite = MustBeAppositives(NP1, NP2);
			if (Apposite) {
				return true;
			}
		}
		// If there is a single left or right parenthesis between the phrases, that means one of the phrases is in a parenthetical expression, and we do not want to create ISA relationships for those (though this can change)
		if (Parenthesis(InterveningList)) {
			return false;
		}

		if (Distance > 1) {

		}
		return false;

	}

	/***
	 * look for the verb BE or REMAIN to license hypernym
	 * @param InterveningList from InterveningPhrasesOk
	 * @return true if
	 */
	public boolean FindVerbs(List<SurfaceElement> InterveningList) {
		for (SurfaceElement se : InterveningList) {
			boolean Be1 = false;

		}
		return false;
	}


	// Checks that InterveningList does not have an unclosed parenthesis, so count left and right parentheses and
	// ensure that they have the same count,

	/**
	 * @param InterveningList from InterveningPhrasesOK. A list of chunks between 2 NP chunks
	 * @return True if there is a closed pharenthesis
	 */
	public boolean Parenthesis(List<SurfaceElement> InterveningList) {
		int size = InterveningList.size();
		Stack<String> stack = new Stack<>();
		for (SurfaceElement se : InterveningList) {
			//not sure where pharenthees are captured; trying few approaches
//			String p = se.getText();
			String p = se.getPos();
			if (p == "(") {
				stack.push("(");
			} else if (p == ")") {
				stack.pop();
			}
		}
		return stack.size() == 0;
	}

	//util for checking comma in btw
	public boolean MustBeAppositives(Chunk NP1, Chunk NP2) {
		String Str1 = NP1.toString();
		String Str2 = NP2.toString();
		//naive method to check for commas; not sure if there is a more starightforward way
		boolean comma = (Str1.contains(",") && Str2.contains(",")); // the analgesic, aspirin,
		boolean LeftPharenthesis = Str2.contains("("); //the analgesic (aspirin)
		boolean trigger = (Str2.contains("such as") || Str2.contains("particularly") || Str2.contains("including"));

		return comma || LeftPharenthesis || trigger;
	}

	/***
	 * // geographical locations can cause false positives (one example was Paris-ISA-France)
	 // this will only allow things like Japan-ISA-Country
	 * @param Concept1
	 * @param Concept2
	 */
	private boolean AllowedGEOA(Concept Concept1, Concept Concept2) {
		Tokenizer tokenizer;
		try {
			InputStream inputstream = new FileInputStream("/Users/shinkamori/IdeaProjects/semrepj-test/src/main/java/gov/nih/nlm/umls/lexicon/GEOA/Islands.txt");
			HashMap<Character, HashSet<String>> islands = linesFromStream(inputstream, "UTF-8");
			





			if (Concept1.getId() == "geoa" && Concept2.getId() == "geoa") {
				String Last = Concept2.getName();
				String LastToken = Last.substring(Last.lastIndexOf(";") + 1);
				//not sure where to check if last is a subset of some concept
				//can just have  a list of countries, etc


			}
		} catch(FileNotFoundException a) {
			System.out.println("file not found");
		} catch(IOException a) {
			System.out.println("io exception");
		}

		return false;
	}

	/***
	 * Reads file and creates a HashMap of the first character of lines in the string and the
	 * string.
	 * @param stream
	 * @param encoding
	 * @return
	 * @throws IOException
	 */
	public static HashMap<Character, HashSet<String>>  linesFromStream(InputStream stream, String encoding) throws IOException {
		String strLine;
		HashMap<Character, HashSet<String>> lines = new HashMap<>();
		BufferedReader br = new BufferedReader(new InputStreamReader(stream,encoding));
		while ((strLine = br.readLine()) != null)  {
			char letter = strLine.charAt(0);
			HashSet<String> list = lines.get(letter);
			if (list == null) {
				list = new HashSet<String>();
				lines.put(letter, list);
			}
			list.add(strLine);

		}
		br.close();
		return lines;
	}

}



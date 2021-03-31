package gov.nih.nlm.semrep;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import gov.nih.nlm.bioscores.core.Expression;
import gov.nih.nlm.bioscores.core.GenericCoreferencePipeline;
import gov.nih.nlm.ling.core.Document;
import gov.nih.nlm.ling.core.MultiWord;
import gov.nih.nlm.ling.core.Sentence;
import gov.nih.nlm.ling.core.SpanList;
import gov.nih.nlm.ling.core.SurfaceElement;
import gov.nih.nlm.ling.core.Word;
import gov.nih.nlm.ling.process.IndicatorAnnotator;
import gov.nih.nlm.ling.sem.Argument;
import gov.nih.nlm.ling.sem.Concept;
import gov.nih.nlm.ling.sem.Entity;
import gov.nih.nlm.ling.sem.Ontology;
import gov.nih.nlm.ling.sem.SemanticItem;
import gov.nih.nlm.ling.sem.SemanticItemFactory;
import gov.nih.nlm.ling.util.FileUtils;
import gov.nih.nlm.ner.AnnotationFilter;
import gov.nih.nlm.ner.LargestSpanFilter;
import gov.nih.nlm.ner.MultiThreadClient;
import gov.nih.nlm.semrep.core.Chunk;
import gov.nih.nlm.semrep.core.MedlineDocument;
import gov.nih.nlm.semrep.core.SRIndicator;
import gov.nih.nlm.semrep.core.SRSentence;
import gov.nih.nlm.semrep.core.SemRepFactory;
import gov.nih.nlm.semrep.core.TokenInfo;
import gov.nih.nlm.semrep.preprocess.CoreNLPProcessing;
import gov.nih.nlm.semrep.preprocess.OpenNLPProcessing;
import gov.nih.nlm.semrep.utils.SemRepOutputUtils;
import gov.nih.nlm.umls.HypernymProcessing;
import gov.nih.nlm.umls.OntologyDatabase;
import gov.nih.nlm.umls.lexicon.LexiconMatch;
import gov.nih.nlm.umls.lexicon.LexiconWrapper;
import nu.xom.ParsingException;
import nu.xom.ValidityException;

/**
 * Main class for SemRep Java implementation
 * 
 * @author Zeshan Peng
 * @author Halil Kilicoglu
 *
 */

public class SemRep {
	private static Logger log = Logger.getLogger(SemRep.class.getName());

	private static MultiThreadClient nerAnnotator;
	private static OpenNLPProcessing openNLPClient;
	private static CoreNLPProcessing coreNLP;
	private static HypernymProcessing hpClient;
	private static LexiconWrapper lexicon;
	private static IndicatorAnnotator indAnnotator;
	private static OntologyDatabase relOntology;
	
	private static SemanticInterpretation interpreter;
//	private static CoreferenceResolution corefResolver;
	
	private static boolean harmonizeWithLexicon = false;
	private static boolean coreferenceResolution = false;
	

	/**
	 * Performs lexical/syntactic analysis of a given document based on
	 * user.preprocess property. The document is expected to have an ID and text.
	 * <p>
	 * This process currently involves sentence splitting, tokenization, POS
	 * tagging, lemmatization, and chunking.
	 * 
	 * @param doc
	 *            The document to process
	 * @throws IOException
	 *             when relevant modules cannot be loaded
	 */
	public static void lexicalSyntacticAnalysis(Document doc) throws IOException {
		String option = System.getProperty("user.preprocess", "corenlp");
		if (option.equals("corenlp"))
			// coreNLPAnalysis(doc);
			coreNLPAnalysisUsingServer(doc);
		else if (option.equals("opennlp"))
			openNLPAnalysis(doc);
		else {
			log.warning("Preprocessing option not valid, processing with CoreNLP by default..");
			coreNLPAnalysis(doc);
		}
	}

	/**
	 * Analyzes the document with respect to lexical and syntactic information using
	 * OpenNLP models. It also incorporates relevant UMLS Specialist Lexicon
	 * records.
	 * 
	 * @param document
	 *            The document to process
	 * @throws IOException
	 *             if an openNLP model is not available
	 */
	public static void openNLPAnalysis(Document doc) throws IOException {
		if (openNLPClient == null)
			openNLPClient = new OpenNLPProcessing(false);
		List<Sentence> sentList = new ArrayList<>();
		openNLPClient.segment(doc.getText(), sentList);
		for (Sentence sent : sentList) {
			SRSentence csent = (SRSentence) sent;
			csent.setDocument(doc);
			csent.setSectionAbbreviation("tx");
			csent.setSentenceIDInSection(sent.getId());
			//tokenize
			List<TokenInfo> tokens = new ArrayList<>();
			openNLPClient.tokenize(csent.getText(), tokens);
			csent.addCompleted(SRSentence.Processing.TOKEN);
			// lexical lookup
			List<LexiconMatch> lexmatches = getLexicalItems(tokens);
			//pos tagging
			//			openNLPClient.tagPOS(tokens,lexmatches);
			openNLPClient.tag(tokens);
			csent.addCompleted(SRSentence.Processing.TAG);
			//lemmatize
			//			openNLPClient.lemmatize(tokens,lexmatches);
			openNLPClient.lemmatize(tokens);
			csent.addCompleted(SRSentence.Processing.LEMMA);
			//chunk
			openNLPClient.chunk(csent, tokens);
			csent.addCompleted(SRSentence.Processing.CHUNK);
			List<LexiconMatch> updatedLexMatches = lexicon.filterLexMatchesByPOS(lexmatches);
			csent.setLexicalItems(updatedLexMatches);
			csent.addCompleted(SRSentence.Processing.LEXREC);
		}
		doc.setSentences(sentList);
		if (harmonizeWithLexicon) {
			for (Sentence sent : doc.getSentences()) {
				harmonizeWithLexMatches((SRSentence) sent);
			}
		}
	}

	/**
	 * Preprocesses a document using Core NLP functionality (except chunking, which
	 * uses openNLP). It also incorporates UMLS Specialist Lexicon records.
	 * 
	 * @param document
	 *            Document to process
	 * @throws IOException
	 *             when openNLP chunking model cannot be loaded
	 */
	public static void coreNLPAnalysis(Document doc) throws IOException {

		if (coreNLP == null)
			coreNLP = CoreNLPProcessing.getInstance(System.getProperties());
		CoreNLPProcessing.coreNLP(doc);

		// Document doc = CoreNLPProcessing.coreNLPUsingServer(orgdoc);
		// List<Sentence> listSent = doc.getSentences();
		
		for (Sentence sent : doc.getSentences()) {
			SRSentence csent = (SRSentence) sent;
			int beg = csent.getSpan().getBegin();
			csent.setSectionAbbreviation("tx");
			csent.setSentenceIDInSection(sent.getId());
			csent.addCompleted(SRSentence.Processing.SSPLIT);
			csent.addCompleted(SRSentence.Processing.TOKEN);
			csent.addCompleted(SRSentence.Processing.TAG);
			csent.addCompleted(SRSentence.Processing.LEMMA);
			List<Word> words = csent.getWords();
			List<TokenInfo> tokenInfos = new ArrayList<>();
			for (Word w : words) {
				tokenInfos.add(new TokenInfo(w.getText(), w.getSpan().getBegin() - beg, w.getSpan().getEnd() - beg,
						w.getPos(), w.getLemma()));
			}
			if (openNLPClient == null)
				openNLPClient = new OpenNLPProcessing(true);
			openNLPClient.chunk(csent, tokenInfos);
			csent.addCompleted(SRSentence.Processing.CHUNK);
			List<LexiconMatch> lexmatches = getLexicalItems(tokenInfos);
			List<LexiconMatch> updatedLexMatches = lexicon.filterLexMatchesByPOS(lexmatches);
			csent.setLexicalItems(updatedLexMatches);
			csent.addCompleted(SRSentence.Processing.LEXREC);
		}
		if (harmonizeWithLexicon) { 
			for (Sentence sent : doc.getSentences()) {
				harmonizeWithLexMatches((SRSentence) sent);
			}
		}
	}

	/**
	 * Preprocesses a document using Core NLP Server functionality (except chunking,
	 * which uses openNLP). It also incorporates UMLS Specialist Lexicon records.
	 * 
	 * @param document
	 *            Document to process
	 * @throws IOException
	 *             when openNLP chunking model cannot be loaded
	 */
	public static void coreNLPAnalysisUsingServer(Document doc) throws IOException {

		//if (coreNLP == null)
		//    coreNLP = CoreNLPProcessing.getInstance(System.getProperties());
		CoreNLPProcessing.coreNLPUsingServer(doc);

		// Document doc = CoreNLPProcessing.coreNLPUsingServer(orgdoc);
		// List<Sentence> listSent = doc.getSentences();
		for (Sentence sent : doc.getSentences()) {
			SRSentence csent = (SRSentence) sent;
			int beg = csent.getSpan().getBegin();
			csent.setSectionAbbreviation("tx");
			csent.setSentenceIDInSection(sent.getId());
			csent.addCompleted(SRSentence.Processing.SSPLIT);
			csent.addCompleted(SRSentence.Processing.TOKEN);
			csent.addCompleted(SRSentence.Processing.TAG);
			csent.addCompleted(SRSentence.Processing.LEMMA);
			List<Word> words = csent.getWords();
			List<TokenInfo> tokenInfos = new ArrayList<>();
			for (Word w : words) {
				tokenInfos.add(new TokenInfo(w.getText(), w.getSpan().getBegin() - beg, w.getSpan().getEnd() - beg,
						w.getPos(), w.getLemma()));
			}
//			if (openNLPClient == null)
//				openNLPClient = new OpenNLPProcessing(true);
//			openNLPClient.chunk(csent, tokenInfos);
//			csent.addCompleted(SRSentence.Processing.CHUNK);
			List<LexiconMatch> lexmatches = getLexicalItems(tokenInfos);
			List<LexiconMatch> updatedLexMatches = lexicon.filterLexMatchesByPOS(lexmatches);
			csent.setLexicalItems(updatedLexMatches);
			csent.addCompleted(SRSentence.Processing.LEXREC);
		}
		if (harmonizeWithLexicon) { 
			for (Sentence sent : doc.getSentences()) {
				harmonizeWithLexMatches((SRSentence) sent);
			}
		}
	}

	private static List<LexiconMatch> getLexicalItems(List<TokenInfo> tokenInfo) {
		List<LexiconMatch> lexmatches = new ArrayList<>();
		try {
			if (lexicon == null)
				// lexicon = LexiconWrapper.getInstance("lexAccess.properties");
				lexicon = LexiconWrapper.getInstance(); // lexAccess.properties are combined to semrepj.properties and removed
			lexmatches = lexicon.findLexiconMatches(tokenInfo);
		} catch (SQLException sqle) {
			log.warning("Unable to identify lexical items for the sentence ... Skipping.");
			;
			sqle.printStackTrace();
		}
		return lexmatches;
	}

	private static void harmonizeWithLexMatches(SRSentence sentence) {
		Document doc = sentence.getDocument();
		int sentOffset = sentence.getSpan().getBegin();
		List<LexiconMatch> lexmatches = sentence.getLexicalItems();
		for (LexiconMatch lex : lexmatches) {
			List<TokenInfo> tokens = lex.getMatch();
			SpanList sp = new SpanList(tokens.get(0).getBegin() + sentOffset,
					tokens.get(tokens.size() - 1).getEnd() + sentOffset);
			List<Word> words = sentence.getWordsInSpan(sp);
			Word head = MultiWord.findHeadFromCategory(words);
			doc.getSurfaceElementFactory().createSurfaceElementIfNecessary(doc, sp, head.getSpan(), true);
		}
	}
	
	private static void openNLPChunking(Document doc) throws IOException {
		for (Sentence sent : doc.getSentences()) {
			SRSentence csent = (SRSentence) sent;
			int beg = csent.getSpan().getBegin();
//			List<Word> words = csent.getWords();
			List<SurfaceElement> ses = csent.getSurfaceElements();
			List<TokenInfo> tokenInfos = new ArrayList<>();
//			for (Word w : words) {
			for (SurfaceElement se: ses) {
//				tokenInfos.add(new TokenInfo(w.getText(), w.getSpan().getBegin() - beg, w.getSpan().getEnd() - beg,
//						w.getPos(), w.getLemma()));
				tokenInfos.add(new TokenInfo(se.getText(), se.getSpan().getBegin() - beg, se.getSpan().getEnd() - beg,
				se.getPos(), se.getLemma()));
			}
			if (openNLPClient == null)
				openNLPClient = new OpenNLPProcessing(true);
			openNLPClient.chunk(csent, tokenInfos);
			csent.addCompleted(SRSentence.Processing.CHUNK);
		}
	}

	/**
	 * Processes a <code>Document</code> object semantically, by identifying named
	 * entities and extracting relations. The document is expected to have gone
	 * through sentence splitting, lexical-syntactic analysis already.
	 * 
	 * @param doc
	 *            the document to process
	 * @throws IOException
	 *             if it fails to open the input file or to create and write to the
	 *             output file
	 */
	public static void semanticAnalysis(Document doc) throws IOException {
		if (doc == null)
			return;
		if (doc.getSentences() == null || doc.getSentences().size() == 0) {
			log.info("Document needs to be sentence-split for semantic processing.." + doc.getId());
			return;
		}
		referentialAnalysis(doc);
		indAnnotator.annotateIndicators(doc, System.getProperties());
		GenericCoreferencePipeline.coreferentialMentionDetection(doc);
		openNLPChunking(doc);
		CoreferenceResolution.mentionReferentLinking(doc);
		hypernymAnalysis(doc);
		relationalAnalysis(doc);
		log.info("Semantic processing complete.");
	}

	/**
	 * Identifies and normalizes named entities. Currently uses MetaMapLite to map
	 * text to UMLS Metathesaurus concepts and GNormPlus to map gene/protein names
	 * to NCBI Gene IDs.
	 * 
	 * @param doc
	 *            The document on which to perform named entity recognition
	 */
	public static void referentialAnalysis(Document doc) {
		// named entity recognition
		Map<SpanList, LinkedHashSet<Ontology>> annotations = new HashMap<>();
		if (nerAnnotator == null)
			nerAnnotator = new MultiThreadClient(System.getProperties());
		nerAnnotator.annotate(doc, System.getProperties(), annotations);
		//for (SpanList sp : annotations.keySet()) {
		//	LinkedHashSet<Ontology> terms = annotations.get(sp);
		//}

		// combine/filter named entity recognition results
		AnnotationFilter merger = new LargestSpanFilter();
		Map<SpanList, LinkedHashSet<Ontology>> mergedAnnotations = merger.filter(annotations);

		// create entities for named entity results
		List<SpanList> sps = new ArrayList<>(mergedAnnotations.keySet());
		Collections.sort(sps, new Comparator<SpanList>() {
			@Override
			public int compare(SpanList a, SpanList b) {
				if (SpanList.atLeft(a, b))
					return -1;
				else if (SpanList.atLeft(b, a))
					return 1;
				else if (a.equals(b))
					return -1;
				int as = a.getBegin();
				int bs = b.getBegin();
				return (as - bs);

			}
		});
		SemanticItemFactory sif = doc.getSemanticItemFactory();
		for (SpanList sp : sps) {
			LinkedHashSet<Ontology> terms = mergedAnnotations.get(sp);
			List<Word> wordList = doc.getWordsInSpan(sp);
			SpanList headSpan = MultiWord.findHeadFromCategory(wordList).getSpan();
			Concept sense = null;
			LinkedHashSet<Concept> concepts = new LinkedHashSet<>();
			Iterator<Ontology> iter = terms.iterator();
			while (iter.hasNext()) {
				Concept conc = (Concept) iter.next();
				if (sense == null)
					sense = conc;
				concepts.add(conc);
			}
			sif.newEntity(doc, sp, headSpan, sense.getSemtypes().toString(), concepts, sense);
		}
		LinkedHashSet<SemanticItem> entities = Document.getSemanticItemsByClass(doc, Entity.class);
		for (SemanticItem sem : entities) {
			Entity ent = (Entity) sem;
			log.info("Entity:	" + ent.toString());
		}
		
	}

	/**
	 * Identifies hypernymic (ISA) relations in a document. We expect that
	 * lexical/syntactic analysis and named entity recognition has been performed.
	 * 
	 * @param doc
	 *            The document to process for hypernymy
	 */
	public static void hypernymAnalysis(Document doc) {
		SemRepFactory sif = (SemRepFactory)doc.getSemanticItemFactory();
		if (hpClient == null)
			hpClient = new HypernymProcessing(System.getProperties());
		List<Argument> args;
		for (Sentence cs : doc.getSentences()) {
			for (Chunk chunk : ((SRSentence) cs).getChunks()) {
				System.out.println("CHUNK:" + chunk.toString());
				args = hpClient.hypernymProcessing(chunk);
				if (args != null)
					sif.newSRImplicitRelation(doc, "ISA", args);
			}
		}
	}

	/**
	 * The main method to extract semantic relationships. We expect that
	 * lexical/syntactic analysis and named entity recognition has been performed.
	 * 
	 * @param doc
	 *            The document to process for semantic relations
	 */
	public static void relationalAnalysis(Document doc) {
		if (interpreter == null) 
			interpreter = new SemanticInterpretation(relOntology);
		interpreter.semanticInterpretation(doc);
	}

	/**
	 * Process from a directory of files if user specified input format is directory
	 * 
	 * @param inPath
	 *            the path of the directory
	 * @param outPath
	 *            the path of the output directory
	 * @throws IOException
	 *             if it fails to open input files or to create and write to the
	 *             output file
	 */
	public static void processDirectory(String inPath, String outPath) throws IOException {
		File[] files = new File(inPath).listFiles();
		File dir = new File(outPath);

		if (!dir.isDirectory()) {
			dir.mkdirs();
		}
		for (File file : files) {
			String filename = file.getName();
			String[] fields = filename.split("\\.");
			processSingleFile(inPath + "/" + filename, outPath + "/" + fields[0]);
		}
	}

	/**
	 * Processes a single file if the user-specified input format is single file.
	 * The file can be in either plaintext, medline or medlinexml format.
	 * 
	 * @param inPath
	 *            the path of the single input file
	 * @param outPath
	 *            the path of the output file
	 * @throws IOException
	 *             if it fails to open the input file or to create and write to the
	 *             output file
	 */
	public static void processSingleFile(String inPath, String outPath) throws IOException {
		String inputTextFormat = System.getProperty("user.inputtextformat");
		BufferedReader br = new BufferedReader(new FileReader(inPath));
		List<Document> processedDocuments = new ArrayList<>();

		if (inputTextFormat.equalsIgnoreCase("plaintext")) {
			log.info("Processing plain text file : " + inPath);
			int count = 0;
			String line;
			StringBuilder sb = new StringBuilder();
			do {
				line = br.readLine();
				if ((line == null || line.trim().isEmpty()) && !sb.toString().trim().isEmpty()) {
					count++;
					Document doc = new Document(Integer.toString(count), sb.toString());
					SemRepFactory sif = new SemRepFactory(doc,new HashMap<Class<? extends SemanticItem>,Integer>());
					doc.setSemanticItemFactory(sif);
					lexicalSyntacticAnalysis(doc);
					sb = new StringBuilder();
					semanticAnalysis(doc);
					processedDocuments.add(doc);
				} else {
					sb.append(line + " ");
				}
			} while (line != null);
		} else if (inputTextFormat.equalsIgnoreCase("medline")) {
			log.info("Processing Medline input file : " + inPath);
			List<MedlineDocument> mdList = MedlineDocument.parseMultiMedLines(br);
			for (MedlineDocument md : mdList) {
				SemRepFactory sif = new SemRepFactory(md,new HashMap<Class<? extends SemanticItem>,Integer>());
				md.setSemanticItemFactory(sif);
				lexicalSyntacticAnalysis(md);
				semanticAnalysis(md);
				processedDocuments.add(md);
			}
		} else if (inputTextFormat.equalsIgnoreCase("medlinexml")) {
			log.info("Processing Medline input file : " + inPath);
			List<MedlineDocument> mdList = MedlineDocument.parseMultiMedLinesXML(inPath);
			for (MedlineDocument md : mdList) {
				SemRepFactory sif = new SemRepFactory(md,new HashMap<Class<? extends SemanticItem>,Integer>());
				md.setSemanticItemFactory(sif);
				lexicalSyntacticAnalysis(md);
				semanticAnalysis(md);
				processedDocuments.add(md);
			}
			System.out.println(mdList.size());
			System.out.println(mdList.get(0).getText());
		}
		BufferedWriter bw = new BufferedWriter(new FileWriter(outPath, false));
		SemRepOutputUtils.writeOutput(processedDocuments, bw);
		bw.close();
		br.close();
	}

	/**
	 * Process interactively on the command line. Expect a line of text to be
	 * entered.
	 * 
	 * @throws IOException
	 *             if it fails to open the input file or to create and write to the
	 *             output file
	 */
	public static void processInteractively() throws IOException {
		Scanner in = new Scanner(System.in);
		BufferedWriter bw = new BufferedWriter(new PrintWriter(System.out));
		List<Document> processedDocuments;

		System.out.println("Enter text to process (type 'exit' to exit):");
		String input = in.nextLine();
		while (input != null && !input.equalsIgnoreCase("exit") && input.length() != 0) {
			Document doc = new Document("00000000", input);
			SemRepFactory sif = new SemRepFactory(doc,new HashMap<Class<? extends SemanticItem>,Integer>());
			doc.setSemanticItemFactory(sif);
			lexicalSyntacticAnalysis(doc);
			semanticAnalysis(doc);
			processedDocuments = new ArrayList<>();
			processedDocuments.add(doc);
			SemRepOutputUtils.writeOutput(processedDocuments, bw,"humanReadable");
			bw.flush();
			System.out.println("\nEnter text to process (type 'exit' to exit):");
			input = in.nextLine();
		}
		bw.close();
		in.close();
	}

	/**
	 * Set up user specified options for the program
	 * 
	 * @param args
	 *            user specified properties
	 * @return properties object with given user specified options
	 * @throws FileNotFoundException
	 *             if default property file is not found
	 * @throws IOException
	 *             if given property file is not existed
	 */
	public static Properties getOptionProps(String[] args) throws FileNotFoundException, IOException {
//		if (args.length < 2) {
//			System.out.println("Usage: semrepj --inputpath={in_path} --outputpath={out_path}.");
//			System.exit(2);
//		}
		Properties optionProps = new Properties();
		int i = 0;
		while (i < args.length) {
			if (args[i].substring(0, 2).equals("--")) {
				String[] fields = args[i].split("=");
				if (fields[0].equals("--configfile")) {
					String configFilename = fields[1];
					File f = new File(configFilename);
					if (f.exists() && !f.isDirectory())
						optionProps.load(new FileReader(new File(configFilename)));
					else {
						System.out.println("Cannot find specified configuration file. Please check file name.");
						System.exit(1);
					}
				} else if (fields[0].equals("--indexdir")) {
					optionProps.setProperty("metamaplite.index.dir.name", fields[1]);
				} else if (fields[0].equals("--modelsdir")) {
					optionProps.setProperty("opennlp.models.dir", fields[1]);
				} else if (fields[0].equals("--inputformat")) {
					optionProps.setProperty("user.inputformat", fields[1]);
				} else if (fields[0].equals("--outputformat")) {
					optionProps.setProperty("user.outputformat", fields[1]);
				} else if (fields[0].equals("--inputpath")) {
					optionProps.setProperty("user.inputpath", fields[1]);
				} else if (fields[0].equals("--outputpath")) {
					optionProps.setProperty("user.outputpath", fields[1]);
				} else if (fields[0].equals("--inputtextformat")) {
					optionProps.setProperty("user.inputtextformat", fields[1]);
				} else if (fields[0].equals("--annsource")) {
					optionProps.setProperty("user.annsource", fields[1]);
				} else if (fields[0].equals("--includes")) {
					optionProps.setProperty("user.output.includes", fields[1]);
				}
			}
			i++;
		}
		return optionProps;
	}

	/**
	 * Set up overall properties for the program (combine both default properties
	 * and user specified properties)
	 * 
	 * @param args
	 *            user specified properties
	 * @return properties object with combined overall properties for the program
	 * @throws FileNotFoundException
	 *             if default property file is not found
	 * @throws IOException
	 *             if given property file does not exist
	 */
	public static Properties getProps(String[] args) throws FileNotFoundException, IOException {
		Properties defaultProps = new Properties(System.getProperties());
		Properties configFileProps = FileUtils.loadPropertiesFromFile("semrepj.properties");
		defaultProps.putAll(configFileProps);
		Properties optionProps = getOptionProps(args);
		defaultProps.putAll(optionProps);
		return defaultProps;
	}

	/**
	 * Initializes logging from a file configuration (logging.properties).
	 * 
	 */
	public static void initLogging() {
		try {
			InputStream config = SemRep.class.getResourceAsStream("/logging.properties");
			LogManager.getLogManager().readConfiguration(config);
			config.close();
		} catch (IOException ex) {
			log.warning("Could not open logging configuration file. Logging not configured (console output only).");
		}
	}

	/**
	 * Initializes logging, named entity recognizers, and other resources.
	 */
	public static void init() throws IOException {
		initLogging();
		// lexicon = LexiconWrapper.getInstance("lexAccess.properties");
		lexicon = LexiconWrapper.getInstance(); // lexAccess.properties is combined to semrepj.properties
		nerAnnotator = new MultiThreadClient(System.getProperties());
		relOntology = OntologyDatabase.getInstance(System.getProperty("ontologyDB.home","ontologyDB"));
		Constants.initSemGroups();
		
		try {
			indAnnotator = new IndicatorAnnotator(
					SRIndicator.loadSRIndicatorsFromFile(System.getProperty("semrulesinfo"), 0));
		} catch (IOException ioe) {
			log.severe("Unable to find the indicator rule file. Won't be able to identify semantic relations.");
			ioe.printStackTrace();
		} catch (ParsingException pe) {
			log.severe("Unable to parse the indicator rule file. Won't be able to identify semantic relations.");
			pe.printStackTrace();
		}
		harmonizeWithLexicon = Boolean.parseBoolean(System.getProperty("user.harmonizeWithLexicon"));
		coreferenceResolution = Boolean.parseBoolean(System.getProperty("user.coreference"));
		if (coreferenceResolution)  {
			try {
				CoreferenceResolution.init(System.getProperties());
			} catch (IOException ioe) {
				log.warning("Unable to start coreference resolver. Won't be able to identify coreference relations.");
				ioe.printStackTrace();
			}
		}
	}

	public static void main(String[] args) throws IOException, ValidityException, ParsingException {
		long beg = System.currentTimeMillis();
		System.setProperties(getProps(args));
		init();

		String inputFormat = System.getProperty("user.inputformat");
		String inPath = System.getProperty("user.inputpath");
		String outPath = System.getProperty("user.outputpath");

		log.info("Starting SemRep processing...");
		if (inputFormat.equalsIgnoreCase("dir")) {
			processDirectory(inPath, outPath);
		} else if (inputFormat.equalsIgnoreCase("singlefile")) {
			processSingleFile(inPath, outPath);
		} else if (inputFormat.equalsIgnoreCase("interactive")) {
			processInteractively();
		}
		long end = System.currentTimeMillis();
		log.info("Completed all " + (end - beg) + " msec.");
	}
}

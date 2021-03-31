package gov.nih.nlm.semrep.preprocess;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

//import gov.nih.nlm.ling.core.Chunk;
import gov.nih.nlm.ling.core.Sentence;
import gov.nih.nlm.ling.core.Span;
import gov.nih.nlm.ling.core.SurfaceElement;
import gov.nih.nlm.ling.core.Word;
import gov.nih.nlm.ling.core.WordLexeme;
import gov.nih.nlm.ling.process.SentenceSegmenter;
import gov.nih.nlm.semrep.core.Chunk;
import gov.nih.nlm.semrep.core.ChunkSE;
import gov.nih.nlm.semrep.core.SRSentence;
import gov.nih.nlm.semrep.core.TokenInfo;
import opennlp.tools.chunker.ChunkerME;
import opennlp.tools.chunker.ChunkerModel;
import opennlp.tools.lemmatizer.DictionaryLemmatizer;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;

/**
 * This class contains functions for processing strings using opennlp
 * 
 * @author Zeshan Peng
 * @author Halil Kilicoglu
 *
 */
public class OpenNLPProcessing implements SentenceSegmenter, Tokenization, POSTagging, Lemmatization, Chunking {

	private POSTaggerME tagger;
	private Tokenizer tokenizer;
	private DictionaryLemmatizer lemmatizer;
	private ChunkerME chunker;
	private SentenceDetectorME sentenceDetector;

	/**
	 * Initializes openNLP models
	 * @param chunkingOnly	whether or not to load the chunking model only
	 * @throws IOException		if a openNLP model file is not found
	 */

	public OpenNLPProcessing(boolean chunkingOnly) throws IOException {
		InputStream modelIn = null;
		if (!chunkingOnly) {
			modelIn = new FileInputStream(
					System.getProperty("opennlp.en-sent.bin.path", "data/models/en-sent.bin"));
			SentenceModel model = new SentenceModel(modelIn);
			sentenceDetector = new SentenceDetectorME(model);

			modelIn = new FileInputStream(
					System.getProperty("opennlp.en-token.bin.path", "data/models/en-token.bin"));
			TokenizerModel tokenModel = new TokenizerModel(modelIn);
			tokenizer = new TokenizerME(tokenModel);

			modelIn = new FileInputStream(
					System.getProperty("opennlp.en-pos.bin.path", "data/models/en-pos-maxent.bin"));
			POSModel posModel = new POSModel(modelIn);
			tagger = new POSTaggerME(posModel);

			modelIn = new FileInputStream(
					System.getProperty("opennlp.en-lemmatizer.bin.path", "data/models/en-lemmatizer.bin"));
			lemmatizer = new DictionaryLemmatizer(modelIn);

		} 

		modelIn = new FileInputStream(
				System.getProperty("opennlp.en-chunker.bin.path", "data/models/en-chunker.bin"));
		ChunkerModel chunkerModel = new ChunkerModel(modelIn);
		chunker = new ChunkerME(chunkerModel);
	}

	@Override
	public void segment(String text, List<Sentence> sentences) {
		String sents[] = sentenceDetector.sentDetect(text);
		opennlp.tools.util.Span[] sentenceSpans = sentenceDetector.sentPosDetect(text);
		SRSentence s;
		for (int i = 0; i < sents.length; i++) {
			s = new SRSentence("S" + Integer.toString(i+1), sents[i],
					new gov.nih.nlm.ling.core.Span(sentenceSpans[i].getStart(), sentenceSpans[i].getEnd()));
			sentences.add(s);
			s.addCompleted(SRSentence.Processing.SSPLIT);
		}
	}

	@Override
	public void tokenize(String sentence, List<TokenInfo> tokens) {
		String[] toks = tokenizer.tokenize(sentence);
		opennlp.tools.util.Span[] tokSpans0 = tokenizer.tokenizePos(sentence);
		int[] begins = new int[tokSpans0.length];
		int[] ends = new int[tokSpans0.length];
		for (int i=0; i < tokSpans0.length; i++) {
			begins[i] = tokSpans0[i].getStart();
			ends[i] = tokSpans0[i].getEnd();
		}
		tokens.addAll(TokenInfo.convert(toks,begins,ends));
		//   	List<TokenInfo> newTsps = fixTokenization(tsps);
		//   	return newTsps;
	}

	@Override
	public void tag(List<TokenInfo> tokenSpans) {
		String[] tokens = TokenInfo.getTokensFromInfo(tokenSpans);
		String tags[] = tagger.tag(tokens);
		for (int i=0; i < tokenSpans.size(); i++) {
			TokenInfo n = tokenSpans.get(i);
			n.setPos(tags[i]);
		}
		//    	fixPOSTagging(tokenSpans,lexmatches);
	}

	@Override
	public void lemmatize(List<TokenInfo> tokenSpans) {
		String[] tokens = TokenInfo.getTokensFromInfo(tokenSpans);
		String[] tags = TokenInfo.getPOSTagsFromInfo(tokenSpans);
		String[] lemmas = lemmatizer.lemmatize(tokens, tags);
		for (int i=0; i < tokenSpans.size(); i++) {
			TokenInfo n = tokenSpans.get(i);
			n.setLemma(lemmas[i]);
		}
		//    	fixLemmatization(tokenSpans,lexmatches);
	}

	@Override
	public void chunk(SRSentence s, List<TokenInfo> tokens)  {
		List<Chunk> chunkList = new ArrayList<>();
		String[] toks = TokenInfo.getTokensFromInfo(tokens);
		String[] tags = TokenInfo.getPOSTagsFromInfo(tokens);
		String[] chunkTags = chunker.chunk(toks, tags);

		SurfaceElement se;
		Chunk chunk = null;
		List<ChunkSE> seList = null;

		for (int i = 0; i < tokens.size(); i++) {
			String[] fields = chunkTags[i].split("-");

			se = s.getSurfaceElementsFromSpan(new Span(tokens.get(i).getBegin() + s.getSpan().getBegin(),
					tokens.get(i).getEnd() + s.getSpan().getBegin())).get(0);
			//	    w.setSentence(s);
			//w.setChunkRole('X');
			//	    wordList.add(w);
			if (fields[0].equals("B")) {
				if (chunk == null) {	
					chunk = new Chunk(null, fields[1]);
					seList = new ArrayList<ChunkSE>();
					//			    w.setChunk(chunk);
					seList.add(new ChunkSE(se,'X'));
				} else {
					chunk.setSurfaceElementList(seList);
					chunkList.add(chunk);
					chunk = new Chunk(null, fields[1]);
					seList = new ArrayList<ChunkSE>();
					//			    w.setChunk(chunk);
					seList.add(new ChunkSE(se,'X'));
				}
			} else if (fields[0].equals("I")) {
				if (seList == null) seList = new ArrayList<ChunkSE>();
				//	    	w.setChunk(chunk);
				seList.add(new ChunkSE(se,'X'));
			} else if (fields[0].equals("O")) {
				if (chunk != null) {
					//				if (chunk.getChunkType().equals("NP")) setChunkRolesForSurfaceElements(seList);
					chunk.setSurfaceElementList(seList);
					chunkList.add(chunk);
				}
				chunk = new Chunk(null, tags[i]);
				seList = new ArrayList<ChunkSE>();
				//			w.setChunk(chunk);
				seList.add(new ChunkSE(se,'X'));
				chunk.setSurfaceElementList(seList);
				chunkList.add(chunk);
				chunk = null;
			}
			if (i == tokens.size() - 1 && chunk != null) {
				//	    	if (chunk.getChunkType().equals("NP")) setChunkRolesForSurfaceElements(seList);
				chunk.setSurfaceElementList(seList);
				chunkList.add(chunk);
			}
		}
		setChunkRoles(chunkList);
		//	s.setWords(wordList);
		//	sentSEList.addAll(wordList);
		//	s.setSurfaceElements(sentSEList);
		s.setChunks(chunkList);
	}
	
	private void setChunkRoles(List<Chunk> chunks) {
		if (chunks == null || chunks.size() ==0) return;
		for (Chunk c: chunks) {
			//    		if (c.isNP() || c.isVP())  {
			List<ChunkSE> sels = c.getSurfaceElementList();
			if (sels == null) continue;
			if (sels.size() == 1) {
				sels.get(0).setChunkRole('H');
				continue;
			}
			String tag;
			boolean headFound = false;
			for (int i=sels.size()-1; i >=0; i--) {
				ChunkSE sec = sels.get(i);
				SurfaceElement se = sec.getSurfaceElement();
				tag = se.getPos();
				if (c.isNP()) {
					if (!headFound) {
						if(tag.startsWith("NN") || tag.startsWith("JJ") || tag.startsWith("VBG")) {
							sec.setChunkRole('H');
							headFound = true;
						}
					} else {
						if (se.isAlphanumeric() && !tag.startsWith("DT")) 
							sec.setChunkRole('M');
					}
				} else if (c.isVP()) {
					if (!headFound) {
						if(tag.startsWith("VB")) {
							sec.setChunkRole('H');
							headFound = true;
						}
					} else {
						if (se.isAlphanumeric()) 
							sec.setChunkRole('M');
					}
				} else if (c.isADJP()) {
					if (!headFound) {
						if(tag.startsWith("JJ")) {
							sec.setChunkRole('H');
							headFound = true;
						}
					} else {
						if (se.isAlphanumeric()) 
							sec.setChunkRole('M');
					}
				} else if (c.isPP()) {
					if (!headFound) {
						if(tag.startsWith("IN")) {
							sec.setChunkRole('H');
							headFound = true;
						}
					} else {
						if (se.isAlphanumeric()) 
							sec.setChunkRole('M');
					}
				} else if (c.isADVP()) {
					if (!headFound) {
						if(tag.startsWith("RB")) {
							sec.setChunkRole('H');
							headFound = true;
						}
					} else {
						if (se.isAlphanumeric()) 
							sec.setChunkRole('M');
					}
				}
			}
		}
	}
}

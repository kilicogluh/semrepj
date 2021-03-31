package gov.nih.nlm.semrep.core;

import java.util.ArrayList;
import java.util.List;

import gov.nih.nlm.ling.core.Sentence;
import gov.nih.nlm.ling.core.Span;
import gov.nih.nlm.ling.core.SpanList;
import gov.nih.nlm.ling.core.SurfaceElement;
import gov.nih.nlm.ling.core.Word;
import gov.nih.nlm.ling.sem.Entity;
import gov.nih.nlm.nls.lexCheck.Lib.LexRecord;
import gov.nih.nlm.semrep.utils.SemRepUtils;
import gov.nih.nlm.umls.lexicon.LexiconMatch;

/**
 * This class extends {@link gov.nih.nlm.ling.core.Sentence} class to include other sentence-related 
 * information relevant to SemRep, such as chunk information.
 * 
 * @author Zeshan Peng
 *
 */
public class SRSentence extends Sentence {

	List<Chunk> chunks;
	List<LexiconMatch> lexicalItems;
	List<SurfaceElement> usedElements;
	
	String subsection;
	String sectionAbbreviation;
	String sentenceIDInSection;
	List<Processing> completedProcessing;
	
	public enum Processing {
		SSPLIT, TOKEN, LEXREC, TAG, LEMMA, PARSE, DEPPARSE, CHUNK, NER
	}; 
	
	public SRSentence(String id, String text, Span span) {
		super(id, text, span);
		this.subsection = "";
		this.sectionAbbreviation = "";
		this.sentenceIDInSection = "";
	}
	
	public SRSentence(String id, String text, Span span, String sectionAbbr) {
		super(id, text, span);
		this.sectionAbbreviation = sectionAbbr;
		this.subsection = "";
		this.sentenceIDInSection = "";
	}
	
	public SRSentence(String id, String text, Span span, List<Chunk> chunks, String sectionAbbr) {
		super(id,text,span);
		this.chunks = chunks;
		this.sectionAbbreviation = sectionAbbr;
		this.subsection = "";
		this.sentenceIDInSection = "";
	}
	
	public void setChunks(List<Chunk> chunks) {
		this.chunks = chunks;
	}
	
	public List<Chunk> getChunks() {
		return this.chunks;
	}

	public List<LexiconMatch> getLexicalItems() {
		return lexicalItems;
	}

	public void setLexicalItems(List<LexiconMatch> lexicalItems) {
		this.lexicalItems = lexicalItems;
	}

	
	public List<SurfaceElement> getUsedElements() {
		return usedElements;
	}

	public void setUsedElements(List<SurfaceElement> usedElements) {
		this.usedElements = usedElements;
	}
	
	public void addUsed(SurfaceElement el) {
		if (usedElements == null) usedElements = new ArrayList<>();
		usedElements.add(el);
	}
	
	public boolean hasBeenUsed(SurfaceElement el) {
		return (usedElements != null && usedElements.contains(el));
	}
	
	public List<String> getTags() {
		List<Word> wordList = this.getWords();
		List<String> tagList = new ArrayList<String>();
		for(int i = 0; i < wordList.size(); i++) {
			tagList.add(wordList.get(i).getPos());
		}
		return tagList;
	}
	
	public List<Processing> getCompleted() {
		return completedProcessing;
	}

	public void setCompleted(List<Processing> completedProcessing) {
		this.completedProcessing = completedProcessing;
	}
	
	public void addCompleted(Processing processing) {
		if (completedProcessing == null) completedProcessing = new ArrayList<>();
		completedProcessing.add(processing);
	}

	public void setSubsection(String subsection) {
		this.subsection = subsection;
	}
	
	public String getSubsection() {
		return this.subsection;
	}
	
	public void setSectionAbbreviation(String abbr) {
		this.sectionAbbreviation = abbr;
	}
	
	public String getSectionAbbreviation() {
		return this.sectionAbbreviation;
	}
	
	public void setSentenceIDInSection(String id) {
		this.sentenceIDInSection = id;
	}
	
	public String getSentenceIDInSection() {
		return this.sentenceIDInSection;
	}
	
	public boolean inTitle() {
		return (sectionAbbreviation.equals("ti"));
	}
	
	public List<String> getRemainingFieldsForTextOutput() {
		List<String> fields = new ArrayList<String>();
		fields.add(Integer.toString(this.getSpan().getBegin()));
		fields.add(Integer.toString(this.getSpan().getEnd()));
		fields.add(this.getText());
		return fields;
	}
	
	public List<List<LexRecord>> getLexicalRecord(SurfaceElement se) {
		Span sp = getSpan();
		if (lexicalItems == null || 
				getSurfaceElements().contains(se) == false) return new ArrayList<>();
		List<List<LexRecord>> recs = new ArrayList<>();
		for (int i=0; i < lexicalItems.size(); i++) {
			LexiconMatch m = lexicalItems.get(i);
			List<TokenInfo> toks = m.getMatch();
			SpanList toksp = new SpanList(toks.get(0).getBegin() + sp.getBegin(),
					toks.get(toks.size() - 1).getEnd() + sp.getBegin());
			if (SpanList.subsume(se.getSpan(), toksp)) {
				recs.add(m.getLexRecords());
			}
		}
		return recs;
	}
		
	public Chunk nextChunk(Chunk ch) {
		if (chunks == null) return null;
		for (int i=0; i < chunks.size(); i++) {
			Chunk ci = chunks.get(i);
			if (ci.equals(ch)) {
				if (i == chunks.size()-1) return null;
				return chunks.get(i+1);
			}
		}
		return null;
	}
	
	public CuedChunk nextCuedChunk(Chunk ch, CueType t) {
		Chunk next = nextChunk(ch);
		while (next != null) {
			Chunk prev = previousChunk(next);
			// Looking for a cued argument
			if (t!= null && prev.equals(ch)) { next = nextChunk(next);continue;}
			
			if ((t==null || t == CueType.PREPOSITION) && prev.isPP()) 
				return new CuedChunk(next,prev,CueType.PREPOSITION);
			if ((t==null || t == CueType.PARENTHESIS) && SemRepUtils.LEFT_PARENTHESES.contains(prev.getText())) 
				return new CuedChunk(next,prev,CueType.PARENTHESIS);
			if ((t==null || t == CueType.APPOSITIVE) && prev.getText().equals(",")) 
				return new CuedChunk(next,prev,CueType.APPOSITIVE);
			if ((t==null || t == CueType.BE) && prev.isBE())
				return new CuedChunk(next,prev,CueType.BE);
			if (t == null) return new CuedChunk(next,null,null);
			next = nextChunk(next);
		}
		return null;
	}
	
	public CuedChunk previousCuedChunk(Chunk ch, CueType t) {
		Chunk prev = previousChunk(ch);
		while (prev != null) {
			Chunk next = nextChunk(prev);
			if (t!= null && next.equals(ch)) { prev = previousChunk(prev);continue;}
			if ((t==null || t == CueType.PREPOSITION) && next.isPP()) 
				return new CuedChunk(prev,next,CueType.PREPOSITION);
			if ((t==null || t == CueType.PARENTHESIS) && SemRepUtils.LEFT_PARENTHESES.contains(next.getText())) 
				return new CuedChunk(prev,next,CueType.PARENTHESIS);
			if ((t==null || t == CueType.APPOSITIVE) && next.getText().equals(",")) 
				return new CuedChunk(prev,next,CueType.APPOSITIVE);
			if ((t==null || t == CueType.BE) && next.isBE()) 
				return new CuedChunk(prev,next,CueType.BE);
			if (t == null) return new CuedChunk(prev,null,null);
			prev = previousChunk(prev);
		}
		return null;
	}
	
	public Chunk previousChunk(Chunk ch) {
		if (chunks == null) return null;
		for (int i=chunks.size()-1; i >= 0; i--) {
			Chunk ci = chunks.get(i);
			if (ci.equals(ch)) {
				if (i == 0) return null;
				return chunks.get(i-1);
			}
		}
		return null;
	}
	
	
	public List<Chunk> chunksRight(Chunk ch) {
		int ind = chunks.indexOf(ch);
		if (ind == chunks.size() -1) return new ArrayList<>();
		return chunks.subList(ind+1, chunks.size());
	}
	
	public List<Chunk> chunksLeft(Chunk ch) {
		int ind = chunks.indexOf(ch);
		if (ind == 0) return new ArrayList<>();
		return chunks.subList(0, ind);
	}
	
	public List<Chunk> interveningChunks(Chunk ch1, Chunk ch2) {
		List<Chunk> intervening = new ArrayList<>();
		int ind1 = chunks.indexOf(ch1);
		int ind2 = chunks.indexOf(ch2);
		if (ind1 == -1 || ind2 == -1 || ind1== ind2) return intervening;
		if (ind1 < ind2)  intervening = chunks.subList(ind1+1,ind2);
		else intervening = chunks.subList(ind2+1, ind1);
		return intervening;
	}
	
	public boolean adjacentChunks(Chunk ch1, Chunk ch2) {
		return (interveningChunks(ch1,ch2).size() == 0);
	}
	
	public Chunk nextNP(Chunk ch, boolean withConcept) {
		Chunk n = nextChunk(ch);
		if (n == null) return null;
		List<Entity> ents = n.getEntities();
		while (n.isNP() == false || (withConcept && ents.size() == 0)) {
			n = nextChunk(n);
			if (n == null) return null;
			ents = n.getEntities();
		}
		if (n.isNP()) return n;
		return null;
	}
	
	public Chunk nextVP(Chunk ch) {
		Chunk v = nextChunk(ch);
		if (v == null) return null;
		while (v.isVP() == false) {
			v = nextChunk(v);
			if (v == null) return null;
		}
		if (v.isVP()) return v;
		return null;
	}
	
	public CuedChunk nextCuedNP(Chunk ch, boolean withConcept, CueType t) {
		CuedChunk n = nextCuedChunk(ch,t);
		if (n == null) return null;
		Chunk nc = n.getChunk();
		List<Entity> ents = nc.getEntities();
		while (nc.isNP() == false || (withConcept && ents.size() == 0)) {
			n = nextCuedChunk(nc,t);
			if (n == null) return null;
			nc = n.getChunk();
			ents = nc.getEntities();
		}
		if (nc.isNP()) return n;
		return null;
	}
	
	public Chunk previousNP(Chunk ch, boolean withConcept) {
		Chunk n = previousChunk(ch);
		if (n == null) return null;
		List<Entity> ents = n.getEntities();
		while (n.isNP() == false || (withConcept && ents.size() == 0)) {
			n = previousChunk(n);
			if (n == null) return null;
			ents = n.getEntities();
		}
		if (n.isNP()) return n;
		return null;
	}
	
	public CuedChunk previousCuedNP(Chunk ch, boolean withConcept, CueType t) {
		CuedChunk n = previousCuedChunk(ch,t);
		if (n == null) return null;
		Chunk nc = n.getChunk();
		List<Entity> ents = nc.getEntities();
		while (nc.isNP() == false || (withConcept && ents.size() == 0)) {
			n = previousCuedChunk(nc,t);
			if (n == null) return null;
			nc = n.getChunk();
			ents = nc.getEntities();
		}
		if (nc.isNP()) return n;
		return null;
	}
	
		
}

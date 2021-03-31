package gov.nih.nlm.semrep.core;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;

import gov.nih.nlm.ling.core.Sentence;
import gov.nih.nlm.ling.core.Span;
import gov.nih.nlm.ling.core.SurfaceElement;
import gov.nih.nlm.ling.core.Word;
import gov.nih.nlm.ling.sem.Entity;
import gov.nih.nlm.ling.sem.Predicate;
import gov.nih.nlm.ling.sem.SemanticItem;
import gov.nih.nlm.semrep.Constants;
import gov.nih.nlm.semrep.utils.SemRepUtils;

public class Chunk {
	
	private List<ChunkSE> seList;
	private String chunkType;

	
	public Chunk(List<ChunkSE> seList, String chunkType) {
		this.seList = seList;
		this.chunkType = chunkType;
	}
	
	
	public String getChunkType() {
		return this.chunkType;
	}
	
	public void setChunkType(String chunkType) {
		this.chunkType = chunkType;
	}
	
	public List<ChunkSE> getSurfaceElementList() {
		return this.seList;
	}
	
	public void setSurfaceElementList(List<ChunkSE> seList) {
		this.seList = seList;
	}
	
	public Span getSpan() {
		if (seList == null) throw new IllegalStateException("Chunk with no elements: " + toString());
		int beg = seList.get(0).getSurfaceElement().getSpan().getBegin();
		int end = seList.get(seList.size()-1).getSurfaceElement().getSpan().getBegin();
		return new Span(beg,end);
	}

	public String getText() {
		StringBuilder sb = new StringBuilder();
		if (seList == null || seList.size() == 0) return sb.toString();
		SurfaceElement se0 = seList.get(0).getSurfaceElement();
		SurfaceElement sen =seList.get(seList.size()-1).getSurfaceElement();
		Sentence sent = se0.getSentence();
		return sent.getStringInSpan(new Span(se0.getSpan().getBegin(),sen.getSpan().getEnd()));
	}
	
	public SRSentence getSentence() {
		if (seList == null || seList.size() == 0) return null;
		SurfaceElement se0 = seList.get(0).getSurfaceElement();
		return (SRSentence)se0.getSentence();
	}
	
	public boolean isNP() {
		return this.chunkType.equals("NP") || this.chunkType.equals("NN");
	}
	
	public boolean isVP() {
		return this.chunkType.equals("VP");
	}
	
	public boolean isPP() {
		return this.chunkType.equals("PP");
	}
	
	public boolean isADJP() {
		return this.chunkType.equals("ADJP");
	}
	
	public boolean isADVP() {
		return this.chunkType.equals("ADVP");
	}
	
	public boolean isSBAR() {
		return this.chunkType.equals("SBAR");
	}
	
	public boolean isBE() {
		return this.isVP() && Constants.BE_VERBS.contains(getHead().getHead().getLemma());
	}
	
	public boolean isINF() {
		return this.isVP() && seList.get(0).getSurfaceElement().getPos().equals("TO");
	}
	
	public boolean isCOMPL() {
		return (this.isPP() || this.isSBAR()) && 
				seList.get(0).getSurfaceElement().getLemma().contentEquals("that");
	}
	
	public SurfaceElement getHead() {
//		if (isNP() == false) return null;
		if (seList == null) throw new IllegalStateException("Chunk with no elements: " + toString());
		for(int i = seList.size() - 1; i >= 0; i--) {
			ChunkSE se = seList.get(i);
			if(se.getChunkRole() == 'H') {
				return se.getSurfaceElement();
			}
			if (se.getChunkRole() == 'M') {
				return null;
			}
		}
		return null;
	}
	
	public List<SurfaceElement> getModifiers() {
		if (isNP() == false) return null;
		List<SurfaceElement> mods = new ArrayList<>();
		if (seList == null) throw new IllegalStateException("Chunk with no elements: " + toString());
		for(int i = 0; i < seList.size(); i++) {
			ChunkSE se = seList.get(i);
			if (se.getChunkRole() == 'M') {
				mods.add(se.getSurfaceElement());
			}
		}
		return mods;
	}
	
	public SurfaceElement rightmostMod() {
		List<SurfaceElement> mods = getModifiers();
		if (mods.size() == 0) return null;
		return mods.get(mods.size()-1);
	}
	
	public SurfaceElement leftmostMod() {
		List<SurfaceElement> mods = getModifiers();
		if (mods.size() == 0) return null;
		return mods.get(0);
	}
	
	public int indexOf(SurfaceElement surfEl) {
		if (seList == null) throw new IllegalStateException("Chunk with no elements: " + toString());
		for(int i = 0; i < seList.size(); i++) {
			ChunkSE se = seList.get(i);
			if (se.getSurfaceElement().equals(surfEl)) return i;
		}
		return -1;
	}

	public static Chunk findChunk(SurfaceElement se) {
		SRSentence sent = (SRSentence)se.getSentence();
		List<Chunk> chunks = sent.getChunks();
		for (Chunk ch: chunks) {
			if (ch.indexOf(se) >=0) return ch;
		}
		return null;
	}
	
	public boolean isHeadOf(SurfaceElement surfEl) {
		return getHead().equals(surfEl);
	}
	
	public boolean isHeadOf(ChunkSE sec) {
		if (seList == null) return false;
		return (seList.contains(sec) && sec.getChunkRole() == 'H');
	}
	
	public boolean isModifierOf(SurfaceElement surfEl) {
		List<SurfaceElement> mods = getModifiers();
		if (mods.size() == 0) return false;
		for (int i=0; i < mods.size(); i++) {
			if (mods.get(i).equals(surfEl)) return true;
		}
		return false;
	}
	
	public boolean isModifierOf(ChunkSE sec) {
		if (seList == null) return false;
		return (seList.contains(sec) && sec.getChunkRole() == 'M');
	}
	
	public boolean isPunctuation() {
		if (seList == null) return false;
		for (ChunkSE se: seList) {
			if (Pattern.matches("\\p{Punct}+", se.getSurfaceElement().getText()) == false) return false;
		}
		return true;
	}
		
	public List<Word> listOfWords() {
		if (seList == null) throw new IllegalStateException("Chunk with no elements: " + toString());
		List<Word> ws = new ArrayList<>();
		for (int i=0; i < seList.size(); i++) {
			ws.addAll(seList.get(i).getSurfaceElement().toWordList());
		}
		return ws;
	}
	
	public static List<Word> listOfWords(List<Chunk> chunks) {
		List<Word> ws = new ArrayList<>();
		for (Chunk ch: chunks) {
			ws.addAll(ch.listOfWords());
		}
		return ws;
	}
	
	public List<Entity> getEntities() {
		if (isNP() == false) return new ArrayList<>();
		List<Entity> ents = new ArrayList<>();
		for (int i=seList.size()-1; i >=0; i--) {
			ents.addAll(SemRepUtils.filterByEntities(seList.get(i).getSurfaceElement(),false));
		}
		return ents;
	}
	
	public List<Predicate> getPredicates() {
		List<Predicate> preds = new ArrayList<>();
		for (int i=seList.size()-1; i >=0; i--) {
			preds.addAll(SemRepUtils.filterByPredicates(seList.get(i).getSurfaceElement()));
		}
		return preds;
	}
	
	public boolean inPassiveVoice() {
		if (seList == null || !isVP()) return false;
		SurfaceElement head= getHead();
		if (head.getPos().equals("VBN") == false) return false;
		SRSentence sent = getSentence();
		Chunk next = sent.nextChunk(this);
		// TODO probably need a limit
		while (next!= null && next.isADVP()) next = sent.nextChunk(next);
		if (next == null) return false;
		if (next.isPP()) {
			SurfaceElement pp = next.getHead();
			String pl = pp.getLemma();
			if (pp.isPrepositional() && 
					( pl.equals("by") || 
					  (pl.equals("with") && head.containsAnyLemma(Constants.VERBS_TAKING_WITH_IN_PASSIVE)))) return true;
		// This probably won't work for 'Headache was treated using aspirin'
		} else if (next.isVP()) {
			List<ChunkSE> nse = next.getSurfaceElementList();
			if (nse == null) return false;
			if (nse.get(0).getSurfaceElement().getText().toLowerCase().equals("using")) return true;
		}
		return false;
	}
	
	/**
	 * Convert chunk object into string representation
	 * 
	 */
	public String toString() {
		StringBuilder sb = new StringBuilder();
		ChunkSE se;
		List<Word> wordList;
		for(int i = 0; i < seList.size(); i++) {
			se = seList.get(i);
			wordList = se.getSurfaceElement().toWordList();
			for(int j = 0; j < wordList.size(); j++) {
				Word w = wordList.get(j);
				sb.append(w.getText() + "(" + w.getPos() + "," + w.getLemma() + ") ");
			}
		}
		return "[" + sb.toString() + " {" + this.chunkType + "}]";
	}

}

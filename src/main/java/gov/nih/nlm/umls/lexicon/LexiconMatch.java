package gov.nih.nlm.umls.lexicon;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import gov.nih.nlm.ling.core.SurfaceElement;
import gov.nih.nlm.nls.lexCheck.Lib.LexRecord;
import gov.nih.nlm.nls.lexCheck.Lib.NounEntry;
import gov.nih.nlm.semrep.core.SRSentence;
import gov.nih.nlm.semrep.core.TokenInfo;

/**
 * A class that represents a match of a text fragment with a UMLS Specialist Lexicon record.
 * The match is many-to-many: a sequence of one or more tokens can map to a set of lexical records.
 * 
 * @author Halil Kilicoglu
 *
 */

public class LexiconMatch {
	List<TokenInfo> tokens; 
    List<LexRecord> lexRecords;
    
	public LexiconMatch(List<TokenInfo> tokens, List<LexRecord> lexRecords) {
		this.tokens = tokens;
		this.lexRecords = lexRecords;
	}

	public List<TokenInfo> getMatch() {
		return tokens;
	}

	public void setToken(List<TokenInfo> token) {
		this.tokens = token;
	}

	public List<LexRecord> getLexRecords() {
		return lexRecords;
	}

	public void setLexRecords(List<LexRecord> lexRecords) {
		this.lexRecords = lexRecords;
	}

	public String toString() {
		StringBuilder buf = new StringBuilder();
		for (TokenInfo t : tokens) {
			buf.append(t.toString() + " ");
		}
		buf.append("\n");
		for (LexRecord rec : lexRecords) {
			buf.append(rec.GetText());
			buf.append("\n");
		}
		return buf.toString().trim();
	}
	
	public static List<String> getNominalComplements(SurfaceElement se) {
		if (se.isNominal() == false) return new ArrayList<>();
		SRSentence sent = (SRSentence)se.getSentence();
		LexRecord headlex = null;
		
		// because lexicon may split things differently, there may be issues here
		List<List<LexRecord>> lexrecs = sent.getLexicalRecord(se);
		if (lexrecs.size() == 0) lexrecs = sent.getLexicalRecord(se.getHead());		
		Vector<String> compls = null;
		if (lexrecs.size() >0) { 
			for (int i=lexrecs.size()-1; i>=0; i--) {
				List<LexRecord> reclist = lexrecs.get(i);
				for (int j=0; j <reclist.size() ; j++) {
					headlex = reclist.get(j);
					NounEntry ne = headlex.GetCatEntry().GetNounEntry();
					if (ne !=null) {
						compls = ne.GetCompl();
						break;
					}
				}
			}
		}
		
		List<String> out = new ArrayList<>();
		if (compls == null) return out;
		for (String compl: compls) {
			if (compl.startsWith("pphr(") && compl.endsWith(",np)")) {
				out.add(compl.substring(5,compl.lastIndexOf(",")));
			}
		}
		return new ArrayList<String>(out);
	}
	
	public static List<String> getVerbalComplements(SurfaceElement se) {
		if (se.isVerbal() == false) return new ArrayList<>();
		SRSentence sent = (SRSentence)se.getSentence();
		LexRecord headlex = null;
		List<List<LexRecord>> lexrecs = sent.getLexicalRecord(se.getHead());
		if (lexrecs.size() >0) headlex = lexrecs.get(0).get(0);
		Vector<String> compls = headlex.GetCatEntry().GetVerbEntry().GetTran();
		return new ArrayList<String>(compls);
	}
}

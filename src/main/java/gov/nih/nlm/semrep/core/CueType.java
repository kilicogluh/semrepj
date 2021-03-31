package gov.nih.nlm.semrep.core;

/**
 * Enumeration of cue used by the system.<p>
 * 
 * @author Halil Kilicoglu
 *
 */
public enum CueType {
	PREPOSITION, BE, PARENTHESIS, APPOSITIVE;
		
	public String getString() {
		switch(this) {
			case PREPOSITION: return "PREP";
			case BE : return "BE";
			case APPOSITIVE : return "APPOS";
			case PARENTHESIS : return "PAREN";
			default : return "NO_CUE";
		}
	}
}

package gov.nih.nlm.semrep.core;

/**
 * Enumeration of indicator types used by the system.<p>
 * 
 * @author Halil Kilicoglu
 *
 */
public enum IndicatorType {
	VERB, MULTIWORD, MULTIPHRASE, NOMINAL, PREPOSITION, ADJECTIVE, MODHEAD, APPOSITIVE, PARENTHESIS, INFERENCE;
		
	public String getString() {
		switch(this) {
			case VERB: return "VERB";
			case MULTIWORD : return "MWORD";
			case MULTIPHRASE : return "MPHRASE";
			case NOMINAL: return "NOM";
			case PREPOSITION : return "PREP";
			case ADJECTIVE : return "ADJ";
			case MODHEAD : return "MODHEAD";
			case APPOSITIVE : return "APPOS";
			case PARENTHESIS : return "PAREN";
			case INFERENCE : return "INFER";
			default : return "NO_TYPE";
		}
	}
}

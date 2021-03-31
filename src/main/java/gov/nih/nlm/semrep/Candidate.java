package gov.nih.nlm.semrep;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import gov.nih.nlm.ling.sem.Concept;
import gov.nih.nlm.ling.sem.Entity;

public class Candidate {

	private Entity entity;
	private Concept concept;
	private String semtype;
	
	public Candidate(Entity entity, Concept concept, String semtype) {
		this.entity = entity;
		this.concept = concept;
		this.semtype = semtype;
	}

	public Entity getEntity() {
		return entity;
	}

	public void setEntity(Entity entity) {
		this.entity = entity;
	}

	public Concept getConcept() {
		return concept;
	}

	public void setConcept(Concept concept) {
		this.concept = concept;
	}
	
	public String getSemtype() {
		return semtype;
	}

	public void setSemtype(String semtype) {
		this.semtype = semtype;
	}

	public static List<Candidate> generateCandidates(Entity ent) {
		List<Candidate> cands = new ArrayList<>();
		Set<Concept> concs = ent.getConcepts();
		for (Concept c: concs) {
			LinkedHashSet<String> semtypes = c.getSemtypes();
			for (String s: semtypes) 
				cands.add(new Candidate(ent,c,s));
		}
		return cands;
	}
	
}

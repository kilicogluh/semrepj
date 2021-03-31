package gov.nih.nlm.semrep;

import java.util.ArrayList;
import java.util.List;

import gov.nih.nlm.ling.core.SurfaceElement;
import gov.nih.nlm.semrep.core.CueType;

public class CandidatePair {
	
	private Candidate subject;
	private Candidate object;
	private SurfaceElement subjectCue = null;
	private SurfaceElement objectCue = null;
	private CueType subjectCueType;
	private CueType objectCueType;
	
	public CandidatePair(Candidate subject, Candidate object) {
		this.subject = subject;
		this.object = object;
	}
	
	public CandidatePair(Candidate subject, SurfaceElement sCue, CueType st, 
			Candidate object, SurfaceElement oCue, CueType ot) {
		this(subject,object);
		this.subjectCue = sCue;
		this.objectCue = oCue;
		this.subjectCueType = st;
		this.objectCueType = ot;
	}

	public Candidate getSubject() {
		return subject;
	}

	public void setSubject(Candidate subject) {
		this.subject = subject;
	}

	public Candidate getObject() {
		return object;
	}

	public void setObject(Candidate object) {
		this.object = object;
	}
	
	public SurfaceElement getSubjectCue() {
		return subjectCue;
	}

	public void setSubjectCue(SurfaceElement subjectCue) {
		this.subjectCue = subjectCue;
	}

	public SurfaceElement getObjectCue() {
		return objectCue;
	}

	public void setObjectCue(SurfaceElement objectCue) {
		this.objectCue = objectCue;
	}

	public CueType getSubjectCueType() {
		return subjectCueType;
	}

	public void setSubjectCueType(CueType subjectCueType) {
		this.subjectCueType = subjectCueType;
	}

	public CueType getObjectCueType() {
		return objectCueType;
	}

	public void setObjectCueType(CueType objectCueType) {
		this.objectCueType = objectCueType;
	}

	public static List<CandidatePair> generateCandidatePairs(List<Candidate> subjs, List<Candidate> objs) {
		List<CandidatePair> pairs = new ArrayList<>();
		for (int j=0; j < objs.size(); j++) {
			for (int i=0; i < subjs.size(); i++) {
				pairs.add(new CandidatePair(subjs.get(i),objs.get(j)));
			}
		}
		return pairs;
	}
	
	public static List<CandidatePair> generateCandidatePairs(List<Candidate> subjs, SurfaceElement sCue, CueType st,
			List<Candidate> objs, SurfaceElement oCue, CueType ot) {
		List<CandidatePair> pairs = new ArrayList<>();
		if (subjs == null || objs == null) return pairs;
		for (int j=0; j < objs.size(); j++) {
			for (int i=0; i < subjs.size(); i++) {
				pairs.add(new CandidatePair(subjs.get(i),sCue,st,objs.get(j),oCue,ot));
			}
		}
		return pairs;
	}

}

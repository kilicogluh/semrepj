package gov.nih.nlm.semrep.core;

import java.util.List;

import gov.nih.nlm.ling.core.SpanList;
import gov.nih.nlm.ling.sem.Argument;
import gov.nih.nlm.ling.sem.ImplicitRelation;
import gov.nih.nlm.ling.sem.SemanticItem;

public class SRImplicitRelation extends ImplicitRelation {
	
	private SpanList triggerSpan;
	private IndicatorType indicatorType;
	
	public SRImplicitRelation(String id) {
		super(id);
	}

	public SRImplicitRelation(String id, String type) {
		super(id, type);
	}

	public SRImplicitRelation(String id, List<Argument> args) {
		super(id, args);
	}

	public SRImplicitRelation(String id, String type, List<Argument> args) {
		super(id, type, args);
	}

	public SemanticItem getSubject() {
		List<Argument> subjects = getArgs("Subject");
		if (subjects == null || subjects.size() == 0) return null;
		return subjects.get(0).getArg();	
	}
	
	public SemanticItem getObject() {
		List<Argument> objects = getArgs("Object");
		if (objects == null || objects.size() == 0) return null;
		return objects.get(0).getArg();	
	}

	public SpanList getTriggerSpan() {
		return triggerSpan;
	}

	public void setTriggerSpan(SpanList triggerSpan) {
		this.triggerSpan = triggerSpan;
	}

	public IndicatorType getIndicatorType() {
		return indicatorType;
	}

	public void setIndicatorType(IndicatorType indicatorType) {
		this.indicatorType = indicatorType;
	}
	
}

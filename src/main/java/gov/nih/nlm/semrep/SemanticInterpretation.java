package gov.nih.nlm.semrep;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import gov.nih.nlm.bioscores.core.CoreferenceChain;
import gov.nih.nlm.bioscores.core.Expression;
import gov.nih.nlm.ling.core.Document;
import gov.nih.nlm.ling.core.Sentence;
import gov.nih.nlm.ling.core.SpanList;
import gov.nih.nlm.ling.core.SurfaceElement;
import gov.nih.nlm.ling.sem.Entity;
import gov.nih.nlm.ling.sem.Predicate;
import gov.nih.nlm.ling.sem.SemanticItem;
import gov.nih.nlm.ling.sem.Sense;
import gov.nih.nlm.semrep.core.Chunk;
import gov.nih.nlm.semrep.core.ChunkSE;
import gov.nih.nlm.semrep.core.CueType;
import gov.nih.nlm.semrep.core.CuedChunk;
import gov.nih.nlm.semrep.core.IndicatorType;
import gov.nih.nlm.semrep.core.SRPredication;
import gov.nih.nlm.semrep.core.SRSentence;
import gov.nih.nlm.semrep.core.SemRepFactory;
import gov.nih.nlm.semrep.utils.SemRepUtils;
import gov.nih.nlm.umls.OntologyDatabase;
import gov.nih.nlm.umls.lexicon.LexiconMatch;

public class SemanticInterpretation {
	
	private static Logger log = Logger.getLogger(SemanticInterpretation.class.getName());	
	
	private static List<String> NOMINAL_SUBJECT_CUES = Arrays.asList("by","with","via");
	private static List<String> NOMINAL_OBJECT_CUES = Arrays.asList("of");	

	private OntologyDatabase relOntology;
	private Map<Entity,List<Entity>> corefMap;
	
	public SemanticInterpretation(OntologyDatabase relOntology) {
		this.relOntology = relOntology;
	}
	
	public void semanticInterpretation(Document doc) {
		LinkedHashSet<SemanticItem> predicates = Document.getSemanticItemsByClass(doc, Predicate.class);
		log.finest("Predicates size: " + predicates.size());
		
		corefMap = createCorefMap(doc);
		
		for (Sentence cs : doc.getSentences()) {
			SRSentence css = (SRSentence)cs;
			Map<SurfaceElement,List<Candidate>> allCandidates = generateCandidates(css);
			for (Chunk ch : css.getChunks()) {
				if (ch.isNP()) nounCompoundInterpretation(css,ch, allCandidates);
				List<ChunkSE> seList = ch.getSurfaceElementList();
				for (int i= seList.size()-1; i>=0; i--) {
					SurfaceElement se = seList.get(i).getSurfaceElement();
					// too coarse, will need to be expanded
					if (css.hasBeenUsed(se)) continue;
					LinkedHashSet<SemanticItem> preds = se.filterByPredicates();
					if (preds.size() == 0) continue;
					if (ch.isVP()) verbalInterpretation(css,ch,preds,se,allCandidates);
					if (ch.isADJP()) adjectivalInterpretation(css,ch,preds,se,allCandidates);
					if (ch.isPP()) prepositionalInterpretation(css,ch,preds,se,allCandidates);
					if (ch.isNP()) nominalInterpretation(css,ch,preds,se, allCandidates);
				}
			}
		}
	}
	
	private Map<Entity,List<Entity>> createCorefMap(Document doc) {
		Map<Entity,List<Entity>> corefMap = new HashMap<>();
		LinkedHashSet<SemanticItem> corefs = Document.getSemanticItemsByClass(doc, CoreferenceChain.class);
		if (corefs.size() == 0) return corefMap;
		for (SemanticItem s: corefs) {
			CoreferenceChain cc = (CoreferenceChain)s;
			List<SemanticItem> refs = cc.getReferents();
			List<Entity> refEnts = new ArrayList<>();
			for (SemanticItem se : refs) {
				Entity refEnt = (Entity)se;
				refEnts.add(refEnt);
			}
			List<SemanticItem> exps = cc.getExpressions();
			for (SemanticItem ss: exps) {
				Expression exp = (Expression)ss;
				SurfaceElement su = exp.getSurfaceElement();
				List<Entity> expents= SemRepUtils.filterByEntities(su, false);
				for (Entity e: expents)
					corefMap.put(e,refEnts);
			}
		}
		return corefMap;
	}
	
	public boolean lookup(String semtype1, String predtype, String semtype2) {
		return relOntology.contains(semtype1 + "-" + predtype + "-" + semtype2);
	}
	
	public Map<SurfaceElement,List<Candidate>> generateCandidates(SRSentence sent) {
		Map<SurfaceElement,List<Candidate>> cands = new HashMap<>();
		for (SurfaceElement se: sent.getSurfaceElements()) {
			List<Entity> ents = SemRepUtils.filterByEntities(se,false);
			if (ents.size() > 0) {
				List<Candidate> seCands = new ArrayList<>();
				for (Entity e: ents) {
					List<Candidate> ecands = Candidate.generateCandidates(e);
					seCands.addAll(ecands);
				}
				cands.put(se, seCands);
			}
		}
		return cands;
	}
	
	public void nounCompoundInterpretation(SRSentence sent, Chunk ch, Map<SurfaceElement,List<Candidate>> candidates) {
		Document doc = sent.getDocument();
		List<ChunkSE> seList = ch.getSurfaceElementList();
		boolean found = false;
		for (int i= seList.size()-1; i>0; i--) {
			SurfaceElement se = seList.get(i).getSurfaceElement();
			if (ch.isHeadOf(se) || ch.isModifierOf(se)) {
				// not sure this is needed
				LinkedHashSet<SemanticItem> rightPrs = se.filterByPredicates();
				if (rightPrs.size() > 0) continue;
			
				List<Candidate> rightCands = candidates.get(se);
				if (rightCands == null ) continue;
			
				SurfaceElement prev = seList.get(i-1).getSurfaceElement();
				if (ch.isModifierOf(prev) == false) continue;
				
				// hyphenated adjective processing
				boolean hyphenatedAdj = false;
				List<Candidate> leftCands = new ArrayList<>();
				LinkedHashSet<SemanticItem> preds = new LinkedHashSet<>();
				if (prev.isAdjectival() && prev.getText().contains("-")) {
					hyphenatedAdj = true;
					SpanList entsp = new SpanList(prev.getSpan().getBegin(),prev.getSpan().getBegin() + prev.getText().lastIndexOf("-"));
					SpanList prsp = new SpanList(prev.getSpan().getBegin() + prev.getText().lastIndexOf("-")+1, prev.getSpan().getEnd());
					
					List<Candidate> tempCands = candidates.get(prev);
					if (tempCands == null) continue;
					
					for (Candidate c: tempCands) {
						if (SpanList.subsume(entsp, c.getEntity().getSpan())) {
							leftCands.add(c);
						}
					}
					LinkedHashSet<SemanticItem> sems = prev.filterByPredicates();
					for (SemanticItem sem: sems) {
						if (SpanList.subsume(prsp, sem.getSpan())) preds.add(sem);
					}
				} 
				else {
					leftCands = (candidates.get(prev) == null ? leftCands : candidates.get(prev));
				}
				
				List<CandidatePair> pairs = CandidatePair.generateCandidatePairs(leftCands, rightCands);
				if (hyphenatedAdj) {
					found = verifyAndGenerate(doc,sent,preds,pairs,IndicatorType.ADJECTIVE);
				} else { 
					found = verifyAndGenerate(doc,sent,null,pairs,IndicatorType.MODHEAD);
				}
				if (found) return;
				// anything to add to used?
			}
		}
		
		// Adjective interpretation within NP (this can probably merged with hyphenated processing
		List<SurfaceElement> mods = ch.getModifiers();
		if (mods.size() > 0) {
			for (int i=mods.size()-1; i>0; i--) {
				SurfaceElement se = mods.get(i);
				int ind = ch.indexOf(se);
				if (se.isAdjectival() == false)  continue;
				LinkedHashSet<SemanticItem> preds = se.filterByPredicates();
				if (preds.size() == 0) continue;
				SurfaceElement next = ch.getSurfaceElementList().get(ind+1).getSurfaceElement();
				SurfaceElement left = ch.getSurfaceElementList().get(ind-1).getSurfaceElement();
				List<Candidate> rightCands = candidates.get(next);
				List<Candidate> leftCands = candidates.get(left);
				if (rightCands == null || leftCands == null) break;
				List<CandidatePair> pairs = CandidatePair.generateCandidatePairs(leftCands, rightCands);
				found = verifyAndGenerate(doc,sent,preds,pairs,IndicatorType.ADJECTIVE);
			}
		}
	}
	

	
	
	public void verbalInterpretation(SRSentence sent, Chunk ch, LinkedHashSet<SemanticItem> preds, SurfaceElement sEl,
			Map<SurfaceElement,List<Candidate>> candidates) {
		boolean subjectToLeft = true;
		if (ch.inPassiveVoice()) subjectToLeft = false;
		Document doc = sent.getDocument();
		
		Chunk prev = sent.previousNP(ch, true);
		CuedChunk next = sent.nextCuedNP(ch, true, null);
		boolean found = false;
		
		while (next != null) {
			SurfaceElement nh = next.getChunk().getHead();
			Chunk cue = next.getCue();
			SurfaceElement cueh = (cue == null ? null: cue.getHead());
			List<Candidate> rightCands = candidates.get(nh);
			while (prev != null) {
				List<Candidate> leftCands = candidates.get(prev.getHead());
				List<CandidatePair> pairs = null;
				if (subjectToLeft) pairs = CandidatePair.generateCandidatePairs(leftCands, null, null, 
																				rightCands, cueh, next.getType());
				else pairs = CandidatePair.generateCandidatePairs(rightCands, cueh, next.getType(),
																  leftCands, null, null);
				found = verifyAndGenerate(doc,sent,preds,pairs,IndicatorType.VERB);
				if (found) {
					sent.addUsed(sEl);
					break;
				}
				if (found) break;
				prev = sent.previousNP(prev, true);
			}
			if (found) return;
			next = sent.nextCuedNP(next.getChunk(), true, null);
			prev = sent.previousNP(ch, true);
		}	
	}
	
	public void adjectivalInterpretation(SRSentence sent, Chunk ch, LinkedHashSet<SemanticItem> preds, SurfaceElement sEl,
			Map<SurfaceElement,List<Candidate>> candidates) {
		Document doc = sent.getDocument();
				
		CuedChunk next = sent.nextCuedNP(ch, true, null);
		Chunk prev = sent.previousNP(ch, true);
		
		boolean found = false;
		while (next != null) {
			SurfaceElement nh = next.getChunk().getHead();
			Chunk cue = next.getCue();
			SurfaceElement cueh = (cue == null ? null: cue.getHead());
			List<Candidate> rightCands = candidates.get(nh);
			while (prev != null) {
				List<Candidate> leftCands = candidates.get(prev.getHead());
				List<CandidatePair> pairs = CandidatePair.generateCandidatePairs(leftCands, null, null, 
																				rightCands, cueh, next.getType());
				found = verifyAndGenerate(doc,sent,preds,pairs,IndicatorType.ADJECTIVE);
				if (found) {
					sent.addUsed(sEl);
					break;
				}
				if (found) break;
				prev = sent.previousNP(prev, true);
			}
			if (found) return;
			next = sent.nextCuedNP(next.getChunk(), true, null);
			prev = sent.previousNP(ch, true);
		}	
	}
	
	public void prepositionalInterpretation(SRSentence sent, Chunk ch, LinkedHashSet<SemanticItem> preds, SurfaceElement sEl,
			Map<SurfaceElement,List<Candidate>> candidates) {
		Document doc = sent.getDocument();		
		Chunk right = sent.nextNP(ch, true);
		if (right == null) return;
		
		Chunk left = sent.previousNP(ch, true);
		boolean found = false;
		while (left != null) {
			SurfaceElement nh = right.getHead();
			List<Candidate> rightCands = candidates.get(nh);
			List<Candidate> leftCands =candidates.get(left.getHead());
			List<CandidatePair> pairs = CandidatePair.generateCandidatePairs(leftCands, rightCands);
			found = verifyAndGenerate(doc,sent,preds,pairs,IndicatorType.PREPOSITION);
			if (found) {
				sent.addUsed(sEl);
				break;
			}
			if (found) break;
			left = sent.previousNP(left, true);
		}		
	}
	
	// in SemRep, for gerunds ("ing") Adjectival processing is used
	// TODO: This is not yet implemented, and I am not sure it is relevant, but needs some thought.
	public void nominalInterpretation(SRSentence sent, Chunk ch, LinkedHashSet<SemanticItem> preds, SurfaceElement sEl,
			Map<SurfaceElement,List<Candidate>> candidates) {
		Document doc = sent.getDocument();
		boolean rightArg = false;
		
		List<String> lexCompls = LexiconMatch.getNominalComplements(sEl);
		
		CuedChunk right1 = sent.nextCuedNP(ch, true, CueType.PREPOSITION);
		Chunk right1ch = null; 
		Chunk right1cue = null;
		CueType right1t = null;
		SurfaceElement right1h = null;
		List<Candidate> right1Cands = null;
		if (right1 != null) {
			right1ch = right1.getChunk();
			right1cue = right1.getCue();
			right1t = right1.getType();
			right1h = right1cue.getHead();
			if (sent.adjacentChunks(ch, right1.getCue())) {
				right1Cands = candidates.get(right1ch.getHead());
				if (right1Cands !=  null) rightArg = true;
			}
		}
		
		boolean found = false;
		if (rightArg) {
			// [NOM] [PREP SUBJ] [PREP OBJ]
			// [NOM] [PREP OBJ] [PREP SUBJ]
			// [NOM] [PREP OBJ] ([SUBJ]) // This does not work, need to modify chunks
			// [NOM] [PREP OBJ], SUBJ, 
			// [NOM] [PREP OBJ] [BE SUBJ]
			CuedChunk right2 = sent.nextCuedNP(right1ch, true, null);
			while (right2 != null) {
				Chunk right2ch = right2.getChunk();
				Chunk right2cue = right2.getCue();
				CueType right2t = right2.getType();
				SurfaceElement right2h = right2cue.getHead();
				List<Candidate> right2Cands = candidates.get(right2ch.getHead());
				List<CandidatePair> pairs = null;
				String re1role = nominalCandidateRole(right1h,right2cue.getHead(),lexCompls);
				if (re1role.equals("S")) 
					pairs = CandidatePair.generateCandidatePairs(right1Cands, right1h, right1t,
																 right2Cands, right2h, right2t);
				else 
					pairs = CandidatePair.generateCandidatePairs(right2Cands, right2h, right1t,
									                             right1Cands, right1h, right2t);
				found = verifyAndGenerate(doc,sent,preds,pairs,IndicatorType.NOMINAL);
				if (found) {
					sent.addUsed(sEl);
					break;
				}
				if (found) break;
				right2 = sent.nextCuedNP(right2ch, true, null);
			} // end of [NOM] [PREP OBJ] [PREP SUBJ]
		
			// [SUBJ NOM] [PREP OBJ]
			// [OBJ NOM] [PREP SUBJ]
			if (!found) {
				List<SurfaceElement> internal = ch.getModifiers();				
				for (int j=internal.size()-1; j >=0; j--) {
					SurfaceElement se = internal.get(j);
					List<Candidate> seCands = candidates.get(se);
					List<CandidatePair> pairs = null;
					String re1role = nominalCandidateRole(right1cue.getHead(),null,lexCompls);
					if (re1role.equals("S")) 
						pairs = CandidatePair.generateCandidatePairs(right1Cands, right1h, right1t, 
																	 seCands, null, null);
					else 
						pairs = CandidatePair.generateCandidatePairs(seCands, null, null,
									                                  right1Cands, right1h, right1t);
					found = verifyAndGenerate(doc,sent,preds,pairs,IndicatorType.NOMINAL);
					if (found) {
						sent.addUsed(sEl);
						break;
					}
					if (found) break;
				}
			} // end of [SUBJ NOM] [PREP OBJ]
			
			// [SUBJ] [PREP NOM] [PREP OBJ]
			if (!found) {
				String re1role = nominalCandidateRole(right1cue.getHead(),null, lexCompls);
				CuedChunk left = sent.previousCuedNP(ch, true,CueType.PREPOSITION);
				// Does the PREP matter? 
				while (left != null) {
					// if we are looping, this does not seem necessary?
					if (sent.adjacentChunks(left.getCue(),ch)) {
						List<Candidate> lCands = candidates.get(left.getChunk().getHead());
						List<CandidatePair> pairs = null;
						if (re1role.equals("S")) 
							pairs = CandidatePair.generateCandidatePairs(right1Cands, right1h, right1t,
											lCands, null, null);
						else 
							pairs = CandidatePair.generateCandidatePairs(lCands, null, null,
											right1Cands, right1h, right1t);
						found = verifyAndGenerate(doc,sent,preds,pairs,IndicatorType.NOMINAL);
						if (found) {
							sent.addUsed(sEl);
							break;
						}
						if (found) break;
					}
				} // end of [SUBJ] [PREP NOM] [PREP OBJ]
				
				// [SUBJ] [BE] [NOM] [PREP OBJ]
				if (!found) {
					left = sent.previousCuedNP(ch, true, CueType.BE);
					while (left != null) {
						List<Candidate> lCands = candidates.get(left.getChunk().getHead());
						List<CandidatePair> pairs = null;
						if (re1role.equals("S")) 
							pairs = CandidatePair.generateCandidatePairs(right1Cands, right1h, right1t,
													lCands, null, null);
						else 
							pairs = CandidatePair.generateCandidatePairs(lCands, null, null,
													right1Cands, right1h, right1t);
						found = verifyAndGenerate(doc,sent,preds,pairs,IndicatorType.NOMINAL);
						if (found) {
							sent.addUsed(sEl);
							break;
						}
						if (found) break;
					}
				} // end of [SUBJ] [BE] [NOM] [PREP OBJ]
			}
		}	
		
		// [OBJ NOM] [BE] [SUBJ]
		if (!found && !rightArg) {
			right1 = sent.nextCuedNP(ch, true, CueType.BE);
			if (right1 != null) {
				right1ch = right1.getChunk();
				right1cue = right1.getCue();
				right1t = right1.getType();
				right1h = right1cue.getHead();
				
				if (sent.adjacentChunks(ch, right1.getCue())) {
					right1Cands = candidates.get(right1ch.getHead());
					if (right1Cands !=  null) rightArg = true;
				}
				if (rightArg) {
					List<SurfaceElement> internal = ch.getModifiers();					
					for (int j=internal.size()-1; j >=0; j--) {
						SurfaceElement se = internal.get(j);
						List<Candidate> lCands = candidates.get(se);
						List<CandidatePair> pairs = null;
						pairs = CandidatePair.generateCandidatePairs(right1Cands, right1h, right1t, 
																 	lCands, null, null);
						found = verifyAndGenerate(doc,sent,preds,pairs,IndicatorType.NOMINAL);
						if (found) {
							sent.addUsed(sEl);
							break;
						}
					}
				}
			}
		} // end of [OBJ NOM] [BE] [SUBJ]
			
		if (!found && !rightArg) {
			List<SurfaceElement> internal = ch.getModifiers();
			// [SUBJ OBJ NOM]
			// [OBJ SUBJ NOM]
			if (internal.size() > 1) {
				for (int i=internal.size()-1; i >=1; i--) {
					SurfaceElement sei = internal.get(i);
					List<Candidate> iCands = candidates.get(sei);
					for (int j=i-1; j>=0; j--) {
						SurfaceElement sej = internal.get(j);
						List<Candidate> jCands = candidates.get(sej);
						List<CandidatePair> pairs = null;
						pairs = CandidatePair.generateCandidatePairs(jCands, null, null, 
																	 iCands, null, null);
						found = verifyAndGenerate(doc,sent,preds,pairs,IndicatorType.NOMINAL);
						if (!found) {
							pairs = CandidatePair.generateCandidatePairs(iCands, null, null,
								                                     	 jCands, null, null);
							found = verifyAndGenerate(doc,sent,preds,pairs,IndicatorType.NOMINAL);
						}
						if (found) {
							sent.addUsed(sEl);
							break;
						}
					}
					if (found) break;
				}
			} // end of [SUBJ OBJ NOM]
			
			//[SUBJ] [PREP OBJ NOM] 
			// [SUBJ] [BE] [OBJ NOM] 
			if (!found) {
				Chunk prev = sent.previousChunk(ch);
				if (prev != null) {
					if (internal.size() > 0) {
						for (int i=internal.size()-1; i >=0; i--) {
							SurfaceElement sei = internal.get(i);
							List<Candidate> iCands = candidates.get(sei);
							List<CandidatePair> pairs = null;
							if (prev.isPP()) {
								Chunk sch = sent.previousNP(prev, true);
								List<Candidate> jCands = candidates.get(sch.getHead());

								pairs = CandidatePair.generateCandidatePairs(jCands, null, null, 
																			 iCands, null, CueType.PREPOSITION);
								found = verifyAndGenerate(doc,sent,preds,pairs,IndicatorType.NOMINAL);
								if (found) {
									sent.addUsed(sEl);
									break;
								} 
							} else {
								CuedChunk sch = sent.previousCuedChunk(ch, CueType.BE);
								if (sch == null) continue;
								List<Candidate> jCands = candidates.get(sch.getChunk().getHead());	
								pairs = CandidatePair.generateCandidatePairs(jCands, null, null, 
																			 iCands, null, null);
								found = verifyAndGenerate(doc,sent,preds,pairs,IndicatorType.NOMINAL);
								if (found) {
									sent.addUsed(sEl);
									break;
								} 
							}
							if (found) break;
						}
					}
				}
			} //end of [SUBJ] [PREP OBJ NOM]
			
		}	
	}
	
	// contribution-to??
	private String nominalCandidateRole(SurfaceElement cue1, SurfaceElement cue2, List<String> lexCompls) {
		String w1 = cue1.getLemma();
		if (cue2 == null) return (NOMINAL_SUBJECT_CUES.contains(w1) ? "S": "O");
		String w2 = cue2.getLemma();
		if (NOMINAL_SUBJECT_CUES.contains(w1)) return "S";
		else if (NOMINAL_SUBJECT_CUES.contains(w2)) return "O";
		if (w1.equals("of")) {
			if (lexCompls.contains(w2)) return "S";
		}
		return "O";
	}
	
	private boolean verifyAndGenerate(Document doc, SRSentence sent,
			LinkedHashSet<SemanticItem> preds, List<CandidatePair> pairs, IndicatorType indType) {
		if (pairs == null || pairs.size() == 0) return false;
		// implicit
		if (preds == null) {
			for (String modhead: Constants.MOD_HEAD_TYPES) {
				boolean inv = false;
				if (modhead.startsWith("inverse:")) {
					inv = true;
					modhead = modhead.substring(8);
				}
				for (CandidatePair pair: pairs) {
					if (inv && lookup(pair.getObject().getSemtype(),modhead,pair.getSubject().getSemtype())) {
						generateImplicitRelation(doc,modhead,indType,pair.getObject().getEntity(), pair.getSubject().getEntity());
						return true;
					}
					else if (lookup(pair.getSubject().getSemtype(),modhead,pair.getObject().getSemtype())) {
						generateImplicitRelation(doc,modhead,indType,pair.getSubject().getEntity(), pair.getObject().getEntity());
						return true;
					}
				}
			}
			return false;
		}
		if (indType == IndicatorType.NOMINAL) {
			boolean found = false;
			for (SemanticItem sem: preds) {
				Predicate pred = (Predicate)sem;
				List<Sense> senses = pred.getIndicator().getSenses();
				for (Sense sense: senses) {
					boolean inverse = sense.isInverse();
					
					String cue = (String)sense.getFeature("cue");
					String scue = null; String ocue= null;
					if (cue.equals("") == false) { 	
						if (cue.contains("-")) { ocue = cue.substring(0,cue.indexOf("-"));  scue = cue.substring(cue.indexOf("-")+1);}
						else ocue= cue;
					}
					
					if (inverse) { String tmp = ocue; ocue = scue; scue = tmp;}
					
					for (CandidatePair pair: pairs) {	
						SurfaceElement sc = pair.getSubjectCue();
						// modifier as argument cuing -- no explicit term
						String scl = (sc == null ? null : sc.getLemma());
						SurfaceElement oc = pair.getObjectCue();
						String ocl = (oc == null ? null : oc.getLemma());
						if (//!inverse && 
							((scl == null && scue == null) || 
							  (scue!= null && scue.equals(scl)) ||  
							  (scue == null && scl !=null && (NOMINAL_SUBJECT_CUES.contains(scl) || scl.equals(",") || 
									  SemRepUtils.LEFT_PARENTHESES.contains(scl) || scl.equals("be")))) && 
							((ocl == null && ocue == null) || 
							   (ocue!= null && ocue.equals(ocl)) ||  
							   (ocue == null && ocl != null && NOMINAL_OBJECT_CUES.contains(ocl))) && 
							lookup(pair.getSubject().getSemtype(),sense.getCategory(),pair.getObject().getSemtype())) {
							generatePredication(doc,pred,sense,indType,
									pair.getSubject().getEntity(), sc,
									pair.getObject().getEntity(), oc);
							found = true;
							break;
						} 
					}
					if (found) break;
				}
				if (found) break;
			}
			return found;
		}
	
		boolean found = false;
		for (SemanticItem sem: preds) {
			Predicate pred = (Predicate)sem;
			List<Sense> senses = pred.getIndicator().getSenses();
			for (Sense sense: senses) {
				boolean inverse = sense.isInverse();
				
				// cues specified in the indicator rule
				String cue = (String)sense.getFeature("cue");
				String scue = null; String ocue= null;
				if (cue.equals("") == false) { 	
					if (cue.contains("-")) { ocue = cue.substring(0,cue.indexOf("-"));  scue = cue.substring(cue.indexOf("-")+1);}
					else ocue= cue;
				}
//				if (inverse) { String tmp = ocue; ocue = scue; scue = tmp;}
				
				for (CandidatePair pair: pairs) {
					SurfaceElement sc = pair.getSubjectCue();
					// modifier as argument cuing -- no explicit term
					String scl = (sc == null ? null : sc.getLemma());
					SurfaceElement oc = pair.getObjectCue();
					String ocl = (oc == null ? null : oc.getLemma());
					if (!inverse && 
						((ocl == null && ocue == null) || (ocue!= null && ocue.equals(ocl))) &&  
						lookup(pair.getSubject().getSemtype(),sense.getCategory(),pair.getObject().getSemtype())) {
						generatePredication(doc,pred,sense,indType,
								pair.getSubject().getEntity(), sc, pair.getObject().getEntity(), oc);
						found = true;
						break;
					} else if (inverse && 
							   ((ocl == null && ocue == null) || (ocue!= null && ocue.equals(ocl))) && 
							   lookup(pair.getObject().getSemtype(),sense.getCategory(),pair.getSubject().getSemtype())) {
						generatePredication(doc,pred,sense,indType, 
								pair.getObject().getEntity(), oc, pair.getSubject().getEntity(), sc);
						found = true;
						break;
					}
				}
				if (found) break;
			}
			if (found) break;
		}
		return found;
	}
	
	private void generatePredication(Document doc, Predicate pred, Sense sense, IndicatorType type, 
									Entity subj, SurfaceElement subjCue, 
									Entity obj, SurfaceElement objCue) {
		SemRepFactory sif = (SemRepFactory)doc.getSemanticItemFactory();
		SRSentence sent = (SRSentence)pred.getSurfaceElement().getSentence();
		
		List<Entity> corefSubjs = (corefMap.containsKey(subj) ? corefMap.get(subj) : null);
		List<Entity> corefObjs = (corefMap.containsKey(obj) ? corefMap.get(obj) : null);
		if (corefSubjs != null && corefObjs != null) {
			for (Entity cs: corefSubjs) {
				for (Entity co: corefObjs) {
					sif.newSRPredication(doc, pred, cs,co,type);
				}
			}
		} else if (corefSubjs != null) {
			for (Entity cs: corefSubjs) {
				sif.newSRPredication(doc, pred, cs,obj,type);
			}
		} else if (corefObjs != null) {
			for (Entity co: corefObjs) {
				sif.newSRPredication(doc, pred, subj,co,type);
			}
		} else 
			sif.newSRPredication(doc, pred, subj, obj, type);
		
		pred.setSense(sense);
		sent.addUsed(subj.getSurfaceElement());
		sent.addUsed(obj.getSurfaceElement());
		if (subjCue != null) sent.addUsed(subjCue);
		if (objCue != null) sent.addUsed(objCue);
	}
	
	private void generateImplicitRelation(Document doc, String relation, IndicatorType type, 
			Entity subj,  Entity obj) {
		SemRepFactory sif = (SemRepFactory)doc.getSemanticItemFactory();
		SRSentence sent = (SRSentence)obj.getSurfaceElement().getSentence();
		sif.newSRImplicitRelation(doc, relation, 
				subj,obj,type);
		sent.addUsed(subj.getSurfaceElement());
		sent.addUsed(obj.getSurfaceElement());
	}
	

	
}

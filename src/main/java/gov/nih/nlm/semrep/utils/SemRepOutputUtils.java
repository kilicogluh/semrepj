package gov.nih.nlm.semrep.utils;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import org.json.JSONArray;
import org.json.JSONObject;

import gov.nih.nlm.bioscores.core.CoreferenceChain;
import gov.nih.nlm.ling.core.Document;
import gov.nih.nlm.ling.core.Sentence;
import gov.nih.nlm.ling.core.SpanList;
import gov.nih.nlm.ling.core.SurfaceElement;
import gov.nih.nlm.ling.sem.AbstractRelation;
import gov.nih.nlm.ling.sem.Concept;
import gov.nih.nlm.ling.sem.Entity;
import gov.nih.nlm.ling.sem.ImplicitRelation;
import gov.nih.nlm.ling.sem.Predicate;
import gov.nih.nlm.ling.sem.SemanticItem;
import gov.nih.nlm.ner.metamap.ScoredUMLSConcept;
import gov.nih.nlm.semrep.core.Chunk;
import gov.nih.nlm.semrep.core.SRImplicitRelation;
import gov.nih.nlm.semrep.core.SRPredication;
import gov.nih.nlm.semrep.core.SRSentence;

/**
 * A class that contains methods to write output of SemReps
 * 
 * @author Halil Kilicoglu
 *
 */

public class SemRepOutputUtils {
    private static Logger log = Logger.getLogger(SemRepOutputUtils.class.getName());
    
    private static String LS = System.lineSeparator();
    private static String SEP = "|";
	
	/**
	 * Outputs results of processing for a list of documents to a output stream
	 * according to the specified options
	 * 
	 * @param docs
	 *            the list of documents to be output
	 * 
	 * @throws IOException
	 *             if unable to write to the buffer
	 */
	public static void writeOutput(List<Document> docs, BufferedWriter writer) throws IOException {
		String format = System.getProperty("user.outputformat");
		if (format == null) format = "simplified";
		writeOutput(docs,writer,format);
	}
	
	public static void writeOutput(List<Document> docs, BufferedWriter writer, String format) throws IOException {
		if (docs == null) return;
		if (format.equalsIgnoreCase("json")) {
			JSONArray docsJsonArray = new JSONArray();
			for (Document doc: docs) {
				JSONObject docJson = getDocumentJSON(doc);
				docsJsonArray.put(docJson);
			}
			writer.write(docsJsonArray.toString(1));
		} else {
			for (Document doc: docs) writeOutput(doc,writer,format);
		}
	}
	
	public static void writeOutput(Document doc, BufferedWriter writer, String format) throws IOException {
		if (format.equalsIgnoreCase("simplified")) writeSimplified(doc,writer);
		else if (format.equalsIgnoreCase("brat")) writeBrat(doc,writer);
		else if (format.equalsIgnoreCase("humanReadable")) writeHumanReadable(doc,writer);		
	}
	
	public static JSONObject getDocumentJSON(Document doc){
		JSONArray sentencesJsonArray, entitiesJsonArray, predicationsJsonArray;
		JSONObject docJson, sentJson, entJson, predicationJson;
		
		docJson = new JSONObject();
		docJson.put("id", doc.getId());
//		docJson.put("text", doc.getText());
		List<Sentence> sentList = doc.getSentences();
		sentencesJsonArray = new JSONArray();
		for (int j = 0; j < sentList.size(); j++) {
			SRSentence cs = (SRSentence) sentList.get(j);
			sentJson = new JSONObject();
			sentJson.put("sid", cs.getId());
			sentJson.put("section", cs.getSubsection());
			sentJson.put("section_abbrv", cs.getSectionAbbreviation());
//			sentJson.put("sentence_id", cs.getSentenceIDInSection());
			sentJson.put("text", cs.getText());
			sentJson.put("begin", cs.getSpan().getBegin());
			sentJson.put("end", cs.getSpan().getEnd());
			entitiesJsonArray = new JSONArray();
			LinkedHashSet<SemanticItem> entities = 
					Document.getSemanticItemsByClassSpan(doc, Entity.class, new SpanList(cs.getSpan()),
					true);
			for (SemanticItem entity : entities) {
				entJson = getEntityJSON((Entity)entity);
				entitiesJsonArray.put(entJson);
			}
			sentJson.put("entities", entitiesJsonArray);
			
			predicationsJsonArray = new JSONArray();
			LinkedHashSet<SemanticItem> predList = Document.getSemanticItemsByClassSpan(doc, AbstractRelation.class,
					new SpanList(cs.getSpan()), true);
			for (SemanticItem si : predList) {
				
				if (si instanceof ImplicitRelation) {
					SRImplicitRelation ir = (SRImplicitRelation)si;
					predicationJson = getImplicitRelationJSON(ir);
					predicationsJsonArray.put(predicationJson);
				} else if (si instanceof CoreferenceChain) {
					CoreferenceChain ch = (CoreferenceChain)si;
					predicationJson = getCoreferenceChainJSON(ch);
					predicationsJsonArray.put(predicationJson);
				}
				else  {
					SRPredication pr = (SRPredication)si;
					predicationJson = getPredicationJSON(pr);
					predicationsJsonArray.put(predicationJson);
				}
				sentJson.put("predications", predicationsJsonArray);	
			}
			sentencesJsonArray.put(sentJson);
		}
		docJson.put("sentences", sentencesJsonArray);
		return docJson;
	}
	
	public static void writeBrat(Document doc, BufferedWriter writer) throws IOException {
		StringBuilder sb = new StringBuilder();
		LinkedHashSet<SemanticItem> siList = Document.getSemanticItemsByClass(doc, Entity.class);
		for (SemanticItem si : siList) {
			Entity ent = (Entity) si;
			sb.append(ent.toStandoffAnnotation(true, 0) + LS);
		}
		siList = Document.getSemanticItemsByClass(doc, Predicate.class);
		for (SemanticItem si : siList) {
			Predicate pred = (Predicate) si;
			sb.append(pred.toStandoffAnnotation() + LS);
		}
		
		siList = Document.getSemanticItemsByClass(doc, AbstractRelation.class);
		for (SemanticItem si : siList) {
			AbstractRelation pr = (AbstractRelation) si;
			sb.append(pr.toStandoffAnnotation() + LS);
		}
		
//		siList = Document.getSemanticItemsByClass(doc, CoreferenceChain.class);
//		for (SemanticItem si : siList) {
//			CoreferenceChain pr = (CoreferenceChain) si;
//			sb.append(pr.toStandoffAnnotation() + LS);
//		}
//		
//		siList = Document.getSemanticItemsByClass(doc, SRImplicitRelation.class);
//		for (SemanticItem si : siList) {
//			SRImplicitRelation pr = (SRImplicitRelation) si;
//			sb.append(pr.toStandoffAnnotation() + LS);
//		}
//		
//		siList = Document.getSemanticItemsByClass(doc, SRPredication.class);
//		for (SemanticItem si : siList) {
//			SRPredication pr = (SRPredication) si;
//			sb.append(pr.toStandoffAnnotation() + LS);
//		}
		writer.write(sb.toString().trim());
	}

	
	public static void writeSimplified(Document doc, BufferedWriter writer) throws IOException {
		String includes = System.getProperty("user.output.includes");
		List<Sentence> sentList = doc.getSentences();
		StringBuilder sb = new StringBuilder();
		for (int j = 0; j < sentList.size(); j++) {
			SRSentence cs = (SRSentence) sentList.get(j);
			String commonElements = getCommonString(cs,true);
			sb.append(commonElements + SEP + cs.getText() + LS);
			if (includes != null)
				sb.append(writeSyntax(cs,includes,true));
			LinkedHashSet<SemanticItem> siList = Document.getSemanticItemsByClassSpan(doc, AbstractRelation.class,
					new SpanList(cs.getSpan()), true);
			for (SemanticItem si : siList) {
				if (si instanceof ImplicitRelation)
					sb.append(((ImplicitRelation) si).toShortString() + LS);
				else if (si instanceof CoreferenceChain)
					sb.append(((CoreferenceChain)si).toShortString() + LS);
				else 
					sb.append(commonElements);
					sb.append(SEP);
					sb.append("relation");
					sb.append(SEP);
					sb.append(getPredicationString((SRPredication)si,true));
					sb.append(LS);
			}
			sb.append(LS);
		}
		writer.write(sb.toString().trim());
	}
	
	
	public static void writeHumanReadable(Document doc, BufferedWriter writer) throws IOException {
		String includes = System.getProperty("user.output.includes");
		LinkedHashSet<SemanticItem> siList;
		
		StringBuilder sb = new StringBuilder();
		List<Sentence> sentList = doc.getSentences();

		for (int j = 0; j < sentList.size(); j++) {
			SRSentence cs = (SRSentence) sentList.get(j);			
			String commonStr = getCommonString(cs,false);
			List<String> textFields = new ArrayList<>();
			textFields.add(commonStr);
			textFields.add("text");
			textFields.addAll(cs.getRemainingFieldsForTextOutput());
			sb.append(String.join(SEP, textFields) + LS);

			if (includes != null)
				sb.append(writeSyntax(cs,includes,false));

			siList = Document.getSemanticItemsByClassSpan(doc, Entity.class, new SpanList(cs.getSpan()),
					true);
			for (SemanticItem entity : siList) {
				List<String> entityFields = new ArrayList<>();
				entityFields.add(commonStr);
				entityFields.add("entity");
				entityFields.add(getEntityString((Entity)entity,false));
				sb.append(String.join(SEP, entityFields)  + LS);
			}
			
			siList = Document.getSemanticItemsByClassSpan(doc, AbstractRelation.class,
					new SpanList(cs.getSpan()), true);

			for (SemanticItem si : siList) {
				List<String> predFields = new ArrayList<>();
				if (si instanceof ImplicitRelation) {
					predFields.add(commonStr);
					predFields.add("relation");
					predFields.add(getImplicitRelationString((SRImplicitRelation)si,false));
					sb.append(String.join(SEP,predFields) + LS);
				} else if (si instanceof CoreferenceChain) {
					predFields.add(commonStr);
					predFields.add("coreference");
					predFields.add(getCoreferenceChainString((CoreferenceChain)si,false));
					sb.append(String.join(SEP,predFields)  + LS);
				}
				else {
					predFields.add(commonStr);
					predFields.add("relation");
					predFields.add(getPredicationString((SRPredication)si,false));
					sb.append(String.join(SEP,predFields)  + LS);
				}
			}
		}
		writer.write(sb.toString().trim());
	}

	public static String getCommonString(SRSentence sentence, boolean simplified) {
		List<String> commonFields = new ArrayList<>();
		StringBuilder sb = new StringBuilder();
		commonFields.add(sentence.getDocument().getId());
		if (!simplified) commonFields.add(sentence.getSubsection());
		commonFields.add(sentence.getSectionAbbreviation());
		commonFields.add(sentence.getSentenceIDInSection());
		sb.append(String.join(SEP, commonFields));
		return sb.toString();
	}

	public static String getEntityString(Entity entity, boolean simplified) {
		List<String> entityFields;
		StringBuilder sb = new StringBuilder();
		Set<Concept> concs = entity.getConcepts();
		if (concs == null) return "";
		for (Concept concept : entity.getConcepts()) {
			entityFields = new ArrayList<>();
			entityFields.add(concept.getId());
			entityFields.add(concept.getName());
			entityFields.add(String.join(",", concept.getSemtypes()));
			if (!simplified) {
				entityFields.add(entity.getText());
				if (concept instanceof ScoredUMLSConcept)
					entityFields.add(Integer.toString(
							(int)Math.round(((ScoredUMLSConcept)concept).getScore())));				
				else entityFields.add("1000");
				entityFields.add(Integer.toString(entity.getSpan().getBegin()));
				entityFields.add(Integer.toString(entity.getSpan().getEnd()));
			}
			sb.append(String.join(SEP, entityFields));
		}
		return sb.toString();
	}
	
	public static JSONObject getEntityJSON(Entity entity) {
		JSONObject entJson = new JSONObject();
		entJson.put("text", entity.getText());
		entJson.put("begin", entity.getSpan().getBegin());
		entJson.put("end", entity.getSpan().getEnd());
		JSONArray concsJson = new JSONArray();
		Set<Concept> concepts = entity.getConcepts();
		if (concepts!= null) {
			for (Concept concept : entity.getConcepts()) {
				JSONObject concJson = new JSONObject();
				concJson.put("cid", concept.getId());
				concJson.put("cname", concept.getName());
				concJson.put("csemtypes", String.join(",", concept.getSemtypes()));
				if (concept instanceof ScoredUMLSConcept)
					concJson.put("score", Integer.toString(
							(int)Math.round(((ScoredUMLSConcept)concept).getScore())));
				else concJson.put("score", "1000");
				concsJson.put(concJson);
			}

		}
		entJson.put("concepts",concsJson);
		return entJson;
	}
	
	public static String getPredicateString(SRPredication predication, boolean simplified) {
		Predicate pred = predication.getPredicate();
		String type = pred.getSense().getCategory().toUpperCase();
		if (simplified) return type;
		List<String> predFields;
		StringBuilder sb = new StringBuilder();
		predFields = new ArrayList<>();
		predFields.add(type);
		predFields.add(predication.getIndicatorType().getString());
		predFields.add(pred.getText());
		predFields.add(Integer.toString(pred.getSpan().getBegin()));
		predFields.add(Integer.toString(pred.getSpan().getEnd()));
		sb.append(String.join(SEP, predFields));
		return sb.toString();
	}
	
	public static String getImplicitPredicateString(SRImplicitRelation rel, boolean simplified) {
		String type = rel.getType().toUpperCase();
		if (simplified) return type;
		List<String> predFields;
		StringBuilder sb = new StringBuilder();
		predFields = new ArrayList<>();
		predFields.add(type);
		predFields.add(rel.getIndicatorType().getString());
		SpanList relSpan = rel.getTriggerSpan();
		if (relSpan != null) {
			predFields.add(rel.getDocument().getStringInSpan(relSpan));
			predFields.add(Integer.toString(relSpan.getBegin()));
			predFields.add(Integer.toString(relSpan.getEnd()));
		} else {
			// Mod-head, no specific trigger
			// Subject + entity combination becomes the trigger
			Entity subj = (Entity)rel.getSubject();
			Entity obj = (Entity)rel.getObject();
			int beg = subj.getSpan().getBegin();
			int end = obj.getSpan().getEnd();
			if (SpanList.atLeft(obj.getSpan(), subj.getSpan())) {
				beg = obj.getSpan().getBegin(); 
				end = subj.getSpan().getEnd();
			}
			predFields.add(rel.getDocument().getStringInSpan(new SpanList(beg,end)));
			predFields.add(Integer.toString(beg));
			predFields.add(Integer.toString(end));
		}
		sb.append(String.join(SEP, predFields));
		return sb.toString();
	}
	
	
	public static JSONObject getPredicateJSON(Predicate predicate) {
		JSONObject predJson = new JSONObject();
		String type = predicate.getSense().getCategory().toUpperCase();
		predJson.put("type", type);
		predJson.put("text", predicate.getText());
		predJson.put("begin", predicate.getSpan().getEnd());
		predJson.put("end", predicate.getSpan().getEnd());
		return predJson;
	}
	
	
	public static String getPredicationString(SRPredication predication, boolean simplified) {
		StringBuilder sb = new StringBuilder();
		sb.append(getEntityString((Entity)predication.getSubject(),simplified));
		sb.append(SEP);
		sb.append(getPredicateString(predication, simplified));
		sb.append(SEP);
		sb.append(getEntityString((Entity)predication.getObject(),simplified));
		return sb.toString();
	}
	
	public static String getImplicitRelationString(SRImplicitRelation relation, boolean simplified) {
		StringBuilder sb = new StringBuilder();
		sb.append(getEntityString((Entity)relation.getSubject(),simplified));
		sb.append(SEP);
		sb.append(getImplicitPredicateString(relation, simplified));
		sb.append(SEP);
		sb.append(getEntityString((Entity)relation.getObject(),simplified));
		return sb.toString();
	}
	
	public static String getCoreferenceChainString(CoreferenceChain chain, boolean simplified) {
		StringBuilder sb = new StringBuilder();
		Entity anaphor = (Entity)chain.getArgs("Anaphor").get(0).getArg();
		Entity antecedent = (Entity)chain.getArgs("Antecedent").get(0).getArg();
		sb.append(getEntityString(anaphor,simplified));
		sb.append(SEP);
		sb.append(getEntityString(antecedent,simplified));
		return sb.toString();
	}
	
	public static JSONObject getPredicationJSON(SRPredication predication) {
		JSONObject predJson = new JSONObject();
		predJson.put("subject", getEntityJSON((Entity)predication.getSubject()));
		predJson.put("pred", getPredicateJSON(predication.getPredicate()));
		predJson.put("object", getEntityJSON((Entity)predication.getObject()));
		return predJson;
	}
	
	public static JSONObject getImplicitRelationJSON(SRImplicitRelation implRel) {
		JSONObject predJson = new JSONObject();
		predJson.put("subject", getEntityJSON((Entity)implRel.getSubject()));
		SpanList sp = implRel.getTriggerSpan();
		if (sp != null) {
			String indString = implRel.getDocument().getStringInSpan(sp);
			predJson.put("indType", indString);
		}
		predJson.put("indType", implRel.getIndicatorType().getString());
		predJson.put("object", getEntityJSON((Entity)implRel.getObject()));
		return predJson;
	}
	
	public static JSONObject getCoreferenceChainJSON(CoreferenceChain chain) {
		JSONObject predJson = new JSONObject();
		Entity anaphor = (Entity)chain.getArgs("Anaphor").get(0).getArg();
		Entity antecedent = (Entity)chain.getArgs("Antecedent").get(0).getArg();
		predJson.put("anaphor", getEntityJSON(anaphor));
		predJson.put("object", getEntityJSON(antecedent));
		return predJson;
	}
	
	/**
	 * Based on the input options, prints additional information, such as chunks or POS tags.
	 * 
	 * @param option	The option to print ("chunk" or "tag")
	 * @return	a string representation of the additional information
	 */
	public static String writeSyntax(SRSentence sentence, String option, boolean simplified) {
		StringBuilder sb = new StringBuilder();
		String common = getCommonString(sentence,simplified);
		if(option.equalsIgnoreCase("chunk")) {
			sb.append(common);
			sb.append(SEP);
			sb.append("chunk");
			sb.append(SEP);
			List<Chunk> chunkList = sentence.getChunks();
			for(int j = 0; j < chunkList.size(); j++) {
				sb.append(chunkList.get(j).toString() + " ");
			}
			sb.append(LS);
		} else if(option.equalsIgnoreCase("tag")) {
			sb.append(common);
			sb.append(SEP);
			sb.append("tag");
			sb.append(SEP);
			List<String> tagList = sentence.getTags();
			sb.append(String.join(" ", tagList) + " ");
			sb.append(LS);
		}	
		return sb.toString();
	}
}

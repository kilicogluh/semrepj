package gov.nih.nlm.semrep.core;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.logging.Logger;

import gov.nih.nlm.ling.sem.Indicator;
import gov.nih.nlm.ling.sem.Sense;
import nu.xom.Builder;
import nu.xom.Element;
import nu.xom.Elements;
import nu.xom.ParsingException;
import nu.xom.ValidityException;

public class SRIndicator extends Indicator{

	private static Logger log = Logger.getLogger(SRIndicator.class.getName());
	private String type;
	
	public SRIndicator(Element el) {
		super(el);
		this.type  = el.getAttributeValue("type");
		
		// inelegant, reattach cues
		List<Sense> senses = this.getSenses();
		Elements semEls = el.getChildElements("SemInfo");
		for (int i=0; i <semEls.size(); i++) {
			Element sEl = semEls.get(i);
			String cue = sEl.getAttributeValue("cue");
			Sense si = senses.get(i);
			si.addFeature("cue", cue);
		}
	}
	
	
	
	public String getType() {
		return this.type;
	}
	
	/**
	 * Loads indicators from an XML file. The indicators can be filtered by corpus frequency,
	 * if specified in the XML file. 
	 * 
	 * @param fileName  the indicator XML file
	 * @param count  	the corpus frequency threshold
	 * @return 			the set of indicators in the XML file
	 * 
	 * @throws FileNotFoundException	if the XML file cannot be found
	 * @throws IOException				if the XML file cannot be read
	 * @throws ParsingException			if the XML cannot be parsed
	 * @throws ValidityException		if the XML is invalid
	 */
	public static LinkedHashSet<Indicator> loadSRIndicatorsFromFile(String fileName, int count) 
			throws FileNotFoundException, IOException, ParsingException, ValidityException {
		LinkedHashSet<Indicator> indicators = new LinkedHashSet<Indicator>();
		Builder builder = new Builder();
		nu.xom.Document xmlDoc = builder.build(new FileInputStream(fileName));
		Element docc = xmlDoc.getRootElement();
		Elements indEls = docc.getChildElements("SRIndicator");
		for (int i=0; i < indEls.size(); i++) {
			Element ind = indEls.get(i);
			if (ind.getAttribute("corpusCount") != null) {
				int corpusCount = Integer.parseInt(ind.getAttributeValue("corpusCount"));
				if (corpusCount < count) continue;
			}
			SRIndicator indicator = new SRIndicator(ind);
			indicators.add(indicator);
		}
		log.info("Loaded " + indicators.size() + " indicators from " + fileName + ".");
		return indicators;		
	}
}

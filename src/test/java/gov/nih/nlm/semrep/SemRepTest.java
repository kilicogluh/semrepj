package gov.nih.nlm.semrep;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.logging.Logger;

import gov.nih.nlm.ling.core.Document;
import gov.nih.nlm.ling.sem.SemanticItem;
import gov.nih.nlm.ling.util.FileUtils;
import gov.nih.nlm.semrep.core.SRPredication;
import gov.nih.nlm.semrep.core.SemRepFactory;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Unit test for SemRep
 * 
 * @author Halil Kilicoglu
 */
public class SemRepTest extends TestCase {
	private static Logger log = Logger.getLogger(SemRepTest.class.getName());

	/**
	 * Create the test case
	 *
	 * @param testName
	 *            name of the test case
	 */
	public SemRepTest(String testName) {
		super(testName);
	}

	/**
	 * @return the suite of tests being tested
	 */
	public static Test suite() {
		return new TestSuite(SemRepTest.class);
	}

	/**
	 * @throws IOException
	 */
	public void testSemRep() throws IOException {
		SemRep.initLogging();
		Properties props = System.getProperties();
		Properties semrepProps = FileUtils.loadPropertiesFromFile("semrepj.properties");
		props.putAll(semrepProps);
		System.setProperties(props);
		SemRep.init();
		//String args[] = new String("--inputformat={}  --inputpath={} --outputpath={}").split("\\s+");
		//System.setProperties(SemRep.getProps(args));
		Document doc = new Document("00000000", "Aspirin treats headache. Headache was treated by aspirin.");
		SemRepFactory sif = new SemRepFactory(doc,new HashMap<Class<? extends SemanticItem>,Integer>());
		doc.setSemanticItemFactory(sif);
		SemRep.lexicalSyntacticAnalysis(doc);
		SemRep.semanticAnalysis(doc);
		LinkedHashSet<SemanticItem> preds = Document.getSemanticItemsByClass(doc, SRPredication.class);
		for (SemanticItem pred: preds)
			log.info("Predication " + pred.toShortString());
		assertTrue(preds.size() == 2);
	}

}

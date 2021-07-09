package gov.nih.nlm.umls;

import java.io.IOException;
import java.util.logging.Logger;
import java.util.Properties;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import gov.nih.nlm.ling.util.FileUtils;
import gov.nih.nlm.semrep.SemRep;

public class OntologyDBTest 
extends TestCase
{
private static Logger log = Logger.getLogger(OntologyDBTest.class.getName());

	/**
	 * Create the test case
	 *
	 * @param testName name of the test case
	 */
	public OntologyDBTest( String testName )
	{
	    super( testName );
	}

	/**
	 * @return the suite of tests being tested
	 */
	public static Test suite()
	{
	    return new TestSuite( OntologyDBTest.class );
	}



	/**
	 * @throws IOException 
	 */
	public void testOntologyDB() throws IOException
	{
        SemRep.initLogging();
        Properties props = System.getProperties();
        Properties semrepProps = FileUtils.loadPropertiesFromFile("semrepj.properties");
        props.putAll(semrepProps);
        System.setProperties(props);
	//	OntologyDatabase ontDB = new OntologyDatabase("ontologyDB", true);
//		OntologyDatabase ontDB = new OntologyDatabase(System.getProperty("ontologyDB.home","ontologyDB"), true);
        OntologyDatabase ontDB = OntologyDatabase.getInstance(System.getProperty("ontologyDB.home","ontologyDB"));
		assertTrue(ontDB.contains("topp-uses-carb"));
	}

}

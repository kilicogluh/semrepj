package gov.nih.nlm.semrep;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import gov.nih.nlm.ling.util.FileUtils;

/**
 * This class contains some type definitions and lists used by SemRep
 * 
 * @author Halil Kilicoglu
 *
 */
public class Constants {
	public static List<String> VERBS_TAKING_WITH_IN_PASSIVE = 
			//ssociate,associated,treat,treated,pretreat,pretreated,control,controlled,
            //alleviate,alleviated,ameliorate,ameliorated,attenuate,attenuated,
			//eliminate,eliminated,manage,managed,mitigate,mitigated,
			//immunize,immunized,prevent,prevented,transfected,transfect,cotransfect,cotransfected
			Arrays.asList("alleviate","ameliorate","associate","attenuate","control","cotransfect","co-transfect",
							"eliminate","immunize","manage","mitigate","pretreat","prevent","transfect","treat");
	
	public static List<String> BE_VERBS = Arrays.asList("be","remain");

//    <SemInfo category="process_of" cue="" inverse="false" negated="false"/>
//    <SemInfo category="process_of" cue="" inverse="true" negated="false"/>
//    <SemInfo category="part_of" cue="" inverse="true" negated="false"/>
//    <SemInfo category="uses" cue="" inverse="true" negated="false"/>
//    <SemInfo category="location_of" cue="" inverse="false" negated="false"/>
//	<Example sentence="Obese patients undergoing spine surgery." PMID="27190743"/> % PROCESS_OF
//	<Example sentence="Plasma glucose was measured before and after surgery." PMID="27525641"/> % LOCATION_OF
//	<Example sentence="Renal sympathetic nerve activity." PMID="27629265"/> % PART_OF
//	<Example sentence="APOE genotyping was conducted to determine three common alleles." PMID="27629265"/> % USES
	public static List<String> MOD_HEAD_TYPES = 
			Arrays.asList("process_of","inverse:uses","location_of","inverse:part_of","inverse:process_of"); 
	
	public static Map<String,List<String>> SEMGROUP_MAP = new HashMap<>();
	
	/**
	 * initialize a map in order to find semantic group info for each semantic type
	 * @throws IOException if semgroup file is not found or cannot be read
	 */
	public static void initSemGroups() throws IOException {
		String filename = System.getProperty("semgroupinfo", "resources/semgroups.txt");
		List<String> lines = FileUtils.linesFromFile(filename, "UTF-8");
		for (String l: lines) {
			if (l.startsWith("#")) continue;
			String[] els = l.split("\\|");
			String semtype = els[0];
			List<String> groups = Arrays.asList(els[1].split(","));
			SEMGROUP_MAP.put(semtype, groups);
		}
	}

	
}

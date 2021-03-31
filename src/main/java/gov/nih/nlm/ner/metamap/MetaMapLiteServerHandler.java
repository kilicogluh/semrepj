package gov.nih.nlm.ner.metamap;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import bioc.BioCDocument;
import gov.nih.nlm.nls.metamap.document.FreeText;
import gov.nih.nlm.nls.metamap.lite.types.ConceptInfo;
import gov.nih.nlm.nls.metamap.lite.types.Entity;
import gov.nih.nlm.nls.metamap.lite.types.Ev;
import gov.nih.nlm.nls.ner.MetaMapLite;
import gov.nih.nlm.semrep.utils.SemRepUtils;

/**
 * This class handles client requests for MetaMapLite server
 * 
 * @author Zeshan Peng
 * @author Halil Kilicoglu
 *
 */

public class MetaMapLiteServerHandler extends Thread {
	private static Logger log = Logger.getLogger(MetaMapLiteServerHandler.class.getName());	
	
	final BufferedInputStream bis;
	final BufferedOutputStream bos;
	final Socket socket;
	MetaMapLite metaMapLiteInst;
	Map<String, List<String>> semgroupMap;
	

	// Constructor
	public MetaMapLiteServerHandler(Socket s, BufferedInputStream bis, BufferedOutputStream bos, MetaMapLite mtmpl) throws IOException {
		this.socket = s;
		this.bis = bis;
		this.bos = bos;
		this.metaMapLiteInst = mtmpl;
	}


	@Override
	public void run() {
		try {
			BufferedReader br = new BufferedReader(new InputStreamReader(bis));
			PrintWriter bw = new PrintWriter(socket.getOutputStream(), true);
			String inputText = br.readLine();
			System.out.println("inputtext: " + inputText);
	    	BioCDocument document = FreeText.instantiateBioCDocument(inputText);
	    	List<Entity> entityList = metaMapLiteInst.processDocument(document);
	    	System.out.println("Entity size: " + entityList.size());
	    	
	    	StringBuilder sb = new StringBuilder(); 
	    	for (Entity entity: entityList) {
	    		sb.append(entity.getStart() + ",," + entity.getLength() + ",,");
				for (Ev ev: entity.getEvSet()) {
					ConceptInfo ci = ev.getConceptInfo();
					Set<String> semtypes = ci.getSemanticTypeSet();
					List<String> semgroups = SemRepUtils.findSemanticGroups(semtypes);
					sb.append(ci.getCUI() + ",," + 
							ci.getPreferredName() + ",," + 
							ev.getConceptString() + ",," +
							ev.getScore() + ",," +
							String.join("::", semtypes) + ",," + 
							String.join("::", semgroups) + ",,");
				}
				sb.append(";;"); 
	    	}
	    	bw.print(sb.toString());
	    	bw.flush();
		} catch (Exception e) {
			log.severe("Unable to process text with MML Server.");
			e.printStackTrace();
		}

		try {
			// closing resources
			this.bis.close();
			this.bos.close();

		} catch (IOException e) {
			log.severe("Unable to close MML Server streams.");
			e.printStackTrace();
		}
	}
}

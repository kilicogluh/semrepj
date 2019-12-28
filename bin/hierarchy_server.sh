# This script will run the UMLS hierarchye server program
#
java -Xmx1g -cp target/semrepj-0.0.1-SNAPSHOT-jar-with-dependencies.jar gov.nih.nlm.umls.HierarchyDBServer $@ 

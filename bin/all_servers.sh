# This script will run metamaplite server program
#
# To run this script, you have to have metamapliteserver.jar file 
# and data folder(which contains opennlp models and metamap files) in the same directory
#
# Available user options are:
# --configfile={path to the configure file}
# --indexdir={path to the metamap index directory}
# --modelsdir={path to the opennlp model directory}

#java -jar metamapliteserver.jar $@
java -Xmx1g -cp target/semrepj-0.0.1-SNAPSHOT-jar-with-dependencies.jar gov.nih.nlm.semrep.preprocess.CoreNLPServer $@  & 
java -Xmx1g -cp target/semrepj-0.0.1-SNAPSHOT-jar-with-dependencies.jar gov.nih.nlm.ner.metamap.MetaMapLiteServer $@ &
java -cp target/semrepj-0.0.1-SNAPSHOT-jar-with-dependencies.jar gov.nih.nlm.ner.wsd.WSDServer $@ &
java -cp target/semrepj-0.0.1-SNAPSHOT-jar-with-dependencies.jar gov.nih.nlm.umls.HierarchyDBServer $@ &
java -Djava.library.path=CRF/.libs -cp target/semrepj-0.0.1-SNAPSHOT-jar-with-dependencies.jar gov.nih.nlm.ner.gnormplus.GNormPlusJNIServer $@ & 

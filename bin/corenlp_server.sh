# This script will run coreNLP server program
#

java -Xmx1g -cp target/semrepjava-0.0.1-SNAPSHOT-jar-with-dependencies.jar gov.nih.nlm.semrep.preprocess.CoreNLPServer $@ 

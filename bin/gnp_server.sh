# This script will run Gnormpluse server program

java -Djava.library.path=CRF/.libs -cp target/semrepjava-0.0.1-SNAPSHOT-jar-with-dependencies.jar gov.nih.nlm.ner.gnormplus.GNormPlusJNIServer $@ 

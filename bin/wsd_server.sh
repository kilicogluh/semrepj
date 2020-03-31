# This script will run word sense disambiguation server program
# 
# To run this script, you need to have wsdserver.jar file, centroids.ben.gz 
# and related .gz file for AEC method in the same directory
#
# Available user options are:
# --configfile={path to the configure file}

#java -jar wsdserver.jar $@
java -Xmx1g -cp target/semrepjava-0.0.1-SNAPSHOT-jar-with-dependencies.jar gov.nih.nlm.ner.wsd.WSDServer $@

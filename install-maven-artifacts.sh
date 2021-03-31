
# You could install an artifact on a specific local repository by
# setting the localRepositoryPath parameter when installing.
#
# mvn install:install-file  -Dfile=path-to-your-artifact-jar \
#                          -DgroupId=your.groupId \
#                          -DartifactId=your-artifactId \
#                          -Dversion=version \
#                          -Dpackaging=jar \
#                          -DlocalRepositoryPath=path-to-specific-local-repo (optional)

mvn install:install-file -Dfile=lib/metamaplite-3.6.2rc1.jar \
		         -DgroupId=gov.nih.nlm.nls \
			 -DartifactId=metamaplite \
			 -Dversion=3.6.2rc1 \
			 -Dpackaging=jar

mvn install:install-file -Dfile=lib/nlmling-1.0.0.jar \
			 -DgroupId=gov.nih.nlm \
			 -DartifactId=nlmling \
			 -Dversion=1.0.0 \
			 -Dpackaging=jar
			 
mvn install:install-file -Dfile=lib/bioscores-2.0.4.jar \
			 -DgroupId=gov.nih.nlm \
			 -DartifactId=bioscores \
			 -Dversion=2.0.4 \
			 -Dpackaging=jar

mvn install:install-file -Dfile=lib/lvg2016dist-0.0.1.jar \
			 -DgroupId=gov.nih.nlm.nls.lvg \
			 -DartifactId=lvg2016dist \
			 -Dversion=0.0.1 \
			 -Dpackaging=jar

mvn install:install-file -Dfile=lib/lexCheck2011dist-1.0.0.jar \
			 -DgroupId=gov.nih.nlm \
			 -DartifactId=lexCheck2011dist \
			 -Dversion=1.0.0 \
			 -Dpackaging=jar

mvn install:install-file -Dfile=lib/gnormplus-1.0.0.jar \
			 -DgroupId=gov.nih.nlm \
			 -DartifactId=gnormplus \
			 -Dversion=1.0.0 \
			 -Dpackaging=jar

mvn install:install-file -Dfile=lib/aec_mrd_wsd-1.0-SNAPSHOT.jar \
			 -DgroupId=gov.nih.nlm.nls \
			 -DartifactId=aec_mrd_wsd \
			 -Dversion=1.0-SNAPSHOT \
			 -Dpackaging=jar

#mvn install:install-file -Dfile=lib/crfpp-java-0.57.jar \
#                         -DgroupId=org.chasen.crfpp \
#                         -DartifactId=crfpp-java \
#                         -Dversion=0.57 \
#                         -Dpackaging=jar


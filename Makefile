
JDK = /opt/jdk16

JAR = $(JDK)/bin/jar
JAVA = $(JDK)/bin/java
JAVAC = $(JDK)/bin/javac

lib/sodium.jar: src
	@rm -fr bin
	@mkdir -p bin
	$(JAVAC) -d bin -sourcepath $< $(shell find $< -name '*.java')
	@mkdir -p lib
	$(JAR) -cf $@ -C bin .


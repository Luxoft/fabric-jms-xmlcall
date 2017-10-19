MESSAGES=messages/Accumulator.proto messages/InsuredRegistry.proto
SERVICES=service/Accumulator.proto service/InsuredRegistry.proto
TOPSRC=../../xmlcall-core/src/main/java
TESTSRC=../../src/test/java

all: messages services xmlcall

.PHONY: messages
messages:
	protoc  --java_out=$(TESTSRC)  $(MESSAGES)

.PHONY: services
services:
	protoc  --descriptor_set_out=services.desc  --include_imports  --java_out=$(TESTSRC) $(SERVICES)

.PHONY: xmlcall
xmlcall:
	protoc  --java_out=$(TOPSRC) xmlcall.proto

check v:
	rm -rf com
	/home/osesov/app/apache-cxf-3.2.0/bin/wsdlvalidator wsdl/services.wsdl
	mkdir -p out && /home/osesov/app/apache-cxf-3.2.0/bin/wsdl2java -d out wsdl/services.wsdl
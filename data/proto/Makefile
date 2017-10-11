MESSAGES=messages/Accumulator.proto messages/InsuredRegistry.proto
SERVICES=service/Accumulator.proto service/InsuredRegistry.proto
TOPSRC=../../src/main/java
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

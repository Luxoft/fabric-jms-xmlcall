#export GOPATH:=$(shell pwd)/../..:$(GOPATH)
export GOPATH:=${GOPATH}:${CURDIR}
GOFLAGS:=-v
PACKAGES:=$(shell ls -1 src)

.PHONY: all
all: packages

.PHONY: packages
packages: $(addprefix bin/,$(PACKAGES))

bin/healthcare: $(shell find src/healthcare -name *.go -o -name *.proto)
	go install -v healthcare

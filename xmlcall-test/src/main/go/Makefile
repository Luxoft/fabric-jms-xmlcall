#export GOPATH:=$(shell pwd)/../..:$(GOPATH)
export GOPATH:=${CURDIR}
GOFLAGS:=-v
PACKAGES:=healthcare

.PHONY: all
all: build

bin/healthcare: $(shell find src/healthcare -name *.go -o -name *.proto)
	go install -v healthcare

.PHONY: get build lint
get: $(addprefix get-,$(PACKAGES))
build: $(addprefix build-,$(PACKAGES))
lint: $(addprefix lint-,$(PACKAGES))

get-%:
	go get -v -d $*

build-%:
	go install -v $*

lint-%:
	golint $*


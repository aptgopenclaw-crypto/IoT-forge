BINARY_NAME=iotforge
GO_BUILD=CGO_ENABLED=0 go build -ldflags="-s -w"

.PHONY: build build-all test clean

build:
	$(GO_BUILD) -o $(BINARY_NAME) ./cmd/iotforge/

build-linux:
	GOOS=linux GOARCH=amd64 $(GO_BUILD) -o $(BINARY_NAME)-linux ./cmd/iotforge/

build-darwin:
	GOOS=darwin GOARCH=amd64 $(GO_BUILD) -o $(BINARY_NAME)-darwin ./cmd/iotforge/

build-windows:
	GOOS=windows GOARCH=amd64 $(GO_BUILD) -o $(BINARY_NAME).exe ./cmd/iotforge/

build-all: build-linux build-darwin build-windows

test:
	go test ./... -v

clean:
	rm -f $(BINARY_NAME) $(BINARY_NAME)-linux $(BINARY_NAME)-darwin $(BINARY_NAME).exe
	rm -rf dist/

dist: build-all
	mkdir -p dist
	mv $(BINARY_NAME)-linux dist/
	mv $(BINARY_NAME)-darwin dist/
	mv $(BINARY_NAME).exe dist/
	@echo "Binaries in dist/"

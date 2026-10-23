.PHONY: build test race cover lint bench selftest release

build:
	go build -o nodeguard ./cmd/checker

test:
	go test ./...

race:
	go test -race ./...

cover:
	go test -coverprofile=coverage.out ./...
	go tool cover -func=coverage.out

lint:
	golangci-lint run

bench:
	go test -bench=. ./...

selftest: build
	./nodeguard -selftest

release:
	go build -trimpath -ldflags="-s -w -X gemini-nodeguard/internal/version.Version=v1.0.0" -o nodeguard ./cmd/checker

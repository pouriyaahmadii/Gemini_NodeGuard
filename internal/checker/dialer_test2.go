package checker

import (
	"context"
	"testing"
	"time"

	"gemini-nodeguard/internal/types"
)

// We want to test the dialer's logic without needing actual binary executions to succeed over network.
// So we use a Fake path that isn't really singbox. We expect NewHTTPClient to fail gracefully.

func TestSingboxBinaryDialer_FailFast(t *testing.T) {
	dialer, err := NewSingboxBinaryDialer("ls") // Use ls as a dummy binary that exits instantly
	if err != nil {
		t.Fatalf("Failed to init dummy dialer: %v", err)
	}

	node := &types.ProxyNode{
		Protocol: types.ProtocolVless,
		Server:   "test",
		Port:     443,
		UUID:     "uuid",
	}

	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel()

	_, cleanup, err := dialer.NewHTTPClient(ctx, node)
	if err == nil {
		if cleanup != nil {
			cleanup()
		}
		t.Fatalf("Expected NewHTTPClient to fail due to process exit, but it succeeded")
	}
}

func TestXrayBinaryDialer_FailFast(t *testing.T) {
	dialer, err := NewXrayBinaryDialer("ls") // Use ls as a dummy binary that exits instantly
	if err != nil {
		t.Fatalf("Failed to init dummy dialer: %v", err)
	}

	node := &types.ProxyNode{
		Protocol: types.ProtocolVless,
		Server:   "test",
		Port:     443,
		UUID:     "uuid",
	}

	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel()

	_, cleanup, err := dialer.NewHTTPClient(ctx, node)
	if err == nil {
		if cleanup != nil {
			cleanup()
		}
		t.Fatalf("Expected NewHTTPClient to fail due to process exit, but it succeeded")
	}
}

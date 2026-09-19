package checker

import (
	"context"
	"gemini-nodeguard/internal/types"
	"net/http"
	"testing"
)

func TestMockDialer(t *testing.T) {
	mockClient := &http.Client{}
	dialer := &MockDialer{Client: mockClient}

	node := &types.ProxyNode{}
	client, cleanup, err := dialer.NewHTTPClient(context.Background(), node)
	if err != nil {
		t.Fatalf("MockDialer returned unexpected error: %v", err)
	}
	defer cleanup()

	if client != mockClient {
		t.Errorf("Expected client to be the mocked client, got %v", client)
	}
}

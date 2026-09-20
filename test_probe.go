package main

import (
	"context"
	"fmt"
	"gemini-nodeguard/internal/checker"
	"gemini-nodeguard/internal/types"
	"net/http"
)

func main() {
	client := &http.Client{}
	node := &types.ProxyNode{Server: "test"}

	checker.Probe(context.Background(), client, node, "https://jules.google.com")
	fmt.Printf("jules: Alive=%v, GeminiCompatible=%v\n", node.IsAlive, node.IsGeminiCompatible)

	node2 := &types.ProxyNode{Server: "test"}
	checker.Probe(context.Background(), client, node2, "https://gemini.google.com")
	fmt.Printf("gemini: Alive=%v, GeminiCompatible=%v\n", node2.IsAlive, node2.IsGeminiCompatible)
}

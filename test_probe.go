package main

import (
	"context"
	"fmt"
	"net/http"
	"gemini-sub-checker/internal/checker"
	"gemini-sub-checker/internal/types"
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

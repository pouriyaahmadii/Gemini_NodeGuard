package checker

import (
	"testing"

	"gemini-nodeguard/internal/types"
)

func TestBuildSingboxOutbound_EdgeCases(t *testing.T) {
	node := &types.ProxyNode{
		Protocol: types.ProtocolShadowsocks,
		Plugin:   "v2ray-plugin",
	}
	_, err := BuildSingboxOutbound(node)
	if err == nil {
		t.Fatal("Expected error for SS plugin on singbox")
	}

	node = &types.ProxyNode{
		Protocol: types.ProtocolHysteria2,
		UUID:     "pass",
		Transport: types.TransportConfig{
			ObfsType: "salamander",
		},
	}
	out, err := BuildSingboxOutbound(node)
	if err != nil {
		t.Fatal(err)
	}
	if out["type"] != "hysteria2" {
		t.Errorf("Expected hysteria2, got %v", out["type"])
	}

	node = &types.ProxyNode{
		Protocol: "unknown",
	}
	_, err = BuildSingboxOutbound(node)
	if err == nil {
		t.Fatal("Expected error for unknown protocol")
	}
}

func TestBuildXrayOutbound_EdgeCases(t *testing.T) {
	node := &types.ProxyNode{
		Protocol: types.ProtocolShadowsocks,
		Plugin:   "v2ray-plugin",
	}
	_, err := BuildXrayOutbound(node)
	if err == nil {
		t.Fatal("Expected error for SS plugin on xray")
	}

	node = &types.ProxyNode{
		Protocol: types.ProtocolHysteria2,
	}
	_, err = BuildXrayOutbound(node)
	if err == nil {
		t.Fatal("Expected error for hysteria2 on xray")
	}

	node = &types.ProxyNode{
		Protocol: "unknown",
	}
	_, err = BuildXrayOutbound(node)
	if err == nil {
		t.Fatal("Expected error for unknown protocol")
	}
}

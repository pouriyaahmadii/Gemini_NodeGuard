package banner

import (
	"fmt"
)

// PrintBanner prints a colored ASCII art banner to standard output.
// It uses ANSI color codes and requires a terminal that supports them.
func PrintBanner() {
	const (
		boldCyan  = "\033[1;36m"
		cyan      = "\033[36m"
		reset     = "\033[0m"
		boldGreen = "\033[1;32m"
	)

	// The banner text
	asciiArt := `
  ____                 _       _    ____  _   _ ____
 / ___| ___ _ __ ___  (_)_ __ (_)  / ___|| | | | __ )
| |  _ / _ \ '_ ` + "`" + ` _ \ | | '_ \| |  \___ \| | | |  _ \
| |_| |  __/ | | | | || | | | | |   ___) | |_| | |_) |
 \____|\___|_| |_| |_||_|_| |_|_|  |____/ \___/|____/
                                                      `

	// Print ASCII Art Title in Bold Cyan
	fmt.Println(boldCyan + asciiArt + reset)

	// Print Box with Borders in Cyan, Labels in Default (Reset), Values in Bold Green
	fmt.Println(cyan + " ┌────────────────────────────────────────────────────────┐" + reset)
	fmt.Printf("%s │%s  🚀 Gemini & Google AI Sub Checker %sv1.0.0              %s│\n", cyan, reset, boldGreen, cyan)
	fmt.Printf("%s │%s  👤 Developed by : %sPouriya Ahmadi                      %s│\n", cyan, reset, boldGreen, cyan)
	fmt.Printf("%s │%s  🔗 GitHub       : %s@pouriyaahmadii                     %s│\n", cyan, reset, boldGreen, cyan)
	fmt.Println(cyan + " └────────────────────────────────────────────────────────┘" + reset)
	fmt.Println()
}

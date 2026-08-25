// Package style provides terminal color/formatting helpers for CLI output.
// Colors are disabled automatically when stdout isn't a TTY or NO_COLOR is set.
package style

import "os"

var enabled = detectColor()

func detectColor() bool {
	if os.Getenv("NO_COLOR") != "" {
		return false
	}
	fi, err := os.Stdout.Stat()
	if err != nil {
		return false
	}
	return (fi.Mode() & os.ModeCharDevice) != 0
}

func paint(code, s string) string {
	if !enabled || s == "" {
		return s
	}
	return "\x1b[" + code + "m" + s + "\x1b[0m"
}

func Bold(s string) string   { return paint("1", s) }
func Dim(s string) string    { return paint("2", s) }
func Red(s string) string    { return paint("31", s) }
func Green(s string) string  { return paint("32", s) }
func Yellow(s string) string { return paint("33", s) }
func Cyan(s string) string   { return paint("36", s) }
func Gray(s string) string   { return paint("90", s) }

// Status colors a workflow/run status keyword.
func Status(s string) string {
	switch s {
	case "RUNNING", "PENDING", "IN_PROGRESS":
		return Yellow(s)
	case "DONE", "COMPLETED", "SUCCESS", "ACTIVE":
		return Green(s)
	case "FAILED", "ERROR":
		return Red(s)
	case "STOPPED", "CANCELLED", "CANCELED", "ARCHIVED":
		return Gray(s)
	default:
		return s
	}
}

// LogType colors a workflow run log entry type.
func LogType(s string) string {
	switch s {
	case "ERROR":
		return Red(s)
	case "TOOL_CALL", "DELEGATION":
		return Cyan(s)
	case "TOOL_RESULT", "LLM_RESPONSE":
		return Green(s)
	default:
		return Gray(s)
	}
}

// PnL colors a formatted percentage/number string based on the sign of v.
func PnL(v float64, formatted string) string {
	switch {
	case v > 0:
		return Green(formatted)
	case v < 0:
		return Red(formatted)
	default:
		return formatted
	}
}

// Header bolds an entire tabwriter header line as a single unit, so
// column-width accounting for individual cells stays untouched.
func Header(s string) string { return Bold(s) }

// OK prefixes a success message with a green checkmark.
func OK(msg string) string { return Green("✓") + " " + msg }

// Warn prefixes a message with a yellow warning marker.
func Warn(msg string) string { return Yellow("!") + " " + msg }

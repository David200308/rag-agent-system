package cmd

const (
	bold  = "\033[1m"
	dim   = "\033[2m"
	reset = "\033[0m"
	red   = "\033[31m"
	green = "\033[32m"
	yel   = "\033[33m"
	cyan  = "\033[36m"
)

func b(s string) string { return bold + s + reset }
func g(s string) string { return green + s + reset }
func c(s string) string { return cyan + s + reset }
func y(s string) string { return yel + s + reset }
func r(s string) string { return red + s + reset }
func d(s string) string { return dim + s + reset }

package model

import "testing"

func TestBuildCronExpr(t *testing.T) {
	tests := []struct {
		minute, hour, day, month, weekday string
		want                              string
	}{
		{"0", "9", "*", "*", "1-5", "0 9 * * 1-5"},
		{"30", "18", "1", "6", "0", "30 18 1 6 0"},
		{"", "", "", "", "", "* * * * *"},
		{"0", "", "", "", "", "0 * * * *"},
		{"", "12", "", "", "", "* 12 * * *"},
	}
	for _, tt := range tests {
		got := BuildCronExpr(tt.minute, tt.hour, tt.day, tt.month, tt.weekday)
		if got != tt.want {
			t.Errorf("BuildCronExpr(%q,%q,%q,%q,%q) = %q, want %q",
				tt.minute, tt.hour, tt.day, tt.month, tt.weekday, got, tt.want)
		}
	}
}

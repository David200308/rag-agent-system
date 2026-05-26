package cronmgr

import "testing"

func TestFormatCron(t *testing.T) {
	tests := []struct {
		cronExpr, timezone, want string
	}{
		{"0 9 * * *", "UTC", "0 9 * * *"},
		{"0 9 * * *", "", "0 9 * * *"},
		{"0 9 * * 1-5", "America/New_York", "CRON_TZ=America/New_York 0 9 * * 1-5"},
		{"*/5 * * * *", "Asia/Tokyo", "CRON_TZ=Asia/Tokyo */5 * * * *"},
	}
	for _, tt := range tests {
		got := formatCron(tt.cronExpr, tt.timezone)
		if got != tt.want {
			t.Errorf("formatCron(%q, %q) = %q, want %q", tt.cronExpr, tt.timezone, got, tt.want)
		}
	}
}

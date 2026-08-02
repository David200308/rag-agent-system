package com.agentsystem.connector.tool;

import com.agentsystem.agent.ToolCallBudget;
import com.agentsystem.travel.dto.TravelRecordDto;
import com.agentsystem.travel.service.TravelService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TravelAgentToolTest {

    @Mock TravelService     travelService;
    @Mock ToolCallBudget    toolCallBudget;
    @InjectMocks TravelAgentTool tool;

    @BeforeEach
    void setUp() {
        lenient().when(toolCallBudget.tryConsume()).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        tool.clearCurrentUserUuid();
    }

    private TravelRecordDto dto(String title, List<Map<String, Object>> stops,
                                 List<Map<String, Object>> expenses, String notes) {
        return new TravelRecordDto("id-1", "user-1", title, "2026-06-27", "2026-07-01",
                stops, expenses, notes, true, Instant.now(), Instant.now());
    }

    // ── access gating ────────────────────────────────────────────────────────

    @Test
    void getTravelRecords_noUuid_returnsNoRecordsMessage() {
        assertThat(tool.getTravelRecords()).isEqualTo("No travel records available.");
    }

    @Test
    void getTravelRecords_budgetExhausted_returnsExhaustedMessage() {
        tool.setCurrentUserUuid("user-1");
        when(toolCallBudget.tryConsume()).thenReturn(false);

        assertThat(tool.getTravelRecords()).isEqualTo(ToolCallBudget.EXHAUSTED_MESSAGE);
    }

    @Test
    void getTravelRecords_noneVisible_saysSo() {
        tool.setCurrentUserUuid("user-1");
        when(travelService.listChatVisible("user-1")).thenReturn(List.of());

        assertThat(tool.getTravelRecords()).isEqualTo("The user has no travel trips marked visible to chat.");
    }

    // ── expense totals ────────────────────────────────────────────────────────

    @Test
    void getTravelRecords_sumsAmountsAcrossItemAndDateExpenses_perCurrency() {
        tool.setCurrentUserUuid("user-1");

        Map<String, Object> itemEntry = Map.of(
                "amounts", Map.of("HKD", 1000.0, "JPY", 20000.0),
                "cashback", 0.0);
        Map<String, Object> dateEntry = Map.of(
                "amounts", Map.of("HKD", 500.5),
                "cashback", 0.0);
        Map<String, Object> dateGroup = Map.of("date", "2026-06-28", "entries", List.of(dateEntry));
        Map<String, Object> expenseData = Map.of(
                "__v", 2,
                "currencies", List.of("HKD", "JPY"),
                "defaultCurrency", "HKD",
                "itemExpenses", List.of(itemEntry),
                "dateExpenses", List.of(dateGroup));

        when(travelService.listChatVisible("user-1"))
                .thenReturn(List.of(dto("2026 Japan", List.of(), List.of(expenseData), null)));

        String result = tool.getTravelRecords();

        assertThat(result).contains("Total HKD: 1500.50");
        assertThat(result).contains("Total JPY: 20000.00");
        assertThat(result).contains("Expenses (2 entries)");
    }

    @Test
    void getTravelRecords_subtractsCashback_onlyFromDefaultCurrencyTotal() {
        tool.setCurrentUserUuid("user-1");

        Map<String, Object> entry = Map.of(
                "amounts", Map.of("HKD", 1000.0, "JPY", 20000.0),
                "cashback", 50.0);
        Map<String, Object> expenseData = Map.of(
                "__v", 2,
                "defaultCurrency", "HKD",
                "itemExpenses", List.of(entry),
                "dateExpenses", List.of());

        when(travelService.listChatVisible("user-1"))
                .thenReturn(List.of(dto("Trip", List.of(), List.of(expenseData), null)));

        String result = tool.getTravelRecords();

        assertThat(result).contains("Total HKD: 1000.00 (net of 50.00 cashback: 950.00)");
        assertThat(result).contains("Total JPY: 20000.00");
        assertThat(result).doesNotContain("JPY: 20000.00 (net");
    }

    @Test
    void getTravelRecords_legacyExpenseFormat_reportsCountWithoutTotals() {
        tool.setCurrentUserUuid("user-1");

        Map<String, Object> legacyEntry = Map.of("category", "Flight", "amount", 1200, "currency", "USD");
        when(travelService.listChatVisible("user-1"))
                .thenReturn(List.of(dto("Trip", List.of(), List.of(legacyEntry), null)));

        String result = tool.getTravelRecords();

        assertThat(result).contains("1 expense record(s) logged (legacy format, totals unavailable)");
    }

    @Test
    void getTravelRecords_noExpenses_omitsExpenseSection() {
        tool.setCurrentUserUuid("user-1");
        when(travelService.listChatVisible("user-1"))
                .thenReturn(List.of(dto("Trip", List.of(), List.of(), null)));

        String result = tool.getTravelRecords();

        assertThat(result).doesNotContain("Expenses");
    }

    // ── route / notes ────────────────────────────────────────────────────────

    @Test
    void getTravelRecords_includesRouteFromStops() {
        tool.setCurrentUserUuid("user-1");
        List<Map<String, Object>> stops = List.of(
                Map.of("city", "Hong Kong"), Map.of("city", "Tokyo"), Map.of("city", "Hong Kong"));
        when(travelService.listChatVisible("user-1"))
                .thenReturn(List.of(dto("2026 Japan", stops, List.of(), null)));

        String result = tool.getTravelRecords();

        assertThat(result).contains("Route: Hong Kong -> Tokyo -> Hong Kong");
    }

    @Test
    void getTravelRecords_multipleTrips_joinsWithSeparator() {
        tool.setCurrentUserUuid("user-1");
        when(travelService.listChatVisible("user-1")).thenReturn(List.of(
                dto("Trip A", List.of(), List.of(), null),
                dto("Trip B", List.of(), List.of(), null)));

        String result = tool.getTravelRecords();

        assertThat(result).contains("Trip: Trip A");
        assertThat(result).contains("Trip: Trip B");
        assertThat(result).contains("---");
    }
}

package com.subhrodip.squarewise.bff.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.subhrodip.squarewise.bff.transport.model.auth.BrowserLoginStartRequest;
import com.subhrodip.squarewise.bff.transport.model.auth.BrowserLoginStartResponse;
import com.subhrodip.squarewise.bff.transport.model.auth.BrowserLoginVerifyRequest;
import com.subhrodip.squarewise.bff.transport.model.auth.BrowserSessionResponse;
import com.subhrodip.squarewise.bff.transport.model.output.BffAllocation;
import com.subhrodip.squarewise.bff.transport.model.output.BffBalance;
import com.subhrodip.squarewise.bff.transport.model.output.BffBalancesResponse;
import com.subhrodip.squarewise.bff.transport.model.output.BffExpense;
import com.subhrodip.squarewise.bff.transport.model.output.BffGroup;
import com.subhrodip.squarewise.bff.transport.model.output.BffMember;
import com.subhrodip.squarewise.bff.transport.model.output.BffMoney;
import com.subhrodip.squarewise.bff.transport.model.output.BffSuggestedSettlement;
import com.subhrodip.squarewise.bff.transport.model.upstream.UpstreamGroup;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Verifies the JVM bean getters used by Spring/Jackson at the BFF boundary. */
class BffJavaBeanCompatibilityTest {
    @Test
    void core_transport_models_expose_their_wire_getters() {
        BffMoney money = new BffMoney("EUR", "100");
        BffAllocation allocation = new BffAllocation("member", money);
        BffBalance balance = new BffBalance("member", money);
        BffMember member = new BffMember("membership", "subject", "Alice", false);
        BffExpense expense = new BffExpense("expense", 2, "Dinner", money, "other", List.of(allocation));
        BffGroup group = new BffGroup("group", "Trip", "TRIP", "ACTIVE", 4, List.of(balance), List.of(expense), List.of(member));
        UpstreamGroup upstream = new UpstreamGroup("group", "Trip", "TRIP", "ACTIVE", 4);
        BffBalancesResponse balances = new BffBalancesResponse("group", List.of(balance));
        BffSuggestedSettlement suggestion = new BffSuggestedSettlement("from", "to", 100, "EUR");

        assertEquals("EUR", money.getCurrency());
        assertEquals("100", money.getMinor());
        assertEquals("member", allocation.getParticipantId());
        assertEquals(money, allocation.getAmount());
        assertEquals("member", balance.getParticipantId());
        assertEquals(money, balance.getAmount());
        assertEquals(money, balance.getMoney());
        assertEquals("membership", member.getMembershipId());
        assertEquals("Alice", member.getDisplayName());
        assertEquals("expense", expense.getExpenseId());
        assertEquals("expense", expense.getId());
        assertEquals("group", group.getGroupId());
        assertEquals("group", group.getId());
        assertEquals(4, group.getRevision());
        assertEquals("group", balances.getGroupId());
        assertEquals("from", suggestion.getFromParticipantId());
        assertEquals("100", suggestion.getAmount().getMinor());
        assertEquals("group", upstream.getGroupId());
        assertEquals("Trip", upstream.getName());
        assertEquals(4, upstream.getRevision());
    }

    @Test
    void auth_transport_models_expose_their_wire_getters() {
        BrowserLoginStartRequest start = new BrowserLoginStartRequest("alice@example.test", "EMAIL");
        BrowserLoginStartResponse response = new BrowserLoginStartResponse("ACCEPTED", 5L);
        BrowserLoginVerifyRequest verify = new BrowserLoginVerifyRequest("credential");
        BrowserSessionResponse session = new BrowserSessionResponse("AUTHENTICATED", 600L);
        assertEquals("alice@example.test", start.getEmail());
        assertEquals("EMAIL", start.getChannel());
        assertEquals("ACCEPTED", response.getStatus());
        assertEquals(5L, response.getRetryAfterSeconds());
        assertEquals("credential", verify.getCredential());
        assertEquals("AUTHENTICATED", session.getStatus());
        assertEquals(600L, session.getExpiresIn());
    }
}

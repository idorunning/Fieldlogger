package com.field.logger.nativeapp;

import static org.junit.Assert.*;
import java.util.*;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class) @Config(sdk = 36)
public class BillingPolicyTest {
  @Test public void accountBindingIsStableHashNeverEmailOrPlainId() {
    String id = "acct-private-123";
    assertEquals("132285fb06a40907fcd935fb57efcade3326bbefdf9e006a1fa3be81d1eb8f9f", BillingPolicy.accountId(id));
    assertEquals(64, BillingPolicy.accountId(id).length());
    assertNotEquals(BillingPolicy.accountId(id), BillingPolicy.accountId("another-user"));
  }
  @Test public void guestCannotProducePurchaseBinding() {
    assertThrows(IllegalArgumentException.class, () -> BillingPolicy.accountId("guest"));
    assertThrows(IllegalArgumentException.class, () -> BillingPolicy.accountId(""));
  }
  @Test public void paidLabelDoesNotGrantEntitlementWithoutExplicitVerification() throws Exception {
    assertFalse(BillingPolicy.verified(new JSONObject().put("plan", "premium")));
    assertFalse(BillingPolicy.verified(new JSONObject().put("verified", false)));
    assertFalse(BillingPolicy.verified(new JSONObject().put("verified", "true")));
    assertFalse(BillingPolicy.verified(null));
    assertTrue(BillingPolicy.verified(new JSONObject().put("verified", true)));
  }
  @Test public void returningSubscriberGetsBasePlanNotAnotherTrial() {
    List<BillingPolicy.Offer> offers = Arrays.asList(
        new BillingPolicy.Offer(false, true, true, false), new BillingPolicy.Offer(true, false, true, false));
    assertEquals(1, BillingPolicy.chooseOffer(offers, false));
    assertEquals(0, BillingPolicy.chooseOffer(offers, true));
  }
  @Test public void unsupportedTrialPeriodIsNotSilentlySelected() {
    List<BillingPolicy.Offer> offers = Arrays.asList(
        new BillingPolicy.Offer(false, false, true, true), new BillingPolicy.Offer(true, false, true, false));
    assertEquals(1, BillingPolicy.chooseOffer(offers, true));
    assertEquals(-1, BillingPolicy.chooseOffer(Collections.singletonList(offers.get(0)), true));
    assertEquals(-1, BillingPolicy.chooseOffer(Collections.singletonList(new BillingPolicy.Offer(true, true, false, false)), true));
  }
  @Test public void unknownServerTrialEligibilityUsesOnlyActualPlayReturnedOffers() throws Exception {
    JSONObject unknown = new JSONObject().put("trialEligible", JSONObject.NULL);
    assertTrue(BillingPolicy.considerPlayTrials(unknown));
    assertTrue(BillingPolicy.considerPlayTrials(new JSONObject()));
    assertFalse(BillingPolicy.considerPlayTrials(new JSONObject().put("trialEligible", false)));
    assertTrue(BillingPolicy.considerPlayTrials(new JSONObject().put("trialEligible", true)));
    assertFalse(BillingPolicy.considerPlayTrials(null));
    List<BillingPolicy.Offer> trialAndBase = Arrays.asList(
        new BillingPolicy.Offer(false, true, true, false), new BillingPolicy.Offer(true, false, true, false));
    assertEquals(0, BillingPolicy.chooseOffer(trialAndBase, BillingPolicy.considerPlayTrials(unknown)));
    // Unknown eligibility cannot create a trial when Google returned only a paid base plan.
    assertEquals(0, BillingPolicy.chooseOffer(Collections.singletonList(trialAndBase.get(1)), BillingPolicy.considerPlayTrials(unknown)));
  }
}

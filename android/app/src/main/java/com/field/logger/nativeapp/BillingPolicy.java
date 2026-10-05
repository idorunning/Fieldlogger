package com.field.logger.nativeapp;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.json.JSONObject;

/** Small, conservative decisions shared by checkout and restore. The server grants entitlements. */
public final class BillingPolicy {
  private BillingPolicy() {}

  public static final Map<String, String> PRODUCTS;
  static {
    LinkedHashMap<String, String> products = new LinkedHashMap<>();
    products.put("trail_plus_monthly", "plus");
    products.put("trail_premium_monthly", "premium");
    products.put("trail_premium_yearly", "annual");
    PRODUCTS = Collections.unmodifiableMap(products);
  }

  public static String accountId(String owner) {
    if (owner == null || owner.isBlank() || owner.equals("guest"))
      throw new IllegalArgumentException("Sign in before starting a subscription.");
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(owner.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder();
      for (byte b : digest) hex.append(String.format(Locale.ROOT, "%02x", b & 255));
      return hex.toString();
    } catch (java.security.NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  public static boolean verified(JSONObject result) {
    return result != null && Boolean.TRUE.equals(result.opt("verified"));
  }

  /** Play omits trial offers for ineligible purchasers. Unknown server eligibility defers to Play. */
  public static boolean considerPlayTrials(JSONObject status) {
    return status != null && !Boolean.FALSE.equals(status.opt("trialEligible"));
  }

  public static final class Offer {
    public final boolean basePlan, oneMonthTrial, hasPaidPhase, hasOtherFreePhase;
    public Offer(boolean basePlan, boolean oneMonthTrial, boolean hasPaidPhase, boolean hasOtherFreePhase) {
      this.basePlan = basePlan;
      this.oneMonthTrial = oneMonthTrial;
      this.hasPaidPhase = hasPaidPhase;
      this.hasOtherFreePhase = hasOtherFreePhase;
    }
  }

  /** Never silently choose an introductory offer with a different free period. */
  public static int chooseOffer(List<Offer> offers, boolean trialEligible) {
    if (trialEligible)
      for (int i = 0; i < offers.size(); i++) {
        Offer offer = offers.get(i);
        if (offer.oneMonthTrial && offer.hasPaidPhase && !offer.hasOtherFreePhase) return i;
      }
    for (int i = 0; i < offers.size(); i++) {
      Offer offer = offers.get(i);
      if (offer.basePlan && offer.hasPaidPhase && !offer.oneMonthTrial && !offer.hasOtherFreePhase)
        return i;
    }
    return -1;
  }
}

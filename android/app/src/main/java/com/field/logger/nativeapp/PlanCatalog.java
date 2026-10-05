package com.field.logger.nativeapp;

import java.util.*;
import org.json.*;

/** Display-only server allowances. Access is still granted solely by verified backend purchases. */
public final class PlanCatalog {
  public static final class Plan {
    public final String id, name, productId;
    public final int monthlyPhotos, annualBonus, monthlyCloserReviews, annualBonusCloserReviews;

    Plan(
        String id,
        String name,
        String productId,
        int photos,
        int bonus,
        int reviews,
        int extraReviews) {
      this.id = id;
      this.name = name;
      this.productId = productId;
      monthlyPhotos = photos;
      annualBonus = bonus;
      monthlyCloserReviews = reviews;
      annualBonusCloserReviews = extraReviews;
    }

    public String allowance() {
      return monthlyPhotos
          + " new photos each month"
          + (annualBonus > 0
              ? ", plus " + annualBonus + " extra photos during each subscription year"
              : "");
    }

    public String closerReviews() {
      return monthlyCloserReviews
          + " automatic closer reviews each month"
          + (annualBonusCloserReviews > 0
              ? ", plus "
                  + annualBonusCloserReviews
                  + " extra closer reviews during each subscription year"
              : "");
    }
  }

  private PlanCatalog() {}

  public static List<Plan> read(JSONObject response) {
    if (response == null) return Collections.emptyList();
    JSONArray data = response.optJSONArray("plans");
    if (data == null || data.length() != 4) return Collections.emptyList();
    Map<String, Plan> result = new HashMap<>();
    for (int i = 0; i < data.length(); i++) {
      JSONObject p = data.optJSONObject(i);
      if (p == null) return Collections.emptyList();
      String id = p.optString("id"), name = p.optString("name").trim();
      int photos = p.optInt("limit", -1), bonus = p.optInt("bonusPhotosPerYear", 0);
      int reviews = p.optInt("closerLookLimit", -1),
          extraReviews = p.optInt("annualBonusCloserLooks", 0);
      if (!Set.of("free", "plus", "premium", "annual").contains(id)
          || result.containsKey(id)
          || name.isEmpty()
          || name.length() > 60
          || photos < 1
          || photos > 100_000
          || !(p.opt("limit") instanceof Number)
          || p.optDouble("limit") != photos
          || !p.has("productId")
          || bonus < 0
          || bonus > 100_000
          || (!id.equals("annual") && bonus != 0)) return Collections.emptyList();
      if (!(p.opt("closerLookLimit") instanceof Number)
          || reviews < 0
          || reviews > 100_000
          || p.optDouble("closerLookLimit") != reviews
          || extraReviews < 0
          || extraReviews > 100_000
          || (!id.equals("annual") && extraReviews != 0)) return Collections.emptyList();
      String product = p.isNull("productId") ? null : p.optString("productId", "");
      if (id.equals("free") ? product != null : !id.equals(BillingPolicy.PRODUCTS.get(product)))
        return Collections.emptyList();
      result.put(id, new Plan(id, name, product, photos, bonus, reviews, extraReviews));
    }
    List<Plan> plans = new ArrayList<>();
    for (String id : new String[] {"free", "plus", "premium", "annual"}) {
      if (!result.containsKey(id)) return Collections.emptyList();
      plans.add(result.get(id));
    }
    return Collections.unmodifiableList(plans);
  }
}

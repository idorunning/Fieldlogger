package com.field.logger.nativeapp;

import static org.junit.Assert.*;

import java.util.List;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 24)
public class PlanCatalogTest {
  private JSONObject catalog() throws Exception {
    JSONArray plans = new JSONArray();
    String[] ids = {"free", "plus", "premium", "annual"};
    String[] products = {
      null, "trail_plus_monthly", "trail_premium_monthly", "trail_premium_yearly"
    };
    for (int i = 0; i < ids.length; i++) {
      JSONObject p = new JSONObject();
      p.put("id", ids[i]);
      p.put("name", "Server " + ids[i]);
      p.put("limit", 20 * (i + 1));
      p.put("productId", products[i] == null ? JSONObject.NULL : products[i]);
      p.put("bonusPhotosPerYear", i == 3 ? 50 : 0);
      p.put("closerLookLimit", 5 * (i + 1));
      p.put("annualBonusCloserLooks", i == 3 ? 10 : 0);
      plans.put(p);
    }
    return new JSONObject().put("plans", plans);
  }

  @Test
  public void nativeAllowancesAndAnnualExtrasComeFromTheServerRatherThanBundleDefaults()
      throws Exception {
    JSONObject source = catalog();
    List<PlanCatalog.Plan> plans = PlanCatalog.read(source);
    assertEquals(4, plans.size());
    assertEquals("Server free", plans.get(0).name);
    assertEquals(20, plans.get(0).monthlyPhotos);
    assertTrue(plans.get(1).allowance().startsWith("40 new photos"));
    assertTrue(plans.get(3).allowance().contains("50 extra photos"));
    assertEquals(5, plans.get(0).monthlyCloserReviews);
    assertTrue(plans.get(3).closerReviews().contains("10 extra closer reviews"));
    source.getJSONArray("plans").getJSONObject(2).put("limit", 240);
    assertEquals(240, PlanCatalog.read(source).get(2).monthlyPhotos);
    source.getJSONArray("plans").getJSONObject(2).put("closerLookLimit", 35);
    assertEquals(35, PlanCatalog.read(source).get(2).monthlyCloserReviews);
  }

  @Test
  public void MissingOrUnrelatedCatalogNeverEnablesCheckoutWithInventedAllowances()
      throws Exception {
    assertTrue(PlanCatalog.read(null).isEmpty());
    assertTrue(PlanCatalog.read(new JSONObject()).isEmpty());
    JSONObject source = catalog();
    source.getJSONArray("plans").getJSONObject(1).put("productId", "unrelated_subscription");
    assertTrue(PlanCatalog.read(source).isEmpty());
    source = catalog();
    source.getJSONArray("plans").getJSONObject(2).put("id", "plus");
    assertTrue(PlanCatalog.read(source).isEmpty());
    source = catalog();
    source.getJSONArray("plans").getJSONObject(0).put("limit", -1);
    assertTrue(PlanCatalog.read(source).isEmpty());
    source = catalog();
    source.getJSONArray("plans").getJSONObject(0).put("closerLookLimit", -1);
    assertTrue(PlanCatalog.read(source).isEmpty());
    source = catalog();
    source.getJSONArray("plans").getJSONObject(1).put("annualBonusCloserLooks", 5);
    assertTrue(PlanCatalog.read(source).isEmpty());
  }
}

package com.field.logger;

import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.*;
import com.android.billingclient.api.ProductDetails;
import com.field.logger.nativeapp.*;
import java.util.*;
import org.json.*;

/** Native account-bound subscriptions. Actual price and purchase confirmation belong to Play. */
public final class SubscriptionActivity extends AppCompatActivity {
  private static final int GREEN = 0xff154e45, PAPER = 0xfffffdf7, MUTED = 0xff68796f;
  private Repository repo;
  private BillingManager billing;
  private LinearLayout content, planCards;
  private TextView serviceStatus, purchaseStatus;
  private JSONObject accountStatus;
  private List<PlanCatalog.Plan> catalog = Collections.emptyList();
  private boolean catalogFinalised, loadingPlans;
  private long plansLoadedAt;
  private final Map<String, TextView> choices = new LinkedHashMap<>();
  private final Map<String, TextView> prices = new LinkedHashMap<>();
  private boolean loading, alive = true;
  private String owner;

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    repo = new Repository(this);
    owner = repo.owner();
    WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
    getWindow().setStatusBarColor(GREEN);
    getWindow().setNavigationBarColor(PAPER);
    new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView())
        .setAppearanceLightStatusBars(false);
    FrameLayout root = new FrameLayout(this);
    root.setBackgroundColor(PAPER);
    setContentView(root);
    ViewCompat.setOnApplyWindowInsetsListener(
        root,
        (v, insets) -> {
          Insets bars =
              insets.getInsets(
                  WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
          v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
          return insets;
        });
    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    root.addView(scroll);
    content = new LinearLayout(this);
    content.setOrientation(LinearLayout.VERTICAL);
    content.setPadding(dp(22), dp(18), dp(22), dp(32));
    scroll.addView(content);
    TextView back = button("‹  Back to your journal", false, this::finish);
    content.addView(back);
    TextView brand = text("My Trail Log", 35, GREEN, true);
    brand.setTypeface(
        androidx.core.content.res.ResourcesCompat.getFont(this, R.font.trail_log_wordmark));
    content.addView(brand);
    gap(14);
    content.addView(text("Your trail, your pace", 27, GREEN, true));
    content.addView(
        text(
            "Keep exploring. Choose a monthly allowance for new photos added to your cloud"
                + " journal.",
            16,
            MUTED,
            false));
    gap(18);
    serviceStatus =
        text(
            repo.session.get() == null
                ? "Sign in from your journal to view a plan."
                : "Checking your allowance…",
            16,
            GREEN,
            true);
    content.addView(serviceStatus);
    gap(12);
    planCards = new LinearLayout(this);
    planCards.setOrientation(LinearLayout.VERTICAL);
    content.addView(planCards);
    renderPlans();
    gap(10);
    content.addView(
        text(
            "Eligible subscribers can get a one-month free trial of their selected paid plan when a"
                + " Google Play offer is available. The trial has the selected plan’s monthly photo"
                + " and automatic closer-review allowances in total; a calendar-month reset does"
                + " not add extra trial allowance. Play confirms eligibility, the renewal date and"
                + " the exact local price before you subscribe.",
            14,
            MUTED,
            false));
    gap(16);
    content.addView(
        button(
            "Restore Google Play purchases",
            false,
            () -> {
              if (billing == null) toast("Sign in before restoring purchases.");
              else {
                billing.restore();
                refresh();
              }
            }));
    gap(8);
    content.addView(
        button(
            "Manage or cancel in Google Play",
            false,
            () ->
                open(
                    "https://play.google.com/store/account/subscriptions?package=com.field.logger")));
    gap(14);
    purchaseStatus = text("", 14, GREEN, false);
    content.addView(purchaseStatus);
    content.addView(
        text(
            "Complex or uncertain photos can receive an automatic closer review. "
                + "This allowance counts submitted review attempts, including a service failure. "
                + "If a closer review is unavailable, the suggested identity stays tentative. "
                + "Basic identification continues when the closer-review allowance is used up.",
            14,
            MUTED,
            false));
    gap(10);
    content.addView(
        text(
            "Offline photo capture remains available when an allowance is used up. Uploads resume"
                + " when your allowance renews or your plan changes. Existing photos, archive"
                + " access and downloading your photos and all journal data as ZIP remain free.",
            14,
            MUTED,
            false));
    gap(12);
    content.addView(
        text(
            "Monthly allowances do not roll over. Any annual bonus shown for a plan can be used"
                + " after a monthly allowance is exhausted and expires at the end of that"
                + " subscription year. Subscriptions renew automatically unless cancelled in Google"
                + " Play. Cancellation stops the next renewal; paid access continues to the end of"
                + " the period. Google Play controls checkout, applicable taxes and refunds.",
            13,
            MUTED,
            false));
    gap(10);
    TextView policy =
        button("Terms and subscription details", false, () -> open(Api.ORIGIN + "/terms"));
    content.addView(policy);
    gap(8);
    content.addView(button("Privacy policy", false, () -> open(Api.ORIGIN + "/privacy")));
    if (repo.session.get() != null) {
      billing =
          new BillingManager(
              this,
              new BillingManager.Listener() {
                public void onCatalog(Map<String, ProductDetails> catalog) {
                  updateChoices();
                }

                public void onStatus(String message) {
                  if (alive) purchaseStatus.setText(message);
                }

                public void onVerified() {
                  refresh();
                  repo.enqueue();
                }
              });
      billing.connect();
      refresh();
    }
    loadPlans();
  }

  private void loadPlans() {
    if (loadingPlans || !alive || System.currentTimeMillis() - plansLoadedAt < 5 * 60_000L) return;
    loadingPlans = true;
    Repository.IO.execute(
        () -> {
          JSONObject response = null;
          try {
            response = repo.api.json("/api/plans", "GET", null, null);
          } catch (Exception ignored) {
          }
          JSONObject result = response;
          runOnUiThread(
              () -> {
                loadingPlans = false;
                if (!alive || !repo.owner().equals(owner)) return;
                catalog = PlanCatalog.read(result);
                catalogFinalised =
                    result != null
                        && result.optBoolean(
                            "pricingFinalised", result.optBoolean("billingReady", false));
                if (!catalog.isEmpty()) plansLoadedAt = System.currentTimeMillis();
                renderPlans();
                updateChoices();
              });
        });
  }

  private void renderPlans() {
    planCards.removeAllViews();
    choices.clear();
    prices.clear();
    int[] tints = {0xffeaf0df, 0xfff5ebc9, 0xffe2edf0, 0xffefe6f3};
    if (catalog.isEmpty()) {
      String[] labels = {"Free", "Plus", "Premium", "Premium annual"};
      String[] products = {
        null, "trail_plus_monthly", "trail_premium_monthly", "trail_premium_yearly"
      };
      for (int i = 0; i < labels.length; i++)
        plan(
            labels[i],
            "Allowance being finalised",
            i == 0 ? "Always free" : "Pricing is being finalised",
            products[i],
            tints[i]);
      return;
    }
    for (int i = 0; i < catalog.size(); i++) {
      PlanCatalog.Plan spec = catalog.get(i);
      plan(
          spec.name,
          spec.id.equals("free") || catalogFinalised
              ? spec.allowance() + "\n" + spec.closerReviews()
              : "Allowance being finalised",
          spec.id.equals("free")
              ? "Always free"
              : catalogFinalised ? "Price confirmed in Google Play" : "Pricing is being finalised",
          spec.productId,
          tints[i]);
    }
  }

  private void plan(
      String title, String allowance, String fallbackPrice, String productId, int tint) {
    LinearLayout card = new LinearLayout(this);
    card.setOrientation(LinearLayout.VERTICAL);
    card.setPadding(dp(17), dp(16), dp(17), dp(16));
    card.setBackground(shape(tint, 22));
    card.addView(text(title, 22, GREEN, true));
    card.addView(text(allowance, 16, GREEN, false));
    TextView price = text(fallbackPrice, 15, MUTED, true);
    card.addView(price);
    if (productId != null) {
      prices.put(productId, price);
      TextView action =
          button(
              "Awaiting Google Play setup",
              true,
              () -> {
                boolean ready =
                    catalogFinalised
                        && !catalog.isEmpty()
                        && accountStatus != null
                        && accountStatus.optBoolean("billingReady", false)
                        && enabledProduct(productId);
                if (billing == null) toast("Sign in before choosing a subscription.");
                else
                  billing.launch(
                      this, productId, BillingPolicy.considerPlayTrials(accountStatus), ready);
              });
      action.setEnabled(false);
      action.setAlpha(.55f);
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(54));
      lp.topMargin = dp(12);
      card.addView(action, lp);
      choices.put(productId, action);
    }
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.bottomMargin = dp(12);
    planCards.addView(card, lp);
  }

  private boolean enabledProduct(String id) {
    if (accountStatus == null) return false;
    JSONArray products = accountStatus.optJSONArray("products");
    if (products == null) return false;
    for (int n = 0; n < products.length(); n++) {
      JSONObject product = products.optJSONObject(n);
      if (product != null
          && id.equals(product.optString("productId"))
          && BillingPolicy.PRODUCTS.get(id).equals(product.optString("plan"))) return true;
    }
    return false;
  }

  private void refresh() {
    if (loading || repo.session.get() == null || !repo.owner().equals(owner)) return;
    loading = true;
    JSONObject user = repo.session.get();
    Repository.IO.execute(
        () -> {
          try {
            JSONObject status =
                repo.api.json("/api/billing", "GET", user.optString("cookie"), null);
            runOnUiThread(
                () -> {
                  loading = false;
                  if (!alive || !repo.owner().equals(owner)) return;
                  accountStatus = status;
                  String title = status.optString("planName", "Your plan");
                  serviceStatus.setText(
                      title
                          + (status.has("limit") && status.has("remaining")
                              ? " · "
                                  + status.optInt("used")
                                  + " of "
                                  + status.optInt("limit")
                                  + " photos used\n"
                                  + Math.max(0, status.optInt("remaining"))
                                  + " remaining this month"
                              : " · current allowance could not be confirmed")
                          + (status.optInt("bonusRemaining") > 0
                              ? " · " + status.optInt("bonusRemaining") + " annual extras"
                              : "")
                          + (status.has("closerLookLimit")
                                  && status.has("closerLooksUsed")
                                  && status.has("closerLooksRemaining")
                              ? "\nAutomatic closer reviews · "
                                  + status.optInt("closerLooksUsed")
                                  + " of "
                                  + status.optInt("closerLookLimit")
                                  + " used · "
                                  + Math.max(0, status.optInt("closerLooksRemaining"))
                                  + " remaining this month"
                              : "")
                          + (status.optInt("annualBonusCloserLooksRemaining") > 0
                              ? " · "
                                  + status.optInt("annualBonusCloserLooksRemaining")
                                  + " annual review extras"
                              : "")
                          + (status.optBoolean("annualBonusPending", false)
                              ? "\n"
                                  + "Annual extras are awaiting Google Play confirmation. Your"
                                  + " monthly allowance is available."
                              : "")
                          + (status.optBoolean("isTrial") && !status.isNull("trialRemaining")
                              ? "\n"
                                  + Math.max(0, status.optInt("trialRemaining"))
                                  + " photos left in this selected-plan trial in total"
                              : "")
                          + (status.optBoolean("isTrial")
                                  && !status.isNull("trialCloserLooksRemaining")
                              ? "\n"
                                  + Math.max(0, status.optInt("trialCloserLooksRemaining"))
                                  + " automatic closer reviews left in this trial in total"
                              : "")
                          + (status.optBoolean("billingReady", false)
                              ? ""
                              : "\nPaid checkout is awaiting Google Play setup."));
                  updateChoices();
                });
          } catch (Exception e) {
            runOnUiThread(
                () -> {
                  loading = false;
                  if (!alive) return;
                  accountStatus = null;
                  serviceStatus.setText(
                      "Could not check your allowance. Your local journal and free exports remain"
                          + " available. Reopen this page when connected.");
                  updateChoices();
                });
          }
        });
  }

  private void updateChoices() {
    if (!alive) return;
    for (Map.Entry<String, TextView> entry : choices.entrySet()) {
      String id = entry.getKey();
      TextView button = entry.getValue();
      boolean trial = BillingPolicy.considerPlayTrials(accountStatus);
      BillingManager.Choice offer = billing == null ? null : billing.choice(id, trial);
      boolean ready =
          catalogFinalised
              && !catalog.isEmpty()
              && accountStatus != null
              && accountStatus.optBoolean("billingReady", false)
              && enabledProduct(id)
              && offer != null;
      button.setEnabled(ready);
      button.setAlpha(ready ? 1f : .55f);
      if (!catalogFinalised || catalog.isEmpty()) {
        button.setText("Pricing is being finalised");
        prices.get(id).setText("Pricing is being finalised");
      } else if (offer == null) button.setText("Awaiting Google Play setup");
      else {
        String period =
            offer.period.equals("P1Y")
                ? "year"
                : offer.period.equals("P1M") ? "month" : "billing period";
        prices
            .get(id)
            .setText(
                offer.price
                    + " / "
                    + period
                    + (offer.trial ? " · after one-month free trial" : ""));
        button.setText(
            !ready
                ? "Checkout not available yet"
                : offer.trial ? "Start one-month free trial" : "Choose this plan");
      }
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (repo != null && !repo.owner().equals(owner)) {
      finish();
      return;
    }
    if (billing != null) {
      billing.restore();
      refresh();
    }
    if (repo != null) loadPlans();
  }

  @Override
  protected void onDestroy() {
    alive = false;
    if (billing != null) billing.close();
    super.onDestroy();
  }

  private int dp(float n) {
    return Math.round(n * getResources().getDisplayMetrics().density);
  }

  private void gap(int n) {
    content.addView(new View(this), new LinearLayout.LayoutParams(1, dp(n)));
  }

  private GradientDrawable shape(int color, int radius) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(dp(radius));
    return d;
  }

  private TextView text(String value, int size, int color, boolean bold) {
    TextView t = new TextView(this);
    t.setText(value);
    t.setTextSize(size);
    t.setTextColor(color);
    t.setLineSpacing(dp(3), 1);
    if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    return t;
  }

  private TextView button(String label, boolean primary, Runnable action) {
    TextView b = text(label, 16, primary ? PAPER : GREEN, true);
    b.setGravity(Gravity.CENTER);
    b.setMinHeight(dp(52));
    b.setPadding(dp(12), dp(10), dp(12), dp(10));
    b.setBackground(shape(primary ? GREEN : 0xffe9efdf, 16));
    b.setClickable(true);
    b.setFocusable(true);
    b.setOnClickListener(v -> action.run());
    return b;
  }

  private void toast(String value) {
    Toast.makeText(this, value, Toast.LENGTH_LONG).show();
  }

  private void open(String url) {
    try {
      startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    } catch (RuntimeException e) {
      toast("No browser is available on this device.");
    }
  }
}

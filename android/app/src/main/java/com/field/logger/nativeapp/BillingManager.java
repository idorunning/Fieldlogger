package com.field.logger.nativeapp;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import com.android.billingclient.api.*;
import java.util.*;
import org.json.*;

/** Play checkout only. A Play callback is never itself a paid entitlement. */
public final class BillingManager implements PurchasesUpdatedListener {
  private static final Map<String, Long> FOREGROUND_RESTORE = new HashMap<>();

  public interface Listener {
    void onCatalog(Map<String, ProductDetails> catalog);

    void onStatus(String message);

    void onVerified();
  }

  public static final class Choice {
    public final ProductDetails details;
    public final ProductDetails.SubscriptionOfferDetails offer;
    public final String price, period;
    public final boolean trial;

    Choice(
        ProductDetails d,
        ProductDetails.SubscriptionOfferDetails o,
        String p,
        String period,
        boolean t) {
      details = d;
      offer = o;
      price = p;
      this.period = period;
      trial = t;
    }
  }

  private final Repository repo;
  private final String owner, cookie;
  private final Handler main = new Handler(Looper.getMainLooper());
  private final BillingClient billing;
  private final Listener listener;
  private final Map<String, ProductDetails> catalog = new LinkedHashMap<>();
  private final Set<String> verifying = new HashSet<>();
  private volatile Purchase current;
  private volatile boolean currentVerified;
  private volatile boolean closed, connected, connecting, restoring, ownershipChecked;

  public BillingManager(Context context, Listener listener) {
    repo = new Repository(context);
    owner = repo.owner();
    JSONObject account = repo.session.get();
    cookie = account == null ? "" : account.optString("cookie");
    this.listener = listener;
    billing =
        BillingClient.newBuilder(context.getApplicationContext())
            .setListener(this)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .build();
  }

  public void connect() {
    if (closed || connected || connecting) return;
    if (cookie.isEmpty() || owner.equals("guest")) {
      status("Sign in to view or restore a subscription.");
      return;
    }
    connecting = true;
    billing.startConnection(
        new BillingClientStateListener() {
          @Override
          public void onBillingSetupFinished(@NonNull BillingResult result) {
            if (closed) return;
            connecting = false;
            if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
              status(
                  "Google Play billing is unavailable on this device. Your journal and exports"
                      + " remain available.");
              return;
            }
            connected = true;
            queryCatalog();
            restore();
          }

          @Override
          public void onBillingServiceDisconnected() {
            connected = false;
            connecting = false;
          }
        });
  }

  private void queryCatalog() {
    List<QueryProductDetailsParams.Product> products = new ArrayList<>();
    for (String id : BillingPolicy.PRODUCTS.keySet())
      products.add(
          QueryProductDetailsParams.Product.newBuilder()
              .setProductId(id)
              .setProductType(BillingClient.ProductType.SUBS)
              .build());
    billing.queryProductDetailsAsync(
        QueryProductDetailsParams.newBuilder().setProductList(products).build(),
        (result, response) -> {
          if (closed) return;
          main.post(
              () -> {
                if (closed) return;
                catalog.clear();
                if (result.getResponseCode() == BillingClient.BillingResponseCode.OK)
                  for (ProductDetails product : response.getProductDetailsList())
                    if (BillingPolicy.PRODUCTS.containsKey(product.getProductId()))
                      catalog.put(product.getProductId(), product);
                listener.onCatalog(Collections.unmodifiableMap(new LinkedHashMap<>(catalog)));
                if (catalog.isEmpty())
                  status(
                      "Subscriptions are awaiting Google Play setup. No payment can be taken here"
                          + " yet.");
              });
        });
  }

  public Choice choice(String productId, boolean trialEligible) {
    ProductDetails d = catalog.get(productId);
    if (d == null || d.getSubscriptionOfferDetails() == null) return null;
    List<ProductDetails.SubscriptionOfferDetails> offers = d.getSubscriptionOfferDetails();
    List<BillingPolicy.Offer> descriptors = new ArrayList<>();
    for (ProductDetails.SubscriptionOfferDetails o : offers) {
      boolean trial = false, paid = false, otherFree = false;
      for (ProductDetails.PricingPhase p : o.getPricingPhases().getPricingPhaseList()) {
        if (p.getPriceAmountMicros() > 0) paid = true;
        else if (p.getBillingPeriod().equals("P1M") && p.getBillingCycleCount() == 1) trial = true;
        else otherFree = true;
      }
      descriptors.add(new BillingPolicy.Offer(o.getOfferId() == null, trial, paid, otherFree));
    }
    int selected = BillingPolicy.chooseOffer(descriptors, trialEligible);
    if (selected < 0) return null;
    ProductDetails.SubscriptionOfferDetails offer = offers.get(selected);
    ProductDetails.PricingPhase recurring = null;
    for (ProductDetails.PricingPhase p : offer.getPricingPhases().getPricingPhaseList())
      if (p.getPriceAmountMicros() > 0) recurring = p;
    if (recurring == null) return null;
    return new Choice(
        d,
        offer,
        recurring.getFormattedPrice(),
        recurring.getBillingPeriod(),
        descriptors.get(selected).oneMonthTrial);
  }

  /** Readiness comes from the signed-in backend, not from catalog availability. */
  public void launch(
      Activity activity, String productId, boolean trialEligible, boolean serviceReady) {
    if (!serviceReady
        || closed
        || !connected
        || !billing.isReady()
        || !repo.owner().equals(owner)) {
      status("Checkout is unavailable. Refresh your plan or sign in again.");
      return;
    }
    if (!ownershipChecked || restoring) {
      status(
          "Google Play is checking your existing purchases. Wait for the check before choosing a"
              + " plan.");
      return;
    }
    Choice selected = choice(productId, trialEligible);
    if (selected == null) {
      status("This plan is not available from Google Play yet.");
      return;
    }
    if (current != null && !currentVerified) {
      status(
          current.getPurchaseState() == Purchase.PurchaseState.PENDING
              ? "A payment is pending in Google Play. Complete or cancel it there before choosing"
                    + " another plan."
              : "Your existing purchase is awaiting verification. Restore it before changing"
                    + " plans.");
      return;
    }
    if (current != null && current.getProducts().contains(productId)) {
      status(
          "You already have this Play subscription. Use Manage subscription to change or cancel"
              + " it.");
      return;
    }
    BillingFlowParams.Builder params =
        BillingFlowParams.newBuilder()
            .setObfuscatedAccountId(BillingPolicy.accountId(owner))
            .setProductDetailsParamsList(
                Collections.singletonList(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(selected.details)
                        .setOfferToken(selected.offer.getOfferToken())
                        .build()));
    if (current != null && current.getPurchaseState() == Purchase.PurchaseState.PURCHASED)
      params.setSubscriptionUpdateParams(
          BillingFlowParams.SubscriptionUpdateParams.newBuilder()
              .setOldPurchaseToken(current.getPurchaseToken())
              .setSubscriptionReplacementMode(
                  BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.WITH_TIME_PRORATION)
              .build());
    BillingResult result = billing.launchBillingFlow(activity, params.build());
    if (result.getResponseCode() != BillingClient.BillingResponseCode.OK)
      status(
          result.getResponseCode() == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED
              ? "Google Play already has this subscription. Tap Restore purchases."
              : "Google Play could not open checkout. No plan change was confirmed.");
  }

  public void restore() {
    if (closed || !billing.isReady()) {
      connect();
      return;
    }
    if (restoring) return;
    restoring = true;
    ownershipChecked = false;
    billing.queryPurchasesAsync(
        QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build(),
        (result, purchases) -> {
          if (closed) return;
          restoring = false;
          if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
            status(
                "Google Play could not check purchases. Try Restore purchases again when"
                    + " connected.");
            return;
          }
          ownershipChecked = true;
          if (purchases.isEmpty()) {
            current = null;
            currentVerified = false;
            status("No active My Trail Log Play subscription was found on this Google account.");
          }
          for (Purchase purchase : purchases) verify(purchase);
        });
  }

  @Override
  public void onPurchasesUpdated(@NonNull BillingResult result, List<Purchase> purchases) {
    if (closed) return;
    if (result.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
      for (Purchase purchase : purchases) verify(purchase);
    } else if (result.getResponseCode() == BillingClient.BillingResponseCode.USER_CANCELED)
      status("Checkout closed. Your journal is unchanged.");
    else if (result.getResponseCode() == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED)
      restore();
    else
      status(
          "Google Play has not confirmed a subscription. Restore purchases if payment was"
              + " completed.");
  }

  private void verify(Purchase purchase) {
    if (closed) return;
    String productId = null;
    for (String id : purchase.getProducts())
      if (BillingPolicy.PRODUCTS.containsKey(id)) {
        productId = id;
        break;
      }
    if (productId == null) return;
    // A token belongs to the account selected at checkout. Never adopt another account's purchase.
    AccountIdentifiers identifiers = purchase.getAccountIdentifiers();
    String linked = identifiers == null ? null : identifiers.getObfuscatedAccountId();
    if (linked == null || linked.isEmpty()) {
      status(
          "This purchase has no My Trail Log account link. Contact support; it cannot be adopted by"
              + " another account.");
      return;
    }
    if (!linked.equals(BillingPolicy.accountId(owner))) {
      status(
          "This subscription belongs to another My Trail Log account. Sign in to that account to"
              + " restore it.");
      return;
    }
    if (purchase.getPurchaseState() == Purchase.PurchaseState.PENDING) {
      current = purchase;
      currentVerified = false;
      status(
          "Payment is pending in Google Play. Your plan changes only after payment and server"
              + " verification.");
      return;
    }
    if (purchase.getPurchaseState() != Purchase.PurchaseState.PURCHASED) return;
    if (current == null || !current.getPurchaseToken().equals(purchase.getPurchaseToken()))
      currentVerified = false;
    current = purchase; // Prevent a second purchase while this account's existing token is checked.
    synchronized (verifying) {
      if (!verifying.add(purchase.getPurchaseToken())) return;
    }
    final String id = productId;
    status("Checking your purchase securely…");
    Repository.IO.execute(
        () -> {
          try {
            if (!repo.owner().equals(owner)) return;
            JSONObject body = new JSONObject();
            body.put("productId", id);
            body.put("purchaseToken", purchase.getPurchaseToken());
            JSONObject result = repo.api.json("/api/billing/verify", "POST", cookie, body);
            if (!BillingPolicy.verified(result))
              throw new java.io.IOException(
                  "Your purchase is not verified yet. Try Restore purchases shortly.");
            main.post(
                () -> {
                  if (closed || !repo.owner().equals(owner)) return;
                  current = purchase;
                  currentVerified = true;
                  // The backend acknowledges only after saving the entitlement; client never grants
                  // it.
                  listener.onVerified();
                  status("Your Google Play subscription has been verified.");
                });
          } catch (Exception e) {
            status(
                e instanceof Api.Failure
                    ? e.getMessage()
                    : "Your purchase needs server verification. Try Restore purchases when"
                          + " connected; do not buy it again.");
          } finally {
            synchronized (verifying) {
              verifying.remove(purchase.getPurchaseToken());
            }
          }
        });
  }

  private void status(String message) {
    main.post(
        () -> {
          if (!closed) listener.onStatus(message);
        });
  }

  public void close() {
    closed = true;
    billing.endConnection();
  }

  /**
   * Quiet foreground restore; tokens remain in Play/memory and are never written to preferences.
   */
  public static void restore(Context context) {
    Context app = context.getApplicationContext();
    if (Looper.myLooper() != Looper.getMainLooper()) {
      new Handler(Looper.getMainLooper()).post(() -> restore(app));
      return;
    }
    Session session = new Session(app);
    if (session.get() == null) return;
    long now = android.os.SystemClock.elapsedRealtime();
    synchronized (FOREGROUND_RESTORE) {
      Long previous = FOREGROUND_RESTORE.get(session.owner());
      if (previous != null && now - previous >= 0 && now - previous < 5 * 60_000L) return;
      FOREGROUND_RESTORE.put(session.owner(), now);
    }
    final BillingManager[] holder = new BillingManager[1];
    holder[0] =
        new BillingManager(
            app,
            new Listener() {
              public void onCatalog(Map<String, ProductDetails> ignored) {}

              public void onStatus(String ignored) {}

              public void onVerified() {}
            });
    holder[0].connect();
    new Handler(Looper.getMainLooper()).postDelayed(() -> holder[0].close(), 90_000);
  }
}

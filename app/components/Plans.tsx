"use client";
import { Check } from "lucide-react";
import { useEffect, useState } from "react";
import { PLANS, PRICING_FINALISED } from "@/lib/billing-policy";

type PublicPlan = { id: string; name: string; limit: number; closerLookLimit: number; monthlyPence: number | null; annualPence: number | null; annualBonusPhotos?: number; bonusPhotosPerYear?: number; annualBonusCloserLooks?: number };
type Plan = { id: string; name: string; pricePence: number; period: "month" | "year" | "free"; monthlyPhotos: number; closerLooks: number; annualBonusPhotos: number; annualBonusCloserLooks: number };
function displayPlan(plan: PublicPlan): Plan {
  return { id: plan.id, name: plan.name, pricePence: plan.annualPence ?? plan.monthlyPence ?? 0, period: plan.annualPence !== null ? "year" : plan.monthlyPence === 0 ? "free" : "month", monthlyPhotos: plan.limit, closerLooks: plan.closerLookLimit, annualBonusPhotos: plan.bonusPhotosPerYear ?? plan.annualBonusPhotos ?? 0, annualBonusCloserLooks: plan.annualBonusCloserLooks ?? 0 };
}
const canonicalPlans = Object.entries(PLANS).map(([id, plan]) => displayPlan({ id, ...plan }));
export default function Plans({ compact = false }: { compact?: boolean }) {
  const [plans, setPlans] = useState(canonicalPlans), [billingReady, setBillingReady] = useState(false), [pricingFinalised, setPricingFinalised] = useState(PRICING_FINALISED), [loaded, setLoaded] = useState(false);
  useEffect(() => {
    const controller = new AbortController();
    fetch("/api/plans", { cache: "no-store", signal: controller.signal }).then(async response => {
      if (!response.ok) return;
      const result = await response.json() as { plans?: PublicPlan[]; billingReady?: boolean; pricingFinalised?: boolean };
      if (Array.isArray(result.plans) && result.plans.length && result.plans.every(plan => typeof plan.id === "string" && typeof plan.name === "string" && Number.isFinite(plan.limit) && plan.limit > 0 && Number.isFinite(plan.closerLookLimit) && plan.closerLookLimit >= 0 && [plan.monthlyPence, plan.annualPence].some(price => typeof price === "number" && Number.isFinite(price) && price >= 0))) setPlans(result.plans.map(displayPlan));
      setBillingReady(result.billingReady === true);
      setPricingFinalised(result.pricingFinalised === true);
    }).catch(() => {}).finally(() => { if (!controller.signal.aborted) setLoaded(true); });
    return () => controller.abort();
  }, []);
  return <div>{!compact && <p className="company-plan-loading" role="status">{!loaded ? "Checking subscription availability…" : billingReady ? "Subscribe and manage your plan in the Android app through Google Play." : pricingFinalised ? "These are our plans. Paid checkout will open in the Android app when Google Play setup is complete." : "Paid plans are being prepared; checkout is unavailable."}</p>}
    <div className={"company-plans " + (!pricingFinalised ? "company-plans-pending" : "")}>{plans.filter(plan => pricingFinalised || plan.pricePence === 0).map(plan => <article className="company-plan" key={plan.id}>
      <h3>{plan.name}</h3><p className="company-plan-caption">{plan.id === "free" ? "A little more wonder, every month." : plan.id === "annual" ? "A year of discoveries, with extra room." : "More room for what catches your eye."}</p>
      <p className="company-plan-price">{plan.pricePence === 0 ? "Free" : new Intl.NumberFormat("en-GB", { style: "currency", currency: "GBP" }).format(plan.pricePence / 100)}{plan.period !== "free" && <small>/{plan.period}</small>}</p>
      <p className="company-plan-limit">{plan.monthlyPhotos.toLocaleString("en-GB")} new cloud photos / month</p>
      <ul><li><Check size={15}/>Smart AI photo suggestions</li><li><Check size={15}/>Up to {plan.closerLooks.toLocaleString("en-GB")} automatic closer reviews / month</li><li><Check size={15}/>Journal, map & optional sharing</li><li><Check size={15}/>Free photo & data ZIP downloads</li>{plan.annualBonusPhotos > 0 && <li><Check size={15}/>{plan.annualBonusPhotos.toLocaleString("en-GB")} extra photos / paid membership year</li>}{plan.annualBonusCloserLooks > 0 && <li><Check size={15}/>{plan.annualBonusCloserLooks.toLocaleString("en-GB")} extra closer reviews for bonus photos / paid membership year</li>}</ul>
      {plan.pricePence === 0 ? <a className="company-plan-status" href="/journal">Open your free journal</a> : <p className="company-plan-status">{billingReady ? "Available through Google Play" : "Google Play checkout coming soon"}</p>}
    </article>)}</div>
    {!compact && <p className="company-pricing-footnote">Smart AI suggests an identity for each online photo. Uncertain photos can receive an automatic closer review while your review allowance is available. A review attempt uses an allowance even if processing cannot finish. Suggestions may remain tentative; check the evidence and never rely on them for edibility or medicine.</p>}
  </div>;
}

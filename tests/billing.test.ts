import {test} from 'node:test';
import assert from 'node:assert/strict';
import {checkedPurchase,paidOrderPeriod,retainedAnnualPeriod,utcPeriod,subscriptionGrants,planForProduct,type GooglePurchase} from '../lib/billing-policy';
const fixture:GooglePurchase={subscriptionState:'SUBSCRIPTION_STATE_ACTIVE',startTime:'2026-10-01T00:00:00Z',acknowledgementState:'ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED',externalAccountIdentifiers:{obfuscatedExternalAccountId:'owner-hash'},lineItems:[{productId:'trail_premium_yearly',expiryTime:'2027-11-01T00:00:00Z',latestSuccessfulOrderId:'GPA-paid',offerDetails:{basePlanId:'yearly',offerId:'trial'},offerPhase:{freeTrial:{}}}]};
const now=Date.parse('2026-10-05T00:00:00Z');
test('purchase account, product and billing period cannot be supplied by another client',()=>{
  assert.throws(()=>checkedPurchase(fixture,'attacker-hash','trail_premium_yearly',now));
  assert.throws(()=>checkedPurchase(fixture,'owner-hash','trail_plus_monthly',now));
  assert.throws(()=>checkedPurchase({...fixture,lineItems:[{...fixture.lineItems![0],offerDetails:{basePlanId:'monthly'}}]},'owner-hash','trail_premium_yearly',now));
  assert.equal(planForProduct('client-premium-override'),null);
});
test('pending, paused, held, refunded/expired and replaced purchases never grant paid access',()=>{
  for(const state of ['SUBSCRIPTION_STATE_PENDING','SUBSCRIPTION_STATE_PAUSED','SUBSCRIPTION_STATE_ON_HOLD','SUBSCRIPTION_STATE_EXPIRED','SUBSCRIPTION_STATE_REPLACED','SUBSCRIPTION_STATE_PENDING_PURCHASE_CANCELED'])assert.equal(checkedPurchase({...fixture,subscriptionState:state},'owner-hash','trail_premium_yearly',now).granted,false);
  assert.equal(subscriptionGrants('SUBSCRIPTION_STATE_ACTIVE','2026-10-04T00:00:00Z',now),false);
  assert.equal(subscriptionGrants('SUBSCRIPTION_STATE_CANCELED','2026-11-01T00:00:00Z',now),true);
});
test('a trial is its current Play phase, independently from recurring offer name',()=>{
  assert.equal(checkedPurchase(fixture,'owner-hash','trail_premium_yearly',now).isTrial,true);
  const paid={...fixture,lineItems:[{...fixture.lineItems![0],offerPhase:undefined}]};
  assert.equal(checkedPurchase(paid,'owner-hash','trail_premium_yearly',now).isTrial,false);
  const prorated={...fixture,lineItems:[{...fixture.lineItems![0],offerPhase:{prorationPeriod:{originalOfferPhaseType:'FREE_TRIAL'}} as any}]};
  assert.equal(checkedPurchase(prorated,'owner-hash','trail_premium_yearly',now).isTrial,true);
});
test('paid annual pools follow the actual paid order after trial and upgrades, including leap dates',()=>{
  const order={purchaseToken:'bound-token',state:'PROCESSED',lineItems:[{productId:'trail_premium_yearly',subscriptionDetails:{basePlanId:'yearly',servicePeriodStartTime:'2028-02-29T09:00:00Z',servicePeriodEndTime:'2029-02-28T09:00:00Z',offerPhaseDetails:{baseDetails:{}}}}]};
  const term=paidOrderPeriod(order,'trail_premium_yearly','bound-token');
  assert.equal(term.start,'2028-02-29T09:00:00.000Z');assert.equal(term.end,'2029-02-28T09:00:00.000Z');
  assert.throws(()=>paidOrderPeriod(order,'trail_plus_monthly','bound-token'));
  assert.throws(()=>paidOrderPeriod(order,'trail_premium_yearly','other-token'));
  assert.throws(()=>paidOrderPeriod({...order,purchaseToken:undefined},'trail_premium_yearly','bound-token'));
  assert.throws(()=>paidOrderPeriod({...order,state:'PENDING'},'trail_premium_yearly','bound-token'));
  assert.throws(()=>paidOrderPeriod({...order,lineItems:[{...order.lineItems[0],subscriptionDetails:{...order.lineItems[0].subscriptionDetails,offerPhaseDetails:{freeTrialDetails:{}}}}]},'trail_premium_yearly','bound-token'));
  for(const phase of [undefined,{}, {prorationPeriodDetails:{originalOfferPhase:'BASE'}},{baseDetails:{},prorationPeriodDetails:{originalOfferPhase:'BASE'}}])assert.throws(()=>paidOrderPeriod({...order,lineItems:[{...order.lineItems[0],subscriptionDetails:{...order.lineItems[0].subscriptionDetails,offerPhaseDetails:phase}}]},'trail_premium_yearly','bound-token'));
  // Google's license-test service period is accelerated; require the paid phase, not an invented 365-day minimum.
  assert.equal(paidOrderPeriod({...order,lineItems:[{...order.lineItems[0],subscriptionDetails:{...order.lineItems[0].subscriptionDetails,servicePeriodEndTime:'2028-02-29T09:05:00Z'}}]},'trail_premium_yearly','bound-token').end,'2028-02-29T09:05:00.000Z');
});
test('temporary order lookup failures only retain proof for the same unexpired paid order',()=>{
  const previous={latest_order_id:'GPA.renewal-1',paid_period_start:'2026-10-01T00:00:00.000Z',paid_period_end:'2027-10-01T00:00:00.000Z'};
  assert.deepEqual(retainedAnnualPeriod(previous,'GPA.renewal-1',now),{start:previous.paid_period_start,end:previous.paid_period_end});
  assert.equal(retainedAnnualPeriod(previous,'GPA.renewal-2',now),null);
  assert.equal(retainedAnnualPeriod(previous,null,now),null);
  assert.equal(retainedAnnualPeriod(previous,'GPA.renewal-1',Date.parse('2027-10-01T00:00:00Z')),null);
});
test('quota reset is server UTC, independent of capture date or local timezone',()=>{
  assert.deepEqual(utcPeriod(new Date('2026-10-31T23:59:59Z')),{key:'2026-10',start:'2026-10-01T00:00:00.000Z',end:'2026-11-01T00:00:00.000Z'});
  assert.equal(utcPeriod(new Date('2026-11-01T01:00:00+01:00')).key,'2026-11');
});

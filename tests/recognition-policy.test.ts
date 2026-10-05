import test from 'node:test';
import assert from 'node:assert/strict';
import {closerLookReasons,recognitionReviewJsonSchema,recognitionReviewSchema,requiresCloserLook,tentativeResult,type RecognitionCandidate,type RecognitionReview} from '../lib/recognition-policy';

const robin:RecognitionCandidate={name:'European robin',scientificName:'Erithacus rubecula',category:'birds',confidence:'high',summary:'A robin has been spotted.',interestingFact:'Robins defend feeding territories.',identifyingFeatures:['Orange-red breast','Brown back and rounded body'],lookCloser:'Observe the breast colour from a distance.',seasonalContext:'Robins can be seen throughout the year.',alternatives:[]};
const clear:RecognitionReview={subjectCount:'single',visibility:'clear',targetRank:'species',lookalikeRisk:false,diagnosticFeaturesVisible:true,complexity:'simple'};

test('a clear distinctive single bird qualifies for quick review; ordinary landmark does not need a binomial',()=>{
  assert.deepEqual(closerLookReasons(robin,clear),[]);assert.equal(requiresCloserLook(robin,clear),false);
  assert.equal(requiresCloserLook({...robin,name:'Stone bridge',scientificName:'',category:'landmarks'},{...clear,targetRank:'non_taxon'}),false);
  assert.equal(requiresCloserLook({...robin,category:'landmarks'},clear),true);
});

test('high confidence cannot bypass lookalike, detail or non-species evidence',()=>{
  const orchid={...robin,name:'Early-purple orchid',scientificName:'Orchis mascula',category:'flowers' as const};
  assert.ok(closerLookReasons(orchid,{...clear,lookalikeRisk:true}).includes('lookalike'));
  assert.equal(requiresCloserLook(orchid,{...clear,complexity:'detailed'}),true);
  for(const targetRank of ['genus','family','unknown','non_taxon'] as const)assert.equal(requiresCloserLook(orchid,{...clear,targetRank}),true);
  assert.equal(requiresCloserLook({...orchid,scientificName:''},clear),true);
  assert.equal(requiresCloserLook({...orchid,scientificName:'Orchis'},clear),true);
});

test('clear but potentially confidently wrong insects, fungi, grasses and sedges require closer review',()=>{
  for(const candidate of [
    {...robin,name:'Honey bee',scientificName:'Apis mellifera',category:'bugs' as const},
    {...robin,name:'Field mushroom',scientificName:'Agaricus campestris',category:'fungi' as const},
    {...robin,name:'Meadow grass',scientificName:'Poa pratensis',category:'plants' as const},
    {...robin,name:'Pendulous sedge',scientificName:'Carex pendula',category:'plants' as const},
    {...robin,name:'Soft rush',scientificName:'Juncus effusus',category:'plants' as const},
    {...robin,name:'Common reed',scientificName:'Phragmites australis',category:'plants' as const},
  ])assert.equal(requiresCloserLook(candidate,clear),true);
  // An incidental English substring must not categorise a bird as grass.
  assert.equal(requiresCloserLook({...robin,name:'Grasshopper warbler',scientificName:'Locustella naevia'},clear),false);
});

test('distant, blurred, hidden, multiple and absent subjects cannot be accepted on confidence alone',()=>{
  for(const visibility of ['blurred','distant','occluded'] as const)assert.ok(closerLookReasons(robin,{...clear,visibility}).includes('poor_visibility'));
  assert.ok(closerLookReasons(robin,{...clear,subjectCount:'multiple'}).includes('multiple_subjects'));
  assert.ok(closerLookReasons(robin,{...clear,subjectCount:'none'}).includes('no_subject'));
  assert.equal(requiresCloserLook({...robin,confidence:'medium'},clear),true);
  assert.equal(requiresCloserLook({...robin,alternatives:['Dunnock']},clear),true);
  assert.equal(requiresCloserLook(robin,{...clear,diagnosticFeaturesVisible:false}),true);
});

test('one repeated descriptive cue cannot masquerade as two diagnostic features',()=>{
  for(const identifyingFeatures of [[],['Red breast'],['Red breast',' red BREAST. ']])assert.ok(closerLookReasons({...robin,identifyingFeatures},clear).includes('few_visible_features'));
});

test('strict model review cannot disable gates with missing, coerced or injected values',()=>{
  assert.equal(recognitionReviewSchema.safeParse({...clear,lookalikeRisk:'false'}).success,false);
  assert.equal(recognitionReviewSchema.safeParse({...clear,diagnosticFeaturesVisible:undefined}).success,false);
  assert.equal(recognitionReviewSchema.safeParse({...clear,forceQuick:true}).success,false);
  assert.equal(recognitionReviewSchema.safeParse({...clear,visibility:'perfect'}).success,false);
  assert.deepEqual(closerLookReasons(robin,{...clear,forceQuick:true} as RecognitionReview),['invalid_review']);
  assert.equal(recognitionReviewJsonSchema.additionalProperties,false);
  assert.deepEqual(recognitionReviewJsonSchema.required,Object.keys(recognitionReviewJsonSchema.properties));
});

test('unavailable closer review removes the unsupported species and its specific story rather than silently declaring success',()=>{
  const original={...robin,scientificName:'Amanita phalloides',name:'Death cap',category:'fungi' as const,summary:'This is a confirmed death cap.',interestingFact:'A species-specific claim.',seasonalContext:'This species emerges in October.',alternatives:['Destroying angel'],identifyingFeatures:['Pale green cap','White gills','Identified as Amanita phalloides','Poisonous mushroom'],provider:'raw model',sources:[{url:'https://example.test/death-cap'}],prompt:'PRIVATE PROVIDER PROMPT',review:clear};
  const before=JSON.stringify(original),result=tentativeResult(original,['fungus','uncertain','fungus','PRIVATE PROVIDER PROMPT']);
  assert.equal(result.name,'Tentative discovery');assert.equal(result.scientificName,'');assert.equal(result.confidence,'low');
  assert.equal(result.interestingFact,'');assert.equal(result.seasonalContext,'');assert.deepEqual(result.alternatives,[]);
  assert.deepEqual(result.identifyingFeatures,['Pale green cap','White gills']);
  assert.deepEqual(result.recognition,{mode:'tentative',reviewReasons:['fungus','uncertain']});
  assert.ok(result.summary.includes('saved'));assert.ok(result.lookCloser.includes('without touching'));
  for(const excluded of ['Amanita','death cap','species-specific','PRIVATE PROVIDER PROMPT','https://example.test','raw model'])assert.equal(JSON.stringify(result).toLowerCase().includes(excluded.toLowerCase()),false);
  assert.equal(JSON.stringify(original),before);
});

test('tentative fallback never carries name-labelled feature assertions into a generic discovery',()=>{
  const result=tentativeResult({...robin,identifyingFeatures:['European robin identified','Erithacus rubecula breeding plumage','Orange-red breast']},['uncertain']);
  assert.deepEqual(result.identifyingFeatures,['Orange-red breast']);
  assert.equal(result.recognition.mode,'tentative');
});

test('tentative fallback records bounded operational reasons without accepting provider error prose',()=>{
  const result=tentativeResult(robin,['closer_limit','closer_unavailable','specialist_disagreement','closer_limit','Upstream SECRET request body']);
  assert.deepEqual(result.recognition.reviewReasons,['closer_limit','closer_unavailable','specialist_disagreement']);
  assert.equal(JSON.stringify(result).includes('SECRET'),false);
  assert.equal(result.confidence,'low');assert.equal(result.scientificName,'');
});

import {z} from 'zod';
import type {Category,Identification} from './types';

/** Model review is evidence for routing, never proof of photo identity. */
export const recognitionReviewSchema=z.object({
  subjectCount:z.enum(['single','multiple','none']),
  visibility:z.enum(['clear','blurred','distant','occluded']),
  targetRank:z.enum(['species','genus','family','unknown','non_taxon']),
  lookalikeRisk:z.boolean(),
  diagnosticFeaturesVisible:z.boolean(),
  complexity:z.enum(['simple','detailed']),
}).strict();
export type RecognitionReview=z.infer<typeof recognitionReviewSchema>;
export const recognitionReviewJsonSchema={
  type:'object',additionalProperties:false,
  properties:{
    subjectCount:{type:'string',enum:['single','multiple','none']},
    visibility:{type:'string',enum:['clear','blurred','distant','occluded']},
    targetRank:{type:'string',enum:['species','genus','family','unknown','non_taxon']},
    lookalikeRisk:{type:'boolean'},diagnosticFeaturesVisible:{type:'boolean'},
    complexity:{type:'string',enum:['simple','detailed']},
  },
  required:['subjectCount','visibility','targetRank','lookalikeRisk','diagnosticFeaturesVisible','complexity'],
} as const;

export type RecognitionCandidate=Pick<Identification,'name'|'scientificName'|'category'|'confidence'|'summary'|'interestingFact'|'identifyingFeatures'|'lookCloser'|'seasonalContext'|'alternatives'>;
export type RecognitionMetadata={mode:'quick'|'closer'|'tentative';reviewReasons:string[];model?:string};
const natureCategories=new Set<Category>(['plants','flowers','bugs','birds','animals','fungi']);
const reasonLabels={
  invalid_review:'The image review was incomplete.',
  uncertain:'The initial identification is uncertain.',
  alternatives:'Similar subjects may share these features.',
  multiple_subjects:'More than one subject is visible.',
  no_subject:'No clear subject was found.',
  poor_visibility:'A sharper or closer view is needed.',
  coarse_taxon:'Species-level details are not established.',
  missing_diagnostics:'Diagnostic features are not clearly visible.',
  few_visible_features:'Too few distinct visible features support the suggestion.',
  lookalike:'Similar species need a closer comparison.',
  fine_details:'Fine details need closer examination.',
  insect:'Small animal and insect details need closer examination.',
  fungus:'Fungi can share deceptively similar appearances.',
  grass_or_sedge:'Grass, sedge and rush details need closer examination.',
  closer_limit:'The closer-review allowance is currently unavailable.',
  closer_unavailable:'Closer review could not be completed.',
  specialist_disagreement:'The independent identification suggestions disagree.',
} as const;
export type RecognitionReason=keyof typeof reasonLabels;
export const recognitionReasonLabels=reasonLabels;
const supportedBinomial=/^[A-Z][a-z-]+\s+[a-z][a-z-]+$/;
const grassOrSedge=/\b(?:grass(?:es)?|sedge(?:s)?|rush(?:es)?|reed(?:s)?|bamboo|poa|carex|festuca|juncus|lolium|agrostis|dactylis|phleum|alopecurus|cyperus|phragmites|elymus|holcus|avena|bromus|molinia|deschampsia|anthoxanthum|nardus|briza|phalaris)\b/i;

function distinctFeatures(candidate:RecognitionCandidate) {
  const values=Array.isArray(candidate.identifyingFeatures)?candidate.identifyingFeatures:[];
  return new Set(values.filter(value=>typeof value==='string'&&value.trim()).map(value=>value.toLowerCase().replace(/[^\p{L}\p{N}]+/gu,' ').trim()));
}

/** Conservative gates apply even when the economy model reports confidence=high. */
export function closerLookReasons(candidate:RecognitionCandidate,review:RecognitionReview):RecognitionReason[] {
  const parsed=recognitionReviewSchema.safeParse(review);
  if(!parsed.success)return ['invalid_review'];
  const value=parsed.data,reasons:RecognitionReason[]=[];
  if(candidate.confidence!=='high')reasons.push('uncertain');
  if(candidate.alternatives.some(alternative=>alternative.trim()))reasons.push('alternatives');
  if(value.subjectCount==='multiple')reasons.push('multiple_subjects');
  if(value.subjectCount==='none')reasons.push('no_subject');
  if(value.visibility!=='clear')reasons.push('poor_visibility');
  if(natureCategories.has(candidate.category)&&(value.targetRank!=='species'||!supportedBinomial.test(candidate.scientificName.trim())))reasons.push('coarse_taxon');
  if(!natureCategories.has(candidate.category)&&(value.targetRank!=='non_taxon'||candidate.scientificName.trim()))reasons.push('coarse_taxon');
  if(!value.diagnosticFeaturesVisible)reasons.push('missing_diagnostics');
  if(distinctFeatures(candidate).size<2)reasons.push('few_visible_features');
  if(value.lookalikeRisk)reasons.push('lookalike');
  if(value.complexity==='detailed')reasons.push('fine_details');
  if(candidate.category==='bugs')reasons.push('insect');
  if(candidate.category==='fungi')reasons.push('fungus');
  if(['plants','flowers'].includes(candidate.category)&&grassOrSedge.test(candidate.name+' '+candidate.scientificName))reasons.push('grass_or_sedge');
  return reasons;
}
export function requiresCloserLook(candidate:RecognitionCandidate,review:RecognitionReview) {
  return closerLookReasons(candidate,review).length>0;
}

function visibleFeatures(candidate:RecognitionCandidate) {
  const names=[candidate.name,candidate.scientificName].map(value=>value.trim().toLowerCase()).filter(Boolean);
  const unsafe=/\b(?:edible|poison(?:ous)?|medicin(?:al|e)|pain\s*relief|safe\s+to\s+eat|identified\s+as|species\s+is)\b/i;
  return candidate.identifyingFeatures.filter(feature=>{
    const normal=feature.trim().toLowerCase();
    return normal&&!unsafe.test(normal)&&!names.some(name=>normal.includes(name));
  }).map(feature=>feature.trim().slice(0,200)).slice(0,2);
}

/** Do not attach taxon-specific facts or sources to an unresolved suggestion.
 * Positive projection also excludes raw review, prompts and specialist metadata.
 */
export function tentativeResult(candidate:RecognitionCandidate,reasons:readonly string[]=[]) : RecognitionCandidate & {recognition:RecognitionMetadata} {
  const reviewReasons=[...new Set(reasons)].filter(reason=>Object.hasOwn(reasonLabels,reason)).slice(0,Object.keys(reasonLabels).length);
  return {
    name:'Tentative discovery',scientificName:'',category:candidate.category,confidence:'low',
    summary:'A closer view is needed to identify this discovery. Your photo is safely saved in your journal.',
    interestingFact:'',identifyingFeatures:visibleFeatures(candidate),
    lookCloser:'Closer review could not be completed. Try a sharper view of one subject without touching or disturbing it.',
    seasonalContext:'',alternatives:[],recognition:{mode:'tentative',reviewReasons},
  };
}

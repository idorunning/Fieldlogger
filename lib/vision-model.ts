import jpeg from 'jpeg-js';

// Official model IDs and Standard USD rates, checked 5 October 2026.
// A bounded Luna first pass handles focused requests; harder cases get one Sol review.
export const DEFAULT_VISION_MODEL = 'gpt-6-luna';
export const STRONG_VISION_MODEL = 'gpt-6.1-sol';
export const DEFAULT_IDENTIFICATION_MODEL = DEFAULT_VISION_MODEL;
export type VisionModel = typeof DEFAULT_VISION_MODEL | typeof STRONG_VISION_MODEL;
export const VISION_OUTPUT_LIMITS = {identify:1200,'identify-simple':1200,'identify-strong':4000,publish:160,test:512} as const;
export const VISION_INPUT_BUDGETS = {identify:6000,'identify-simple':6000,'identify-strong':6000,publish:4000,test:1000} as const;
export type VisionPurpose=keyof typeof VISION_OUTPUT_LIMITS;
export function visionRequestOptions(configured: string | undefined, purpose:VisionPurpose) {
  const strong=purpose==='identify-strong'||purpose==='test'||purpose==='identify'&&configured===STRONG_VISION_MODEL;
  const model:VisionModel=strong?STRONG_VISION_MODEL:DEFAULT_VISION_MODEL;
  const output=purpose==='identify'&&strong?VISION_OUTPUT_LIMITS['identify-strong']:VISION_OUTPUT_LIMITS[purpose];
  return {model,service_tier:'default' as const,reasoning:{effort:model===DEFAULT_VISION_MODEL?'none' as const:'low' as const},max_output_tokens:output};
}
export const VISION_MAX_DIMENSION=1024;
export const IDENTIFICATION_MAX_DIMENSION=1800;
export const VISION_MAX_INPUT_BYTES=4*1024*1024;
// Actual pixel decode rejects decompression bombs. This metadata-free smaller
// copy is for the AI only; the journal upload remains unchanged.
export function prepareVisionJpeg(input:Uint8Array,maxDimension:1024|1800=VISION_MAX_DIMENSION):Uint8Array {
  // The caller selects one of two fixed sizes, never an arbitrary client value.
  if(maxDimension!==VISION_MAX_DIMENSION&&maxDimension!==IDENTIFICATION_MAX_DIMENSION)
    throw new Error('This photo could not be prepared for identification. Your photo is saved.');
  if(input.length<4||input.length>VISION_MAX_INPUT_BYTES||input[0]!==255||input[1]!==216)
    throw new Error('This photo could not be prepared for identification. Your photo is saved.');
  let image:{width:number;height:number;data:Uint8Array};
  try{image=jpeg.decode(input,{useTArray:true,tolerantDecoding:false,maxResolutionInMP:4,maxMemoryUsageInMB:64});}
  catch{throw new Error('This photo could not be opened for identification. Your photo is saved.');}
  let resized:Uint8Array|undefined;
  try {
    if(image.width<1||image.height<1)throw new Error('Invalid image');
    const scale=Math.min(1,maxDimension/Math.max(image.width,image.height));
    const width=Math.max(1,Math.round(image.width*scale)),height=Math.max(1,Math.round(image.height*scale));
    resized=new Uint8Array(width*height*4);
    // Bilinear sampling preserves narrow leaves/feathers better than dropping pixels.
    for(let y=0;y<height;y++) {
      const sourceY=Math.max(0,Math.min(image.height-1,(y+.5)/scale-.5)),y0=Math.floor(sourceY),y1=Math.min(image.height-1,y0+1),fy=sourceY-y0;
      for(let x=0;x<width;x++) {
        const sourceX=Math.max(0,Math.min(image.width-1,(x+.5)/scale-.5)),x0=Math.floor(sourceX),x1=Math.min(image.width-1,x0+1),fx=sourceX-x0,offset=(y*width+x)*4;
        for(let c=0;c<3;c++) {
          const top=image.data[(y0*image.width+x0)*4+c]*(1-fx)+image.data[(y0*image.width+x1)*4+c]*fx;
          const bottom=image.data[(y1*image.width+x0)*4+c]*(1-fx)+image.data[(y1*image.width+x1)*4+c]*fx;
          resized[offset+c]=Math.round(top*(1-fy)+bottom*fy);
        }
        resized[offset+3]=255;
      }
    }
    return new Uint8Array(jpeg.encode({width,height,data:resized},88).data);
  } finally {image.data.fill(0);resized?.fill(0);}
}
export function visionDataUrl(input:Uint8Array) {
  let binary='';for(let offset=0;offset<input.length;offset+=8192)binary+=String.fromCharCode(...input.subarray(offset,offset+8192));
  return 'data:image/jpeg;base64,'+btoa(binary);
}

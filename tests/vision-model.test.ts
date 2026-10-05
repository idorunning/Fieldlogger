import test from 'node:test';
import assert from 'node:assert/strict';
import jpeg from 'jpeg-js';
import {DEFAULT_VISION_MODEL,STRONG_VISION_MODEL,IDENTIFICATION_MAX_DIMENSION,prepareVisionJpeg,visionRequestOptions} from '../lib/vision-model';

test('bounded simple and stronger stages keep fixed models, reasoning and output limits',()=>{
  assert.deepEqual(visionRequestOptions(undefined,'identify'),{model:DEFAULT_VISION_MODEL,service_tier:'default',reasoning:{effort:'none'},max_output_tokens:1200});
  assert.equal(visionRequestOptions('gpt-6.1-luna','publish').model,DEFAULT_VISION_MODEL);
  assert.equal(visionRequestOptions('gpt-6-astra','identify').model,DEFAULT_VISION_MODEL);
  assert.deepEqual(visionRequestOptions(STRONG_VISION_MODEL,'identify'),{model:STRONG_VISION_MODEL,service_tier:'default',reasoning:{effort:'low'},max_output_tokens:4000});
  assert.equal(visionRequestOptions(STRONG_VISION_MODEL,'identify-simple').model,DEFAULT_VISION_MODEL);
  assert.equal(visionRequestOptions(DEFAULT_VISION_MODEL,'identify-strong').model,STRONG_VISION_MODEL);
  assert.equal(visionRequestOptions(DEFAULT_VISION_MODEL,'test').model,STRONG_VISION_MODEL);
  assert.equal(visionRequestOptions(STRONG_VISION_MODEL,'publish').model,DEFAULT_VISION_MODEL);
  assert.equal(visionRequestOptions(undefined,'publish').max_output_tokens,160);
  assert.equal(visionRequestOptions(undefined,'publish').reasoning.effort,'none');
});

test('AI preprocessing bounds actual dimensions and leaves the stored JPEG untouched',()=>{
  const data=new Uint8Array(1800*900*4);for(let i=0;i<data.length;i+=4){data[i]=60;data[i+1]=140;data[i+2]=70;data[i+3]=255;}
  const original=new Uint8Array(jpeg.encode({width:1800,height:900,data},80).data),copy=original.slice();
  const safe=prepareVisionJpeg(original),image=jpeg.decode(safe,{useTArray:true});
  assert.equal(image.width,1024);assert.equal(image.height,512);assert.deepEqual(original,copy);
  assert.ok(Math.abs(image.data[100]-60)<10);assert.ok(Math.abs(image.data[101]-140)<10);
  const identification=jpeg.decode(prepareVisionJpeg(original,IDENTIFICATION_MAX_DIMENSION),{useTArray:true});
  assert.equal(identification.width,1800);assert.equal(identification.height,900);assert.deepEqual(original,copy);
});

test('invalid JPEG and over-large payloads never reach a model',()=>{
  assert.throws(()=>prepareVisionJpeg(new Uint8Array([255,216,255,1,0,1])),/could not be opened/);
  assert.throws(()=>prepareVisionJpeg(new Uint8Array(4*1024*1024+1)),/could not be prepared/);
  assert.throws(()=>prepareVisionJpeg(new Uint8Array([255,216]),4096 as 1800),/could not be prepared/);
});

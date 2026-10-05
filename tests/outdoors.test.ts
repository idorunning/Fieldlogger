import test from 'node:test';
import assert from 'node:assert/strict';
import {weatherKind} from '../lib/weather-policy';
test('weather scenes distinguish rain, snow, storms and wind without turning every cloudy day into rain',()=>{
  assert.equal(weatherKind(0,10),'sunny');assert.equal(weatherKind(3,10),'cloudy');assert.equal(weatherKind(61,10),'rain');assert.equal(weatherKind(71,10),'snow');assert.equal(weatherKind(95,40),'storm');assert.equal(weatherKind(2,35),'wind');assert.equal(weatherKind(45,5),'fog');
});

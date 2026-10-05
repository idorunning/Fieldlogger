import { test } from 'node:test';
import assert from 'node:assert/strict';
import { photonPlace, placeCell } from '../lib/place';
test('place names combine nearby feature and locality without repeating it', () => {
  assert.equal(photonPlace({features:[{properties:{name:'Carfax',city:'Oxford',country:'United Kingdom'}}]}), 'Carfax, Oxford');
  assert.equal(photonPlace({features:[{properties:{name:'Oxford',city:'Oxford'}}]}), 'Oxford');
  assert.equal(photonPlace({features:[{properties:{street:'Woodland Lane',county:'Oxfordshire'}}]}), 'Woodland Lane, Oxfordshire');
});
test('missing or malformed geocoder results do not invent a place', () => {
  for (const data of [null, {}, {features:[]}, {features:[{properties:{name:15}}]}]) assert.equal(photonPlace(data),'');
  assert.equal(photonPlace({features:[{properties:{name:'x'.repeat(300)}}]}).length,160);
  assert.equal(placeCell(0,0),'0.0000,0.0000');
});

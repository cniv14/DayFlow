// Original DayFlow adapter. Yjs wire encoding is provided by the MIT libraries.
import * as Y from 'yjs';
import * as sync from 'y-protocols/sync';
import * as enc from 'lib0/encoding';
import * as dec from 'lib0/decoding';

const alphabet='ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
function b64(bytes){let out='';for(let i=0;i<bytes.length;i+=3){const a=bytes[i],b=bytes[i+1],c=bytes[i+2],n=(a<<16)|((b||0)<<8)|(c||0);out+=alphabet[(n>>>18)&63]+alphabet[(n>>>12)&63]+(i+1<bytes.length?alphabet[(n>>>6)&63]:'=')+(i+2<bytes.length?alphabet[n&63]:'=');}return out;}
function un64(s){if(s.length>24*1024*1024)throw Error('Document exceeds safe size');const clean=s.replace(/=+$/,'');const out=new Uint8Array(Math.floor(clean.length*6/8));let bits=0,value=0,index=0;for(const c of clean){const n=alphabet.indexOf(c);if(n<0)throw Error('Invalid base64');value=(value<<6)|n;bits+=6;if(bits>=8){bits-=8;out[index++]=(value>>>bits)&255;}}return out;}
let doc,updates=[],received=0;
const tables=new Set(['task_type','schedule','dayflow_record']);
function table(name){if(!tables.has(name))throw Error('Unknown table');return doc.getMap(name);}
function packet(update){const e=enc.createEncoder();enc.writeVarUint(e,0);sync.writeUpdate(e,update);return b64(enc.toUint8Array(e));}
function plain(row){if(!(row instanceof Y.Map))throw Error('Invalid Yjs row');return row.toJSON();}
function setField(row,key,value){if(key==='__proto__'||key==='constructor'||key==='prototype')throw Error('Invalid field');if(key==='notes'){let text=row.get(key);if(!(text instanceof Y.Text)){text=new Y.Text();row.set(key,text);}const old=text.toString(),next=value==null?'':String(value);if(old===next)return;let left=0;while(left<old.length&&left<next.length&&old[left]===next[left])left++;let right=0;while(right<old.length-left&&right<next.length-left&&old[old.length-1-right]===next[next.length-1-right])right++;const remove=old.length-left-right;if(remove)text.delete(left,remove);const insert=next.slice(left,next.length-right);if(insert)text.insert(left,insert);return;}if(JSON.stringify(row.get(key))!==JSON.stringify(value))row.set(key,value);}

globalThis.DayFlowBridge={
  init(cache){if(doc)doc.destroy();doc=new Y.Doc({gc:true});if(cache)Y.applyUpdate(doc,un64(cache),'cache');updates=[];received=0;doc.on('update',(update,origin)=>{if(origin==='local')updates.push(packet(update));});return 'ready';},
  hello(){const e=enc.createEncoder();enc.writeVarUint(e,0);sync.writeSyncStep1(e,doc);return b64(enc.toUint8Array(e));},
  receive(base64){const d=dec.createDecoder(un64(base64)),e=enc.createEncoder();const type=dec.readVarUint(d);if(type===0){enc.writeVarUint(e,0);const subtype=sync.readSyncMessage(d,e,doc,'network');if(subtype===1)received++;}return JSON.stringify({reply:enc.length(e)>1?b64(enc.toUint8Array(e)):'',received});},
  page(name,offset,limit){const rows=table(name),ids=[...rows.keys()].sort();return JSON.stringify({total:ids.length,rows:ids.slice(offset,offset+Math.min(50,limit)).map(id=>({id,fields:plain(rows.get(id))}))});},
  get(name,id){const row=table(name).get(id);return row instanceof Y.Map?JSON.stringify(plain(row)):'null';},
  apply(json){const changes=JSON.parse(json);if(!Array.isArray(changes)||changes.length>50)throw Error('Batch too large');doc.transact(()=>{for(const change of changes){if(typeof change.id!=='string'||change.id.length>256)throw Error('Invalid row id');const rows=table(change.table);let row=rows.get(change.id);if(row!=null&&!(row instanceof Y.Map))throw Error('Row has incompatible structure');if(!row){row=new Y.Map();rows.set(change.id,row);}for(const [key,value] of Object.entries(change.fields))setField(row,key,value);}},'local');return String(changes.length);},
  drain(){const pending=updates;updates=[];return JSON.stringify(pending);},
  save(){return b64(Y.encodeStateAsUpdate(doc));},
  close(){if(doc)doc.destroy();doc=null;updates=[];return 'closed';}
};

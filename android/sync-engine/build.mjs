import { build } from 'esbuild';
import { mkdir, copyFile } from 'node:fs/promises';
await build({entryPoints:['bridge.js'],bundle:true,format:'iife',platform:'browser',target:'es2020',minify:true,legalComments:'eof',outfile:'../app/src/main/assets/taskflow-engine.js'});
await mkdir('../app/src/main/assets/licenses/yjs',{recursive:true});
for(const name of ['yjs','y-protocols','lib0'])await copyFile(`node_modules/${name}/LICENSE`,`../app/src/main/assets/licenses/yjs/${name}-MIT.txt`);

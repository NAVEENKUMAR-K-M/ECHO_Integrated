import http from 'node:http';
import { readFile } from 'node:fs/promises';
import { extname, join, normalize } from 'node:path';
const root = new URL('.', import.meta.url).pathname;
const types = {'.html':'text/html; charset=utf-8','.js':'text/javascript; charset=utf-8','.css':'text/css; charset=utf-8'};
http.createServer(async (req,res)=>{try{const pathname=decodeURIComponent(new URL(req.url,'http://localhost').pathname);const safe=normalize(pathname).replace(/^([.][.][/\\])+/, '');const file=join(root,safe==='/'?'index.html':safe);const data=await readFile(file);res.writeHead(200,{'content-type':types[extname(file)]||'application/octet-stream'});res.end(data)}catch{res.writeHead(404);res.end('Not found')}}).listen(4173,()=>console.log('ECHO POC running at http://localhost:4173'));

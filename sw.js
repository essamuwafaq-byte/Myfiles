const V='myfiles-v1';
const SHELL=['./','index.html','manifest.webmanifest','icon-192.png','icon-512.png','maskable-192.png','maskable-512.png','apple-180.png'];
self.addEventListener('install',e=>{e.waitUntil(caches.open(V).then(c=>c.addAll(SHELL)).then(()=>self.skipWaiting()));});
self.addEventListener('activate',e=>{e.waitUntil(caches.keys().then(ks=>Promise.all(ks.filter(k=>k!==V&&k!=='incoming').map(k=>caches.delete(k)))).then(()=>self.clients.claim()));});
self.addEventListener('fetch',e=>{
  const u=new URL(e.request.url);
  // files shared to the app from WhatsApp / Files
  if(e.request.method==='POST'&&u.origin===location.origin&&u.pathname.endsWith('/share')){
    e.respondWith((async()=>{let n=0;try{const fd=await e.request.formData();const c=await caches.open('incoming');
      for(const f of fd.getAll('files')){if(!f||typeof f==='string')continue;        await c.put(new Request('incoming/'+Date.now()+'-'+n),new Response(f,{headers:{'content-type':'text/html','x-name':encodeURIComponent(f.name)}}));n++;}}catch(err){}
      return Response.redirect('./?shared='+n,303);})());return;}
  if(e.request.method!=='GET'||u.origin!==location.origin)return;
  e.respondWith(caches.match(e.request,{ignoreSearch:true}).then(hit=>{
    const net=fetch(e.request).then(r=>{if(r&&r.ok){const cp=r.clone();caches.open(V).then(c=>c.put(e.request,cp));}return r;}).catch(()=>hit);
    return hit||net;}));
});

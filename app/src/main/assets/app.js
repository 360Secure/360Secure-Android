'use strict';
const $=id=>document.getElementById(id);
const API='https://camera.360secure.in';
const native=window.SecureNative||null;
let jwt='',refreshToken='',phone='',otpRequestId='',cameras=[],currentCamera=null;
let ws=null,pc=null,pendingCandidates=[],wantLive=false,reconnectTimer=null,reconnectAttempt=0,mediaMic=null,speakerEnabled=false;
let segments=[],segmentCache=new Map(),timelineBase=0,timelineBusy=false,timelineTimer=null,screen='auth',callRequested=null;
let pendingEventId='',eventPhotoUrl=null;
const DAY=86400000,PXDAY=1400,SPAN=5,BASE=2;
function notify(message){const t=$('toast');t.textContent=message;t.classList.remove('hidden');clearTimeout(notify.timer);notify.timer=setTimeout(()=>t.classList.add('hidden'),3400)}
function error(id,message){const x=$(id);x.textContent=message;x.classList.remove('hidden')}
function clearError(id){$(id).classList.add('hidden');$(id).textContent=''}
async function request(path,method='GET',body,retry=true){
  const res=await fetch(API+path,{method,headers:{'Content-Type':'application/json',...(jwt?{Authorization:'Bearer '+jwt}:{})},body:body?JSON.stringify(body):undefined});
  if(res.status===401 && retry && refreshToken && path!=='/api/auth/refresh'){
    try{await refreshSession();return request(path,method,body,false)}catch{logout();throw Error('Your session has expired. Please sign in again.')}
  }
  let data;try{data=await res.json()}catch{throw Error('Service unavailable ('+res.status+')')}
  if(!res.ok)throw Error(data.error||data.message||('Request failed ('+res.status+')'));
  return data;
}
async function refreshSession(){const r=await request('/api/auth/refresh','POST',{refreshToken},false);const access=r.accessToken||r.token||r.jwt;if(!access)throw Error('No access token');jwt=access;refreshToken=r.refreshToken||refreshToken;native?.storeSession(jwt,refreshToken,phone)}
function showAuthenticated(){ $('auth').classList.remove('active');$('signedApp').classList.remove('hidden');$('accountPhone').textContent=phone||'Verified account';if(pendingEventId){navigate('events');openEvent(pendingEventId)}else navigate('home') }
function showAuth(){disconnectLive();$('signedApp').classList.add('hidden');$('auth').classList.add('active');screen='auth'}
async function requestOtp(){
  clearError('authError');const n=$('phone').value.replace(/\D/g,'');
  if(!/^\d{10}$/.test(n)){error('authError','Please enter your 10-digit Indian mobile number.');return}
  const number='+91'+n;$('sendOtp').disabled=true;
  try{const r=await request('/api/auth/otp/request','POST',{phone:number});phone=number;otpRequestId=r.requestId||r.challengeId||'';
    $('otpDestination').textContent='Enter the code sent to '+phone;$('phoneForm').classList.add('hidden');$('otpForm').classList.remove('hidden');$('otp').focus();}
  catch(e){error('authError',e.message+' · SMS OTP backend integration is required.');}
  finally{$('sendOtp').disabled=false}
}
async function verifyOtp(){
  clearError('authError');const code=$('otp').value.trim();if(!/^\d{4,8}$/.test(code)){error('authError','Please enter the verification code.');return}
  try{const r=await request('/api/auth/otp/verify','POST',{phone,otp:code,...(otpRequestId?{requestId:otpRequestId}:{})});
    jwt=r.accessToken||r.token||r.jwt||'';refreshToken=r.refreshToken||'';
    if(!jwt)throw Error('Verification response did not include an access token.');
    native?.storeSession(jwt,refreshToken,phone);showAuthenticated();}
  catch(e){error('authError',e.message)}
}
function backToPhone(){clearError('authError');$('otpForm').classList.add('hidden');$('phoneForm').classList.remove('hidden')}
function logout(){jwt='';refreshToken='';phone='';cameras=[];currentCamera=null;native?.clearSession();showAuth()}
async function init(){
  $('recordDate').value=dateKey(Date.now());
  try{const s=JSON.parse(native?.getSession?.()||'{}');jwt=s.accessToken||'';refreshToken=s.refreshToken||'';phone=s.phone||'';}catch{}
  pendingEventId=native?.takePendingEvent?.()||pendingEventId;
  if(jwt){showAuthenticated();native?.resyncPush?.();}else showAuth();
}
function navigate(target){
  if(!jwt)return showAuth();
  if(screen==='live' && target!=='live')disconnectLive();
  screen=target;
  document.querySelectorAll('#signedApp>.screen').forEach(el=>el.classList.toggle('active',el.id===target));
  for(const [id,tab] of [['tabHome','home'],['tabEvents','events'],['tabDevices','pair'],['tabSettings','settings']])$(id).classList.toggle('active',tab===target);
  if(target==='home')loadCameras();if(target==='events'){closeEvent();loadEvents();}if(target==='recordings')loadSegments();
  if(target==='live')connectLive();
}
async function loadCameras(){
  try{const r=await request('/api/cameras');cameras=Array.isArray(r)?r:(r.cameras||[]);
    const remembered=native?.rememberedCamera?.();
    currentCamera=cameras.find(c=>c.cameraId===remembered)||cameras[0]||null;
    $('heroName').textContent=currentCamera?.name||'Front Door Camera';
    $('heroStatus').textContent=currentCamera?'Tap to connect live':'No camera paired yet';
    $('deviceState').textContent=currentCamera?'Ready to connect':'Not paired';
    if(currentCamera){native?.rememberCamera?.(currentCamera.cameraId);$('liveTitle').textContent=currentCamera.name||'Smart Door Lock'}
    if(callRequested){const id=callRequested.cameraId;callRequested=null;const match=cameras.find(c=>c.cameraId===id);
      if(match){currentCamera=match;navigate('live')}else notify('This doorbell camera is not linked to your account.')}
  }catch(e){$('deviceState').textContent='Unavailable';$('heroStatus').textContent='Could not reach camera service';}
}
async function claimCamera(){
  clearError('pairStatus');const cameraId=$('camId').value.trim().toUpperCase(),password=$('camPassword').value,name=$('camName').value.trim()||'Front Door';
  if(!cameraId||!password)return error('pairStatus','Enter your camera ID and pairing password.');
  try{await request('/api/cameras/claim','POST',{cameraId,password,name});notify('Camera paired');navigate('home')}
  catch(e){error('pairStatus',e.message)}
}
function openPrimaryCamera(){if(currentCamera)navigate('live');else navigate('pair')}
function liveMessage(message){$('videoStatus').textContent=message}
function stopConnection(){
  if(ws){const old=ws;ws=null;old.onclose=null;old.onerror=null;try{old.close()}catch{}}
  if(pc){try{pc.close()}catch{}pc=null}
  if(mediaMic){mediaMic.getTracks().forEach(t=>t.stop());mediaMic=null}
  $('liveVideo').srcObject=null;pendingCandidates=[];
}
function disconnectLive(){wantLive=false;clearTimeout(reconnectTimer);reconnectTimer=null;reconnectAttempt=0;stopConnection();$('videoOverlay').classList.remove('hidden');$('liveIndicator').textContent='OFFLINE';liveMessage('Live view paused')}
function scheduleReconnect(){
  if(!wantLive||screen!=='live'||!currentCamera)return;
  clearTimeout(reconnectTimer);const delay=Math.min(15000,1000*Math.pow(1.8,Math.min(reconnectAttempt++,6)));
  $('videoOverlay').classList.remove('hidden');$('liveIndicator').textContent='CONNECTING';liveMessage('Reconnecting to your camera…');reconnectTimer=setTimeout(()=>{if(wantLive)openSocket()},delay);
}
function connectLive(){if(!jwt||!currentCamera){liveMessage('Pair a camera first');return}
  if(wantLive&&pc?.connectionState==='connected')return;
  wantLive=true;reconnectAttempt=0;clearTimeout(reconnectTimer);openSocket();}
function reconnectLive(){disconnectLive();connectLive()}
async function maybeAddAudio(offer){
  // The existing Pi SDP historically offers video only. Do NOT assume two-way audio exists.
  // If future Pi offer includes audio, negotiate our microphone with AEC enabled.
  if(!/m=audio\s/.test(offer.sdp)){ $('micLabel').textContent='Unavailable';$('audioNote').classList.remove('hidden');return; }
  try{mediaMic=await navigator.mediaDevices.getUserMedia({audio:{echoCancellation:true,noiseSuppression:true,autoGainControl:true}});
    mediaMic.getAudioTracks().forEach(track=>{track.enabled=false;pc.addTrack(track,mediaMic)});
    $('micLabel').textContent='Muted';$('audioNote').classList.add('hidden');
  }catch{$('micLabel').textContent='Mic denied';}
}
function openSocket(){
  if(!wantLive||!currentCamera||screen!=='live')return;
  stopConnection();$('videoOverlay').classList.remove('hidden');$('liveIndicator').textContent='CONNECTING';liveMessage('Connecting securely…');
  const cameraId=currentCamera.cameraId;let socket;
  try{socket=new WebSocket('wss://camera.360secure.in/ws?role=viewer&cameraId='+encodeURIComponent(cameraId)+'&token='+encodeURIComponent(jwt))}catch{scheduleReconnect();return}
  ws=socket;socket.onopen=()=>{reconnectAttempt=0};
  socket.onmessage=async event=>{
    if(ws!==socket)return;let m;try{m=JSON.parse(event.data)}catch{return}
    try{
      if(m.type==='connected')liveMessage('Waiting for camera…');
      if(m.type==='camera-online')liveMessage('Camera connected · starting video…');
      if(m.type==='camera-offline'){scheduleReconnect();return}
      if(m.type==='offer'){
        if(pc)pc.close();pc=new RTCPeerConnection({iceServers:[{urls:'stun:stun.l.google.com:19302'}]});const peer=pc;
        peer.onconnectionstatechange=()=>{if(pc!==peer)return;
          if(peer.connectionState==='connected'){$('liveIndicator').textContent='LIVE';reconnectAttempt=0}
          else if(['failed','disconnected','closed'].includes(peer.connectionState))scheduleReconnect();};
        peer.ontrack=e=>{if(pc!==peer)return;
          if(e.track.kind==='video'){$('liveVideo').srcObject=e.streams[0]||new MediaStream([e.track]);$('liveVideo').play().catch(()=>{});$('videoOverlay').classList.add('hidden');$('liveIndicator').textContent='LIVE'}
          if(e.track.kind==='audio'){let audio=$('remoteAudio');if(!audio){audio=document.createElement('audio');audio.id='remoteAudio';audio.autoplay=true;document.body.append(audio)}audio.srcObject=e.streams[0]||new MediaStream([e.track]);audio.muted=!speakerEnabled;audio.play().catch(()=>{})}
        };
        peer.onicecandidate=e=>{if(e.candidate&&ws===socket&&socket.readyState===1)socket.send(JSON.stringify({type:'candidate',candidate:e.candidate.candidate,sdpMLineIndex:e.candidate.sdpMLineIndex}))};
        await peer.setRemoteDescription({type:'offer',sdp:m.sdp});
        await maybeAddAudio({sdp:m.sdp});
        for(const c of pendingCandidates.splice(0))await peer.addIceCandidate(c);
        const answer=await peer.createAnswer();await peer.setLocalDescription(answer);
        if(socket.readyState===1)socket.send(JSON.stringify({type:'answer',sdp:peer.localDescription.sdp}));
      }
      if(m.type==='candidate'){
        const c={candidate:m.candidate,sdpMLineIndex:m.sdpMLineIndex};
        if(pc?.remoteDescription)await pc.addIceCandidate(c);else pendingCandidates.push(c);
      }
      if(m.type==='error')scheduleReconnect();
    }catch{scheduleReconnect()}
  };
  socket.onerror=()=>{if(ws===socket)scheduleReconnect()};socket.onclose=()=>{if(ws===socket)scheduleReconnect()};
}
function toggleSpeaker(){speakerEnabled=!speakerEnabled;const a=$('remoteAudio');if(a)a.muted=!speakerEnabled;
  $('speakerLabel').textContent=speakerEnabled?'Speaker on':'Speaker off';notify(a?(speakerEnabled?'Audio enabled':'Audio muted'):'Camera has no audio track yet');}
function toggleMicrophone(){const tracks=mediaMic?.getAudioTracks()||[];if(!tracks.length){notify('Two-way talk requires the Pi microphone and WebRTC audio support.');return}
  const on=!tracks[0].enabled;tracks.forEach(t=>t.enabled=on);$('micLabel').textContent=on?'Talking':'Muted';notify(on?'Microphone on':'Microphone muted');}
window.secureDoorbell=info=>{if(!info||!info.cameraId)return;callRequested=info;
  if(!jwt){showAuth();notify('Sign in to answer your doorbell.');return}
  if(cameras.length){const match=cameras.find(c=>c.cameraId===info.cameraId);if(match){currentCamera=match;callRequested=null;navigate('live')}else notify('This doorbell camera is not linked to your account.')}
  else loadCameras();
};
function dateKey(ms){const d=new Date(ms);return d.getFullYear()+'-'+String(d.getMonth()+1).padStart(2,'0')+'-'+String(d.getDate()).padStart(2,'0')}
function dayStart(ms){const d=new Date(ms);return new Date(d.getFullYear(),d.getMonth(),d.getDate()).getTime()}
function dayShift(ms,n){const d=new Date(ms);d.setDate(d.getDate()+n);return d.getTime()}
function parseSegments(r){const arr=Array.isArray(r)?r:(r.recordings||r.segments||[]);return arr.map(x=>({...x,start:new Date(x.startTime||x.start||x.timestamp).getTime(),end:new Date(x.endTime||x.end||x.stopTime).getTime()})).filter(x=>Number.isFinite(x.start)).map(x=>({...x,end:Number.isFinite(x.end)?x.end:x.start+60000})).sort((a,b)=>a.start-b.start)}
async function fetchDay(day){const key=dateKey(day),camera=currentCamera?.cameraId;if(!camera)return[];const cache=camera+':'+key;if(segmentCache.has(cache))return segmentCache.get(cache);const r=await request('/api/cameras/'+encodeURIComponent(camera)+'/recordings?date='+encodeURIComponent(key));const result=parseSegments(r);segmentCache.set(cache,result);return result}
async function loadSegments(){if(!currentCamera)return;const day=new Date($('recordDate').value+'T00:00:00').getTime();if(!Number.isFinite(day))return;timelineBase=dayStart(day);await renderTimeline(true)}
function selectedMs(){const ticks=$('ticks');return timelineBase+(ticks.scrollLeft+ticks.clientWidth/2)/PXDAY*DAY}
async function renderTimeline(reset=false){
  if(!currentCamera||timelineBusy)return;timelineBusy=true;
  const ticks=$('ticks'),marks=$('marks');const selected=reset?timelineBase+12*3600000:selectedMs(),start=dayShift(dayStart(selected),-BASE);
  timelineBase=start;marks.style.width=(SPAN*PXDAY)+'px';marks.replaceChildren();
  for(let i=0;i<SPAN;i++){const day=dayShift(start,i);for(let h=0;h<24;h++){
    const x=i*PXDAY+h*PXDAY/24;const line=document.createElement('div');line.className='hourline';line.style.left=x+'px';marks.append(line);
    if(h%3===0){const label=document.createElement('span');label.style.left=x+'px';label.textContent=(h===0?dateKey(day).slice(5)+' · ':'')+String(h).padStart(2,'0')+':00';marks.append(label)}
  }}
  const found=await Promise.allSettled(Array.from({length:SPAN},(_,i)=>fetchDay(dayShift(start,i))));segments=[];let failures=0;
  for(const f of found){if(f.status==='fulfilled')segments.push(...f.value);else failures++}
  for(const s of segments){const a=Math.max(0,(s.start-start)/DAY*PXDAY),b=Math.min(SPAN*PXDAY,(s.end-start)/DAY*PXDAY);
    if(b>a){const el=document.createElement('div');el.className='segment';el.style.left=a+'px';el.style.width=Math.max(2,b-a)+'px';marks.append(el)}}
  ticks.onscroll=null;ticks.scrollLeft=(selected-start)/DAY*PXDAY-ticks.clientWidth/2;
  timelineBusy=false;const update=()=>{const ms=selectedMs();$('selectedTime').textContent=new Date(ms).toLocaleString();$('recordDate').value=dateKey(ms);clearTimeout(timelineTimer);timelineTimer=setTimeout(()=>{if(ticks.scrollLeft<PXDAY/2||ticks.scrollLeft>ticks.scrollWidth-ticks.clientWidth-PXDAY/2)renderTimeline(false)},200)};
  ticks.onscroll=update;ticks.onclick=e=>{const r=ticks.getBoundingClientRect();seekTo(start+(e.clientX-r.left+ticks.scrollLeft)/PXDAY*DAY)};update();
  $('recordStatus').textContent=failures?'Recording service unavailable or partially unavailable.':(segments.length?'Tap blue footage to play':'No footage available for this date.');
}
async function seekTo(ms){const s=segments.find(x=>x.start<=ms&&x.end>ms);if(!s){$('recordStatus').textContent='No recording at that time.';return}
  try{let url=s.playbackUrl||s.url;if(!url){const r=await request('/api/cameras/'+encodeURIComponent(currentCamera.cameraId)+'/recordings/'+encodeURIComponent(s._id||s.id)+'/playback');url=r.playbackUrl||r.url}
    if(!url)throw Error('Playback URL missing');const resolved=new URL(url,API);if(resolved.protocol!=='https:')throw Error('Playback requires HTTPS');
    const player=$('recordVideo');player.src=resolved.href;player.onloadedmetadata=()=>{player.currentTime=Math.max(0,(ms-s.start)/1000);player.play().catch(()=>{})};
    $('recordStatus').textContent='Playing '+new Date(s.start).toLocaleTimeString();
  }catch(e){$('recordStatus').textContent='Playback unavailable: '+e.message}
}
async function loadEvents(){
  const list=$('eventsList');list.replaceChildren();list.classList.remove('empty-card');
  try{
    const r=await request('/api/events?date='+encodeURIComponent(dateKey(Date.now())));
    const events=Array.isArray(r)?r:(r.events||[]);
    if(!events.length){list.classList.add('empty-card');list.innerHTML='<div class="empty-icon">◷</div><b>No events today</b><p>Visitor history will appear here when person monitoring is connected.</p>';return}
    list.style.display='block';list.style.textAlign='left';
    events.slice(0,80).forEach(ev=>{
      const id=ev.id||ev._id;if(typeof id!=='string'||!/^[A-Za-z0-9_-]{8,128}$/.test(id))return;
      const box=document.createElement('button');box.type='button';box.className='settings-row';
      box.style.width='100%';box.style.textAlign='left';box.style.color='inherit';box.style.background='transparent';
      const inner=document.createElement('div'),title=document.createElement('b'),sub=document.createElement('p');
      title.textContent=ev.title||'Person detected';
      const date=new Date(ev.timestamp||ev.createdAt);
      sub.textContent=Number.isNaN(date.getTime())?'Visitor event':date.toLocaleString();
      inner.append(title,sub);box.append(inner);
      box.onclick=()=>openEvent(id);
      list.append(box);
    });
  }catch{
    list.classList.add('empty-card');
    list.innerHTML='<div class="empty-icon">◷</div><b>Events unavailable</b><p>Connect the event service to view visitors.</p>';
  }
}
function closeEvent(){
  if(eventPhotoUrl){URL.revokeObjectURL(eventPhotoUrl);eventPhotoUrl=null}
  $('eventDetailPhoto').removeAttribute('src');$('eventDetailPhoto').classList.add('hidden');
  $('eventDetail').classList.add('hidden');$('eventsList').classList.remove('hidden');
}
async function loadEventPhoto(id){
  try{
    let res=await fetch(API+'/api/events/'+encodeURIComponent(id)+'/photo',{headers:{Authorization:'Bearer '+jwt}});
    if(res.status===401 && refreshToken){await refreshSession();res=await fetch(API+'/api/events/'+encodeURIComponent(id)+'/photo',{headers:{Authorization:'Bearer '+jwt}})}
    if(!res.ok)throw Error('Photo unavailable');
    const type=res.headers.get('content-type')||'';
    if(!/^image\/(jpeg|png)/.test(type))throw Error('Unsupported photograph');
    const bytes=await res.blob();
    if(bytes.size>1000000)throw Error('Image too large');
    if(pendingEventId!==id)return;
    eventPhotoUrl=URL.createObjectURL(bytes);
    $('eventDetailPhoto').src=eventPhotoUrl;
    $('eventDetailPhoto').classList.remove('hidden');
    $('eventPhotoPlaceholder').classList.add('hidden');
  }catch{$('eventPhotoPlaceholder').textContent='Photograph unavailable or not captured';$('eventPhotoPlaceholder').classList.remove('hidden')}
}
async function openEvent(id){
  if(typeof id!=='string'||!/^[A-Za-z0-9_-]{8,128}$/.test(id))return;
  pendingEventId=id;
  if(!jwt){showAuth();return}
  if(screen!=='events')navigate('events');
  closeEvent();$('eventsList').classList.add('hidden');$('eventDetail').classList.remove('hidden');
  $('eventDetailTitle').textContent='Visitor event';$('eventDetailTime').textContent='';
  $('eventPhotoPlaceholder').textContent='Loading visitor photograph…';
  $('eventPhotoPlaceholder').classList.remove('hidden');$('eventDetailStatus').textContent='Loading event details…';
  try{
    const ev=await request('/api/events/'+encodeURIComponent(id));
    if(pendingEventId!==id)return;
    $('eventDetailTitle').textContent=ev.title||'Visitor detected';
    const dt=new Date(ev.timestamp||ev.createdAt);
    $('eventDetailTime').textContent=Number.isNaN(dt.getTime())?'':dt.toLocaleString();
    $('eventDetailStatus').textContent=ev.cameraName||ev.cameraId||'Front Door';
    if(ev.hasPhoto)await loadEventPhoto(id);
    else{$('eventPhotoPlaceholder').textContent='No photograph captured for this event';$('eventPhotoPlaceholder').classList.remove('hidden')}
  }catch{
    $('eventDetailStatus').textContent='This event is unavailable or your account does not have permission.';
    $('eventPhotoPlaceholder').textContent='Unable to display photograph';
  }
}
window.secureOpenEvent=id=>{
  if(typeof id!=='string'||!/^[A-Za-z0-9_-]{8,128}$/.test(id))return;
  pendingEventId=id;
  if(!jwt){showAuth();return}
  openEvent(id);
};
window.appBack=()=>{if(['live','events','persons','access','settings','pair'].includes(screen))navigate('home');else if(screen==='recordings')navigate('live');else if(screen==='home')notify('360Secure is running securely')};
window.addEventListener('load',init);

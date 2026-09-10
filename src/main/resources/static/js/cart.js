const csrf=()=>document.querySelector('#csrf-form input[name="_csrf"]')?.value||decodeURIComponent(document.cookie.split('; ').find(c=>c.startsWith('XSRF-TOKEN='))?.split('=')[1]||'');
async function cartRequest(url,method,body){
  const r=await fetch(url,{method,headers:{'Content-Type':'application/json','X-XSRF-TOKEN':csrf()},body:body?JSON.stringify(body):undefined});
  if(!r.ok)throw new Error();
  return r.json();
}
document.querySelectorAll('.cart-item').forEach(el=>{
  const input=el.querySelector('.quantity input');
  el.querySelector('[data-cart-minus]').onclick=async()=>{if(+input.value<=1)return;await update(+input.value-1)};
  el.querySelector('[data-cart-plus]').onclick=async()=>{if(+input.value>=20)return;await update(+input.value+1)};
  el.querySelector('[data-remove]').onclick=async()=>{
    if(!window.confirm('Remover este item da sacola?'))return;
    await cartRequest('/api/cart/items/'+el.dataset.key,'DELETE');
    location.reload();
  };
  async function update(q){await cartRequest('/api/cart/items/'+el.dataset.key,'PATCH',{quantity:q});location.reload()}
});
const notes=document.querySelector('[data-cart-notes]');
if(notes){
  let timer;
  notes.oninput=()=>{clearTimeout(timer);timer=setTimeout(()=>cartRequest('/api/cart/notes','PATCH',{notes:notes.value}),500)};
}

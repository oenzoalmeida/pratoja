const money = new Intl.NumberFormat('pt-BR',{style:'currency',currency:'BRL'});
const csrfToken=form=>form?.querySelector('input[name="_csrf"]')?.value||decodeURIComponent(document.cookie.split('; ').find(c=>c.startsWith('XSRF-TOKEN='))?.split('=')[1]||'');
function toast(message,error=false){const el=document.createElement('div');el.className='toast'+(error?' error':'');el.textContent=message;document.body.append(el);setTimeout(()=>el.remove(),2800)}
async function cartCount(){try{const r=await fetch('/api/cart');if(!r.ok)return;const c=await r.json();document.querySelectorAll('[data-cart-count]').forEach(e=>e.textContent=c.count)}catch{}}
document.querySelectorAll('[data-minus]').forEach(b=>b.onclick=()=>{const i=b.parentElement.querySelector('input');i.value=Math.max(1,+i.value-1)});
document.querySelectorAll('[data-plus]').forEach(b=>b.onclick=()=>{const i=b.parentElement.querySelector('input');i.value=Math.min(20,+i.value+1)});
document.querySelectorAll('.builder fieldset').forEach(group=>group.addEventListener('change',e=>{const max=+group.dataset.max;if(e.target.type==='checkbox'&&group.querySelectorAll('input:checked').length>max){e.target.checked=false;toast(`Escolha no máximo ${max} opções.`,true)}updateBuilder()}));
function updateBuilder(){const form=document.querySelector('.builder');if(!form)return;let total=+form.dataset.basePrice;const names=[];form.querySelectorAll('input:checked').forEach(i=>{total+=+i.dataset.price;names.push(i.closest('label').querySelector('strong').textContent)});document.querySelector('[data-builder-total]').textContent=money.format(total);document.querySelector('[data-selection-summary]').textContent=names.length?names.join(' · '):'Faça suas escolhas para continuar.'}
document.querySelectorAll('.add-cart-form').forEach(form=>form.addEventListener('submit',async e=>{e.preventDefault();const options=[...form.querySelectorAll('input[type=radio]:checked,input[type=checkbox]:checked')].map(i=>+i.value);for(const group of form.querySelectorAll('fieldset')){if(group.querySelectorAll('input:checked').length<+group.dataset.min){toast('Complete as escolhas obrigatórias.',true);group.scrollIntoView({behavior:'smooth',block:'center'});return}}const payload={productId:+form.dataset.product,quantity:+(form.querySelector('[name=quantity]')?.value||1),optionIds:options,notes:form.querySelector('[name=notes]')?.value||''};const token=csrfToken(form);const headers={'Content-Type':'application/json'};if(token)headers['X-XSRF-TOKEN']=token;const r=await fetch('/api/cart/items',{method:'POST',headers,body:JSON.stringify(payload)});if(r.ok){toast('Adicionado à sacola!');cartCount()}else{const d=await r.json().catch(()=>({message:'Não foi possível adicionar.'}));toast(d.message||'Não foi possível adicionar.',true)}}));
updateBuilder();cartCount();

document.querySelectorAll('.quick-add-form').forEach(form=>form.addEventListener('submit',async event=>{
  event.preventDefault();
  const button=form.querySelector('button');
  button.disabled=true;
  try{
    const token=csrfToken(form);
    const headers={'Content-Type':'application/json'};
    if(token)headers['X-XSRF-TOKEN']=token;
    const response=await fetch('/api/cart/items',{method:'POST',headers,body:JSON.stringify({productId:+form.dataset.product,quantity:1,optionIds:[],notes:''})});
    if(!response.ok){
      const data=await response.json().catch(()=>({message:'Não foi possível adicionar.'}));
      throw new Error(data.message||'Não foi possível adicionar.');
    }
    toast('Adicionado à sacola!');
    cartCount();
  }catch(error){
    toast(error.message||'Não foi possível adicionar.',true);
  }finally{
    button.disabled=false;
  }
}));

document.querySelectorAll('form[data-confirm]').forEach(form=>form.addEventListener('submit',event=>{
  if(!window.confirm(form.dataset.confirm))event.preventDefault();
}));

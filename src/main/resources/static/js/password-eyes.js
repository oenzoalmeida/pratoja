(function(){
  'use strict';
  document.querySelectorAll('.auth-card input[type="password"]').forEach(function(input){
    var wrap=input.parentNode,btn=wrap.querySelector('[data-pw-toggle]');
    if(!btn){
      wrap=document.createElement('span');wrap.className='auth-password';
      input.parentNode.insertBefore(wrap,input);wrap.appendChild(input);
      btn=document.createElement('button');btn.type='button';btn.className='pw-toggle';
      wrap.appendChild(btn);
    }
    btn.innerHTML='<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M2 12s3-7 10-7 10 7 10 7-3 7-10 7-10-7-10-7z"/><circle cx="12" cy="12" r="3"/><path class="eye-off" d="m4 20 16-16"/></svg>';
    btn.setAttribute('aria-label','Mostrar senha');btn.setAttribute('aria-pressed','false');
    btn.addEventListener('click',function(){
      var show=input.type==='password';input.type=show?'text':'password';
      btn.setAttribute('aria-pressed',String(show));
      btn.setAttribute('aria-label',show?'Ocultar senha':'Mostrar senha');
    });
  });
})();

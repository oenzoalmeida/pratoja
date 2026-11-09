(function(){
  'use strict';
  var form=document.querySelector('[data-auth-form]');
  if(!form)return;
  form.noValidate=true; /* mensagens amigáveis no lugar do balão do navegador; o servidor continua validando tudo */
  var EMAIL=/^[^@\s]+@[^@\s]+\.[^@\s]+$/;
  function value(name){var el=form.elements[name];return el?el.value:''}
  var rules={
    required:function(v,el){return v.trim()?'':'Digite '+(el.dataset.label||'este campo')+'.'},
    email:function(v){if(!v.trim())return 'Digite seu e-mail.';return EMAIL.test(v.trim())?'':'Esse e-mail não parece certo. Confira se tem @ e o final, como .com.'},
    name:function(v){return v.trim().length>=3?'':'Digite seu nome completo.'},
    phone:function(v){return v.replace(/\D/g,'').length>=10?'':'Informe um telefone válido, com DDD.'},
    newpassword:function(v){if(v.length<8)return 'Use pelo menos 8 caracteres.';if(!/[A-Z]/.test(v)||!/[a-z]/.test(v)||!/\d/.test(v))return 'Falta uma maiúscula, uma minúscula ou um número.';return ''},
    confirm:function(v){if(!v)return 'Repita a senha.';return v===value('password')?'':'As senhas não são iguais.'}
  };
  function show(field,msg){
    var input=field.querySelector('input'),out=field.querySelector('.field-error');
    out.textContent=msg;out.classList.toggle('show',!!msg);
    field.classList.toggle('invalid',!!msg);
    input.setAttribute('aria-invalid',msg?'true':'false');
  }
  function check(field){
    var input=field.querySelector('input'),rule=rules[input.dataset.rule];
    var msg=rule?rule(input.value,input):'';
    show(field,msg);return !msg;
  }
  var fields=[].slice.call(form.querySelectorAll('[data-field]'));
  fields.forEach(function(f){
    var input=f.querySelector('input');
    input.addEventListener('blur',function(){if(input.value||f.dataset.touched)f.dataset.touched='1',check(f)});
    input.addEventListener('input',function(){
      if(f.classList.contains('invalid')||input.classList.contains('was-bad'))check(f);
      if(input.name==='password'){var c=form.querySelector('[data-field] input[name="confirmPassword"]');if(c&&c.value)check(c.closest('[data-field]'))}
    });
  });
  form.addEventListener('submit',function(e){
    var first=null;
    fields.forEach(function(f){if(!check(f)&&!first)first=f});
    if(first){e.preventDefault();first.querySelector('input').focus()}
  });
})();

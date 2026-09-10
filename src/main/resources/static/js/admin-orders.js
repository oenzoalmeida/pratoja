const adminEvents=new EventSource('/admin/pedidos/events');adminEvents.addEventListener('order',()=>setTimeout(()=>location.reload(),500));

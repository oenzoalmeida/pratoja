document.querySelectorAll('form[data-confirm]').forEach(form=>form.addEventListener('submit',event=>{
  if(!window.confirm(form.dataset.confirm))event.preventDefault();
}));

const tableFilter=document.querySelector('[data-table-filter]');
if(tableFilter){
  const rows=[...document.querySelectorAll('[data-filter-table] tbody tr')];
  const empty=document.querySelector('.table-no-results');
  tableFilter.addEventListener('input',()=>{
    const query=tableFilter.value.trim().toLocaleLowerCase('pt-BR');
    let visible=0;
    rows.forEach(row=>{
      const matches=!query||row.textContent.toLocaleLowerCase('pt-BR').includes(query);
      row.hidden=!matches;
      if(matches)visible++;
    });
    if(empty)empty.hidden=visible>0;
  });
}

document.querySelectorAll('[data-board-filter]').forEach(button=>button.addEventListener('click',()=>{
  document.querySelectorAll('[data-board-filter]').forEach(item=>item.classList.toggle('active',item===button));
  document.querySelectorAll('[data-board-column]').forEach(column=>{
    column.hidden=button.dataset.boardFilter!=='all'&&column.dataset.boardColumn!==button.dataset.boardFilter;
  });
}));

document.querySelectorAll('.order-column').forEach(column=>{
  const hasCards=column.querySelector('.order-card');
  const empty=column.querySelector('.column-empty');
  if(empty)empty.hidden=Boolean(hasCards);
});

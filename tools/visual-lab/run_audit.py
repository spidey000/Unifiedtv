import asyncio, json, os, pathlib, zipfile
from playwright.async_api import async_playwright
OUT=pathlib.Path(__file__).parent
SHOTS=pathlib.Path(os.environ.get('UNIFIEDTV_CAPTURES', str(OUT/'capturas')))
SHOTS.mkdir(parents=True,exist_ok=True)
URL='about:blank'
REACT=(OUT/'assets/react.production.min.js').read_text()
DOM=(OUT/'assets/react-dom.production.min.js').read_text()
CSS=(OUT/'style.css').read_text()
APP=(OUT/'assets/native-resources.js').read_text()+'\n'+(OUT/'app.js').read_text()
async def load(page,params):
  from urllib.parse import parse_qsl
  state=json.dumps(dict(parse_qsl(params.lstrip('?'))),ensure_ascii=False)
  html='<html lang=\"es\"><head><meta charset=\"utf-8\"><style>'+CSS+'</style></head><body><div id=\"root\"></div><script>window.__PREVIEW_PARAMS='+state+';</script><script>'+REACT+'</script><script>'+DOM+'</script><script>'+APP+'</script></body></html>'
  await page.goto('about:blank')
  await page.set_content(html,wait_until='load')
  await page.locator('.main-shell,.settings-shell').first.wait_for(timeout=8000)
SCREENS=[('01_inicio','home',''),('02_peliculas','movies',''),('03_series','series',''),('04_mi_lista','watch',''),('05_buscar','search',''),('06_apps_y_fuentes','apps',''),('07_tv_en_directo','live','')]
SETTINGS=['appearance','catalog','platforms','watch','updates','permissions','recovery','about']
RECORD=[]
async def check(page,label):
    js="""() => {
      const doc=document.documentElement, body=document.body, root=document.querySelector('#root');
      const visible=[...document.querySelectorAll('button,input')].filter(e=>{let r=e.getBoundingClientRect(),s=getComputedStyle(e);return r.width>0&&r.height>0&&s.visibility!=='hidden'});
      const leftClipped=visible.filter(e=>e.getBoundingClientRect().right<0||e.getBoundingClientRect().left>innerWidth);
      const offRight=visible.filter(e=>e.getBoundingClientRect().left<innerWidth&&e.getBoundingClientRect().right>innerWidth+1);
      const overflow=[...document.querySelectorAll('.page-head,.shelf-header,.setting-row,.app-logo,.nav-entry')].filter(e=>e.scrollWidth>e.clientWidth+2).map(e=>e.className+': '+e.textContent.slice(0,50));
      return {viewport:[innerWidth,innerHeight],docScrollX:doc.scrollWidth>innerWidth+1,bodyScrollX:body.scrollWidth>innerWidth+1,rootScrollX:root.scrollWidth>innerWidth+1,buttons:visible.length,offRight:offRight.map(e=>e.textContent.slice(0,50)),overflowText:overflow.slice(0,20),visibleOffLeft:leftClipped.length};
    }"""
    v=await page.evaluate(js)
    RECORD.append({'screen':label,**v})
async def main():
  async with async_playwright() as p:
    b=await p.chromium.launch(headless=True, executable_path=os.environ.get('CHROMIUM_PATH'),args=['--no-sandbox','--disable-dev-shm-usage','--disable-gpu','--hide-scrollbars'])
    page=await b.new_page(viewport={'width':1280,'height':720},device_scale_factor=1)
    errors=[]
    page.on('pageerror',lambda err:errors.append(str(err)))
    for label,view,_ in SCREENS:
      await load(page,'?page='+view)
      await page.screenshot(path=str(SHOTS/(label+'.png')),full_page=False)
      await check(page,label)
    for n,section in enumerate(SETTINGS,8):
      await load(page,'?page=settings&section='+section)
      label=f'{n:02d}_ajustes_{section}'
      await page.screenshot(path=str(SHOTS/(label+'.png')),full_page=False)
      await check(page,label)
    specials=[('16_menu_expandido','?page=home&nav=expanded'),('17_catalogo_columnas','?page=movies&mode=columns'),('18_vinculacion_qr','?page=settings&section=catalog&dialog=qr'),('19_ficha_detalle','?page=home&dialog=details:El%20%C3%BAltimo%20horizonte'),('20_permiso_dialogo','?page=settings&section=recovery&dialog=recover')]
    for label,suffix in specials:
      await load(page,suffix)
      await page.screenshot(path=str(SHOTS/(label+'.png')),full_page=False)
      await check(page,label)
    await load(page,'?page=home')
    await page.get_by_role('button',name='Películas').first.click()
    RECORD.append({'interaction':'click Películas','result':await page.locator('.hero-title').first.inner_text()})
    await page.get_by_role('button',name='Ajustes').first.click()
    RECORD.append({'interaction':'click Ajustes','heading':await page.locator('.settings-h1').first.inner_text()})
    await page.get_by_role('button',name='Catálogo',exact=True).first.click()
    RECORD.append({'interaction':'click Catálogo','heading':await page.locator('.settings-h1').first.inner_text()})
    await page.get_by_role('button',name='Configurar TMDB desde el móvil').click()
    RECORD.append({'interaction':'QR dialog','visible':await page.get_by_role('dialog').is_visible()})
    await page.keyboard.press('Escape')
    RECORD.append({'interaction':'Escape dialog','visible':await page.get_by_role('dialog').count()})
    for (width,height,suffix,label) in [(1920,1080,'?page=home','21_inicio_1080p'),(1920,1080,'?page=apps','22_apps_1080p'),(3840,2160,'?page=home','23_inicio_4k'),(3840,2160,'?page=settings&section=permissions','24_permisos_4k')]:
      await page.set_viewport_size({'width':width,'height':height})
      await load(page,suffix)
      await page.screenshot(path=str(SHOTS/(label+'.png')),full_page=False)
      await check(page,label)
    await page.set_viewport_size({'width':1280,'height':720})
    for label,params,selector in [
      ('25_ajustes_permisos_inferior','?page=settings&section=permissions','.settings-content'),
      ('26_ajustes_plataformas_inferior','?page=settings&section=platforms','.settings-content'),
      ('27_ajustes_seguir_viendo_inferior','?page=settings&section=watch','.settings-content'),
      ('28_inicio_desplazado','?page=home','.main-viewport'),
      ('29_apps_desplazado','?page=apps','.main-viewport')]:
      await load(page,params)
      await page.locator(selector).evaluate('(el) => { el.scrollTop = el.scrollHeight; }')
      await page.wait_for_timeout(100)
      await page.screenshot(path=str(SHOTS/(label+'.png')),full_page=False)
      await check(page,label)
    for label,params,selector in [
      ('30_foco_menu','?page=home','.nav-entry:nth-child(3)'),
      ('31_foco_poster','?page=home','.poster:first-of-type'),
      ('32_foco_ajustes','?page=settings&section=catalog','.setting-row:first-of-type')]:
      await load(page,params)
      await page.locator(selector).first.focus()
      await page.wait_for_timeout(300)
      await page.screenshot(path=str(SHOTS/(label+'.png')),full_page=False)
      await check(page,label)
    await b.close()
    with open(SHOTS/'resultado_chromium.json','w') as f:json.dump({'screens':RECORD,'errors':errors},f,ensure_ascii=False,indent=2)
    print(json.dumps({'screenshots':len(list(SHOTS.glob('*.png'))),'errors':errors,'overflow':[x for x in RECORD if x.get('docScrollX') or x.get('bodyScrollX') or x.get('overflowText')], 'interactions':[x for x in RECORD if x.get('interaction')]},ensure_ascii=False,indent=2))
    if errors: raise RuntimeError('Errores JavaScript: '+str(errors))
if __name__=='__main__': asyncio.run(main())

import {test,expect} from '@playwright/test';
import {mkdir} from 'node:fs/promises';
import path from 'node:path';

test.beforeEach(async({page})=>{await page.goto('/signin');await page.getByRole('button',{name:'Explore the local demo'}).click();await expect(page.getByRole('heading',{name:'Workspace overview'})).toBeVisible();});
test('appearance control persists the selected accessible theme',async({page})=>{
  await expect(page.locator('html')).toHaveAttribute('data-theme','dark');
  await page.getByRole('button',{name:'Switch to light theme'}).click();
  await expect(page.locator('html')).toHaveAttribute('data-theme','light');
  await page.reload();
  await expect(page.locator('html')).toHaveAttribute('data-theme','light');
  await page.getByRole('button',{name:'Switch to dark theme'}).click();
  await expect(page.locator('html')).toHaveAttribute('data-theme','dark');
});
test('demo review journey uses stored results and editable local policy',async({page})=>{
  const errors:string[]=[];page.on('pageerror',error=>errors.push(error.message));
  await expect(page.locator('.metric').filter({hasText:'Pull requests reviewed'}).locator('.metric-value')).toHaveText('6');
  await expect(page.getByText('Sample repositories and stored demo reviews.')).toBeVisible();
  if(process.env.CODEGUARD_SCREENSHOTS_DIR){await mkdir(process.env.CODEGUARD_SCREENSHOTS_DIR,{recursive:true});await page.screenshot({path:path.join(process.env.CODEGUARD_SCREENSHOTS_DIR,'dashboard.png'),fullPage:true});}
  await page.getByRole('navigation',{name:'Main navigation'}).getByRole('link',{name:'Repositories'}).click();
  await page.getByRole('textbox',{name:'Search repositories'}).fill('payment');
  await page.getByRole('link',{name:'demo/payment-service',exact:true}).click();
  await expect(page.getByRole('switch',{name:/Allow cloud AI analysis/})).toBeDisabled();
  const enabled=page.getByRole('switch',{name:/Automatic pull request reviews/});
  await enabled.uncheck();await page.getByRole('button',{name:'Save policy'}).click();await expect(page.getByText('Policy saved')).toBeVisible();
  await enabled.check();await page.getByRole('button',{name:'Save policy'}).click();await expect(page.getByText('Policy saved')).toBeVisible();
  await page.getByRole('link',{name:'View pull requests'}).click();
  await page.getByRole('link',{name:/Add payment reconciliation job/}).click();
  await expect(page.getByRole('heading',{name:'Review summary'})).toBeVisible();
  await expect(page.getByRole('heading',{name:'Publish to GitHub'})).toBeVisible();
  await expect(page.getByText(/Demo reviews cannot be published/)).toBeVisible();
  await page.getByRole('button',{name:'Preview feedback'}).click();
  await expect(page.getByText(/This preview uses the stored diff/)).toBeVisible();
  await expect(page.getByRole('button',{name:'Publish this feedback'})).toHaveCount(0);
  await page.getByRole('button',{name:'Hide feedback preview'}).click();
  await expect(page.getByText(/DEMO FIXTURE/)).toBeVisible();
  await page.getByRole('button',{name:'1 finding at line 3'}).click();
  await expect(page.locator('.focused-line')).toContainText('Runtime');
  await expect(page.getByText('Use an allowlist of operations', {exact:false})).toBeVisible();
  if(process.env.CODEGUARD_SCREENSHOTS_DIR)await page.screenshot({path:path.join(process.env.CODEGUARD_SCREENSHOTS_DIR,'review.png'),fullPage:true});
  await page.getByRole('combobox',{name:'Filter findings by severity'}).selectOption('LOW');
  await expect(page.getByRole('button',{name:'Avoid uncontrolled stack trace output'})).toBeVisible();
  await expect(page.getByRole('button',{name:'Review process execution'})).toHaveCount(0);
  await page.getByRole('navigation',{name:'Main navigation'}).getByRole('link',{name:'Analytics'}).click();
  await expect(page.getByRole('heading',{name:'AI usage & estimates'})).toBeVisible();
  await expect(page.locator('.usage-cost')).toContainText('$0.00');
  await page.getByRole('button',{name:'Sign out',exact:true}).click();
  await expect(page).toHaveURL(/\/signin$/);
  await page.goto('/app');await expect(page).toHaveURL(/\/signin$/);
  expect(errors).toEqual([]);
});
test('repository failures offer a retry and mobile layout stays within the viewport',async({page})=>{
  await page.route('**/api/repositories?**',route=>route.fulfill({status:503,contentType:'application/problem+json',body:JSON.stringify({detail:'Temporarily unavailable'})}));
  await page.getByRole('navigation',{name:'Main navigation'}).getByRole('link',{name:'Repositories'}).click();
  await expect(page.getByRole('alert')).toContainText('Temporarily unavailable');
  await page.unroute('**/api/repositories?**');await page.getByRole('button',{name:'Try again'}).click();
  await expect(page.getByRole('link',{name:'demo/payment-service',exact:true})).toBeVisible();
  await page.setViewportSize({width:390,height:844});
  await page.getByRole('navigation',{name:'Main navigation'}).getByRole('link',{name:'Overview'}).click();
  await expect(page.getByRole('heading',{name:'Workspace overview'})).toBeVisible();
  expect(await page.evaluate(()=>document.documentElement.scrollWidth)).toBeLessThanOrEqual(390);
  if(process.env.CODEGUARD_SCREENSHOTS_DIR)await page.screenshot({path:path.join(process.env.CODEGUARD_SCREENSHOTS_DIR,'mobile.png'),fullPage:true});
  await page.getByRole('button',{name:'Sign out of workspace'}).click();
  await expect(page).toHaveURL(/\/signin$/);
});

test('authenticated API documentation loads and anonymous schema access is denied',async({page})=>{
  await page.goto('/api/docs');
  await expect(page.locator('.swagger-ui').first()).toBeVisible();
  await expect(page.locator('.opblock-summary-path').filter({hasText:'/api/repositories'}).first()).toBeVisible();
  const schema=await page.request.get('/v3/api-docs');
  expect(schema.status()).toBe(200);
  const document=await schema.json();
  expect(document.paths['/api/reviews/{id}/publication']).toBeDefined();
  await page.goto('/app');
  await page.getByRole('button',{name:'Sign out',exact:true}).click();
  await expect(page).toHaveURL(/\/signin$/);
  expect((await page.request.get('/v3/api-docs')).status()).toBe(401);
});

export default async function run(page, ui) {
  await page.goto('http://localhost:8080/resume.html', { waitUntil: 'domcontentloaded', timeout: 30000 });
  await page.waitForTimeout(2000);
  
  const fileInput = await page.locator('#resumeFileInput');
  await fileInput.setInputFiles('E:/AI Agent/agent-platform/Samiuddin_IT_B.Tech.docx');
  await page.waitForTimeout(2000);
  
  const uploadBtn = await page.locator('#uploadBtn');
  await uploadBtn.click();
  console.log('Upload clicked');
  
  try {
    await page.waitForSelector('#resumeProfileSection:not([hidden])', { timeout: 660000 });
    console.log('Profile section visible');
  } catch (e) {
    console.log('Timeout waiting for profile section');
    return { error: 'Timeout waiting for profile' };
  }
  
  const candidateId = await page.evaluate(() => localStorage.getItem('agentplatform:candidateId'));
  const candidateName = await page.evaluate(() => localStorage.getItem('agentplatform:candidateName'));
  console.log('Candidate ID:', candidateId);
  console.log('Candidate Name:', candidateName);
  
  return { success: true, candidateId, candidateName };
}
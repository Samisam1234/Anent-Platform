export default async function run(page, ui) {
  // First, navigate to matches.html and wait for profile to load
  await page.goto('http://localhost:8080/matches.html', { waitUntil: 'domcontentloaded', timeout: 30000 });
  await page.waitForTimeout(3000);
  
  // Check if profile is loaded
  const profileBadge = await page.$('#profileStatusText');
  const profileText = await profileBadge.textContent();
  console.log('Profile status:', profileText);
  
  // Check if we have a candidate profile loaded
  const candidateId = await page.evaluate(() => localStorage.getItem('agentplatform:candidateId'));
  console.log('Candidate ID in localStorage:', candidateId);
  
  if (!candidateId) {
    console.log('No candidate profile found - need to upload resume first');
    return { error: 'No candidate profile found' };
  }
  
  // Find a match card and click Career Analysis button
  await page.waitForSelector('.btn-career-agent', { timeout: 10000 });
  
  // Click the first Career Analysis button
  const careerAnalysisBtn = await page.$('.btn-career-agent');
  if (!careerAnalysisBtn) {
    console.log('No Career Analysis button found');
    return { error: 'No Career Analysis button found' };
  }
  
  await careerAnalysisBtn.click();
  await page.waitForTimeout(2000);
  
  // Check if the modal opened
  const modalOpen1 = await page.evaluate(() => {
    const overlay = document.querySelector('.ms-overlay');
    return overlay && !overlay.hidden;
  });
  
  console.log('Modal opened:', modalOpen1);
  
  if (!modalOpen1) {
    const snap = await ui.snapshot({full: true});
    console.log('Modal did not open. Snapshot:', snap);
    return { error: 'Modal did not open' };
  }
  
  // Check if job title and company are displayed in the advisor modal
  const jobTitleEl = await page.$('#advisorReviewJobTitle');
  const companyEl = await page.$('#advisorReviewCompany');
  
  const jobTitle = jobTitleEl ? await jobTitleEl.textContent() : 'NOT FOUND';
  const company = companyEl ? await companyEl.textContent() : 'NOT FOUND';
  
  console.log('Job Title in modal:', jobTitle);
  console.log('Company in modal:', company);
  
  // Check for duplicate DOM IDs
  const advisorReviewStrengths = await page.$$('#advisorReviewStrengths');
  const advisorReviewDetails = await page.$$('#advisorReviewDetails');
  const advisorReviewConcerns = await page.$$('#advisorReviewConcerns');
  const advisorReviewActions = await page.$$('#advisorReviewActions');
  
  console.log('advisorReviewStrengths count:', advisorReviewStrengths.length);
  console.log('advisorReviewDetails count:', advisorReviewDetails.length);
  console.log('advisorReviewConcerns count:', advisorReviewConcerns.length);
  console.log('advisorReviewActions count:', advisorReviewActions.length);
  
  // Close the modal
  await page.keyboard.press('Escape');
  await page.waitForTimeout(500);
  
  // Now test Prepared Application modal
  console.log('\n--- Testing Prepared Application Modal ---');
  
  const prepareBtn = await page.$('.application-prepare-btn');
  if (!prepareBtn) {
    console.log('No Prepare Application button found');
    return { error: 'No Prepare Application button found' };
  }
  
  await prepareBtn.click();
  await page.waitForTimeout(2000);
  
  // Check if prepReviewOverlay exists
  const prepOverlay = await page.$('#prepReviewOverlay');
  console.log('prepReviewOverlay exists:', !!prepOverlay);
  
  if (prepOverlay) {
    const isHidden = await prepOverlay.evaluate(el => el.hidden);
    console.log('prepReviewOverlay hidden:', isHidden);
  }
  
  // Check if modal opened
  const modalOpen2 = await page.evaluate(() => {
    const overlay = document.querySelector('.ms-overlay');
    return overlay && !overlay.hidden;
  });
  
  console.log('Prepared Application modal opened:', modalOpen2);
  
  if (modalOpen2) {
    const snap = await ui.snapshot({full: true});
    console.log('Prepared Application modal snapshot:', snap);
  }
  
  return {
    advisorModalOpened: modalOpen1,
    jobTitle: jobTitle,
    company: company,
    duplicateIds: {
      advisorReviewStrengths: advisorReviewStrengths.length,
      advisorReviewDetails: advisorReviewDetails.length,
      advisorReviewConcerns: advisorReviewConcerns.length,
      advisorReviewActions: advisorReviewActions.length
    }
  };
}
export default async function run(page, ui) {
  // Upload resume
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
  
  // Navigate to matches page
  await page.goto('http://localhost:8080/matches.html', { waitUntil: 'domcontentloaded', timeout: 30000 });
  
  // Wait for initial match to complete (loading to hide) - longer timeout for cold start
  await page.waitForFunction(() => {
    const loading = document.getElementById('matchesLoading');
    return loading && loading.hidden;
  }, { timeout: 300000 });
  await page.waitForTimeout(2000);
  
  // Check profile status
  const profileStatus = await page.$eval('#profileStatusText', el => el.textContent);
  console.log('Profile status on matches page:', profileStatus);
  
  // Check match controls
  const matchControlsHidden = await page.$eval('#matchControls', el => el.hidden);
  console.log('Match controls hidden:', matchControlsHidden);
  
  // Check minScoreSelect value
  const minScoreValue = await page.$eval('#matchesMinScoreSelect', el => el.value);
  console.log('Min score select value:', minScoreValue);
  
  // Check for matches after initial load
  let cards = await page.$$('.match-card');
  console.log('Number of match cards after initial load:', cards.length);
  
  // Check if empty state is shown
  const emptyHidden = await page.$eval('#matchesEmpty', el => el.hidden).catch(() => true);
  console.log('Empty state hidden:', emptyHidden);
  
  // Check results count
  const resultsCount = await page.$eval('#matchesResultsCount', el => el.textContent);
  console.log('Results count:', resultsCount);
  
  // Check source banner
  const sourceBannerHidden = await page.$eval('#matchesSourceBanner', el => el.hidden).catch(() => true);
  console.log('Source banner hidden:', sourceBannerHidden);
  
  // If no matches, try clicking find matches again
  if (cards.length === 0) {
    console.log('No matches found, clicking Find Matches button...');
    // Wait for button to be enabled
    await page.waitForFunction(() => {
      const btn = document.getElementById('findMatchesBtn');
      return btn && !btn.disabled;
    }, { timeout: 120000 });
    await page.click('#findMatchesBtn');
    await page.waitForTimeout(30000);
    
    cards = await page.$$('.match-card');
    console.log('Number of match cards after retry:', cards.length);
  }
  
  if (cards.length > 0) {
    // Test Application Advisor modal
    const advisorBtn = await page.$('.application-advisor-btn');
    if (advisorBtn) {
      await advisorBtn.click();
      await page.waitForTimeout(2000);
      
      const modalOpen = await page.$eval('.ms-overlay', el => !el.hidden).catch(() => false);
      console.log('Advisor modal opened:', modalOpen);
      
      if (modalOpen) {
        const jobTitle = await page.$eval('#advisorReviewJobTitle', el => el.textContent).catch(() => 'NOT FOUND');
        const company = await page.$eval('#advisorReviewCompany', el => el.textContent).catch(() => 'NOT FOUND');
        console.log('Advisor Job Title:', jobTitle);
        console.log('Advisor Company:', company);
        
        const strengthsCount = await page.$$eval('#advisorReviewStrengths', els => els.length);
        const detailsCount = await page.$$eval('#advisorReviewDetails', els => els.length);
        const concernsCount = await page.$$eval('#advisorReviewConcerns', els => els.length);
        const actionsCount = await page.$$eval('#advisorReviewActions', els => els.length);
        
        console.log('Duplicate IDs - Strengths:', strengthsCount, 'Details:', detailsCount, 'Concerns:', concernsCount, 'Actions:', actionsCount);
        
        await page.keyboard.press('Escape');
        await page.waitForTimeout(500);
      }
      
      // Test Prepared Application modal
      const prepareBtn = await page.$('.application-prepare-btn');
      if (prepareBtn) {
        await prepareBtn.click();
        await page.waitForTimeout(2000);
        
        const modalOpen2 = await page.$eval('.ms-overlay', el => !el.hidden).catch(() => false);
        console.log('Prepared Application modal opened:', modalOpen2);
        
        if (modalOpen2) {
          const snap = await ui.snapshot({full: true});
          console.log('Prepared Application modal opened successfully');
        }
        
        await page.keyboard.press('Escape');
        await page.waitForTimeout(500);
      }
    }
    
    return { success: true };
  }
  
  return { error: 'No matches found' };
}
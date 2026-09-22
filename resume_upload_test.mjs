export default async function run(page, ui) {
  // Navigate to resume page
  await page.goto('http://localhost:8080/resume.html', { waitUntil: 'domcontentloaded', timeout: 30000 });
  await page.waitForTimeout(2000);

  // Check initial state
  const initialSnapshot = await ui.snapshot();
  console.log('Initial page snapshot:', initialSnapshot);

  // Upload the resume file - use the file input directly
  const fileInput = await page.locator('#resumeFileInput');
  await fileInput.setInputFiles('E:/AI Agent/agent-platform/Samiuddin_IT_B.Tech.docx');
  await page.waitForTimeout(2000);

  // Get fresh snapshot after file selection
  const fileSelectedSnapshot = await ui.snapshot();
  console.log('After file selection:', fileSelectedSnapshot);

  // Find and click upload button
  const uploadBtn = page.locator('#uploadBtn');
  await uploadBtn.click();
  console.log('Upload clicked');

  // Wait for analyzing to complete (up to 11 minutes for cold start)
  try {
    await page.waitForSelector('#resumeProfileSection:not([hidden])', { timeout: 660000 });
    console.log('Profile section visible');
  } catch (e) {
    console.log('Timeout waiting for profile section');
    const snap = await ui.snapshot({full: true});
    console.log('Snapshot at timeout:', snap);
    return { error: 'Timeout waiting for profile' };
  }

  // Get the profile details
  const profileSnapshot = await ui.snapshot({full: true});
  console.log('Profile snapshot:', profileSnapshot);

  // Extract key profile data
  const profileData = await page.evaluate(() => {
    const result = {};
    result.name = document.querySelector('.resume-identity-name')?.innerText || '';
    const contactItems = document.querySelectorAll('.resume-contact-item');
    result.email = contactItems[0]?.innerText || '';
    result.phone = contactItems[1]?.innerText || '';
    result.location = contactItems[2]?.innerText || '';
    
    const lists = document.querySelectorAll('.resume-profile-list');
    result.preferredRoles = Array.from(lists[0]?.querySelectorAll('li') || []).map(li => li.innerText);
    result.skills = Array.from(lists[1]?.querySelectorAll('li') || []).map(li => li.innerText);
    result.education = Array.from(lists[2]?.querySelectorAll('li') || []).map(li => li.innerText);
    result.experience = Array.from(lists[3]?.querySelectorAll('li') || []).map(li => li.innerText);
    result.projects = Array.from(lists[4]?.querySelectorAll('li') || []).map(li => li.innerText);
    result.certifications = Array.from(lists[5]?.querySelectorAll('li') || []).map(li => li.innerText);
    result.hardwareSkills = Array.from(lists[6]?.querySelectorAll('li') || []).map(li => li.innerText);
    result.softwareSkills = Array.from(lists[7]?.querySelectorAll('li') || []).map(li => li.innerText);
    result.internships = Array.from(lists[8]?.querySelectorAll('li') || []).map(li => li.innerText);
    return result;
  });

  console.log('Profile data:', JSON.stringify(profileData, null, 2));

  return { profileData, snapshot: profileSnapshot };
}